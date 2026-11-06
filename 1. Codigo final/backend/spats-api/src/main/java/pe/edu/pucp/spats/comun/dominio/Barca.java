package pe.edu.pucp.spats.comun.dominio;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pe.edu.pucp.spats.comun.error.ReglaNegocioException;

/**
 * Barca de una partida o máquina, p. ej. {@code "400 C"}: capacidad en kg y clasificación de color
 * (RN-06). Una partida solo va a una máquina con su misma barca.
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
