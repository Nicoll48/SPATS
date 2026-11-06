package pe.edu.pucp.spats.incidencia.entity;

/** Los tres tipos de incidencia (RN-24), cada uno con su entidad afectada. */
public enum TipoIncidencia {
    /** Máquina; con fecha y hora prevista de disponibilidad. */
    INCIDENCIA_MAQUINA,
    /** Partida; con fecha y hora prevista de disponibilidad. */
    PROBLEMA_COMPLEMENTO,
    /** Pedido; con nueva fecha de entrega y sin fecha prevista de disponibilidad. */
    CAMBIO_PRIORIDAD
}
