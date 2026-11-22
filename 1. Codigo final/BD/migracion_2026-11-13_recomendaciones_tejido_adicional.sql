-- D-56 (RF30, HU-13): recomendaciones de tejido adicional para la misma semana, por versión de planificación.
-- Aplicar sobre una base existente; BD.sql ya trae la tabla.
BEGIN;
CREATE TABLE recomendacion_tejido (
    id_recomendacion   SERIAL PRIMARY KEY,
    id_planificacion   INT NOT NULL REFERENCES planificacion_semanal (id_planificacion) ON DELETE CASCADE,
    id_maquina         INT NOT NULL REFERENCES maquina_tintoreria (id_maquina),
    dia_llena          DATE NOT NULL,
    id_partida         INT NOT NULL REFERENCES partida (id_partida),
    id_detalle_pedido  INT NOT NULL REFERENCES detalle_pedido (id_detalle_pedido),
    tipo_componente    VARCHAR(20) NOT NULL CHECK (tipo_componente IN ('PRINCIPAL', 'COMPLEMENTO')),
    kg_a_tejer         NUMERIC(12,3) NOT NULL CHECK (kg_a_tejer > 0),
    dia_tejido         DATE NOT NULL CHECK (dia_tejido < dia_llena),
    fecha_entrega      DATE,
    orden              INT NOT NULL CHECK (orden > 0)
);
CREATE INDEX ix_recomendacion_tejido_planificacion ON recomendacion_tejido (id_planificacion);

COMMIT;
