package pe.edu.pucp.spats.comun.error;

/**
 * Excepción de dominio de SPATS. El {@code codigo} es estable para que el frontend decida qué hacer
 * sin depender del texto; el mensaje está escrito para el jefe de planeamiento.
 */
public abstract sealed class SpatsException extends RuntimeException
        permits RecursoNoEncontradoException, ReglaNegocioException, ConflictoException,
                ArchivoInvalidoException, FalloPlanificacionException, CredencialesInvalidasException {

    private final String codigo;

    protected SpatsException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    protected SpatsException(String codigo, String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
