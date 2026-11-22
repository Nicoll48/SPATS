-- =====================================================================
-- Migración de una base ya creada (D-51, reemplaza a D-49 y D-50).
-- Las bases nuevas no la necesitan: BD.sql ya incluye estos cambios.
--   * maquina_tonalidad_aceptada (nueva tabla): a todas las máquinas
--     existentes se les registran las cuatro tonalidades.
--   * Sale maquina_tintoreria.tonalidad_maxima (D-50), si existe.
--   * planificacion_partida.motivo_bloqueo: motivo de las BLOQUEADA.
-- Se puede volver a ejecutar.
-- =====================================================================
BEGIN;

CREATE TABLE IF NOT EXISTS maquina_tonalidad_aceptada (
    id_maquina  INT NOT NULL REFERENCES maquina_tintoreria (id_maquina) ON DELETE CASCADE,
    tonalidad   VARCHAR(10) NOT NULL
                CHECK (tonalidad IN ('BLANCO', 'CLARO', 'MEDIO', 'OSCURO')),
    PRIMARY KEY (id_maquina, tonalidad)
);

-- Solo las máquinas que todavía no tienen ninguna tonalidad registrada.
INSERT INTO maquina_tonalidad_aceptada (id_maquina, tonalidad)
SELECT m.id_maquina, t.tonalidad
FROM maquina_tintoreria m
CROSS JOIN (VALUES ('BLANCO'), ('CLARO'), ('MEDIO'), ('OSCURO')) AS t (tonalidad)
WHERE NOT EXISTS (SELECT 1 FROM maquina_tonalidad_aceptada a WHERE a.id_maquina = m.id_maquina);

ALTER TABLE maquina_tintoreria DROP COLUMN IF EXISTS tonalidad_maxima;

ALTER TABLE planificacion_partida ADD COLUMN IF NOT EXISTS motivo_bloqueo VARCHAR(120);

COMMIT;
