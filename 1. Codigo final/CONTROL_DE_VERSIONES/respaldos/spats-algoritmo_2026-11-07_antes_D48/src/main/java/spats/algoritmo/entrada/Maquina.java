package spats.algoritmo.entrada;

/**
 * Máquina de tintorería.
 *
 * @param codigo          identificador (p. ej. "M400-1")
 * @param capacidad       capacidad en kg
 * @param tipo            tipo de color que tiñe ("C" color, "B" blanco)
 * @param barca           barca de la máquina (capacidad y tipo, p. ej. "400 C")
 * @param objetivoSemanal partidas por semana que define el jefe de planeamiento
 */
public record Maquina(String codigo, double capacidad, String tipo, String barca, int objetivoSemanal) { }
