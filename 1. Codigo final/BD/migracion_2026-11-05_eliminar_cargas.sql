-- =====================================================================
-- Migración de una base ya creada (no borra pedidos, partidas ni cargas).
-- Las bases nuevas no la necesitan: BD.sql ya incluye estos cambios.
--   D-40: id_carga en pedido, detalle_pedido, partida y proyeccion_tejeduria.
--   D-43: sale maquina_tintoreria.tiempo_procesamiento (no se usaba).
--   bitacora_auditoria.tipo_operacion suma ELIMINAR.
-- Se puede volver a ejecutar.
-- =====================================================================
BEGIN;

ALTER TABLE pedido               ADD COLUMN IF NOT EXISTS id_carga INT REFERENCES carga_datos (id_carga) ON DELETE SET NULL;
ALTER TABLE detalle_pedido       ADD COLUMN IF NOT EXISTS id_carga INT REFERENCES carga_datos (id_carga) ON DELETE SET NULL;
ALTER TABLE partida              ADD COLUMN IF NOT EXISTS id_carga INT REFERENCES carga_datos (id_carga) ON DELETE SET NULL;
ALTER TABLE proyeccion_tejeduria ADD COLUMN IF NOT EXISTS id_carga INT REFERENCES carga_datos (id_carga) ON DELETE SET NULL;

ALTER TABLE maquina_tintoreria DROP COLUMN IF EXISTS tiempo_procesamiento;

ALTER TABLE bitacora_auditoria DROP CONSTRAINT IF EXISTS bitacora_auditoria_tipo_operacion_check;
ALTER TABLE bitacora_auditoria ADD CONSTRAINT bitacora_auditoria_tipo_operacion_check
    CHECK (tipo_operacion IN (
        'INICIAR_SESION', 'IMPORTAR', 'CREAR', 'MODIFICAR',
        'GENERAR', 'INVALIDAR', 'CERRAR', 'COMPLETAR',
        'REGISTRAR', 'RESTAURAR',
        'PROPONER', 'CONFIRMAR', 'DESCARTAR', 'ELIMINAR'
    ));

-- Datos cargados antes de esta migración: solo se pueden atribuir con certeza si hubo
-- una única carga de ese tipo. Si hubo varias, quedan sin carga y se eliminan fila por fila.
UPDATE pedido SET id_carga = (SELECT min(id_carga) FROM carga_datos WHERE tipo_dato = 'PEDIDOS')
WHERE id_carga IS NULL AND (SELECT count(*) FROM carga_datos WHERE tipo_dato = 'PEDIDOS') = 1;

UPDATE detalle_pedido SET id_carga = (SELECT min(id_carga) FROM carga_datos WHERE tipo_dato = 'PEDIDOS')
WHERE id_carga IS NULL AND (SELECT count(*) FROM carga_datos WHERE tipo_dato = 'PEDIDOS') = 1;

UPDATE partida SET id_carga = (SELECT min(id_carga) FROM carga_datos WHERE tipo_dato = 'PARTIDAS')
WHERE id_carga IS NULL AND (SELECT count(*) FROM carga_datos WHERE tipo_dato = 'PARTIDAS') = 1;

UPDATE proyeccion_tejeduria SET id_carga = (SELECT min(id_carga) FROM carga_datos WHERE tipo_dato = 'PROYECCION_TEJEDURIA')
WHERE id_carga IS NULL
  AND (SELECT count(*) FROM carga_datos WHERE tipo_dato = 'PROYECCION_TEJEDURIA') = 1
  AND (SELECT count(*) FROM proyeccion_tejeduria) = 1;

COMMIT;
