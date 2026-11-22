package pe.edu.pucp.spats.planificacion.motor;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import pe.edu.pucp.spats.auditoria.entity.TipoOperacion;
import pe.edu.pucp.spats.auditoria.service.BitacoraService;
import pe.edu.pucp.spats.comun.error.ConflictoException;
import pe.edu.pucp.spats.planificacion.entity.Enumeraciones.EstadoPlanificacion;
import pe.edu.pucp.spats.planificacion.entity.PlanificacionSemanal;
import pe.edu.pucp.spats.planificacion.repository.PlanificacionSemanalRepository;
import pe.edu.pucp.spats.tejeduria.service.ProyeccionPorCambiarEvent;

/**
 * Decisión D-06, acotada por D-52: corregir o eliminar un registro de una versión de la proyección deja sin
 * vigencia las planificaciones generadas con esa versión (la vigente y su candidata), porque ya no corresponden a
 * los datos. Se conservan en el historial como INVALIDADA. Si la vigente era una de ellas, el calendario queda en
 * blanco hasta volver a generar. Como altera la planificación vigente, exige confirmación (RF59). Cargar una
 * versión nueva no pasa por aquí: no cambia la vigente.
 */
@Component
public class InvalidacionPorProyeccion {

    private final PlanificacionSemanalRepository planificaciones;
    private final BitacoraService bitacora;
    private final PropuestasPendientes propuestas;
    private final ReglasVigencia reglasVigencia;

    public InvalidacionPorProyeccion(PlanificacionSemanalRepository planificaciones, BitacoraService bitacora,
                                     PropuestasPendientes propuestas, ReglasVigencia reglasVigencia) {
        this.planificaciones = planificaciones;
        this.bitacora = bitacora;
        this.propuestas = propuestas;
        this.reglasVigencia = reglasVigencia;
    }

    @EventListener
    public void alCambiarProyeccion(ProyeccionPorCambiarEvent evento) {
        PlanificacionSemanal vigente = planificaciones.findBySemanaIdAndEsVigenteTrue(evento.idSemana()).orElse(null);
        if (vigente != null && vigente.getEstado() == EstadoPlanificacion.CERRADA)
            throw new ConflictoException("SEMANA_CERRADA",
                    "La planificación de esta semana ya se cerró; su proyección de tejeduría no se puede cambiar.");
        List<PlanificacionSemanal> afectadas = new ArrayList<>(planificaciones.candidatasDe(evento.idProyeccion()));
        boolean tocaVigente = vigente != null && vigente.getProyeccion().getId().equals(evento.idProyeccion());
        if (tocaVigente) afectadas.addFirst(vigente);
        if (afectadas.isEmpty()) return;
        if (!evento.confirmado())
            throw new ConflictoException("CONFIRMACION_REQUERIDA", tocaVigente
                    ? "La semana tiene la planificación vigente (versión " + vigente.getNumeroVersion() + ") generada con "
                            + "esta proyección. Si continúas, esa planificación deja de ser vigente, se conserva en el "
                            + "historial y el calendario queda en blanco hasta que vuelvas a generarla."
                    : "Con esta versión de la proyección se generó una planificación que no es la vigente. Si continúas, "
                            + "deja de estar disponible (se conserva en el historial); la vigente no cambia.");
        if (tocaVigente) propuestas.eliminarTodas(evento.idSemana(), "cambió la proyección de tejeduría de la semana");
        for (PlanificacionSemanal p : afectadas) {
            p.quitarVigencia(EstadoPlanificacion.INVALIDADA);
            bitacora.registrar(TipoOperacion.INVALIDAR, "planificacion_semanal", p.getId(),
                    "Corrección de la proyección de tejeduría de la semana " + evento.lunes());
        }
        if (tocaVigente) reglasVigencia.marcarSemanaSiguiente(evento.lunes());
    }
}
