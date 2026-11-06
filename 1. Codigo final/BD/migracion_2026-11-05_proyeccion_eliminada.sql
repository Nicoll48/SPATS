-- =====================================================================
-- Migración de una base ya creada (no borra datos). Las bases nuevas no la
-- necesitan: BD.sql ya incluye este cambio.
--   D-46: proyeccion_tejeduria.estado admite ELIMINADA.
-- Se puede volver a ejecutar.
-- =====================================================================
BEGIN;

ALTER TABLE proyeccion_tejeduria DROP CONSTRAINT IF EXISTS proyeccion_tejeduria_estado_check;
ALTER TABLE proyeccion_tejeduria ADD CONSTRAINT proyeccion_tejeduria_estado_check
    CHECK (estado IN ('VIGENTE', 'REEMPLAZADA', 'ELIMINADA'));

COMMIT;
