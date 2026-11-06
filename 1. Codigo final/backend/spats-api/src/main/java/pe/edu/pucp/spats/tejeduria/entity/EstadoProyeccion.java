package pe.edu.pucp.spats.tejeduria.entity;

/**
 * Dominio de {@code proyeccion_tejeduria.estado} (vacío 6 del contexto; valores propuestos). La versión
 * VIGENTE es la que se usa para planificar; al cargar una nueva, la anterior pasa a REEMPLAZADA y se
 * conserva como historial (D-06). Una ELIMINADA sigue en el historial y se puede volver a ella, pero no se
 * usa para planificar (D-46).
 */
public enum EstadoProyeccion { VIGENTE, REEMPLAZADA, ELIMINADA }
