package pe.edu.pucp.spats.planificacion.motor;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import pe.edu.pucp.spats.auditoria.service.BitacoraService;
import pe.edu.pucp.spats.comun.error.ConflictoException;
import pe.edu.pucp.spats.planificacion.entity.PlanificacionSemanal;
import pe.edu.pucp.spats.planificacion.repository.PlanificacionSemanalRepository;

/**
 * Reglas sobre el encadenamiento de semanas por el saldo de las bolsas (RN-03): la semana siguiente toma
 * como stock inicial el saldo del domingo de la versión vigente de esta semana.
 */
@Component
public class ReglasVigencia {

    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("d/M");

    private final PlanificacionSemanalRepository planificaciones;
    private final BitacoraService bitacora;

    public ReglasVigencia(PlanificacionSemanalRepository planificaciones, BitacoraService bitacora) {
        this.planificaciones = planificaciones;
        this.bitacora = bitacora;
    }

    /**
     * Si alguna semana posterior ya tiene una planificación vigente, cambiar esta semana deja esa
     * planificación con un stock inicial desactualizado. No se bloquea (D-19); se avisa al planificador
     * y la posterior queda marcada (ver {@link #marcarSemanaSiguiente}).
     */
    public Optional<String> advertenciaSemanasPosteriores(LocalDate lunes) {
        List<LocalDate> posteriores = planificaciones.semanasPosterioresConVigente(lunes);
        if (posteriores.isEmpty()) return Optional.empty();
        return Optional.of("Las semanas que inician el " + posteriores.stream().map(LocalDate::toString)
                .collect(Collectors.joining(", ")) + " ya tienen planificación vigente calculada con el saldo de "
                + "las bolsas anterior a este cambio; su stock inicial no se actualiza solo.");
    }

    /**
     * D-19: cambió la planificación vigente de la semana {@code lunes} (se generó otra versión, se invalidó por
     * la proyección o se confirmó una re-planificación). Si la semana siguiente tiene planificación vigente
     * abierta, queda guardada como desactualizada ({@code stock_desactualizado}). Cerrar una semana no cambia
     * su vigente, así que no marca la siguiente.
     */
    public void marcarSemanaSiguiente(LocalDate lunes) {
        planificaciones.findBySemanaFechaInicioAndEsVigenteTrue(lunes.plusWeeks(1))
                .filter(p -> p.abierta() && !p.isStockDesactualizado())
                .ifPresent(p -> {
                    p.marcarStockDesactualizado();
                    bitacora.registrarCambio("planificacion_semanal", p.getId(), "stock_desactualizado", false, true);
                });
    }

    /** Aviso de la marca guardada (D-19); no recalcula nada. */
    public Optional<String> stockDesactualizado(PlanificacionSemanal plan) {
        if (!plan.isEsVigente() || !plan.abierta() || !plan.isStockDesactualizado()) return Optional.empty();
        LocalDate anterior = plan.getSemana().getFechaInicio().minusWeeks(1);
        return Optional.of(planificaciones.findBySemanaFechaInicioAndEsVigenteTrue(anterior).isPresent()
                ? "Desactualizada: el stock inicial cambió porque cambió la planificación de la semana del "
                        + anterior.format(DIA_MES) + ". Vuelve a generarla."
                : "Desactualizada: la semana del " + anterior.format(DIA_MES) + " quedó sin planificación vigente. "
                        + "Genera esa semana y luego vuelve a generar esta.");
    }

    /** Cierre en orden (D-19): no se cierra una semana mientras la anterior tenga planificación vigente abierta. */
    public void exigirSemanaAnteriorCerrada(LocalDate lunes) {
        LocalDate anterior = lunes.minusWeeks(1);
        planificaciones.findBySemanaFechaInicioAndEsVigenteTrue(anterior).filter(PlanificacionSemanal::abierta)
                .ifPresent(p -> {
                    throw new ConflictoException("SEMANA_ANTERIOR_ABIERTA",
                            "Cierra primero la semana del " + anterior.format(DIA_MES) + ".");
                });
    }
}
