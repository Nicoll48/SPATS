package pe.edu.pucp.spats.planificacion.motor;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.pucp.spats.auditoria.entity.TipoOperacion;
import pe.edu.pucp.spats.auditoria.service.BitacoraService;
import pe.edu.pucp.spats.comun.error.ConflictoException;
import pe.edu.pucp.spats.planificacion.entity.PlanificacionSemanal;
import pe.edu.pucp.spats.planificacion.repository.BolsaArticuloRepository;
import pe.edu.pucp.spats.planificacion.repository.PlanificacionIncidenciaRepository;
import pe.edu.pucp.spats.planificacion.repository.PlanificacionMaquinaRepository;
import pe.edu.pucp.spats.planificacion.repository.PlanificacionPartidaRepository;
import pe.edu.pucp.spats.planificacion.repository.PlanificacionSemanalRepository;

/**
 * Re-planificaciones propuestas que esperan confirmación (D-32). A lo más hay una pendiente por semana. Una
 * propuesta solo se puede confirmar el mismo día D en que se generó: desde el día siguiente está vencida y se
 * elimina en la próxima operación que escribe sobre la semana. Mientras haya una pendiente no se puede cerrar
 * la semana, volver a generarla ni generar la semana siguiente.
 */
@Component
public class PropuestasPendientes {

    private final PlanificacionSemanalRepository planificaciones;
    private final PlanificacionPartidaRepository partidas;
    private final PlanificacionMaquinaRepository maquinas;
    private final BolsaArticuloRepository bolsas;
    private final PlanificacionIncidenciaRepository incidencias;
    private final BitacoraService bitacora;
    private final Clock reloj;

    public PropuestasPendientes(PlanificacionSemanalRepository planificaciones, PlanificacionPartidaRepository partidas,
                                PlanificacionMaquinaRepository maquinas, BolsaArticuloRepository bolsas,
                                PlanificacionIncidenciaRepository incidencias, BitacoraService bitacora, Clock reloj) {
        this.planificaciones = planificaciones;
        this.partidas = partidas;
        this.maquinas = maquinas;
        this.bolsas = bolsas;
        this.incidencias = incidencias;
        this.bitacora = bitacora;
        this.reloj = reloj;
    }

    /** true si la propuesta se generó un día anterior a hoy: ya no se puede confirmar. */
    public boolean vencida(PlanificacionSemanal p) {
        return p.getFechaGeneracion().toLocalDate().isBefore(LocalDate.now(reloj));
    }

    /** La propuesta pendiente (no vencida) de la semana, si la hay. */
    public Optional<PlanificacionSemanal> pendiente(Integer idSemana) {
        return planificaciones.propuestasDe(idSemana).stream().filter(p -> !vencida(p)).findFirst();
    }

    public void exigirSinPendiente(Integer idSemana, String accion) {
        pendiente(idSemana).ifPresent(p -> {
            throw new ConflictoException("PROPUESTA_PENDIENTE", "La semana del " + p.getSemana().getFechaInicio()
                    + " tiene una re-planificación propuesta (versión " + p.getNumeroVersion() + ") sin confirmar. "
                    + "Confírmala o descártala antes de " + accion + ".");
        });
    }

    /** Elimina las propuestas vencidas de la semana. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void eliminarVencidas(Integer idSemana) {
        for (PlanificacionSemanal p : planificaciones.propuestasDe(idSemana))
            if (vencida(p)) eliminar(p, "venció sin confirmarse el mismo día");
    }

    /** Elimina todas las propuestas de la semana (por ejemplo, porque su vigente dejó de serlo). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void eliminarTodas(Integer idSemana, String motivo) {
        for (PlanificacionSemanal p : planificaciones.propuestasDe(idSemana)) eliminar(p, motivo);
    }

    /** Elimina la propuesta con todas sus filas (D-26). Las incidencias siguen PENDIENTE. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void eliminar(PlanificacionSemanal p, String motivo) {
        Integer id = p.getId();
        eliminarFilas(p);
        bitacora.registrar(TipoOperacion.DESCARTAR, "planificacion_semanal", id,
                "Re-planificación propuesta de la semana " + p.getSemana().getFechaInicio() + ", versión "
                        + p.getNumeroVersion() + ": " + motivo);
    }

    /** Elimina una planificación de cualquier estado con todas sus filas (D-44). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void eliminarDefinitiva(PlanificacionSemanal p, String motivo) {
        Integer id = p.getId();
        eliminarFilas(p);
        bitacora.registrar(TipoOperacion.ELIMINAR, "planificacion_semanal", id, "Semana "
                + p.getSemana().getFechaInicio() + ", versión " + p.getNumeroVersion() + ": " + motivo);
    }

    private void eliminarFilas(PlanificacionSemanal p) {
        Integer id = p.getId();
        incidencias.eliminarDe(id);
        bolsas.eliminarDe(id);
        maquinas.eliminarDe(id);
        partidas.eliminarDe(id);
        planificaciones.delete(p);
        planificaciones.flush();
    }
}
