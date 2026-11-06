package spats.algoritmo.entrada;

import java.time.LocalDate;

/**
 * Partida conformada antes del día de inicio de la re-planificación: su tela ya está separada.
 *
 * @param partida           código de la partida
 * @param fechaConformacion día en que se conformó
 * @param ubicableDesde     primer día en que puede ubicarse (nunca antes del día de inicio)
 */
public record ConformacionConservada(String partida, LocalDate fechaConformacion, LocalDate ubicableDesde) { }
