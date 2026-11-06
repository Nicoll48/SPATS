package pe.edu.pucp.spats.pedido.entity;

/**
 * Dominio de {@code pedido.estado} (vacío 6 del contexto; valores propuestos). Un pedido se completa
 * automáticamente cuando todas sus partidas están COMPLETADA (sección 6.1).
 */
public enum EstadoPedido { PENDIENTE, COMPLETADO }
