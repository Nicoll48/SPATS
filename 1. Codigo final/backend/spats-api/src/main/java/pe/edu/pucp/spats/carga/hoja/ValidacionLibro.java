package pe.edu.pucp.spats.carga.hoja;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pe.edu.pucp.spats.comun.error.ArchivoInvalidoException;
import pe.edu.pucp.spats.comun.error.ProblemaValidacion;
import pe.edu.pucp.spats.comun.hoja.LectorLibro;

/**
 * Valida un libro contra su plantilla (RF09, HU-04 C4 y C5) y acumula los problemas encontrados. Los
 * problemas bloqueantes rechazan la importación completa (RF10); las advertencias se informan.
 */
public final class ValidacionLibro {

    private static final DateTimeFormatter DIA_MES_ANIO = DateTimeFormatter.ofPattern("d/M/uuuu");
    private static final Pattern PREFIJO_NUMERICO = Pattern.compile("^\\(\\d+\\)\\s*");

    private final Plantilla plantilla;
    private final Map<String, LectorLibro.Hoja> libro;
    private final List<ProblemaValidacion> problemas = new ArrayList<>();

    public ValidacionLibro(Plantilla plantilla, Map<String, LectorLibro.Hoja> libro) {
        this.plantilla = plantilla;
        this.libro = libro;
    }

    /** Verifica que estén todas las hojas y cabeceras; si falta alguna, rechaza el archivo de inmediato. */
    public ValidacionLibro exigirEstructura() {
        for (Plantilla.Hoja definicion : plantilla.hojas()) {
            Optional<LectorLibro.Hoja> hoja = LectorLibro.hoja(libro, definicion.nombre());
            if (hoja.isEmpty()) {
                error(definicion.nombre(), 0, "", "Falta la hoja «" + definicion.nombre() + "» de la plantilla.");
                continue;
            }
            for (String columna : definicion.columnas())
                if (!hoja.get().cabeceras().contains(columna))
                    error(definicion.nombre(), 1, columna, "Falta la cabecera obligatoria «" + columna + "».");
        }
        rechazarSiHayErrores();
        return this;
    }

    public List<LectorLibro.Fila> filas(String hoja) {
        return LectorLibro.hoja(libro, hoja).map(LectorLibro.Hoja::filas).orElse(List.of());
    }

    public void rechazarSiHayErrores() {
        if (hayErrores())
            throw new ArchivoInvalidoException("El archivo presenta errores y no se importó ningún registro.", problemas);
    }

    public boolean hayErrores() {
        return problemas.stream().anyMatch(ProblemaValidacion::bloqueante);
    }

    public List<ProblemaValidacion> advertencias() {
        return problemas.stream().filter(p -> !p.bloqueante()).toList();
    }

    public void error(String hoja, int fila, String campo, String motivo) {
        problemas.add(ProblemaValidacion.error(hoja, fila, campo, motivo));
    }

    public void advertencia(String hoja, int fila, String campo, String motivo) {
        problemas.add(ProblemaValidacion.advertencia(hoja, fila, campo, motivo));
    }

    // ------------------------------------------------------------ campos
    public String texto(String hoja, LectorLibro.Fila fila, String columna, int maximo) {
        String v = fila.valor(columna);
        if (v.isEmpty()) { error(hoja, fila.numero(), columna, "El campo es obligatorio."); return null; }
        if (v.length() > maximo) { error(hoja, fila.numero(), columna, "Admite como máximo " + maximo + " caracteres."); return null; }
        return v;
    }

    /**
     * Código de artículo (D-25). Si empieza con un prefijo numérico entre paréntesis, por ejemplo
     * «(313811)TW2021AA00P03», se quita ese prefijo y se avisa con el código original y el limpio.
     */
    public String codigoArticulo(String hoja, LectorLibro.Fila fila, String columna) {
        String original = fila.valor(columna);
        Matcher m = PREFIJO_NUMERICO.matcher(original);
        if (!m.find() || m.end() == original.length()) return texto(hoja, fila, columna, 80);
        String limpio = original.substring(m.end()).trim();
        if (limpio.length() > 80) { error(hoja, fila.numero(), columna, "Admite como máximo 80 caracteres."); return null; }
        advertencia(hoja, fila.numero(), columna, "El código «" + original + "» se registró como «" + limpio
                + "»: se quitó el prefijo numérico entre paréntesis.");
        return limpio;
    }

    /** Número positivo con como máximo {@code enteros} dígitos enteros y {@code decimales} decimales. */
    public BigDecimal positivo(String hoja, LectorLibro.Fila fila, String columna, int enteros, int decimales) {
        String v = fila.valor(columna);
        if (v.isEmpty()) { error(hoja, fila.numero(), columna, "El campo es obligatorio."); return null; }
        try {
            BigDecimal n = new BigDecimal(v.replace(',', '.')).stripTrailingZeros();
            if (n.signum() <= 0) { error(hoja, fila.numero(), columna, "Debe ser mayor que cero."); return null; }
            if (Math.max(n.scale(), 0) > decimales || n.precision() - Math.max(n.scale(), 0) > enteros) {
                error(hoja, fila.numero(), columna, "Admite hasta " + enteros + " enteros y " + decimales + " decimales.");
                return null;
            }
            return n.setScale(decimales);
        } catch (NumberFormatException e) {
            error(hoja, fila.numero(), columna, "«" + v + "» no es un número válido.");
            return null;
        }
    }

    /** Fecha en formato aaaa-mm-dd (o d/m/aaaa). */
    public LocalDate fecha(String hoja, LectorLibro.Fila fila, String columna) {
        String v = fila.valor(columna);
        if (v.isEmpty()) { error(hoja, fila.numero(), columna, "El campo es obligatorio."); return null; }
        try {
            return v.contains("/") ? LocalDate.parse(v, DIA_MES_ANIO) : LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            error(hoja, fila.numero(), columna, "«" + v + "» no es una fecha válida (use aaaa-mm-dd).");
            return null;
        }
    }

    public <E extends Enum<E>> E opcion(String hoja, LectorLibro.Fila fila, String columna, Class<E> tipo,
                                        Map<String, E> sinonimos) {
        String v = fila.valor(columna);
        if (v.isEmpty()) { error(hoja, fila.numero(), columna, "El campo es obligatorio."); return null; }
        String clave = sinAcentos(v).toUpperCase();
        if (sinonimos.containsKey(clave)) return sinonimos.get(clave);
        for (E e : tipo.getEnumConstants()) if (e.name().equals(clave)) return e;
        StringBuilder validos = new StringBuilder();
        for (E e : tipo.getEnumConstants()) validos.append(validos.isEmpty() ? "" : ", ").append(e.name());
        error(hoja, fila.numero(), columna, "«" + v + "» no es válido. Valores admitidos: " + validos + ".");
        return null;
    }

    public Boolean siNo(String hoja, LectorLibro.Fila fila, String columna) {
        String v = sinAcentos(fila.valor(columna)).toUpperCase();
        return switch (v) {
            case "SI", "S", "1", "TRUE", "VERDADERO" -> Boolean.TRUE;
            case "NO", "N", "0", "FALSE", "FALSO" -> Boolean.FALSE;
            default -> {
                error(hoja, fila.numero(), columna, v.isEmpty() ? "El campo es obligatorio." : "Use SI o NO.");
                yield null;
            }
        };
    }

    private static String sinAcentos(String texto) {
        return java.text.Normalizer.normalize(texto.trim(), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
