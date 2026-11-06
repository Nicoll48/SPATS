package pe.edu.pucp.spats.carga.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import pe.edu.pucp.spats.comun.error.ProblemaValidacion;

/**
 * Lo que dejó una importación: registros nuevos por entidad, registros que ya existían y se conservaron
 * sin reemplazarlos (RF11), y las advertencias no bloqueantes.
 */
public record ResultadoImportacion(Map<String, Integer> nuevos, int existentes, List<ProblemaValidacion> advertencias) {

    public static final class Constructor {
        private final Map<String, Integer> nuevos = new LinkedHashMap<>();
        private int existentes;

        public Constructor nuevo(String entidad) {
            nuevos.merge(entidad, 1, Integer::sum);
            return this;
        }

        public Constructor nuevos(String entidad, int cantidad) {
            if (cantidad > 0) nuevos.merge(entidad, cantidad, Integer::sum);
            return this;
        }

        public Constructor existente() {
            existentes++;
            return this;
        }

        public ResultadoImportacion construir(List<ProblemaValidacion> advertencias) {
            return new ResultadoImportacion(Map.copyOf(nuevos), existentes, List.copyOf(advertencias));
        }
    }
}
