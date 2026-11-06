package pe.edu.pucp.spats.comun.error;

import java.util.List;

/** El archivo importado no cumple la plantilla (RF09, RF10). Se rechaza completo y no se guarda nada. */
public final class ArchivoInvalidoException extends SpatsException {

    private final List<ProblemaValidacion> problemas;

    public ArchivoInvalidoException(String mensaje, List<ProblemaValidacion> problemas) {
        super("ARCHIVO_CON_ERRORES", mensaje);
        this.problemas = List.copyOf(problemas);
    }

    public List<ProblemaValidacion> problemas() {
        return problemas;
    }
}
