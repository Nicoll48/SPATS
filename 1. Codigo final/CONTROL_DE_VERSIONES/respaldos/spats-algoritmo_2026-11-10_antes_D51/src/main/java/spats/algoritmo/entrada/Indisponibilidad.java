package spats.algoritmo.entrada;

import java.time.LocalDate;

/** Día en que una máquina no puede recibir partidas. */
public record Indisponibilidad(String maquina, LocalDate fecha, String motivo) { }
