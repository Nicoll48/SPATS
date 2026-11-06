package pe.edu.pucp.spats.planificacion.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.pucp.spats.comun.error.ConflictoException;
import pe.edu.pucp.spats.comun.fecha.SemanaPlanificacion;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.ConfirmacionResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.ContextoReplanificacionResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.PartidaDelDiaResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.PlanificacionResumen;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.ReplanificacionRequest;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.ReplanificacionResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.ResultadoReplanificacion;
import pe.edu.pucp.spats.planificacion.entity.PlanificacionSemanal;
import pe.edu.pucp.spats.planificacion.mapper.PlanificacionMapper;
import pe.edu.pucp.spats.planificacion.motor.DecisionReplanificacion;
import pe.edu.pucp.spats.planificacion.motor.EjecutorAlgoritmo;
import pe.edu.pucp.spats.planificacion.motor.EntradaReplanificacion;
import pe.edu.pucp.spats.planificacion.motor.GuardadoReplanificacion;
import pe.edu.pucp.spats.planificacion.motor.LectorDatosReplanificacion;
import pe.edu.pucp.spats.planificacion.motor.PropuestasPendientes;
import pe.edu.pucp.spats.planificacion.motor.ReglasReplanificacion;
import pe.edu.pucp.spats.planificacion.motor.ResultadoTraducido;
import pe.edu.pucp.spats.planificacion.motor.TraductorResultado;
import pe.edu.pucp.spats.planificacion.repository.PlanificacionPartidaRepository;
import pe.edu.pucp.spats.planificacion.repository.PlanificacionSemanalRepository;
import pe.edu.pucp.spats.tejeduria.service.SemanaService;
import spats.algoritmo.salida.Indicadores;
import spats.algoritmo.salida.ResultadoPlanificacion;

/**
 * Re-planificación desde el día actual D (HU-18, RF53–RF55, RN-25). Sigue los mismos pasos que la generación:
 * leer (solo lectura), optimizar (sin transacción) y guardar (una transacción). Además evalúa la vigente en el
 * mismo escenario para decidir si propone la nueva (RF55). La propuesta solo pasa a vigente cuando el
 * planificador la confirma (D-13b); si la descarta, se elimina (D-26).
 */
@Service
public class ReplanificacionService {

    private final LectorDatosReplanificacion lector;
    private final EjecutorAlgoritmo ejecutor;
    private final GuardadoReplanificacion guardado;
    private final PropuestasPendientes propuestas;
    private final SemanaService semanas;
    private final PlanificacionSemanalRepository planificaciones;
    private final PlanificacionPartidaRepository partidas;
    private final PlanificacionMapper mapper;
    private final Clock reloj;
    private final Set<LocalDate> enCurso = ConcurrentHashMap.newKeySet();

    public ReplanificacionService(LectorDatosReplanificacion lector, EjecutorAlgoritmo ejecutor,
                                  GuardadoReplanificacion guardado, PropuestasPendientes propuestas, SemanaService semanas,
                                  PlanificacionSemanalRepository planificaciones, PlanificacionPartidaRepository partidas,
                                  PlanificacionMapper mapper, Clock reloj) {
        this.lector = lector;
        this.ejecutor = ejecutor;
        this.guardado = guardado;
        this.propuestas = propuestas;
        this.semanas = semanas;
        this.planificaciones = planificaciones;
        this.partidas = partidas;
        this.mapper = mapper;
        this.reloj = reloj;
    }

    /** Día D, si se puede re-planificar, partidas de hoy en la vigente y propuesta pendiente. */
    @Transactional(readOnly = true)
    public ContextoReplanificacionResponse contexto(LocalDate lunes) {
        LocalDate hoy = LocalDate.now(reloj);
        var semana = semanas.buscar(lunes).orElse(null);
        PlanificacionSemanal vigente = semana == null ? null : planificaciones.findBySemanaIdAndEsVigenteTrue(semana.getId()).orElse(null);
        String motivo = ReglasReplanificacion.motivoNoPermitida(vigente, new SemanaPlanificacion(lunes), hoy).orElse(null);
        List<PartidaDelDiaResponse> deHoy = vigente == null ? List.of() : partidas.deLaPlanificacion(vigente.getId()).stream()
                .filter(f -> hoy.equals(f.getDiaAbastecimiento()))
                .map(f -> new PartidaDelDiaResponse(f.getPartida().getNumeroPartida(), f.getMaquina().getCodigo(),
                        f.getClasificacionResultado(), f.getOrdenSecuencia())).toList();
        PlanificacionResumen propuesta = semana == null ? null : propuestas.pendiente(semana.getId())
                .map(p -> new PlanificacionResumen(p.getId(), p.getNumeroVersion(), p.getEstado(), p.getFechaGeneracion(),
                        p.getProyeccion().getId())).orElse(null);
        return new ContextoReplanificacionResponse(hoy, motivo == null, motivo, vigente == null ? null : vigente.getId(),
                deHoy, propuesta);
    }

    @PreAuthorize("hasRole('PLANIFICADOR')")
    public ReplanificacionResponse replanificar(LocalDate lunes, ReplanificacionRequest s) {
        if (!enCurso.add(lunes))
            throw new ConflictoException("PLANIFICACION_EN_CURSO", "Ya se está re-planificando esta semana.");
        try {
            Set<String> enMaquina = s.partidasEnMaquina() == null ? Set.of() : new HashSet<>(s.partidasEnMaquina());
            EntradaReplanificacion e = lector.leer(lunes, s.incidencias(), enMaquina);
            ResultadoPlanificacion nueva = ejecutor.ejecutar(e.datos());
            ResultadoTraducido traducido = TraductorResultado.traducirReplanificacion(e.datos(), nueva, e.dia(), e.bolsaVigente());
            Indicadores vigente = ejecutor.evaluar(e.datos(), e.alternativaVigente());
            List<String> advertencias = new ArrayList<>(e.advertencias());
            advertencias.addAll(traducido.advertencias());
            Integer idVigente = e.entrada().idVigenteAnterior();
            if (DecisionReplanificacion.decidir(nueva.indicadores(), vigente) == DecisionReplanificacion.Resultado.CONSERVAR_VIGENTE) {
                advertencias.add(0, "La re-planificación no encontró una alternativa con menor desviación que la vigente. "
                        + "Se conserva la planificación vigente y no se guardó nada.");
                return new ReplanificacionResponse(ResultadoReplanificacion.SE_CONSERVA_VIGENTE, e.dia(), null, null, idVigente,
                        decimal(nueva.indicadores().funcionObjetivo()), decimal(vigente.funcionObjetivo()), true, List.of(),
                        null, nueva.indicadores().detenidaPorTiempo(), List.copyOf(advertencias));
            }
            PlanificacionSemanal plan = guardado.proponer(e, traducido);
            return new ReplanificacionResponse(ResultadoReplanificacion.PROPUESTA, e.dia(), plan.getId(), plan.getNumeroVersion(),
                    idVigente, decimal(nueva.indicadores().funcionObjetivo()), decimal(vigente.funcionObjetivo()),
                    vigente.factible(), vigente.incumplimientos(), mapper.indicadores(plan),
                    nueva.indicadores().detenidaPorTiempo(), List.copyOf(advertencias));
        } finally {
            enCurso.remove(lunes);
        }
    }

    @PreAuthorize("hasRole('PLANIFICADOR')")
    public ConfirmacionResponse confirmar(Integer idPropuesta) {
        if (guardado.eliminarSiVencida(idPropuesta))
            throw new ConflictoException("PROPUESTA_VENCIDA", "La propuesta no se confirmó el mismo día en que se generó y se "
                    + "descartó. Vuelve a re-planificar si todavía hace falta.");
        GuardadoReplanificacion.Confirmacion c = guardado.confirmar(idPropuesta);
        return new ConfirmacionResponse(c.plan().getId(), c.plan().getNumeroVersion(), c.incidenciasConsideradas(),
                c.pedidosActualizados());
    }

    @PreAuthorize("hasRole('PLANIFICADOR')")
    public void descartar(Integer idPropuesta) {
        guardado.descartar(idPropuesta);
    }

    private static BigDecimal decimal(double valor) {
        return BigDecimal.valueOf(valor).setScale(3, RoundingMode.HALF_UP);
    }
}
