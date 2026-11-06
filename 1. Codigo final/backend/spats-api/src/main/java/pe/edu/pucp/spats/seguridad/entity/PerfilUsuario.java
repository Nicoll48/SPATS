package pe.edu.pucp.spats.seguridad.entity;

/**
 * Los dos perfiles del sistema (sección 4 del contexto). Planificador: todas las operaciones.
 * Consulta: solo lectura, sin niveles intermedios (RF06).
 */
public enum PerfilUsuario {
    PLANIFICADOR("Planificador"),
    CONSULTA("Consulta");

    private final String etiqueta;

    PerfilUsuario(String etiqueta) { this.etiqueta = etiqueta; }

    public String etiqueta() { return etiqueta; }
}
