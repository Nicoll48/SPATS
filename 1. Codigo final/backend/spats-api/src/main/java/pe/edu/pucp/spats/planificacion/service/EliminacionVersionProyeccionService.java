package pe.edu.pucp.spats.planificacion.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.pucp.spats.auditoria.entity.TipoOperacion;
import pe.edu.pucp.spats.auditoria.service.BitacoraService;
import pe.edu.pucp.spats.comun.error.ConflictoException;
import pe.edu.pucp.spats.incidencia.repository.IncidenciaRepository;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.VersionProyeccionEliminadaResponse;
import pe.edu.pucp.spats.planificacion.entity.Enumeraciones.EstadoPlanificacion;
import pe.edu.pucp.spats.planificacion.entity.PlanificacionSemanal;
import pe.edu.pucp.spats.planificacion.motor.PropuestasPendientes;
import pe.edu.pucp.spats.planificacion.motor.ReglasVigencia;
import pe.edu.pucp.spats.planificacion.repository.BolsaArticuloRepository;
import pe.edu.pucp.spats.planificacion.repository.PlanificacionSemanalRepository;
import pe.edu.pucp.spats.tejeduria.entity.EstadoProyeccion;
import pe.edu.pucp.spats.tejeduria.entity.ProyeccionTejeduria;
import pe.edu.pucp.spats.tejeduria.service.ProyeccionService;

/**
 * Marca como eliminada una versión de la proyección y borra las planificaciones generadas a partir de ella (D-44,
 * D-46), para corregir una carga o una generación hecha por error. La versión sigue en el historial y se puede
 * volver a ella. Las partidas siguen disponibles porque una planificación abierta no cambia su estado. No se
 * elimina lo que ya tuvo efectos: una semana cerrada o con incidencias.
 */
@Service
public class EliminacionVersionProyeccionService {

    private final ProyeccionService proyecciones;
    private final PlanificacionSemanalRepository planificaciones;
    private final PropuestasPendientes propuestas;
    private final ReglasVigencia reglasVigencia;
    private final IncidenciaRepository incidencias;
    private final BolsaArticuloRepository bolsas;
    private final BitacoraService bitacora;

    public EliminacionVersionProyeccionService(ProyeccionService proyecciones, PlanificacionSemanalRepository planificaciones,
                                               PropuestasPendientes propuestas, ReglasVigencia reglasVigencia,
                                               IncidenciaRepository incidencias, BolsaArticuloRepository bolsas,
                                               BitacoraService bitacora) {
        this.proyecciones = proyecciones;
        this.planificaciones = planificaciones;
        this.propuestas = propuestas;
        this.reglasVigencia = reglasVigencia;
        this.incidencias = incidencias;
        this.bolsas = bolsas;
        this.bitacora = bitacora;
    }

    @Transactional
    public VersionProyeccionEliminadaResponse eliminar(Integer idProyeccion, boolean confirmado) {
        ProyeccionTejeduria version = proyecciones.buscar(idProyeccion);
        if (version.getEstado() == EstadoProyeccion.ELIMINADA)
            throw new ConflictoException("PROYECCION_YA_ELIMINADA", "Esa versión ya está eliminada.");
        List<PlanificacionSemanal> suyas = planificaciones.findByProyeccionIdOrderByNumeroVersionAsc(idProyeccion);
        if (suyas.stream().anyMatch(p -> p.getEstado() == EstadoPlanificacion.CERRADA))
            throw new ConflictoException("PROYECCION_EN_SEMANA_CERRADA", "Con esta versión se generó una planificación "
                    + "que ya se cerró, así que no se puede eliminar.");
        if (!suyas.isEmpty() && incidencias.existsByPlanificacionOrigenIn(suyas))
            throw new ConflictoException("PROYECCION_CON_INCIDENCIAS", "Una planificación generada con esta versión "
                    + "tiene incidencias registradas, así que no se puede eliminar.");
        if (!suyas.isEmpty() && !confirmado)
            throw new ConflictoException("CONFIRMACION_REQUERIDA", "Con esta versión se generaron " + suyas.size()
                    + " planificaciones. Si continúas, también se eliminarán.");

        // La semana siguiente toma su stock inicial del saldo de la vigente (D-19): si no dejó saldo, nada le cambia.
        boolean quitaSaldo = suyas.stream().filter(PlanificacionSemanal::isEsVigente)
                .anyMatch(p -> bolsas.existsByPlanificacionIdAndKgSaldoFinalGreaterThan(p.getId(), BigDecimal.ZERO));
        String motivo = "se eliminó la versión " + version.getVersion() + " de la proyección";
        for (PlanificacionSemanal p : suyas) propuestas.eliminarDefinitiva(p, motivo);
        if (quitaSaldo) reglasVigencia.marcarSemanaSiguiente(version.getSemana().getFechaInicio());

        int registros = proyecciones.marcarEliminada(version);
        bitacora.registrar(TipoOperacion.ELIMINAR, "proyeccion_tejeduria", idProyeccion, "Semana "
                + version.getSemana().getFechaInicio() + ", versión " + version.getVersion() + ": " + registros
                + " registros y " + suyas.size() + " planificaciones");
        return new VersionProyeccionEliminadaResponse(version.getVersion(), registros, suyas.size());
    }
}
