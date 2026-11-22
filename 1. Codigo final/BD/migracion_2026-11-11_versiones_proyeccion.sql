-- =====================================================================
-- Migración de una base ya creada (no borra datos). Las bases nuevas no la
-- necesitan: BD.sql ya incluye este cambio.
--   D-52: versiones de la proyección de una semana.
--     * proyeccion_tejeduria.estado: CARGADA reemplaza a REEMPLAZADA. Una
--       versión que no es la vigente ya no está "reemplazada": se puede
--       planificar y usar como vigente.
--     * planificacion_semanal.estado admite CANDIDATA.
--     * bitacora_auditoria.tipo_operacion admite CAMBIAR_VIGENTE.
-- Se puede volver a ejecutar.
-- =====================================================================
BEGIN;

ALTER TABLE proyeccion_tejeduria DROP CONSTRAINT IF EXISTS proyeccion_tejeduria_estado_check;
UPDATE proyeccion_tejeduria SET estado = 'CARGADA' WHERE estado = 'REEMPLAZADA';
ALTER TABLE proyeccion_tejeduria ADD CONSTRAINT proyeccion_tejeduria_estado_check
    CHECK (estado IN ('VIGENTE', 'CARGADA', 'ELIMINADA'));

-- La VIGENTE es la de la planificación vigente de la semana, si la hay.
UPDATE proyeccion_tejeduria p SET estado = 'CARGADA'
 WHERE p.estado = 'VIGENTE'
   AND EXISTS (SELECT 1 FROM planificacion_semanal ps
                WHERE ps.id_semana = p.id_semana AND ps.es_vigente AND ps.id_proyeccion <> p.id_proyeccion);
UPDATE proyeccion_tejeduria p SET estado = 'VIGENTE'
 WHERE p.estado = 'CARGADA'
   AND EXISTS (SELECT 1 FROM planificacion_semanal ps WHERE ps.id_proyeccion = p.id_proyeccion AND ps.es_vigente);

ALTER TABLE planificacion_semanal DROP CONSTRAINT IF EXISTS planificacion_semanal_estado_check;
ALTER TABLE planificacion_semanal ADD CONSTRAINT planificacion_semanal_estado_check
    CHECK (estado IN ('ABIERTA', 'CERRADA', 'REEMPLAZADA', 'INVALIDADA', 'PROPUESTA', 'CANDIDATA'));

ALTER TABLE bitacora_auditoria DROP CONSTRAINT IF EXISTS bitacora_auditoria_tipo_operacion_check;
ALTER TABLE bitacora_auditoria ADD CONSTRAINT bitacora_auditoria_tipo_operacion_check
    CHECK (tipo_operacion IN (
        'INICIAR_SESION', 'IMPORTAR', 'CREAR', 'MODIFICAR',
        'GENERAR', 'INVALIDAR', 'CERRAR', 'COMPLETAR',
        'REGISTRAR', 'RESTAURAR',
        'PROPONER', 'CONFIRMAR', 'DESCARTAR', 'ELIMINAR',
        'CAMBIAR_VIGENTE'));

COMMIT;
