package pe.edu.pucp.spats.comun.error;

import java.util.Map;

/**
 * La operación choca con el estado actual (409): semana cerrada, planificación en curso, o una
 * acción que altera la planificación vigente y todavía no fue confirmada (RF59). {@code datos} viaja como
 * propiedades del problem+json para que el frontend arme la confirmación (p. ej. listas de partidas, D-51).
 */
public final class ConflictoException extends SpatsException {

    private final Map<String, Object> datos;

    public ConflictoException(String codigo, String mensaje) {
        this(codigo, mensaje, Map.of());
    }

    public ConflictoException(String codigo, String mensaje, Map<String, Object> datos) {
        super(codigo, mensaje);
        this.datos = datos == null ? Map.of() : datos;
    }

    public Map<String, Object> datos() {
        return datos;
    }
}
