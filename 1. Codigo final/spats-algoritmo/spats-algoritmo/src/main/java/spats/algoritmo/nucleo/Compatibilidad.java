package spats.algoritmo.nucleo;

import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import spats.algoritmo.entrada.Maquina;
import spats.algoritmo.entrada.Partida;
import spats.algoritmo.entrada.Tonalidad;

/**
 * Compatibilidad entre partida y máquina (D-51): el número de la barca de la partida es igual a la capacidad de la
 * máquina ("600 C" y "600 B" dan 600) y la máquina acepta la tonalidad de la partida. La letra de la barca no se usa.
 */
public final class Compatibilidad {

    private static final Pattern NUMERO_INICIAL = Pattern.compile("^\\s*(\\d+(?:[.,]\\d+)?)");
    private static final double TOLERANCIA = 1e-6;

    private Compatibilidad() { }

    /** Número con que empieza la barca; vacío si no empieza con un número. */
    public static OptionalDouble capacidadDeBarca(String barca) {
        if (barca == null) return OptionalDouble.empty();
        Matcher m = NUMERO_INICIAL.matcher(barca);
        return m.find() ? OptionalDouble.of(Double.parseDouble(m.group(1).replace(',', '.'))) : OptionalDouble.empty();
    }

    public static boolean compatible(Maquina m, String barcaPartida, Tonalidad tonalidad) {
        OptionalDouble cap = capacidadDeBarca(barcaPartida);
        return cap.isPresent() && Math.abs(cap.getAsDouble() - m.capacidad()) < TOLERANCIA
                && tonalidad != null && m.tonalidadesAceptadas().contains(tonalidad);
    }

    public static boolean compatible(Maquina m, Partida p) {
        return compatible(m, p.barca(), p.tonalidad());
    }
}
