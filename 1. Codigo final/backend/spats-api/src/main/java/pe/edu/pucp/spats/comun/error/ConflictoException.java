package pe.edu.pucp.spats.comun.error;

/**
 * La operación choca con el estado actual (409): semana cerrada, planificación en curso, o una
 * acción que altera la planificación vigente y todavía no fue confirmada (RF59).
 */
public final class ConflictoException extends SpatsException {

    public ConflictoException(String codigo, String mensaje) {
        super(codigo, mensaje);
    }
}
