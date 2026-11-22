package spats.algoritmo.entrada;

import java.time.LocalDate;

/** Pedido de un cliente; su fecha de entrega define la prioridad de sus partidas (RD-07). */
public record Pedido(String codigo, String cliente, LocalDate fechaPedido, LocalDate fechaEntrega) { }
