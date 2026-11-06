package pe.edu.pucp.spats.comun.error;

/** Credenciales incorrectas o cuenta inactiva (RF02, 401). */
public final class CredencialesInvalidasException extends SpatsException {

    public CredencialesInvalidasException(String mensaje) {
        super("CREDENCIALES_INVALIDAS", mensaje);
    }
}
