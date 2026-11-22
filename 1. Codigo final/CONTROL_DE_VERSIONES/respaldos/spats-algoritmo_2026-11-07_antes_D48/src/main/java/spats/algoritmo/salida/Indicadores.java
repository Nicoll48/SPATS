package spats.algoritmo.salida;

import java.util.List;

/**
 * Indicadores de la solución entregada.
 *
 * @param funcionObjetivo   F = α·E_S + β·E_D + γ·Λ
 * @param desbalanceSemanal E_S
 * @param desbalanceDiario  E_D
 * @param lavados           Λ
 * @param objetivoTotal     suma de objetivos semanales efectivos
 * @param conformadas       partidas conformadas
 * @param asignadas         partidas dentro del objetivo (Asignada)
 * @param sobrantes         partidas Sobrantes
 * @param bloqueadas     partidas pendientes que no se conformaron
 * @param evaluaciones      soluciones evaluadas por la colonia de hormigas
 * @param iteraciones       iteraciones de la colonia de hormigas
 * @param tiempoMs          tiempo de ejecución
 * @param detenidaPorTiempo true si se alcanzó el tiempo máximo
 * @param incumplimientos   reglas incumplidas según el validador (vacía si es factible)
 */
public record Indicadores(double funcionObjetivo, int desbalanceSemanal, int desbalanceDiario, int lavados,
                          int objetivoTotal, int conformadas, int asignadas, int sobrantes, int bloqueadas,
                          int evaluaciones, int iteraciones, long tiempoMs, boolean detenidaPorTiempo,
                          List<String> incumplimientos) {

    public boolean factible() { return incumplimientos.isEmpty(); }
}
