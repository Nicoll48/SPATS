package pe.edu.pucp.spats.maquina.entity;

/**
 * Dominio de {@code maquina_tintoreria.estado} (RF15, RF17; vacío 6 del contexto, valores propuestos).
 * El algoritmo no usa el estado: los días que una máquina no recibe partidas salen de sus intervalos de
 * indisponibilidad (RN-11).
 */
public enum EstadoMaquina {
    OPERATIVA("Operativa"),
    INDISPONIBLE("Indisponible"),
    EN_MANTENIMIENTO("En mantenimiento");

    private final String etiqueta;

    EstadoMaquina(String etiqueta) { this.etiqueta = etiqueta; }

    public String etiqueta() { return etiqueta; }
}
