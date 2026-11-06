package spats.algoritmo.salida;

import java.time.LocalDate;

import spats.algoritmo.entrada.Tonalidad;

/**
 * Partida conformada y su ubicación en el calendario.
 *
 * @param partida          código de la partida
 * @param pedido           pedido al que pertenece
 * @param fechaEntrega     fecha de entrega del pedido
 * @param barca            barca de la partida
 * @param maquina          máquina asignada
 * @param fecha            día del calendario en que se ubica
 * @param estado           Asignada o Sobrante
 * @param fechaDisponible  día en que la tela permitió completarla
 * @param ordenConformacion orden en que recibió la tela
 * @param tonalidad        tonalidad del color
 * @param requiereLavado   true si la máquina debe lavarse antes de esta partida
 */
public record AsignacionPartida(String partida, String pedido, LocalDate fechaEntrega, String barca, String maquina,
                                LocalDate fecha, EstadoAsignacion estado, LocalDate fechaDisponible,
                                int ordenConformacion, Tonalidad tonalidad, boolean requiereLavado) { }
