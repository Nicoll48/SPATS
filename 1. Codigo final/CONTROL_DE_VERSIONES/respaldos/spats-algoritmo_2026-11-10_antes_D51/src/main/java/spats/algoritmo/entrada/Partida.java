package spats.algoritmo.entrada;

/**
 * Partida: unidad de trabajo que se tiñe de una sola vez en una máquina.
 *
 * @param codigo        identificador de la partida
 * @param pedido        pedido al que pertenece
 * @param codColor      código de color
 * @param color         nombre del color
 * @param peso          kg de la partida
 * @param tonalidad     tonalidad del color
 * @param barca         barca en la que puede teñirse (p. ej. "400 C")
 * @param ordenRegistro orden en que el planificador la registró
 * @param estado        estado al iniciar la semana
 */
public record Partida(String codigo, String pedido, String codColor, String color, double peso,
                      Tonalidad tonalidad, String barca, int ordenRegistro, EstadoPartida estado) {

    public Partida conEstado(EstadoPartida nuevo) {
        return new Partida(codigo, pedido, codColor, color, peso, tonalidad, barca, ordenRegistro, nuevo);
    }
}
