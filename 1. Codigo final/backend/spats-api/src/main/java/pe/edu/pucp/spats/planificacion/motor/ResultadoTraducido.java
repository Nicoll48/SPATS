package pe.edu.pucp.spats.planificacion.motor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import pe.edu.pucp.spats.planificacion.entity.Enumeraciones.ClasificacionResultado;
import spats.algoritmo.salida.Indicadores;

/**
 * Resultado del algoritmo traducido a las filas que se guardan en la base (sección 9.3 del contexto).
 * Usa códigos de negocio; {@code GuardadoPlanificacion} los resuelve a entidades.
 */
public record ResultadoTraducido(List<FilaPartida> partidas,
                                 List<String> bloqueadas,
                                 List<FilaMaquina> maquinas,
                                 List<FilaBolsa> bolsas,
                                 Indicadores indicadores,
                                 List<String> advertencias) {

    /** Fila de {@code planificacion_partida} para una partida conformada (ASIGNADA o SOBRANTE). */
    public record FilaPartida(String numeroPartida, String codigoMaquina, LocalDate diaAbastecimiento,
                              ClasificacionResultado clasificacion, int ordenSecuencia, boolean requiereLavado,
                              LocalDate fechaDisponible) { }

    /** Fila de {@code planificacion_maquina}. */
    public record FilaMaquina(String codigoMaquina, int objetivoAplicado, int minimoDiario, int maximoDiario,
                              int asignadas, int sobrantes, int diferencia, int lavados) { }

    /**
     * Fila de {@code bolsa_articulo}: saldo de la bolsa de un artículo en un día.
     * {@code devueltos}: tela que vuelve a la bolsa el día D de una re-planificación (D-30); 0 en los demás.
     */
    public record FilaBolsa(String codigoArticulo, LocalDate fecha, BigDecimal saldoInicial, BigDecimal ingresados,
                            BigDecimal devueltos, BigDecimal consumidos, BigDecimal saldoFinal) { }
}
