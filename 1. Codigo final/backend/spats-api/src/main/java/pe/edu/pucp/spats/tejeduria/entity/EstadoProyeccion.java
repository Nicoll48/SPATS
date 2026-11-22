package pe.edu.pucp.spats.tejeduria.entity;

/**
 * Dominio de {@code proyeccion_tejeduria.estado} (D-52). Una semana puede tener varias versiones cargadas y
 * cada una puede tener su propia planificación.
 * <ul>
 *   <li>VIGENTE: la versión de la planificación vigente de la semana. Si la semana no tiene vigente, la que se
 *       usa por omisión al generar (la última cargada).</li>
 *   <li>CARGADA: cualquier otra versión. Se puede planificar y usar como vigente.</li>
 *   <li>ELIMINADA: sigue en el historial y se puede volver a ella, pero no se planifica (D-46).</li>
 * </ul>
 */
public enum EstadoProyeccion { VIGENTE, CARGADA, ELIMINADA }
