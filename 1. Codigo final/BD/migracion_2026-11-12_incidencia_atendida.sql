-- D-54: una incidencia pendiente se puede cerrar sin re-planificar (estado ATENDIDA).
-- Aplicar sobre una base existente; BD.sql ya trae el CHECK nuevo.
BEGIN;
ALTER TABLE incidencia DROP CONSTRAINT IF EXISTS incidencia_estado_check;
ALTER TABLE incidencia ADD CONSTRAINT incidencia_estado_check CHECK (estado IN ('PENDIENTE', 'CONSIDERADA', 'ATENDIDA'));
COMMIT;
