package spats.algoritmo.nucleo;

import spats.algoritmo.entrada.Partida;

/** Partida pendiente que no puede conformarse en la semana y el motivo (RF34). */
public record PartidaExcluida(Partida partida, String motivo) {
    public static final String SIN_MAQUINA = "No existe máquina de su barca";
    public static final String SIN_TELA = "La tela de la semana no alcanza para completarla";
}
