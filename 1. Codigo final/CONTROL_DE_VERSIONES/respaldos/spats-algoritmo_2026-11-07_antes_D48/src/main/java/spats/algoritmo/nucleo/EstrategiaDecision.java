package spats.algoritmo.nucleo;

/**
 * Las dos decisiones que toma el algoritmo mientras se simula la semana.
 * El simulador solo las consulta cuando existe más de una alternativa.
 */
public interface EstrategiaDecision {

    /**
     * Elige qué partida recibe la tela entre las que la tela acumulada permite completar
     * y comparten la fecha de entrega más próxima.
     *
     * @param empatadas partidas empatadas (posiciones 0..n-1)
     * @param n         cantidad de partidas empatadas (al menos 2)
     * @param carga     partidas asignadas hasta el momento a cada máquina
     * @return la partida elegida (uno de los valores de {@code empatadas})
     */
    int elegirPartida(InstanciaSemana I, int[] empatadas, int n, int[] carga);

    /**
     * Elige la máquina de la barca de la partida (la partida tiene al menos dos máquinas compatibles).
     *
     * @return la máquina elegida (uno de los valores de {@code I.compat[p]})
     */
    int elegirMaquina(InstanciaSemana I, int p, int[] carga);
}
