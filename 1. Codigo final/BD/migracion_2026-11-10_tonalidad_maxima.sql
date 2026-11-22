-- =====================================================================
-- Migración de una base ya creada (D-50, modifica D-49).
-- Las bases nuevas no la necesitan: BD.sql ya incluye este cambio.
--   maquina_tintoreria.tonalidad_maxima: hasta qué tonalidad puede teñir la máquina.
--   Las máquinas C quedan en OSCURO (todas); las B, en CLARO, que es lo que fijaba D-49.
-- Se puede volver a ejecutar.
-- =====================================================================
BEGIN;

ALTER TABLE maquina_tintoreria ADD COLUMN IF NOT EXISTS tonalidad_maxima VARCHAR(10);

UPDATE maquina_tintoreria SET tonalidad_maxima = 'OSCURO' WHERE tonalidad_maxima IS NULL AND clasificacion_color = 'C';
UPDATE maquina_tintoreria SET tonalidad_maxima = 'CLARO'  WHERE tonalidad_maxima IS NULL AND clasificacion_color = 'B';

ALTER TABLE maquina_tintoreria ALTER COLUMN tonalidad_maxima SET NOT NULL;

ALTER TABLE maquina_tintoreria DROP CONSTRAINT IF EXISTS maquina_tintoreria_tonalidad_maxima_check;
ALTER TABLE maquina_tintoreria ADD CONSTRAINT maquina_tintoreria_tonalidad_maxima_check
    CHECK (tonalidad_maxima IN ('BLANCO', 'CLARO', 'MEDIO', 'OSCURO'));

ALTER TABLE maquina_tintoreria DROP CONSTRAINT IF EXISTS ck_maquina_tonalidad_maxima_clasificacion;
ALTER TABLE maquina_tintoreria ADD CONSTRAINT ck_maquina_tonalidad_maxima_clasificacion
    CHECK ((clasificacion_color = 'C' AND tonalidad_maxima = 'OSCURO')
        OR (clasificacion_color = 'B' AND tonalidad_maxima IN ('BLANCO', 'CLARO', 'MEDIO')));

COMMIT;
