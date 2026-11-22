package spats.algoritmo.salida;

import java.time.LocalDate;

/** Partida pendiente que no se conformó en la semana y el motivo. */
public record PartidaBloqueada(String partida, String pedido, LocalDate fechaEntrega, String barca, String motivo) {
    public static final String TELA_PRIORIZADA =
            "La tela acumulada no alcanzó para completarla después de abastecer a otras partidas";
}
