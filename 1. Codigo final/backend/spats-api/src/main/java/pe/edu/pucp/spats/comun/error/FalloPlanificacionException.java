package pe.edu.pucp.spats.comun.error;

import java.util.List;

/**
 * La ejecución del algoritmo falló o devolvió una solución no factible. La planificación vigente
 * queda intacta y no se guarda ninguna versión parcial (RN-19, RNF06).
 */
public final class FalloPlanificacionException extends SpatsException {

    private final List<String> detalles;

    public FalloPlanificacionException(String mensaje, List<String> detalles) {
        super("FALLO_PLANIFICACION", mensaje);
        this.detalles = List.copyOf(detalles);
    }

    public FalloPlanificacionException(String mensaje, Throwable causa) {
        super("FALLO_PLANIFICACION", mensaje, causa);
        this.detalles = List.of();
    }

    public List<String> detalles() {
        return detalles;
    }
}
