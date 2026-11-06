package pe.edu.pucp.spats.carga.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import pe.edu.pucp.spats.carga.entity.TipoDato;
import pe.edu.pucp.spats.comun.error.ProblemaValidacion;

public final class CargaDtos {

    private CargaDtos() { }

    /** Carga registrada (RF14), con lo que dejó: registros nuevos, existentes conservados y advertencias. */
    public record CargaResponse(Integer id, TipoDato tipo, String archivo, LocalDateTime fechaHora, String usuario,
                                Map<String, Integer> nuevos, int existentes, List<ProblemaValidacion> advertencias) { }

    /** Lo que se quitó al eliminar una carga: por tipo de registro, la cantidad eliminada. */
    public record CargaEliminadaResponse(TipoDato tipo, String archivo, Map<String, Integer> eliminados) { }

    public record CargaHistorialResponse(Integer id, TipoDato tipo, String archivo, LocalDateTime fechaHora, String usuario,
                                         boolean eliminada) { }
}
