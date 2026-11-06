package pe.edu.pucp.spats.comun.error;

/**
 * Problema encontrado al validar un archivo importado.
 *
 * @param hoja       hoja de la plantilla
 * @param fila       fila de la hoja (1 = cabecera); 0 si el problema es del archivo completo
 * @param campo      columna afectada, o vacío
 * @param motivo     explicación para el planificador
 * @param bloqueante true si impide la importación; false si solo es una advertencia
 */
public record ProblemaValidacion(String hoja, int fila, String campo, String motivo, boolean bloqueante) {

    public static ProblemaValidacion error(String hoja, int fila, String campo, String motivo) {
        return new ProblemaValidacion(hoja, fila, campo, motivo, true);
    }

    public static ProblemaValidacion advertencia(String hoja, int fila, String campo, String motivo) {
        return new ProblemaValidacion(hoja, fila, campo, motivo, false);
    }
}
