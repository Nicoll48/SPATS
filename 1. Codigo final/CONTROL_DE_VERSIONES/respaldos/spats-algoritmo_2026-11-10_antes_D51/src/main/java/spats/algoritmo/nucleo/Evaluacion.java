package spats.algoritmo.nucleo;

/** Valor de la función objetivo de una solución y sus componentes. */
public final class Evaluacion {
    public double F;
    /** Desbalance semanal: suma de (partidas de la máquina − objetivo)². */
    public int desbalanceSemanal;
    /** Desbalance diario: suma de (faltante bajo el mínimo)² + (exceso sobre el máximo)². */
    public int desbalanceDiario;
    /** Pasos de una tonalidad a otra más clara en una misma máquina. */
    public int lavados;
    public int conformadas, asignadas, sobrantes;
    /** Partidas de cada máquina (asignadas y Sobrantes). */
    public int[] partidasPorMaquina;
    /** true si antes de la partida la máquina requiere lavado. */
    public boolean[] requiereLavado;
    public Calendario calendario;
}
