package pe.edu.pucp.spats.comun.error;

/** El recurso pedido no existe (404). */
public final class RecursoNoEncontradoException extends SpatsException {

    public RecursoNoEncontradoException(String codigo, String mensaje) {
        super(codigo, mensaje);
    }
}
