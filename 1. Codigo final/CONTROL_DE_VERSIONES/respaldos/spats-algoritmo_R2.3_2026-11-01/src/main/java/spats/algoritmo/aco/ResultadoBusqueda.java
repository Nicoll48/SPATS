package spats.algoritmo.aco;

import spats.algoritmo.nucleo.Evaluacion;
import spats.algoritmo.nucleo.Solucion;

/**
 * Mejor solución encontrada por la colonia de hormigas.
 *
 * @param solucion          mejor semana encontrada
 * @param evaluacion        su función objetivo
 * @param evaluaciones      soluciones evaluadas
 * @param iteraciones       iteraciones completadas
 * @param tiempoMs          tiempo de ejecución en milisegundos
 * @param detenidaPorTiempo true si se detuvo por alcanzar el tiempo máximo
 */
public record ResultadoBusqueda(Solucion solucion, Evaluacion evaluacion, int evaluaciones, int iteraciones,
                                long tiempoMs, boolean detenidaPorTiempo) { }
