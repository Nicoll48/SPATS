package pe.edu.pucp.spats.incidencia.entity;

/**
 * Dominio de {@code incidencia.estado}. PENDIENTE hasta que una re-planificación confirmada (o la generación de una
 * semana que aún no empieza) la considere: entonces pasa a CONSIDERADA. ATENDIDA: el planificador la cerró sin
 * re-planificar, porque la resolvió en planta (D-54). ANULADA: el planificador la canceló (registrada por error o ya no
 * ocurrió); nunca más se aplica y su efecto se deshace al volver a generar o re-planificar (D-57).
 */
public enum EstadoIncidencia { PENDIENTE, CONSIDERADA, ATENDIDA, ANULADA }
