-- Revierte D-55 (primer RF30): elimina la tabla de recomendaciones de tejido y avisos.
-- No restaura ningún respaldo: solo quita lo que agregó migracion_2026-11-12_recomendaciones_tejido.sql.
BEGIN;
DROP INDEX IF EXISTS ix_recomendacion_tejido_planificacion;
DROP TABLE IF EXISTS recomendacion_tejido;
COMMIT;
