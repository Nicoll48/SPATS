package pe.edu.pucp.spats.comun.fecha;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import pe.edu.pucp.spats.comun.error.ReglaNegocioException;

/**
 * Semana de planificación de tintorería: de lunes a domingo (sección 5 del contexto).
 *
 * <p>La ventana de la proyección de tejeduría va del sábado anterior al viernes de la semana. Es un
 * filtro sobre las fechas de tejido; la regla de disponibilidad (día siguiente, o lunes si se tejió el
 * sábado) la aplica el algoritmo con {@code RegistroProyeccion.fechaDisponible}.</p>
 */
public record SemanaPlanificacion(LocalDate lunes) {

    public static final int DIAS = 7;

    public SemanaPlanificacion {
        if (lunes == null || lunes.getDayOfWeek() != DayOfWeek.MONDAY)
            throw new ReglaNegocioException("SEMANA_INVALIDA",
                    "La semana de planificación debe iniciar en lunes" + (lunes == null ? "." : ": " + lunes + "."));
    }

    public LocalDate domingo() {
        return lunes.plusDays(DIAS - 1);
    }

    public LocalDate inicioVentanaProyeccion() {
        return lunes.minusDays(2);
    }

    public LocalDate finVentanaProyeccion() {
        return lunes.plusDays(4);
    }

    public boolean enVentanaProyeccion(LocalDate fechaTejido) {
        return !fechaTejido.isBefore(inicioVentanaProyeccion()) && !fechaTejido.isAfter(finVentanaProyeccion());
    }

    public boolean contiene(LocalDate fecha) {
        return !fecha.isBefore(lunes) && !fecha.isAfter(domingo());
    }

    public SemanaPlanificacion anterior() {
        return new SemanaPlanificacion(lunes.minusDays(DIAS));
    }

    public List<LocalDate> dias() {
        List<LocalDate> dias = new ArrayList<>(DIAS);
        for (int d = 0; d < DIAS; d++) dias.add(lunes.plusDays(d));
        return dias;
    }

    /**
     * Días de la semana que toca un intervalo [inicio, fin). Un día tocado en parte cuenta como día
     * completo (decisión D-08).
     */
    public List<LocalDate> diasTocados(LocalDateTime inicio, LocalDateTime fin) {
        List<LocalDate> tocados = new ArrayList<>();
        for (LocalDate dia : dias()) {
            LocalDateTime desde = dia.atStartOfDay();
            LocalDateTime hasta = dia.plusDays(1).atStartOfDay();
            if (inicio.isBefore(hasta) && fin.isAfter(desde)) tocados.add(dia);
        }
        return tocados;
    }
}
