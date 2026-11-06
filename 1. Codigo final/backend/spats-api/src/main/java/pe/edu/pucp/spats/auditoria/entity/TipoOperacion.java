package pe.edu.pucp.spats.auditoria.entity;

/**
 * Dominio de {@code bitacora_auditoria.tipo_operacion}. Es el vacío 6 del contexto: los valores son una
 * propuesta, pendiente de confirmar.
 */
public enum TipoOperacion {
    INICIAR_SESION,
    IMPORTAR,
    CREAR,
    MODIFICAR,
    GENERAR,
    INVALIDAR,
    CERRAR,
    COMPLETAR,
    REGISTRAR,
    RESTAURAR,
    PROPONER,
    CONFIRMAR,
    DESCARTAR,
    ELIMINAR
}
