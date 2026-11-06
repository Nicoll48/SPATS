package spats.algoritmo.entrada;

import java.text.Normalizer;

/**
 * Tonalidad del color de una partida, ordenada de la más clara a la más oscura.
 * Pasar en una misma máquina de una tonalidad a otra más clara requiere lavado (RD-06).
 */
public enum Tonalidad {
    BLANCO(1, "Blanco"), CLARO(2, "Claro"), MEDIO(3, "Medio"), OSCURO(4, "Oscuro");

    private final int rango;
    private final String nombre;

    Tonalidad(int rango, String nombre) { this.rango = rango; this.nombre = nombre; }

    /** 1 = Blanco, 2 = Claro, 3 = Medio, 4 = Oscuro. */
    public int rango() { return rango; }

    public String nombre() { return nombre; }

    /** Acepta el nombre ("Claro") o el rango ("2"). */
    public static Tonalidad desdeTexto(String texto) {
        if (texto == null || texto.isBlank()) throw new IllegalArgumentException("Tonalidad vacía");
        String t = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        for (Tonalidad x : values())
            if (x.nombre.equalsIgnoreCase(t) || String.valueOf(x.rango).equals(t)) return x;
        throw new IllegalArgumentException("Tonalidad no reconocida: " + texto);
    }
}
