package pe.edu.pucp.spats.comun.dominio;

/** Tipo de color de la barca (D-07): C = color, B = blanco. Decide a qué máquinas puede ir una partida. */
public enum ClasificacionColor {
    C("Color"),
    B("Blanco");

    private final String etiqueta;

    ClasificacionColor(String etiqueta) { this.etiqueta = etiqueta; }

    public String etiqueta() { return etiqueta; }
}
