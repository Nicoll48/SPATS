package pe.edu.pucp.spats.maquina.entity;

/**
 * Tipo de la indisponibilidad ({@code indisponibilidad_maquina.tipo_indisponibilidad}). Determina el estado
 * que toma la máquina durante el intervalo (RF17).
 */
public enum TipoIndisponibilidad {
    INDISPONIBLE(EstadoMaquina.INDISPONIBLE),
    MANTENIMIENTO(EstadoMaquina.EN_MANTENIMIENTO);

    private final EstadoMaquina estadoResultante;

    TipoIndisponibilidad(EstadoMaquina estadoResultante) {
        this.estadoResultante = estadoResultante;
    }

    public EstadoMaquina estadoResultante() { return estadoResultante; }
}
