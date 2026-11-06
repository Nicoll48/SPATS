package spats.algoritmo.nucleo;

/** Resultado de simular la semana con una estrategia: conformación y máquina de cada partida. */
public final class Solucion {
    /** Día de conformación (1 = lunes); -1 si la partida no se conformó. */
    public int[] diaConformacion;
    /** Máquina asignada; -1 si la partida no se conformó. */
    public int[] maquina;
    /** Orden en que la partida recibió la tela (1, 2, ...); -1 si no se conformó. */
    public int[] orden;
    /** kg que quedan en cada bolsa al terminar la semana (memoria para la semana siguiente). */
    public double[] stockFinal;

    public boolean conformada(int p) { return maquina[p] >= 0; }
}
