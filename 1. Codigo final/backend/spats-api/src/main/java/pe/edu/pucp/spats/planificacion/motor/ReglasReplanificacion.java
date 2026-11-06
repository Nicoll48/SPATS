package pe.edu.pucp.spats.planificacion.motor;

import java.time.LocalDate;
import java.util.Optional;

import pe.edu.pucp.spats.comun.error.ConflictoException;
import pe.edu.pucp.spats.comun.error.ReglaNegocioException;
import pe.edu.pucp.spats.comun.fecha.SemanaPlanificacion;
import pe.edu.pucp.spats.planificacion.entity.PlanificacionSemanal;

/** Cuándo se puede re-planificar (HU-18): la vigente ABIERTA de la semana que contiene el día D. */
public final class ReglasReplanificacion {

    private ReglasReplanificacion() { }

    /** Motivo por el que no se puede re-planificar, o vacío si se puede. */
    public static Optional<String> motivoNoPermitida(PlanificacionSemanal vigente, SemanaPlanificacion periodo, LocalDate hoy) {
        if (vigente == null) return Optional.of("La semana no tiene planificación vigente.");
        if (!vigente.abierta()) return Optional.of("La planificación de esta semana ya se cerró.");
        if (!periodo.contiene(hoy))
            return Optional.of("Solo se re-planifica la semana en curso: hoy (" + hoy + ") no está en la semana del "
                    + periodo.lunes() + ".");
        return Optional.empty();
    }

    static void exigirPermitida(PlanificacionSemanal vigente, SemanaPlanificacion periodo, LocalDate hoy) {
        if (!vigente.abierta()) throw new ConflictoException("SEMANA_CERRADA", "La planificación de esta semana ya se cerró.");
        motivoNoPermitida(vigente, periodo, hoy).ifPresent(m -> { throw new ReglaNegocioException("FUERA_DE_LA_SEMANA", m); });
    }

    static ReglaNegocioException sinVigente() {
        return new ReglaNegocioException("SIN_PLANIFICACION_VIGENTE", "La semana no tiene planificación vigente para re-planificar.");
    }
}
