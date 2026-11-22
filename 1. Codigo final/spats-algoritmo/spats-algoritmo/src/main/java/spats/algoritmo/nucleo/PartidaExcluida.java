package spats.algoritmo.nucleo;

import spats.algoritmo.entrada.Partida;

/** Partida pendiente que no puede conformarse en la semana y el motivo (RF34). */
public record PartidaExcluida(Partida partida, String motivo) {
    /** D-51: ninguna máquina tiene la capacidad de su barca y acepta su tonalidad. */
    public static final String SIN_MAQUINA_COMPATIBLE = "Ninguna máquina de su capacidad acepta su tonalidad";
    public static final String SIN_TELA = "La tela de la semana no alcanza para completarla";
    /** Re-planificación: no queda ningún día hábil en sus máquinas compatibles desde que puede ubicarse. */
    public static final String SIN_DIA_DISPONIBLE = "No queda ningún día hábil en su barca desde que está disponible";
    /** Re-planificación: partida con tela separada que ya no tiene día hábil; su tela vuelve a la bolsa el día D. */
    public static final String TELA_DEVUELTA =
            "No queda ningún día hábil en su barca desde que puede ubicarse; su tela vuelve a la bolsa";
}
