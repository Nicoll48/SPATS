package spats.algoritmo.entrada;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Máquina de tintorería.
 *
 * <p>Compatibilidad (D-51): una partida puede ir a la máquina si el número de su barca es igual a la
 * {@code capacidad} de la máquina y su tonalidad está en {@code tonalidadesAceptadas}. La letra de la barca
 * (C/B) es solo informativa.</p>
 *
 * @param codigo                identificador (p. ej. "M400-1")
 * @param capacidad             capacidad en kg; la compara con el número de la barca de la partida
 * @param tipo                  tipo de color que tiñe ("C" color, "B" blanco); informativo
 * @param barca                 barca de la máquina (capacidad y tipo, p. ej. "400 C"); informativa
 * @param objetivoSemanal       partidas por semana que define el jefe de planeamiento
 * @param tonalidadesAceptadas  tonalidades que la máquina puede teñir; si llega null, las cuatro
 */
public record Maquina(String codigo, double capacidad, String tipo, String barca, int objetivoSemanal,
                      Set<Tonalidad> tonalidadesAceptadas) {

    public Maquina {
        tonalidadesAceptadas = tonalidadesAceptadas == null ? todas()
                : Collections.unmodifiableSet(tonalidadesAceptadas.isEmpty()
                        ? EnumSet.noneOf(Tonalidad.class) : EnumSet.copyOf(tonalidadesAceptadas));
    }

    /** Máquina que acepta las cuatro tonalidades. */
    public Maquina(String codigo, double capacidad, String tipo, String barca, int objetivoSemanal) {
        this(codigo, capacidad, tipo, barca, objetivoSemanal, null);
    }

    public static Set<Tonalidad> todas() {
        return Collections.unmodifiableSet(EnumSet.allOf(Tonalidad.class));
    }
}
