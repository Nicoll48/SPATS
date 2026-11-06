package pe.edu.pucp.spats.comun.error;

/** Una regla de negocio impide la operación (422). */
public final class ReglaNegocioException extends SpatsException {

    public ReglaNegocioException(String codigo, String mensaje) {
        super(codigo, mensaje);
    }
}
