package pe.edu.pucp.spats.comun.dominio;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pe.edu.pucp.spats.comun.error.ReglaNegocioException;

/**
 * Barca de una partida o máquina, p. ej. {@code "400 C"}: capacidad en kg y clasificación de color. Desde D-51
 * la compatibilidad usa solo el número (la capacidad); la letra es informativa (RN-06).
 */
public record Barca(BigDecimal capacidad, ClasificacionColor clasificacion) {

    private static final Pattern FORMATO = Pattern.compile("^\\s*(\\d+(?:[.,]\\d+)?)\\s+([CBcb])\\s*$");

    /** Interpreta el texto; vacío si no tiene el formato "capacidad letra". */
    public static Optional<Barca> interpretar(String texto) {
        if (texto == null) return Optional.empty();
        Matcher m = FORMATO.matcher(texto);
        if (!m.matches()) return Optional.empty();
        return Optional.of(new Barca(new BigDecimal(m.group(1).replace(',', '.')),
                ClasificacionColor.valueOf(m.group(2).toUpperCase())));
    }

    private static final Pattern NUMERO_INICIAL = Pattern.compile("^\\s*(\\d+(?:[.,]\\d+)?)");

    /**
     * Número con que empieza la barca (D-51): de "600 C", "600 B" o "600" se toma 600. Es lo único que usa la
     * compatibilidad; la letra es informativa. Vacío si no empieza con un número.
     */
    public static Optional<BigDecimal> capacidadDe(String texto) {
        if (texto == null) return Optional.empty();
        Matcher m = NUMERO_INICIAL.matcher(texto);
        return m.find() ? Optional.of(new BigDecimal(m.group(1).replace(',', '.'))) : Optional.empty();
    }

    /**
     * Barca de una partida (D-51): debe empezar con un número. Devuelve la forma normalizada ("400 C" si tiene
     * capacidad y letra; si no, el texto sin espacios sobrantes), o null si viene vacía.
     */
    public static String validadaPorCapacidad(String texto) {
        if (texto == null || texto.isBlank()) return null;
        if (capacidadDe(texto).isEmpty())
            throw new ReglaNegocioException("BARCA_INVALIDA",
                    "La barca «" + texto.trim() + "» debe empezar con la capacidad en kg, por ejemplo «400 C».");
        return normalizar(texto);
    }

    /** Forma normalizada con la que se guarda y se compara: "400 C". */
    public String texto() {
        return capacidad.stripTrailingZeros().toPlainString() + " " + clasificacion.name();
    }

    public static String normalizar(String texto) {
        return interpretar(texto).map(Barca::texto).orElse(texto == null ? null : texto.trim());
    }

    /**
     * Valida una barca opcional: debe tener el formato "capacidad letra" y su letra debe coincidir con la
     * clasificación de color de su dueño (D-07). Devuelve la forma normalizada, o null si viene vacía.
     *
     * @param duenio "la máquina" o "el color", para el mensaje
     */
    public static String validada(String texto, ClasificacionColor clasificacion, String duenio) {
        if (texto == null || texto.isBlank()) return null;
        Barca barca = interpretar(texto).orElseThrow(() -> new ReglaNegocioException("BARCA_INVALIDA",
                "La barca «" + texto + "» no tiene el formato capacidad y letra, por ejemplo «400 C»."));
        if (barca.clasificacion() != clasificacion)
            throw new ReglaNegocioException("BARCA_INCOMPATIBLE", "La barca «" + barca.texto()
                    + "» no coincide con la clasificación " + clasificacion.etiqueta() + " de " + duenio + ".");
        return barca.texto();
    }
}
