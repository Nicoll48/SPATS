package spats.algoritmo.entrada;

import java.time.LocalDate;

/** Partida que no puede conformarse antes de {@code disponibleDesde} (HU-18 C8). */
public record DisponibilidadPartida(String partida, LocalDate disponibleDesde) { }
