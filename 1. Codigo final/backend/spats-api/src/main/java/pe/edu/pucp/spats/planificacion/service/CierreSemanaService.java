package pe.edu.pucp.spats.planificacion.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.pucp.spats.auditoria.entity.TipoOperacion;
import pe.edu.pucp.spats.auditoria.service.BitacoraService;
import pe.edu.pucp.spats.comun.error.ConflictoException;
import pe.edu.pucp.spats.comun.error.RecursoNoEncontradoException;
import pe.edu.pucp.spats.comun.error.ReglaNegocioException;
import pe.edu.pucp.spats.partida.entity.EstadoPartida;
import pe.edu.pucp.spats.partida.entity.Partida;
import pe.edu.pucp.spats.partida.service.PartidaService;
import pe.edu.pucp.spats.pedido.service.PedidoService;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.ArrastradaResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.CierreResponse;
import pe.edu.pucp.spats.planificacion.entity.Enumeraciones.EstadoConfirmacionCierre;
import pe.edu.pucp.spats.planificacion.entity.PlanificacionPartida;
import pe.edu.pucp.spats.planificacion.entity.PlanificacionSemanal;
import pe.edu.pucp.spats.planificacion.motor.PropuestasPendientes;
import pe.edu.pucp.spats.planificacion.motor.ReglasVigencia;
import pe.edu.pucp.spats.planificacion.repository.PlanificacionPartidaRepository;
import pe.edu.pucp.spats.planificacion.repository.PlanificacionSemanalRepository;

/** Cierre de la semana (HU-15, sección 9.4) y partidas arrastradas (HU-16). */
@Service
@Transactional(readOnly = true)
public class CierreSemanaService {

    private final PlanificacionSemanalRepository planificaciones;
    private final PlanificacionPartidaRepository partidas;
    private final PartidaService partidaService;
    private final PedidoService pedidoService;
    private final BitacoraService bitacora;
    private final ReglasVigencia reglasVigencia;
    private final PropuestasPendientes propuestas;
    private final Clock reloj;

    public CierreSemanaService(PlanificacionSemanalRepository planificaciones, PlanificacionPartidaRepository partidas,
                               PartidaService partidaService, PedidoService pedidoService, BitacoraService bitacora,
                               ReglasVigencia reglasVigencia, PropuestasPendientes propuestas, Clock reloj) {
        this.planificaciones = planificaciones;
        this.partidas = partidas;
        this.partidaService = partidaService;
        this.pedidoService = pedidoService;
        this.bitacora = bitacora;
        this.reglasVigencia = reglasVigencia;
        this.propuestas = propuestas;
        this.reloj = reloj;
    }

    /**
     * Las Asignadas y Sobrantes confirmadas pasan a COMPLETADA; las demás, a ARRASTRADA. Las Bloqueadas no
     * cambian y siguen PENDIENTE. Se completan los pedidos con todas sus partidas COMPLETADA.
     */
    @Transactional
    @PreAuthorize("hasRole('PLANIFICADOR')")
    public CierreResponse cerrar(Integer idPlanificacion, List<String> partidasCompletadas) {
        PlanificacionSemanal plan = planificaciones.findWithSemanaById(idPlanificacion).orElseThrow(() ->
                new RecursoNoEncontradoException("PLANIFICACION_NO_ENCONTRADA", "No existe la versión de planificación indicada."));
        if (!plan.isEsVigente() || !plan.abierta())
            throw new ConflictoException("PLANIFICACION_NO_ABIERTA", "Solo se cierra la planificación vigente de una semana abierta.");
        reglasVigencia.exigirSemanaAnteriorCerrada(plan.getSemana().getFechaInicio());
        propuestas.eliminarVencidas(plan.getSemana().getId());
        propuestas.exigirSinPendiente(plan.getSemana().getId(), "cerrar la semana");

        Map<String, PlanificacionPartida> conformadas = new LinkedHashMap<>();
        for (PlanificacionPartida f : partidas.deLaPlanificacion(idPlanificacion))
            if (f.conformada()) conformadas.put(f.getPartida().getNumeroPartida(), f);
        Set<String> confirmadas = new HashSet<>(partidasCompletadas);
        TreeSet<String> ajenas = new TreeSet<>(confirmadas);
        ajenas.removeAll(conformadas.keySet());
        if (!ajenas.isEmpty())
            throw new ReglaNegocioException("PARTIDAS_FUERA_DEL_PLAN", "Estas partidas no son Asignadas ni Sobrantes de la "
                    + "planificación: " + String.join(", ", ajenas) + ".");

        int completadas = 0, arrastradas = 0;
        Set<Integer> pedidos = new HashSet<>();
        for (PlanificacionPartida f : conformadas.values()) {
            Partida partida = f.getPartida();
            if (partida.getEstadoPartida() != EstadoPartida.PENDIENTE)
                throw new ConflictoException("PARTIDA_NO_PENDIENTE", "La partida " + partida.getNumeroPartida()
                        + " ya no está pendiente; revisa la planificación antes de cerrar.");
            boolean completada = confirmadas.contains(partida.getNumeroPartida());
            f.confirmarCierre(completada ? EstadoConfirmacionCierre.COMPLETADA : EstadoConfirmacionCierre.ARRASTRADA);
            bitacora.registrarCambio("partida", partida.getId(), "estado_partida", partida.getEstadoPartida(),
                    completada ? EstadoPartida.COMPLETADA : EstadoPartida.ARRASTRADA);
            partida.confirmarCierre(completada);
            if (completada) { completadas++; pedidos.add(partida.getPedido().getId()); } else arrastradas++;
        }
        plan.cerrar(LocalDateTime.now(reloj));
        bitacora.registrar(TipoOperacion.CERRAR, "planificacion_semanal", plan.getId(),
                "Semana " + plan.getSemana().getFechaInicio() + ": " + completadas + " completadas, " + arrastradas + " arrastradas");
        int pedidosCompletados = pedidoService.completarPedidosTerminados(pedidos);
        return new CierreResponse(completadas, arrastradas, pedidosCompletados);
    }

    /**
     * Lista única de partidas ARRASTRADA de cualquier semana, independiente de la semana seleccionada (D-27,
     * RF45, RF46), con la semana de su cierre y las semanas que lleva arrastrada.
     */
    public List<ArrastradaResponse> arrastradas() {
        LocalDate lunesActual = LocalDate.now(reloj).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Map<String, ArrastradaResponse> ultima = new LinkedHashMap<>();
        for (PlanificacionPartida f : partidas.arrastradasPendientes()) {
            var p = f.getPartida();
            LocalDate semanaCierre = f.getPlanificacion().getSemana().getFechaInicio();
            ultima.putIfAbsent(p.getNumeroPartida(), new ArrastradaResponse(p.getNumeroPartida(), p.getPedido().getNumeroOp(),
                    p.getColor().getNombre(), p.getPesoTotal(), f.getClasificacionResultado(),
                    f.getMaquina() == null ? null : f.getMaquina().getCodigo(), f.getDiaAbastecimiento(),
                    semanaCierre, Math.max(0, ChronoUnit.WEEKS.between(semanaCierre, lunesActual))));
        }
        return List.copyOf(ultima.values());
    }

    /**
     * El planificador marca una arrastrada como completada (RF49, HU-16 C4): sale de la lista. Su tela no vuelve
     * a la bolsa. La fecha queda en la bitácora; la partida no tiene columna de fecha de completado (D-27).
     */
    @Transactional
    @PreAuthorize("hasRole('PLANIFICADOR')")
    public void completarArrastrada(String numeroPartida) {
        Partida partida = partidaService.buscarPorNumero(numeroPartida);
        bitacora.registrarCambio("partida", partida.getId(), "estado_partida", partida.getEstadoPartida(), EstadoPartida.COMPLETADA);
        partida.completarArrastrada();
        bitacora.registrar(TipoOperacion.COMPLETAR, "partida", partida.getId(), numeroPartida);
        pedidoService.completarPedidosTerminados(Set.of(partida.getPedido().getId()));
    }
}
