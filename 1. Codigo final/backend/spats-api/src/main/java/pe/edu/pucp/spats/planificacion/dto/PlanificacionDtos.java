package pe.edu.pucp.spats.planificacion.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.NotNull;

import pe.edu.pucp.spats.comun.dominio.ClasificacionColor;
import pe.edu.pucp.spats.partida.entity.TipoComponente;
import pe.edu.pucp.spats.partida.entity.TonalidadColor;
import pe.edu.pucp.spats.planificacion.entity.Enumeraciones.ClasificacionResultado;
import pe.edu.pucp.spats.planificacion.entity.Enumeraciones.EstadoConfirmacionCierre;
import pe.edu.pucp.spats.planificacion.entity.Enumeraciones.EstadoPlanificacion;
import pe.edu.pucp.spats.planificacion.entity.Enumeraciones.TipoGeneracion;
import pe.edu.pucp.spats.tejeduria.entity.EstadoProyeccion;

/**
 * Contrato JSON de la planificación semanal. Todo lo que muestra sale de lo guardado en la base; las
 * pantallas no recalculan indicadores, saldos ni clasificaciones (regla 4 del contexto).
 */
public final class PlanificacionDtos {

    /** Resultado de eliminar una versión de la proyección (D-44). */
    public record VersionProyeccionEliminadaResponse(int version, int registros, int planificaciones) { }

    private PlanificacionDtos() { }

    public record IndicadoresResponse(BigDecimal valorFuncionObjetivo, BigDecimal desbalanceSemanal,
                                      BigDecimal desbalanceDiario, Integer numeroLavados, Integer evaluaciones,
                                      Long tiempoEjecucionMs) { }

    /**
     * Resultado de una generación: la versión creada y los avisos para el planificador.
     *
     * @param vigente           false si quedó como candidata porque se generó con otra versión de la proyección (D-52)
     * @param versionProyeccion versión de la proyección con la que se generó (V1, V2, ...)
     */
    public record GeneracionResponse(Integer idPlanificacion, int numeroVersion, boolean vigente, int versionProyeccion,
                                     IndicadoresResponse indicadores, boolean detenidaPorTiempo, List<String> advertencias) { }

    public record ResumenResponse(int asignadas, int sobrantes, int bloqueadas, int maquinasEnObjetivo,
                                  int totalMaquinas, int lavados) { }

    /** Partida ubicada en la cola de una máquina y un día, en su orden de secuencia (RN-15). */
    public record PartidaCalendarioResponse(String numeroPartida, String numeroOp, LocalDate fechaEntrega,
                                            String codigoColor, String color, ClasificacionColor clasificacionColor,
                                            TonalidadColor tonalidad, BigDecimal kg,
                                            ClasificacionResultado clasificacion, boolean requiereLavado,
                                            int ordenSecuencia, LocalDate fechaDisponible,
                                            EstadoConfirmacionCierre estadoConfirmacionCierre) { }

    /**
     * @param indisponible  el día está cubierto por una indisponibilidad registrada de la máquina
     * @param regulares     partidas Asignadas del día (las Sobrantes no cuentan para el reparto diario)
     */
    public record DiaMaquinaResponse(LocalDate fecha, boolean indisponible, String motivoIndisponibilidad,
                                     int regulares, List<PartidaCalendarioResponse> partidas) { }

    public record MaquinaPlanResponse(String codigo, String barca, ClasificacionColor clasificacionColor,
                                      BigDecimal capacidad, int objetivoAplicado, int minimoDiario, int maximoDiario,
                                      int asignadas, int sobrantes, int diferencia, int lavados,
                                      List<DiaMaquinaResponse> dias) { }

    /** @param kgSaldoFinalArticulo saldo final del domingo de la bolsa del artículo en esta versión */
    public record ComponenteBloqueadaResponse(String codigoArticulo, String nombreArticulo,
                                              TipoComponente tipoComponente, BigDecimal kgRequeridos,
                                              BigDecimal kgSaldoFinalArticulo) { }

    /** @param motivo motivo del algoritmo (D-51); null en versiones generadas antes de guardarlo */
    public record PartidaBloqueadaResponse(String numeroPartida, String numeroOp, LocalDate fechaEntrega,
                                           String color, TonalidadColor tonalidad, String barca, BigDecimal kg,
                                           List<ComponenteBloqueadaResponse> componentes, String motivo) { }

    /** Distribución de Asignadas y Sobrantes por clasificación de color (RF40). */
    public record DistribucionColorResponse(ClasificacionColor clasificacionColor, int asignadas, int sobrantes) { }

    public enum TipoAlerta { FALTANTE, EXCEDENTE, DESBALANCE_DIARIO }

    /** Desviación respecto del objetivo (HU-13 C1) o del reparto diario (RN-09). */
    public record AlertaResponse(TipoAlerta tipo, String maquina, int cantidad, String mensaje) { }

    public record PlanificacionDetalleResponse(Integer id, LocalDate lunes, LocalDate domingo, int numeroVersion,
                                               TipoGeneracion tipoGeneracion, LocalDateTime fechaGeneracion,
                                               boolean esVigente, EstadoPlanificacion estado,
                                               LocalDateTime fechaCierre, int versionProyeccion,
                                               IndicadoresResponse indicadores, ResumenResponse resumen,
                                               List<LocalDate> dias, List<MaquinaPlanResponse> maquinas,
                                               List<PartidaBloqueadaResponse> bloqueadas,
                                               List<DistribucionColorResponse> distribucionColor,
                                               List<AlertaResponse> alertas) { }

    public record VersionResponse(Integer id, LocalDate lunes, int numeroVersion, TipoGeneracion tipoGeneracion,
                                  LocalDateTime fechaGeneracion, boolean esVigente, EstadoPlanificacion estado,
                                  LocalDateTime fechaCierre, int versionProyeccion, IndicadoresResponse indicadores,
                                  int asignadas, int sobrantes, int bloqueadas) { }

    public record ProyeccionResumen(Integer id, int version, LocalDateTime fechaCarga) { }

    public record PlanificacionResumen(Integer id, int numeroVersion, EstadoPlanificacion estado,
                                       LocalDateTime fechaGeneracion, Integer idProyeccion) { }

    /**
     * Estado de una semana para habilitar las acciones de la pantalla.
     *
     * @param proyeccionDesactualizada la planificación vigente se generó con una proyección que ya no es la vigente
     */
    public record EstadoSemanaResponse(LocalDate lunes, LocalDate domingo, ProyeccionResumen proyeccion,
                                       PlanificacionResumen planificacionVigente, int versiones,
                                       boolean proyeccionDesactualizada, String stockDesactualizado,
                                       PlanificacionResumen propuestaPendiente, List<String> maquinasCambiadas) {
        /** @param maquinasCambiadas máquinas con tonalidades aceptadas cambiadas después de generar la vigente (D-51) */
        public EstadoSemanaResponse { maquinasCambiadas = maquinasCambiadas == null ? List.of() : List.copyOf(maquinasCambiadas); }
    }

    // ------------------------------------------------------------ re-planificación (HU-18)
    /**
     * @param incidencias       incidencias PENDIENTE que se consideran juntas (RF52)
     * @param partidasEnMaquina partidas del día D que ya entraron a máquina: quedan fijas (D-31)
     */
    /** @param porAnulacion re-planificar desde hoy para deshacer una incidencia anulada: no exige incidencias (D-57) */
    public record ReplanificacionRequest(@NotNull(message = "Indica las incidencias.") List<Integer> incidencias,
                                         List<String> partidasEnMaquina, Boolean porAnulacion) {
        public ReplanificacionRequest(List<Integer> incidencias, List<String> partidasEnMaquina) {
            this(incidencias, partidasEnMaquina, false);
        }
    }

    /** Partida ubicada hoy en la vigente: el planificador puede marcarla como ya en máquina. */
    public record PartidaDelDiaResponse(String numeroPartida, String maquina, ClasificacionResultado clasificacion,
                                        Integer ordenSecuencia) { }

    /** Lo que la pantalla necesita para re-planificar: el día D, si se puede, las partidas de hoy y la propuesta pendiente. */
    public record ContextoReplanificacionResponse(LocalDate dia, boolean permitida, String motivo, Integer idVigente,
                                                  List<PartidaDelDiaResponse> partidasDelDia,
                                                  PlanificacionResumen propuestaPendiente) { }

    public enum ResultadoReplanificacion { PROPUESTA, SE_CONSERVA_VIGENTE }

    /**
     * Resultado de re-planificar (RF55). Con PROPUESTA, la versión queda guardada sin ser vigente hasta que se
     * confirme el mismo día. Con SE_CONSERVA_VIGENTE no se guarda nada.
     *
     * @param fVigente         F de la vigente evaluada sobre la misma instancia nueva (semana completa, D-28)
     * @param vigenteFactible  false si la vigente ya no cumple las reglas en la instancia nueva
     */
    public record ReplanificacionResponse(ResultadoReplanificacion resultado, LocalDate dia, Integer idPropuesta,
                                          Integer numeroVersion, Integer idVigente, BigDecimal fNueva, BigDecimal fVigente,
                                          boolean vigenteFactible, List<String> incumplimientosVigente,
                                          IndicadoresResponse indicadores, boolean detenidaPorTiempo,
                                          List<String> advertencias) { }

    public record ConfirmacionResponse(Integer idPlanificacion, int numeroVersion, int incidenciasConsideradas,
                                       int pedidosActualizados) { }

    // ------------------------------------------------------------ versiones de la proyección (D-52)
    /** Planificación en uso de una versión de la proyección: la vigente o su candidata. */
    public record PlanificacionDeVersionResponse(Integer id, int numeroVersion, EstadoPlanificacion estado, boolean esVigente,
                                                 TipoGeneracion tipoGeneracion, LocalDateTime fechaGeneracion,
                                                 boolean stockDesactualizado, IndicadoresResponse indicadores,
                                                 int asignadas, int sobrantes, int bloqueadas) { }

    /** @param planificacion null si la versión todavía no tiene planificación generada */
    public record VersionProyeccionPlanResponse(Integer idProyeccion, int version, LocalDateTime fechaCarga,
                                                EstadoProyeccion estado, BigDecimal kgTotales,
                                                PlanificacionDeVersionResponse planificacion) { }

    /**
     * Versiones de la proyección de la semana con su planificación (panel de versiones).
     *
     * @param cerrada si la planificación de la semana ya se cerró: todo queda en solo lectura
     */
    public record VersionesSemanaResponse(LocalDate lunes, boolean cerrada, Integer idVigente,
                                          List<VersionProyeccionPlanResponse> versiones) { }

    /**
     * Lo que el planificador debe saber antes de usar una candidata como vigente.
     *
     * @param semanaIniciada  hoy ya está dentro de la semana (o después): se re-planifica desde hoy (sección 9.5)
     * @param partidasDelDia  partidas de hoy en la vigente, para marcar las que ya entraron a máquina (D-31)
     */
    public record ContextoCambioVigenteResponse(Integer idCandidata, int numeroVersion, int versionProyeccion,
                                                Integer idVigente, Integer numeroVersionVigente,
                                                Integer versionProyeccionVigente, LocalDate dia, boolean semanaIniciada,
                                                boolean permitido, String motivo, int incidenciasPendientes,
                                                int incidenciasConsideradas, int replanificaciones,
                                                boolean stockDesactualizado, List<PartidaDelDiaResponse> partidasDelDia) { }

    /** @param partidasEnMaquina partidas de hoy que ya entraron a máquina: quedan fijas (D-31) */
    public record CambioVigenteRequest(List<String> partidasEnMaquina) { }

    /** DIRECTO: la semana no empezó y la candidata pasa a vigente. REPLANIFICACION: se propone desde hoy. */
    public enum ModoCambioVigente { DIRECTO, REPLANIFICACION }

    /** @param replanificacion resultado de la re-planificación desde hoy, solo con el modo REPLANIFICACION */
    public record CambioVigenteResponse(ModoCambioVigente modo, Integer idVigente, Integer numeroVersion,
                                        ReplanificacionResponse replanificacion) { }

    public record CierreRequest(@NotNull(message = "Indica las partidas completadas (puede ser una lista vacía).")
                                List<String> partidasCompletadas) { }

    public record CierreResponse(int completadas, int arrastradas, int pedidosCompletados) { }

    /**
     * @param semanaCierre       lunes de la semana cuyo cierre la dejó ARRASTRADA
     * @param semanasArrastrada  semanas transcurridas desde esa semana hasta la semana actual (D-27)
     */
    public record ArrastradaResponse(String numeroPartida, String numeroOp, String color, BigDecimal kg,
                                     ClasificacionResultado clasificacionOrigen, String maquina, LocalDate dia,
                                     LocalDate semanaCierre, long semanasArrastrada) { }

    public record BolsaResponse(String codigoArticulo, String nombreArticulo, LocalDate fecha, BigDecimal kgSaldoInicial,
                                BigDecimal kgIngresados, BigDecimal kgDevueltos, BigDecimal kgConsumidos,
                                BigDecimal kgSaldoFinal) { }

    public record ComparacionMaquinaResponse(String codigo, Integer asignadasA, Integer sobrantesA, Integer diferenciaA,
                                             Integer asignadasB, Integer sobrantesB, Integer diferenciaB) { }

    public record UbicacionResponse(String maquina, LocalDate dia, ClasificacionResultado clasificacion) { }

    public record CambioPartidaResponse(String numeroPartida, UbicacionResponse a, UbicacionResponse b) { }

    public record ComparacionResponse(VersionResponse a, VersionResponse b, List<ComparacionMaquinaResponse> maquinas,
                                      List<CambioPartidaResponse> cambios) { }
}
