package pe.edu.pucp.spats.planificacion.motor;

import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import pe.edu.pucp.spats.auditoria.entity.TipoOperacion;
import pe.edu.pucp.spats.auditoria.service.BitacoraService;
import pe.edu.pucp.spats.comun.error.ConflictoException;
import pe.edu.pucp.spats.partida.service.PartidaPorCambiarEvent;
import pe.edu.pucp.spats.planificacion.entity.Enumeraciones.EstadoPlanificacion;
import pe.edu.pucp.spats.planificacion.entity.PlanificacionSemanal;
import pe.edu.pucp.spats.planificacion.repository.PlanificacionSemanalRepository;

/**
 * Decisión D-24: si se modifica una partida que está Asignada o Sobrante en una planificación vigente
 * abierta, esa planificación ya no corresponde a los datos. Igual que D-06: exige confirmación (RF59), deja
 * de ser vigente (INVALIDADA, se conserva en el historial), se eliminan las propuestas de la semana y la
 * vigente de la semana siguiente queda desactualizada (D-19).
 */
@Component
public class InvalidacionPorPartida {

    private final PlanificacionSemanalRepository planificaciones;
    private final BitacoraService bitacora;
    private final PropuestasPendientes propuestas;
    private final ReglasVigencia reglasVigencia;

    public InvalidacionPorPartida(PlanificacionSemanalRepository planificaciones, BitacoraService bitacora,
                                  PropuestasPendientes propuestas, ReglasVigencia reglasVigencia) {
        this.planificaciones = planificaciones;
        this.bitacora = bitacora;
        this.propuestas = propuestas;
        this.reglasVigencia = reglasVigencia;
    }

    @EventListener
    public void alCambiarPartida(PartidaPorCambiarEvent evento) {
        List<PlanificacionSemanal> afectadas = planificaciones.vigentesAbiertasConPartidaUbicada(evento.idPartida());
        if (afectadas.isEmpty()) return;
        if (!evento.confirmado()) {
            PlanificacionSemanal p = afectadas.getFirst();
            throw new ConflictoException("CONFIRMACION_REQUERIDA", "La partida " + evento.numeroPartida()
                    + " está en la planificación vigente de la semana del " + p.getSemana().getFechaInicio()
                    + " (versión " + p.getNumeroVersion() + "). Si continúas, esa planificación deja de ser vigente, "
                    + "se conserva en el historial y el calendario queda en blanco hasta que vuelvas a generarla.");
        }
        for (PlanificacionSemanal vigente : afectadas) {
            propuestas.eliminarTodas(vigente.getSemana().getId(), "se modificó la partida " + evento.numeroPartida());
            vigente.quitarVigencia(EstadoPlanificacion.INVALIDADA);
            bitacora.registrar(TipoOperacion.INVALIDAR, "planificacion_semanal", vigente.getId(),
                    "Modificación de la partida " + evento.numeroPartida());
            reglasVigencia.marcarSemanaSiguiente(vigente.getSemana().getFechaInicio());
        }
    }
}
