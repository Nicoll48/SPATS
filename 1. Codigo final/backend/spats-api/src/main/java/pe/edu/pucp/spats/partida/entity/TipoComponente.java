package pe.edu.pucp.spats.partida.entity;

/** Tela principal o complemento (rib, cuello, puño…) de una partida. */
public enum TipoComponente {
    PRINCIPAL("Principal"),
    COMPLEMENTO("Complemento");

    private final String rolAlgoritmo;

    TipoComponente(String rolAlgoritmo) { this.rolAlgoritmo = rolAlgoritmo; }

    /** Texto del rol que espera {@code spats.algoritmo.entrada.Componente}. */
    public String rolAlgoritmo() { return rolAlgoritmo; }
}
