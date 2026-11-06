package pe.edu.pucp.spats.partida.entity;

/**
 * Estado general de la partida en el tiempo (sección 6.1). Solo PENDIENTE puede entrar a una
 * planificación; ARRASTRADA pasa a COMPLETADA solo cuando el planificador la marca (RF49).
 */
public enum EstadoPartida { PENDIENTE, ARRASTRADA, COMPLETADA }
