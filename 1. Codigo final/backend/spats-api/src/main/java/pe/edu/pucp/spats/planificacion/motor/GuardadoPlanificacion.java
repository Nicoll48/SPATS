package pe.edu.pucp.spats.planificacion.motor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.pucp.spats.auditoria.entity.TipoOperacion;
import pe.edu.pucp.spats.auditoria.service.BitacoraService;
import pe.edu.pucp.spats.comun.error.ConflictoException;
import pe.edu.pucp.spats.maquina.entity.MaquinaTintoreria;
import pe.edu.pucp.spats.partida.entity.Partida;
import pe.edu.pucp.spats.pedido.entity.Articulo;
import pe.edu.pucp.spats.planificacion.entity.BolsaArticulo;
import pe.edu.pucp.spats.planificacion.entity.Enumeraciones.EstadoPlanificacion;
import pe.edu.pucp.spats.planificacion.entity.Enumeraciones.TipoGeneracion;
import pe.edu.pucp.spats.planificacion.entity.PlanificacionMaquina;
import pe.edu.pucp.spats.planificacion.entity.PlanificacionPartida;
import pe.edu.pucp.spats.planificacion.entity.PlanificacionSemanal;
import pe.edu.pucp.spats.planificacion.repository.BolsaArticuloRepository;
import pe.edu.pucp.spats.planificacion.repository.PlanificacionMaquinaRepository;
import pe.edu.pucp.spats.planificacion.repository.PlanificacionPartidaRepository;
import pe.edu.pucp.spats.planificacion.repository.PlanificacionSemanalRepository;
import pe.edu.pucp.spats.tejeduria.entity.EstadoProyeccion;
import pe.edu.pucp.spats.tejeduria.entity.ProyeccionTejeduria;
import pe.edu.pucp.spats.tejeduria.entity.Semana;
import pe.edu.pucp.spats.tejeduria.service.ProyeccionService;
import spats.algoritmo.salida.Indicadores;

/**
 * Último paso de la generación: guarda todo el resultado en una sola transacción (RN-19). Si algo falla,
 * no queda ninguna versión parcial y la vigente sigue como estaba. Si la versión nueva pasa a vigente, la
 * anterior deja de serlo en la misma transacción (RN-22, D-14).
 */
@Component
public class GuardadoPlanificacion {

    private final PlanificacionSemanalRepository planificaciones;
    private final PlanificacionPartidaRepository partidas;
    private final PlanificacionMaquinaRepository maquinas;
    private final BolsaArticuloRepository bolsas;
    private final ProyeccionService proyecciones;
    private final EntityManager em;
    private final BitacoraService bitacora;
    private final PropuestasPendientes propuestas;
    private final ReglasVigencia reglasVigencia;
    private final Clock reloj;
    private final pe.edu.pucp.spats.planificacion.repository.PlanificacionIncidenciaRepository planificacionIncidencias;
    private final pe.edu.pucp.spats.incidencia.repository.IncidenciaRepository incidencias;
    private final pe.edu.pucp.spats.pedido.service.PedidoService pedidos;

    private final pe.edu.pucp.spats.planificacion.recomendacion.RecomendacionesTejidoService recomendaciones;

    public GuardadoPlanificacion(PlanificacionSemanalRepository planificaciones, PlanificacionPartidaRepository partidas,
                                 PlanificacionMaquinaRepository maquinas, BolsaArticuloRepository bolsas,
                                 ProyeccionService proyecciones, EntityManager em, BitacoraService bitacora,
                                 PropuestasPendientes propuestas, ReglasVigencia reglasVigencia, Clock reloj,
                                 pe.edu.pucp.spats.planificacion.repository.PlanificacionIncidenciaRepository planificacionIncidencias,
                                 pe.edu.pucp.spats.incidencia.repository.IncidenciaRepository incidencias,
                                 pe.edu.pucp.spats.pedido.service.PedidoService pedidos,
            pe.edu.pucp.spats.planificacion.recomendacion.RecomendacionesTejidoService recomendaciones) {
        this.recomendaciones = recomendaciones;
        this.planificacionIncidencias = planificacionIncidencias;
        this.incidencias = incidencias;
        this.pedidos = pedidos;
        this.planificaciones = planificaciones;
        this.partidas = partidas;
        this.maquinas = maquinas;
        this.bolsas = bolsas;
        this.proyecciones = proyecciones;
        this.em = em;
        this.bitacora = bitacora;
        this.propuestas = propuestas;
        this.reglasVigencia = reglasVigencia;
        this.reloj = reloj;
    }

    /**
     * Guarda la generación (D-52):
     * <ul>
     *   <li>Si la semana no tiene vigente, o se generó con la versión de la proyección de la vigente, la nueva pasa a
     *       vigente y la anterior queda REEMPLAZADA (D-14); su versión de la proyección queda VIGENTE.</li>
     *   <li>Si se generó con otra versión de la proyección, queda CANDIDATA: la vigente no cambia. La candidata
     *       anterior de esa versión queda REEMPLAZADA (cada versión tiene a lo más una planificación en uso).</li>
     * </ul>
     */
    @Transactional
    public PlanificacionSemanal guardar(EntradaPlanificacion entrada, ResultadoTraducido r) {
        exigirDatosSinCambios(entrada);
        propuestas.eliminarVencidas(entrada.idSemana());
        PlanificacionSemanal vigente = planificaciones.findBySemanaIdAndEsVigenteTrue(entrada.idSemana()).orElse(null);
        boolean candidata = vigente != null && !vigente.getProyeccion().getId().equals(entrada.idProyeccion());
        ProyeccionTejeduria proyeccion = proyecciones.buscar(entrada.idProyeccion());
        for (PlanificacionSemanal anterior : planificaciones.candidatasDe(entrada.idProyeccion())) {
            anterior.quitarVigencia(EstadoPlanificacion.REEMPLAZADA);
            bitacora.registrarCambio("planificacion_semanal", anterior.getId(), "estado", EstadoPlanificacion.CANDIDATA,
                    EstadoPlanificacion.REEMPLAZADA);
        }
        if (vigente != null && !candidata) {
            vigente.quitarVigencia(EstadoPlanificacion.REEMPLAZADA);
            bitacora.registrarCambio("planificacion_semanal", vigente.getId(), "es_vigente", true, false);
            planificaciones.flush();   // libera uq_vigente_por_semana antes de insertar la nueva vigente
        }

        Indicadores ind = r.indicadores();
        Semana semana = em.getReference(Semana.class, entrada.idSemana());
        int numero = planificaciones.ultimaVersion(entrada.idSemana()) + 1;
        LocalDateTime ahora = LocalDateTime.now(reloj);
        PlanificacionSemanal plan = planificaciones.save(candidata
                ? PlanificacionSemanal.candidata(semana, proyeccion, numero, ahora, decimal(ind.funcionObjetivo()),
                        decimal(ind.desbalanceSemanal()), decimal(ind.desbalanceDiario()), ind.lavados(), ind.evaluaciones(),
                        ind.tiempoMs())
                : new PlanificacionSemanal(semana, proyeccion, numero, ahora, TipoGeneracion.INICIAL,
                        decimal(ind.funcionObjetivo()), decimal(ind.desbalanceSemanal()), decimal(ind.desbalanceDiario()),
                        ind.lavados(), ind.evaluaciones(), ind.tiempoMs()));

        guardarFilas(plan, entrada, r);
        if (!candidata) {
            proyecciones.hacerVigente(proyeccion);
            reglasVigencia.marcarSemanaSiguiente(entrada.lunes());
            considerarIncidencias(plan, entrada.idsIncidencias());
        }
        // RF30 (D-56): tejido adicional de esta versión, con su propio resultado y en la misma transacción
        recomendaciones.calcular(plan);
        bitacora.registrar(TipoOperacion.GENERAR, "planificacion_semanal", plan.getId(),
                "Semana " + entrada.lunes() + ", versión " + plan.getNumeroVersion() + " con la proyección V"
                        + proyeccion.getVersion() + (candidata ? " (no vigente; la vigente sigue siendo la versión "
                        + vigente.getNumeroVersion() + ")" : ""));
        return plan;
    }

    /**
     * Incidencias pendientes aplicadas al generar una semana que aún no empieza: se vinculan a la nueva vigente
     * (RF54), pasan a CONSIDERADA y cada cambio de prioridad actualiza la fecha de entrega del pedido (D-13b).
     */
    private void considerarIncidencias(PlanificacionSemanal plan, List<Integer> ids) {
        for (Integer id : ids) {
            var i = incidencias.findById(id).orElse(null);
            if (i == null) continue;
            boolean pendiente = i.getEstado() == pe.edu.pucp.spats.incidencia.entity.EstadoIncidencia.PENDIENTE;
            if (!pendiente && i.getEstado() != pe.edu.pucp.spats.incidencia.entity.EstadoIncidencia.CONSIDERADA) continue;
            planificacionIncidencias.save(new pe.edu.pucp.spats.planificacion.entity.PlanificacionIncidencia(plan, i));
            if (!pendiente) continue;   // ya considerada al generar antes la semana: solo se vincula a la versión nueva (D-57)
            bitacora.registrarCambio("incidencia", i.getId(), "estado", i.getEstado(), "CONSIDERADA");
            i.considerar();
            if (i.getTipoIncidencia() == pe.edu.pucp.spats.incidencia.entity.TipoIncidencia.CAMBIO_PRIORIDAD
                    && !java.util.Objects.equals(i.getPedido().getFechaEntrega(), i.getNuevaFechaEntrega()))
                pedidos.cambiarFechaEntregaPorPrioridad(i.getPedido(), i.getNuevaFechaEntrega());
        }
    }

    /** Filas de una versión: partidas (ubicadas y Bloqueadas), máquinas y bolsas. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void guardarFilas(PlanificacionSemanal plan, EntradaPlanificacion entrada, ResultadoTraducido r) {
        partidas.saveAll(r.partidas().stream().map(f -> PlanificacionPartida.ubicada(plan,
                partida(entrada, f.numeroPartida()), maquina(entrada, f.codigoMaquina()), f.diaAbastecimiento(),
                f.ordenSecuencia(), f.clasificacion(), f.requiereLavado(), f.fechaDisponible())).toList());
        partidas.saveAll(r.bloqueadas().stream()
                .map(numero -> PlanificacionPartida.bloqueada(plan, partida(entrada, numero), r.motivosBloqueo().get(numero))).toList());
        maquinas.saveAll(r.maquinas().stream().map(m -> new PlanificacionMaquina(plan, maquina(entrada, m.codigoMaquina()),
                m.objetivoAplicado(), m.minimoDiario(), m.maximoDiario(), m.asignadas(), m.sobrantes(), m.diferencia(),
                m.lavados())).toList());
        bolsas.saveAll(r.bolsas().stream().map(b -> new BolsaArticulo(plan, articulo(entrada, b.codigoArticulo()),
                b.fecha(), b.saldoInicial(), b.ingresados(), b.devueltos(), b.consumidos(), b.saldoFinal())).toList());
    }

    /**
     * La versión vigente no debe haber cambiado desde la lectura de los datos, y la versión de la proyección usada
     * debe seguir sin eliminar.
     */
    void exigirDatosSinCambios(EntradaPlanificacion entrada) {
        Integer vigenteActual = planificaciones.findBySemanaIdAndEsVigenteTrue(entrada.idSemana())
                .map(PlanificacionSemanal::getId).orElse(null);
        boolean proyeccionEliminada = proyecciones.buscar(entrada.idProyeccion()).getEstado() == EstadoProyeccion.ELIMINADA;
        if (!Objects.equals(vigenteActual, entrada.idVigenteAnterior()) || proyeccionEliminada)
            throw new ConflictoException("DATOS_CAMBIARON",
                    "La planificación o la proyección de la semana cambiaron mientras se generaba. Vuelve a intentarlo.");
    }

    private Partida partida(EntradaPlanificacion e, String numero) {
        return em.getReference(Partida.class, requerido(e.idPartidaPorNumero().get(numero), "partida", numero));
    }

    private MaquinaTintoreria maquina(EntradaPlanificacion e, String codigo) {
        return em.getReference(MaquinaTintoreria.class, requerido(e.idMaquinaPorCodigo().get(codigo), "máquina", codigo));
    }

    private Articulo articulo(EntradaPlanificacion e, String codigo) {
        return em.getReference(Articulo.class, requerido(e.idArticuloPorCodigo().get(codigo), "artículo", codigo));
    }

    private static Integer requerido(Integer id, String que, String codigo) {
        if (id == null) throw new pe.edu.pucp.spats.comun.error.FalloPlanificacionException(
                "El resultado del algoritmo menciona un dato que no se envió.", List.of(que + " " + codigo));
        return id;
    }

    private static BigDecimal decimal(double valor) {
        return BigDecimal.valueOf(valor).setScale(3, RoundingMode.HALF_UP);
    }
}
