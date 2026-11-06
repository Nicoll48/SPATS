package pe.edu.pucp.spats.planificacion.motor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.pucp.spats.auditoria.entity.TipoOperacion;
import pe.edu.pucp.spats.auditoria.service.BitacoraService;
import pe.edu.pucp.spats.comun.error.ConflictoException;
import pe.edu.pucp.spats.comun.error.RecursoNoEncontradoException;
import pe.edu.pucp.spats.incidencia.entity.Incidencia;
import pe.edu.pucp.spats.incidencia.entity.TipoIncidencia;
import pe.edu.pucp.spats.pedido.service.PedidoService;
import pe.edu.pucp.spats.planificacion.entity.Enumeraciones.EstadoPlanificacion;
import pe.edu.pucp.spats.planificacion.entity.PlanificacionIncidencia;
import pe.edu.pucp.spats.planificacion.entity.PlanificacionSemanal;
import pe.edu.pucp.spats.planificacion.repository.PlanificacionIncidenciaRepository;
import pe.edu.pucp.spats.planificacion.repository.PlanificacionSemanalRepository;
import pe.edu.pucp.spats.tejeduria.entity.ProyeccionTejeduria;
import pe.edu.pucp.spats.tejeduria.entity.Semana;
import spats.algoritmo.salida.Indicadores;

/**
 * Guardado de una re-planificación en tres pasos, cada uno en su propia transacción (HU-18):
 * <ol>
 *   <li>Proponer: versión REPLANIFICACION en estado PROPUESTA, no vigente, con todas sus filas y sus
 *       incidencias en {@code planificacion_incidencia}.</li>
 *   <li>Confirmar: la propuesta pasa a vigente y la anterior a REEMPLAZADA; las incidencias pasan a
 *       CONSIDERADA; cada cambio de prioridad actualiza {@code pedido.fecha_entrega} (D-13b); la vigente
 *       de la semana siguiente queda desactualizada (D-19).</li>
 *   <li>Descartar: la propuesta se elimina con todas sus filas (D-26).</li>
 * </ol>
 */
@Component
public class GuardadoReplanificacion {

    private final PlanificacionSemanalRepository planificaciones;
    private final PlanificacionIncidenciaRepository planificacionIncidencias;
    private final GuardadoPlanificacion guardado;
    private final PropuestasPendientes propuestas;
    private final PedidoService pedidos;
    private final ReglasVigencia reglasVigencia;
    private final EntityManager em;
    private final BitacoraService bitacora;
    private final Clock reloj;

    public GuardadoReplanificacion(PlanificacionSemanalRepository planificaciones,
                                   PlanificacionIncidenciaRepository planificacionIncidencias, GuardadoPlanificacion guardado,
                                   PropuestasPendientes propuestas, PedidoService pedidos, ReglasVigencia reglasVigencia,
                                   EntityManager em, BitacoraService bitacora, Clock reloj) {
        this.planificaciones = planificaciones;
        this.planificacionIncidencias = planificacionIncidencias;
        this.guardado = guardado;
        this.propuestas = propuestas;
        this.pedidos = pedidos;
        this.reglasVigencia = reglasVigencia;
        this.em = em;
        this.bitacora = bitacora;
        this.reloj = reloj;
    }

    /** Resultado de confirmar una propuesta. */
    public record Confirmacion(PlanificacionSemanal plan, int incidenciasConsideradas, int pedidosActualizados) { }

    @Transactional
    public PlanificacionSemanal proponer(EntradaReplanificacion e, ResultadoTraducido r) {
        EntradaPlanificacion entrada = e.entrada();
        guardado.exigirDatosSinCambios(entrada);
        propuestas.eliminarVencidas(entrada.idSemana());
        propuestas.exigirSinPendiente(entrada.idSemana(), "re-planificar otra vez");
        Indicadores ind = r.indicadores();
        PlanificacionSemanal plan = planificaciones.save(PlanificacionSemanal.propuesta(
                em.getReference(Semana.class, entrada.idSemana()),
                em.getReference(ProyeccionTejeduria.class, entrada.idProyeccion()),
                planificaciones.ultimaVersion(entrada.idSemana()) + 1, LocalDateTime.now(reloj),
                decimal(ind.funcionObjetivo()), decimal(ind.desbalanceSemanal()), decimal(ind.desbalanceDiario()),
                ind.lavados(), ind.evaluaciones(), ind.tiempoMs()));
        guardado.guardarFilas(plan, entrada, r);
        planificacionIncidencias.saveAll(e.idsIncidencias().stream()
                .map(id -> new PlanificacionIncidencia(plan, em.getReference(Incidencia.class, id))).toList());
        bitacora.registrar(TipoOperacion.PROPONER, "planificacion_semanal", plan.getId(),
                "Re-planificación de la semana " + entrada.lunes() + " desde " + e.dia() + ", versión "
                        + plan.getNumeroVersion());
        return plan;
    }

    /** Si la propuesta venció (no se confirmó el mismo día), la elimina y devuelve true. */
    @Transactional
    public boolean eliminarSiVencida(Integer id) {
        PlanificacionSemanal p = propuesta(id);
        if (!propuestas.vencida(p)) return false;
        propuestas.eliminar(p, "venció sin confirmarse el mismo día");
        return true;
    }

    @Transactional
    public Confirmacion confirmar(Integer id) {
        PlanificacionSemanal p = propuesta(id);
        PlanificacionSemanal vigente = planificaciones.findBySemanaIdAndEsVigenteTrue(p.getSemana().getId())
                .filter(PlanificacionSemanal::abierta)
                .orElseThrow(() -> new ConflictoException("VIGENTE_CAMBIO",
                        "La planificación vigente de la semana ya no está abierta; descarta la propuesta."));
        vigente.quitarVigencia(EstadoPlanificacion.REEMPLAZADA);
        bitacora.registrarCambio("planificacion_semanal", vigente.getId(), "es_vigente", true, false);
        planificaciones.flush();   // libera uq_vigente_por_semana antes de que la propuesta pase a vigente
        p.confirmar();
        bitacora.registrarCambio("planificacion_semanal", p.getId(), "estado", EstadoPlanificacion.PROPUESTA, EstadoPlanificacion.ABIERTA);
        // La propuesta parte del saldo de la vigente que reemplaza: si esa estaba desactualizada, también lo está
        if (vigente.isStockDesactualizado()) p.marcarStockDesactualizado();
        reglasVigencia.marcarSemanaSiguiente(p.getSemana().getFechaInicio());

        int consideradas = 0, actualizados = 0;
        for (Incidencia i : planificacionIncidencias.incidenciasDe(p.getId())) {
            bitacora.registrarCambio("incidencia", i.getId(), "estado", i.getEstado(), "CONSIDERADA");
            i.considerar();
            consideradas++;
            if (i.getTipoIncidencia() == TipoIncidencia.CAMBIO_PRIORIDAD
                    && !Objects.equals(i.getPedido().getFechaEntrega(), i.getNuevaFechaEntrega())) {
                pedidos.cambiarFechaEntregaPorPrioridad(i.getPedido(), i.getNuevaFechaEntrega());
                actualizados++;
            }
        }
        bitacora.registrar(TipoOperacion.CONFIRMAR, "planificacion_semanal", p.getId(),
                "Re-planificación de la semana " + p.getSemana().getFechaInicio() + ", versión " + p.getNumeroVersion()
                        + ": " + consideradas + " incidencias consideradas, " + actualizados + " pedidos con fecha nueva");
        return new Confirmacion(p, consideradas, actualizados);
    }

    @Transactional
    public void descartar(Integer id) {
        propuestas.eliminar(propuesta(id), "descartada por el planificador");
    }

    private PlanificacionSemanal propuesta(Integer id) {
        PlanificacionSemanal p = planificaciones.findWithSemanaById(id).orElseThrow(() ->
                new RecursoNoEncontradoException("PLANIFICACION_NO_ENCONTRADA", "No existe la versión de planificación indicada."));
        if (!p.propuesta())
            throw new ConflictoException("NO_ES_PROPUESTA", "La versión " + p.getNumeroVersion() + " no es una re-planificación propuesta.");
        return p;
    }

    private static BigDecimal decimal(double valor) {
        return BigDecimal.valueOf(valor).setScale(3, RoundingMode.HALF_UP);
    }


}
