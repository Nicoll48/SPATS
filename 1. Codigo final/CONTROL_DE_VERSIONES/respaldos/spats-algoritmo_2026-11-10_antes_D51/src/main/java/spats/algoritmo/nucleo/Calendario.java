package spats.algoritmo.nucleo;

/** Ubicación de las partidas conformadas en los días de la semana. */
public final class Calendario {
    /** Día de cada partida (1 = lunes); -1 si no se conformó. */
    public int[] dia;
    /** true si la partida es Sobrante. */
    public boolean[] sobrante;
    /** Partidas asignadas (no Sobrantes) de cada máquina en cada día [M][ND]. */
    public int[][] partidasPorDia;
}
