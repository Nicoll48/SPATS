package spats.algoritmo.entrada;

/** Estado de la partida al iniciar la semana. Solo las pendientes se evalúan; las arrastradas se excluyen (RF47). */
public enum EstadoPartida {
    PENDIENTE, PLANIFICADA, COMPLETADA, ARRASTRADA;

    public static EstadoPartida desdeTexto(String texto) {
        if (texto == null || texto.isBlank()) return PENDIENTE;
        return valueOf(texto.trim().toUpperCase());
    }
}
