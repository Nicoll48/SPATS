package spats.algoritmo.entrada;

import java.time.LocalDate;

/**
 * Partida ya ubicada que la re-planificación conserva tal cual.
 *
 * @param partida         código de la partida
 * @param maquina         máquina en que está ubicada
 * @param fecha           día en que está ubicada
 * @param sobrante        true si es Sobrante; false si es Asignada
 * @param fechaDisponible día en que se conformó (su tela salió de la bolsa ese día)
 * @param ordenSecuencia  posición dentro de la máquina y el día
 */
public record AsignacionFija(String partida, String maquina, LocalDate fecha, boolean sobrante,
                             LocalDate fechaDisponible, int ordenSecuencia) { }
