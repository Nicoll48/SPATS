package pe.edu.pucp.spats.planificacion.motor;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.pucp.spats.comun.error.ConflictoException;
import pe.edu.pucp.spats.comun.error.ReglaNegocioException;
import pe.edu.pucp.spats.comun.fecha.SemanaPlanificacion;
import pe.edu.pucp.spats.incidencia.entity.EstadoIncidencia;
import pe.edu.pucp.spats.incidencia.entity.Incidencia;
import pe.edu.pucp.spats.incidencia.entity.TipoIncidencia;
import pe.edu.pucp.spats.incidencia.repository.IncidenciaRepository;
import pe.edu.pucp.spats.planificacion.entity.BolsaArticulo;
import pe.edu.pucp.spats.planificacion.entity.PlanificacionPartida;
import pe.edu.pucp.spats.planificacion.entity.PlanificacionSemanal;
import pe.edu.pucp.spats.planificacion.motor.ArmadoReplanificacion.FilaVigente;
import pe.edu.pucp.spats.planificacion.motor.ArmadoReplanificacion.IncidenciaAplicada;
import pe.edu.pucp.spats.planificacion.motor.ResultadoTraducido.FilaBolsa;
import pe.edu.pucp.spats.planificacion.repository.BolsaArticuloRepository;
import pe.edu.pucp.spats.planificacion.repository.PlanificacionPartidaRepository;
import pe.edu.pucp.spats.planificacion.repository.PlanificacionSemanalRepository;
import pe.edu.pucp.spats.tejeduria.entity.Semana;
import pe.edu.pucp.spats.tejeduria.service.SemanaService;

/**
 * Primer paso de la re-planificación (transacción de solo lectura): lee la vigente ABIERTA de la semana en curso,
 * sus filas, su bolsa y las incidencias seleccionadas, y arma la entrada con {@link ArmadoReplanificacion}.
 * D es la fecha del servidor.
 */
@Component
public class LectorDatosReplanificacion {

    private final SemanaService semanas;
    private final PlanificacionSemanalRepository planificaciones;
    private final PlanificacionPartidaRepository partidas;
    private final BolsaArticuloRepository bolsas;
    private final IncidenciaRepository incidencias;
    private final LectorDatosPlanificacion lector;
    private final PropuestasPendientes propuestas;
    private final Clock reloj;

    public LectorDatosReplanificacion(SemanaService semanas, PlanificacionSemanalRepository planificaciones,
                                      PlanificacionPartidaRepository partidas, BolsaArticuloRepository bolsas,
                                      IncidenciaRepository incidencias, LectorDatosPlanificacion lector,
                                      PropuestasPendientes propuestas, Clock reloj) {
        this.semanas = semanas;
        this.planificaciones = planificaciones;
        this.partidas = partidas;
        this.bolsas = bolsas;
        this.incidencias = incidencias;
        this.lector = lector;
        this.propuestas = propuestas;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public EntradaReplanificacion leer(LocalDate lunes, List<Integer> idsIncidencias, Set<String> enMaquina) {
        return leer(lunes, idsIncidencias, enMaquina, true, null, false);
    }

    /**
     * Re-planificación desde hoy para deshacer una incidencia anulada (D-57): no exige incidencias; se vuelven a aplicar
     * las de máquina y de complemento ya consideradas que siguen teniendo efecto desde hoy (las anuladas no) y las
     * pendientes que se indiquen.
     */
    @Transactional(readOnly = true)
    public EntradaReplanificacion leerPorAnulacion(LocalDate lunes, List<Integer> idsIncidencias, Set<String> enMaquina) {
        return leer(lunes, idsIncidencias, enMaquina, false, null, true);
    }

    /**
     * Re-planificación desde hoy para usar otra versión de la proyección como vigente (D-52): lo anterior a hoy se
     * conserva de la vigente y desde hoy se decide con la proyección de esa versión. No exige incidencias. Las
     * incidencias de máquina y de complemento ya consideradas en la semana que siguen teniendo efecto desde hoy se
     * vuelven a aplicar; las PENDIENTE no (siguen pendientes).
     */
    @Transactional(readOnly = true)
    public EntradaReplanificacion leerCambioVersion(LocalDate lunes, Integer idProyeccion, Set<String> enMaquina) {
        return leer(lunes, List.of(), enMaquina, false, idProyeccion, false);
    }

    private EntradaReplanificacion leer(LocalDate lunes, List<Integer> idsIncidencias, Set<String> enMaquina,
                                        boolean exigirIncidencias, Integer idProyeccion, boolean heredar) {
        LocalDate hoy = LocalDate.now(reloj);
        Semana semana = semanas.buscar(lunes).orElseThrow(ReglasReplanificacion::sinVigente);
        PlanificacionSemanal vigente = planificaciones.findBySemanaIdAndEsVigenteTrue(semana.getId())
                .orElseThrow(ReglasReplanificacion::sinVigente);
        ReglasReplanificacion.exigirPermitida(vigente, new SemanaPlanificacion(lunes), hoy);
        propuestas.exigirSinPendiente(semana.getId(), idProyeccion == null ? "re-planificar otra vez" : "usar otra versión como vigente");
        if (idsIncidencias.isEmpty() && exigirIncidencias)
            throw new ReglaNegocioException("SIN_INCIDENCIAS", "Selecciona al menos una incidencia pendiente para re-planificar.");

        List<IncidenciaAplicada> aplicadas = new ArrayList<>();
        if (idProyeccion != null || heredar) aplicadas.addAll(heredadas(semana.getId(), hoy));
        for (Integer id : new LinkedHashSet<>(idsIncidencias)) {
            Incidencia i = incidencias.findById(id).orElseThrow(() -> new ReglaNegocioException("INCIDENCIA_NO_ENCONTRADA",
                    "No existe la incidencia " + id + "."));
            if (!i.getPlanificacionOrigen().getSemana().getId().equals(semana.getId()))
                throw new ReglaNegocioException("INCIDENCIA_DE_OTRA_SEMANA", "La incidencia " + id + " es de otra semana.");
            if (i.getEstado() != EstadoIncidencia.PENDIENTE)
                throw new ConflictoException("INCIDENCIA_YA_CONSIDERADA", "La incidencia " + id + " ya fue considerada en una re-planificación.");
            aplicadas.add(new IncidenciaAplicada(i.getId(), i.getTipoIncidencia(),
                    i.getMaquina() == null ? null : i.getMaquina().getCodigo(),
                    i.getPartida() == null ? null : i.getPartida().getNumeroPartida(),
                    i.getPedido() == null ? null : i.getPedido().getNumeroOp(),
                    i.getFechaHoraDisponibilidad(), i.getNuevaFechaEntrega()));
        }

        // D-52: desde D se usa la proyección de la vigente, o la de la versión que pasará a ser vigente
        EntradaPlanificacion base = lector.leerParaReplanificar(lunes,
                idProyeccion != null ? idProyeccion : vigente.getProyeccion().getId());
        if (!vigente.getId().equals(base.idVigenteAnterior()))
            throw new ConflictoException("DATOS_CAMBIARON", "La planificación vigente cambió mientras se leía. Vuelve a intentarlo.");
        List<FilaVigente> filas = new ArrayList<>();
        for (PlanificacionPartida f : partidas.deLaPlanificacion(vigente.getId()))
            filas.add(new FilaVigente(f.getPartida().getNumeroPartida(), f.getPartida().getPedido().getNumeroOp(),
                    f.getMaquina() == null ? null : f.getMaquina().getCodigo(), f.getDiaAbastecimiento(),
                    f.getClasificacionResultado(), f.getOrdenSecuencia() == null ? 0 : f.getOrdenSecuencia(), f.getFechaDisponible()));
        Map<String, Integer> idArticulo = new HashMap<>(base.idArticuloPorCodigo());
        List<FilaBolsa> bolsa = new ArrayList<>();
        for (BolsaArticulo b : bolsas.deLaPlanificacion(vigente.getId())) {
            idArticulo.put(b.getArticulo().getCodigo(), b.getArticulo().getId());
            bolsa.add(new FilaBolsa(b.getArticulo().getCodigo(), b.getFecha(), b.getKgSaldoInicial(), b.getKgIngresados(),
                    b.getKgDevueltos(), b.getKgConsumidos(), b.getKgSaldoFinal()));
        }
        ArmadoReplanificacion.Armado armado = ArmadoReplanificacion.armar(base.datos(), hoy, filas, bolsa, aplicadas, enMaquina);
        EntradaPlanificacion entrada = new EntradaPlanificacion(base.idSemana(), base.lunes(), base.idProyeccion(),
                base.idVigenteAnterior(), armado.datos(), base.idMaquinaPorCodigo(), base.idPartidaPorNumero(), idArticulo,
                base.advertencias());
        List<String> advertencias = new ArrayList<>(base.advertencias());
        advertencias.addAll(armado.advertencias());
        return new EntradaReplanificacion(entrada, hoy, armado.datos(), armado.alternativaVigente(), bolsa,
                aplicadas.stream().map(IncidenciaAplicada::id).toList(), List.copyOf(advertencias));
    }

    /**
     * Incidencias de máquina y de complemento ya consideradas en la semana cuya fecha prevista de disponibilidad es
     * posterior al inicio de hoy: siguen teniendo efecto desde D (D-52). Los cambios de prioridad no se repiten: ya
     * cambiaron la fecha de entrega del pedido al confirmarse (D-13b).
     */
    private List<IncidenciaAplicada> heredadas(Integer idSemana, LocalDate hoy) {
        return incidencias.deLaSemana(idSemana).stream()
                .filter(i -> i.getEstado() == EstadoIncidencia.CONSIDERADA)
                .filter(i -> i.getTipoIncidencia() == TipoIncidencia.INCIDENCIA_MAQUINA
                        || i.getTipoIncidencia() == TipoIncidencia.PROBLEMA_COMPLEMENTO)
                .filter(i -> i.getFechaHoraDisponibilidad() != null && i.getFechaHoraDisponibilidad().isAfter(hoy.atStartOfDay()))
                .map(i -> new IncidenciaAplicada(i.getId(), i.getTipoIncidencia(),
                        i.getMaquina() == null ? null : i.getMaquina().getCodigo(),
                        i.getPartida() == null ? null : i.getPartida().getNumeroPartida(), null,
                        i.getFechaHoraDisponibilidad(), null, true))
                .toList();
    }
}
