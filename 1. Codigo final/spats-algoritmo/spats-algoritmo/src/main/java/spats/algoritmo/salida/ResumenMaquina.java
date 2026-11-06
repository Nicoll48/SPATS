package spats.algoritmo.salida;

/**
 * Carga de una máquina en la semana.
 *
 * @param maquina       código
 * @param barca         barca
 * @param objetivo      objetivo semanal efectivo
 * @param minimoDiario  mínimo de partidas por día hábil
 * @param maximoDiario  máximo de partidas por día hábil
 * @param asignadas     partidas dentro del objetivo (clasificación Asignada)
 * @param sobrantes     partidas Sobrantes
 * @param partidasPorDia partidas asignadas por día (−1 si el día es indisponible)
 */
public record ResumenMaquina(String maquina, String barca, int objetivo, int minimoDiario, int maximoDiario,
                             int asignadas, int sobrantes, int[] partidasPorDia) {

    public int total() { return asignadas + sobrantes; }

    /** Partidas recibidas menos el objetivo (negativo: faltan; positivo: exceden). */
    public int diferencia() { return total() - objetivo; }
}
