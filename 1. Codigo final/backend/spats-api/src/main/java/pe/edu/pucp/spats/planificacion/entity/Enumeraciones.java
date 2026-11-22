package pe.edu.pucp.spats.planificacion.entity;

/** Contenedor de los dominios fijos del módulo de planificación. */
public final class Enumeraciones {

    private Enumeraciones() { }

    /** {@code planificacion_semanal.tipo_generacion}. */
    public enum TipoGeneracion { INICIAL, REPLANIFICACION }

    /**
     * {@code planificacion_semanal.estado} (vacío 6 del contexto; valores propuestos).
     * <ul>
     *   <li>ABIERTA: vigente y la semana sigue abierta.</li>
     *   <li>CERRADA: vigente y la semana ya se cerró (HU-15).</li>
     *   <li>REEMPLAZADA: dejó de ser vigente porque se generó una versión nueva de la semana (D-14).</li>
     *   <li>INVALIDADA: dejó de corresponder a los datos: se corrigió un registro de su proyección (D-06) o se
     *       modificó una de sus partidas (D-24).</li>
     *   <li>PROPUESTA: re-planificación que espera la confirmación del planificador; no es vigente. Si se
     *       descarta, o no se confirma el mismo día, se elimina con todas sus filas (D-26, D-32).</li>
     *   <li>CANDIDATA: generada con una versión de la proyección distinta de la vigente; no es vigente. El
     *       planificador decide si la usa como vigente (D-52). Al cerrar la semana queda en solo lectura.</li>
     * </ul>
     */
    public enum EstadoPlanificacion { ABIERTA, CERRADA, REEMPLAZADA, INVALIDADA, PROPUESTA, CANDIDATA }

    /** {@code planificacion_partida.clasificacion_resultado} (sección 6.2). */
    public enum ClasificacionResultado { ASIGNADA, SOBRANTE, BLOQUEADA }

    /** {@code planificacion_partida.estado_confirmacion_cierre} (sección 6.3). Null mientras la semana está abierta. */
    public enum EstadoConfirmacionCierre { COMPLETADA, ARRASTRADA }
}
