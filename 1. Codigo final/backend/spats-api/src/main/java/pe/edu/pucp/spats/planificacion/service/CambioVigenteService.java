package pe.edu.pucp.spats.planificacion.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.pucp.spats.comun.error.ConflictoException;
import pe.edu.pucp.spats.comun.error.RecursoNoEncontradoException;
import pe.edu.pucp.spats.comun.error.ReglaNegocioException;
import pe.edu.pucp.spats.comun.fecha.SemanaPlanificacion;
import pe.edu.pucp.spats.incidencia.entity.EstadoIncidencia;
import pe.edu.pucp.spats.incidencia.entity.Incidencia;
import pe.edu.pucp.spats.incidencia.repository.IncidenciaRepository;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.CambioVigenteResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.ContextoCambioVigenteResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.ModoCambioVigente;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.PartidaDelDiaResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.ReplanificacionResponse;
import pe.edu.pucp.spats.planificacion.entity.Enumeraciones.TipoGeneracion;
import pe.edu.pucp.spats.planificacion.entity.PlanificacionSemanal;
import pe.edu.pucp.spats.planificacion.motor.GuardadoCambioVigente;
import pe.edu.pucp.spats.planificacion.motor.PropuestasPendientes;
import pe.edu.pucp.spats.planificacion.repository.PlanificacionPartidaRepository;
import pe.edu.pucp.spats.planificacion.repository.PlanificacionSemanalRepository;

/**
 * «Usar como vigente» una planificación generada con otra versión de la proyección (D-52). Solo hay una vigente por
 * semana (RF58) y nunca cambia sin que el planificador lo pida (RF59).
 * <ul>
 *   <li>Si la semana no empezó (o no tiene vigente), la candidata pasa directamente a vigente.</li>
 *   <li>Si ya empezó, se re-planifica desde hoy con esa versión (sección 9.5): lo anterior a hoy se conserva de la
 *       vigente y el resultado queda como propuesta que el planificador confirma o descarta.</li>
 *   <li>Si la semana ya terminó, no se cambia: lo planificado ya se ejecutó; se cierra con la vigente.</li>
 * </ul>
 */
@Service
public class CambioVigenteService {

    private final PlanificacionSemanalRepository planificaciones;
    private final PlanificacionPartidaRepository partidas;
    private final IncidenciaRepository incidencias;
    private final GuardadoCambioVigente guardado;
    private final ReplanificacionService replanificacion;
    private final PropuestasPendientes propuestas;
    private final Clock reloj;

    public CambioVigenteService(PlanificacionSemanalRepository planificaciones, PlanificacionPartidaRepository partidas,
                                IncidenciaRepository incidencias, GuardadoCambioVigente guardado,
                                ReplanificacionService replanificacion, PropuestasPendientes propuestas, Clock reloj) {
        this.planificaciones = planificaciones;
        this.partidas = partidas;
        this.incidencias = incidencias;
        this.guardado = guardado;
        this.replanificacion = replanificacion;
        this.propuestas = propuestas;
        this.reloj = reloj;
    }

    /** Avisos antes de confirmar: incidencias y re-planificaciones de la vigente, día de hoy y si hay que re-planificar. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('PLANIFICADOR')")
    public ContextoCambioVigenteResponse contexto(Integer idCandidata) {
        PlanificacionSemanal c = buscar(idCandidata);
        LocalDate hoy = LocalDate.now(reloj);
        SemanaPlanificacion periodo = c.getSemana().periodo();
        PlanificacionSemanal vigente = planificaciones.findBySemanaIdAndEsVigenteTrue(c.getSemana().getId()).orElse(null);
        String motivo = motivoNoPermitido(c, vigente, periodo, hoy).orElse(null);
        boolean iniciada = !hoy.isBefore(periodo.lunes());
        List<Incidencia> deLaSemana = incidencias.deLaSemana(c.getSemana().getId());
        int pendientes = (int) deLaSemana.stream().filter(i -> i.getEstado() == EstadoIncidencia.PENDIENTE).count();
        int consideradas = deLaSemana.size() - pendientes;
        int replanificaciones = (int) planificaciones.findBySemanaIdOrderByNumeroVersionDesc(c.getSemana().getId()).stream()
                .filter(p -> p.getTipoGeneracion() == TipoGeneracion.REPLANIFICACION && !p.propuesta()).count();
        List<PartidaDelDiaResponse> deHoy = vigente == null || modo(vigente, periodo, hoy) != ModoCambioVigente.REPLANIFICACION
                ? List.of()
                : partidas.deLaPlanificacion(vigente.getId()).stream().filter(f -> hoy.equals(f.getDiaAbastecimiento()))
                        .map(f -> new PartidaDelDiaResponse(f.getPartida().getNumeroPartida(), f.getMaquina().getCodigo(),
                                f.getClasificacionResultado(), f.getOrdenSecuencia())).toList();
        return new ContextoCambioVigenteResponse(c.getId(), c.getNumeroVersion(), c.getProyeccion().getVersion(),
                vigente == null ? null : vigente.getId(), vigente == null ? null : vigente.getNumeroVersion(),
                vigente == null ? null : vigente.getProyeccion().getVersion(), hoy, iniciada, motivo == null, motivo,
                pendientes, consideradas, replanificaciones, c.isStockDesactualizado(), deHoy);
    }

    @PreAuthorize("hasRole('PLANIFICADOR')")
    public CambioVigenteResponse usar(Integer idCandidata, List<String> partidasEnMaquina) {
        PlanificacionSemanal c = buscar(idCandidata);
        LocalDate hoy = LocalDate.now(reloj);
        SemanaPlanificacion periodo = c.getSemana().periodo();
        PlanificacionSemanal vigente = planificaciones.findBySemanaIdAndEsVigenteTrue(c.getSemana().getId()).orElse(null);
        motivoNoPermitido(c, vigente, periodo, hoy).ifPresent(m -> { throw new ReglaNegocioException("CAMBIO_NO_PERMITIDO", m); });
        if (vigente == null || modo(vigente, periodo, hoy) == ModoCambioVigente.DIRECTO) {
            PlanificacionSemanal nueva = guardado.usarDirecto(idCandidata);
            return new CambioVigenteResponse(ModoCambioVigente.DIRECTO, nueva.getId(), nueva.getNumeroVersion(), null);
        }
        ReplanificacionResponse r = replanificacion.replanificarConVersion(periodo.lunes(), c.getProyeccion().getId(),
                partidasEnMaquina);
        return new CambioVigenteResponse(ModoCambioVigente.REPLANIFICACION, null, null, r);
    }

    /** Con la semana sin empezar se cambia directamente; con la semana en curso se re-planifica desde hoy. */
    private static ModoCambioVigente modo(PlanificacionSemanal vigente, SemanaPlanificacion periodo, LocalDate hoy) {
        return vigente == null || hoy.isBefore(periodo.lunes()) ? ModoCambioVigente.DIRECTO : ModoCambioVigente.REPLANIFICACION;
    }

    private Optional<String> motivoNoPermitido(PlanificacionSemanal c, PlanificacionSemanal vigente,
                                               SemanaPlanificacion periodo, LocalDate hoy) {
        if (!c.candidata()) return Optional.of("Esta planificación no se puede usar como vigente.");
        if (vigente != null && !vigente.abierta())
            return Optional.of("La planificación de esta semana ya se cerró; sus versiones quedan en solo lectura.");
        if (propuestas.pendiente(c.getSemana().getId()).isPresent())
            return Optional.of("Hay una re-planificación propuesta sin confirmar. Confírmala o descártala antes.");
        if (vigente != null && hoy.isAfter(periodo.domingo()))
            return Optional.of("La semana ya terminó: lo planificado ya se ejecutó con la vigente. Ciérrala con esa planificación.");
        return Optional.empty();
    }

    private PlanificacionSemanal buscar(Integer id) {
        PlanificacionSemanal c = planificaciones.findWithSemanaById(id).orElseThrow(() ->
                new RecursoNoEncontradoException("PLANIFICACION_NO_ENCONTRADA", "No existe la versión de planificación indicada."));
        if (c.propuesta())
            throw new ConflictoException("NO_ES_CANDIDATA", "Una re-planificación propuesta se confirma o se descarta.");
        return c;
    }
}
