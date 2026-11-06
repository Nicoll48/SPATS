package pe.edu.pucp.spats.planificacion.motor;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import pe.edu.pucp.spats.auditoria.entity.TipoOperacion;
import pe.edu.pucp.spats.auditoria.service.BitacoraService;
import pe.edu.pucp.spats.comun.error.ConflictoException;
import pe.edu.pucp.spats.planificacion.entity.Enumeraciones.EstadoPlanificacion;
import pe.edu.pucp.spats.planificacion.repository.PlanificacionSemanalRepository;
import pe.edu.pucp.spats.tejeduria.service.ProyeccionPorCambiarEvent;

/**
 * Decisión D-06: si cambia la proyección de una semana con planificación vigente abierta, esa planificación
 * ya no corresponde a los datos. Deja de ser vigente (se conserva en el historial) y el calendario queda en
 * blanco hasta volver a generar. Como altera la planificación vigente, exige confirmación (RF59).
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
        planificaciones.findBySemanaIdAndEsVigenteTrue(evento.idSemana()).ifPresent(vigente -> {
            if (vigente.getEstado() == EstadoPlanificacion.CERRADA)
                throw new ConflictoException("SEMANA_CERRADA",
                        "La planificación de esta semana ya se cerró; su proyección de tejeduría no se puede cambiar.");
            if (!evento.confirmado())
                throw new ConflictoException("CONFIRMACION_REQUERIDA", "La semana tiene la planificación vigente "
                        + "(versión " + vigente.getNumeroVersion() + ") generada con la proyección actual. Si continúas, "
                        + "esa planificación deja de ser vigente, se conserva en el historial y el calendario queda en "
                        + "blanco hasta que vuelvas a generarla.");
            propuestas.eliminarTodas(evento.idSemana(), "cambió la proyección de tejeduría de la semana");
            vigente.quitarVigencia(EstadoPlanificacion.INVALIDADA);
            bitacora.registrar(TipoOperacion.INVALIDAR, "planificacion_semanal", vigente.getId(),
                    "Cambio de la proyección de tejeduría de la semana " + evento.lunes());
            reglasVigencia.marcarSemanaSiguiente(evento.lunes());
        });
    }
}
