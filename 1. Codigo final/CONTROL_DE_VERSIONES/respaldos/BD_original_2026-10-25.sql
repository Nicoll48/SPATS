-- RESPALDO: sentencias de BD/BD.sql tal como lo entregó la autora el 2026-10-25, antes de
-- las correcciones S1–S4 y P4 (ver CONTROL_DE_VERSIONES/CHANGELOG.md, versión 0.1.0).
-- Las sentencias SQL son idénticas al original; se omitieron los bloques de comentario
-- que encabezaban cada tabla (su texto se conserva, corregido, en BD/BD.sql).

-- ELIMINACION DE LAS TABLAS

DROP TABLE IF EXISTS planificacion_incidencia CASCADE;
DROP TABLE IF EXISTS incidencia CASCADE;
DROP TABLE IF EXISTS planificacion_partida CASCADE;
DROP TABLE IF EXISTS planificacion_semanal CASCADE;
DROP TABLE IF EXISTS indisponibilidad_maquina CASCADE;
DROP TABLE IF EXISTS maquina_tintoreria CASCADE;
DROP TABLE IF EXISTS detalle_proyeccion_tejeduria CASCADE;
DROP TABLE IF EXISTS proyeccion_tejeduria CASCADE;
DROP TABLE IF EXISTS semana CASCADE;
DROP TABLE IF EXISTS partida_componente CASCADE;
DROP TABLE IF EXISTS partida CASCADE;
DROP TABLE IF EXISTS color CASCADE;
DROP TABLE IF EXISTS detalle_pedido CASCADE;
DROP TABLE IF EXISTS articulo CASCADE;
DROP TABLE IF EXISTS pedido CASCADE;
DROP TABLE IF EXISTS cliente CASCADE;
DROP TABLE IF EXISTS bitacora_auditoria CASCADE;
DROP TABLE IF EXISTS carga_datos CASCADE;
DROP TABLE IF EXISTS usuario CASCADE;
DROP TABLE IF EXISTS rol CASCADE;

-- =====================================================================
-- SPATS — Sistema de Planificación de Abastecimiento de Tintorería
-- Script de creación de base de datos — PostgreSQL
--
-- Base: modelo físico del R3.3 (20 tablas) con los dominios confirmados.
-- Cambios de esta versión (2026-10-25):
--   * color.tonalidad (nueva columna): tono para detectar lavados.
--   * partida_componente.peso_disponible_acumulado eliminado: la tela
--     pertenece a la bolsa del artículo, nunca a una partida.
--   * planificacion_semanal: indicadores del resultado del algoritmo.
--   * planificacion_partida: fecha_conformacion y fecha_disponible, y
--     regla BLOQUEADA = sin máquina.
--   * planificacion_maquina (nueva tabla): resultado por máquina.
--   * bolsa_articulo (nueva tabla): saldo de tela por artículo y día.
--
-- Si se ejecuta desde pgAdmin conectado a "tintoreria", borrar las dos
-- líneas siguientes (CREATE DATABASE y \c), que solo sirven en psql.
-- =====================================================================

CREATE TABLE rol (
    id_rol      SERIAL PRIMARY KEY,
    nombre      VARCHAR(50)  NOT NULL UNIQUE,
    descripcion VARCHAR(255)
);

CREATE TABLE usuario (
    id_usuario       SERIAL PRIMARY KEY,
    id_rol           INT NOT NULL REFERENCES rol (id_rol),
    nombre_completo  VARCHAR(150) NOT NULL,
    usuario_login    VARCHAR(80)  NOT NULL UNIQUE,
    contrasena       VARCHAR(255) NOT NULL,
    modo_interaccion VARCHAR(20)  NOT NULL
                     CHECK (modo_interaccion IN ('GUIADO', 'EXPERTO')),
    fecha_creacion   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    estado           VARCHAR(30)  NOT NULL
);

CREATE TABLE carga_datos (
    id_carga       SERIAL PRIMARY KEY,
    id_usuario     INT NOT NULL REFERENCES usuario (id_usuario),
    tipo_dato      VARCHAR(40) NOT NULL
                   CHECK (tipo_dato IN ('PEDIDOS', 'PARTIDAS', 'PROYECCION_TEJEDURIA')),
    nombre_archivo VARCHAR(255) NOT NULL,
    fecha_hora     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE bitacora_auditoria (
    id_bitacora       SERIAL PRIMARY KEY,
    id_usuario        INT NOT NULL REFERENCES usuario (id_usuario),
    fecha_hora        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    entidad_afectada  VARCHAR(80) NOT NULL,
    campo_modificado  VARCHAR(100),
    valor_anterior    VARCHAR(80),
    valor_nuevo       VARCHAR(80),
    tipo_operacion    VARCHAR(30) NOT NULL
);

CREATE TABLE cliente (
    id_cliente SERIAL PRIMARY KEY,
    nombre     VARCHAR(150) NOT NULL
);

CREATE TABLE pedido (
    id_pedido     SERIAL PRIMARY KEY,
    id_cliente    INT NOT NULL REFERENCES cliente (id_cliente),
    numero_op     VARCHAR(50) NOT NULL UNIQUE,
    fecha_entrega DATE NOT NULL,
    estado        VARCHAR(30) NOT NULL
);

CREATE TABLE articulo (
    id_articulo SERIAL PRIMARY KEY,
    codigo      VARCHAR(80)  NOT NULL UNIQUE,
    nombre      VARCHAR(150) NOT NULL
);

CREATE TABLE detalle_pedido (
    id_detalle_pedido    SERIAL PRIMARY KEY,
    id_pedido             INT NOT NULL REFERENCES pedido (id_pedido),
    id_articulo           INT NOT NULL REFERENCES articulo (id_articulo),
    peso_total_requerido  NUMERIC(10,2) NOT NULL CHECK (peso_total_requerido > 0)
);

CREATE TABLE color (
    id_color             SERIAL PRIMARY KEY,
    codigo_color         VARCHAR(50)  NOT NULL UNIQUE,
    nombre                VARCHAR(100) NOT NULL,
    clasificacion_color   VARCHAR(50)  NOT NULL,
    tonalidad             VARCHAR(10)  NOT NULL
                          CHECK (tonalidad IN ('BLANCO', 'CLARO', 'MEDIO', 'OSCURO'))
);

CREATE TABLE partida (
    id_partida      SERIAL PRIMARY KEY,
    id_pedido       INT NOT NULL REFERENCES pedido (id_pedido),
    id_color        INT NOT NULL REFERENCES color (id_color),
    numero_partida  VARCHAR(50) NOT NULL UNIQUE,
    barca           VARCHAR(80),
    peso_total      NUMERIC(12,3) NOT NULL CHECK (peso_total > 0),
    fecha_registro  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    estado_partida  VARCHAR(20) NOT NULL
                    CHECK (estado_partida IN ('PENDIENTE', 'ARRASTRADA', 'COMPLETADA'))
);

CREATE TABLE partida_componente (
    id_partida_componente      SERIAL PRIMARY KEY,
    id_partida                  INT NOT NULL REFERENCES partida (id_partida),
    id_detalle_pedido           INT NOT NULL REFERENCES detalle_pedido (id_detalle_pedido),
    tipo_componente              VARCHAR(20) NOT NULL
                                 CHECK (tipo_componente IN ('PRINCIPAL', 'COMPLEMENTO')),
    tubular_abierto              BOOLEAN NOT NULL,
    peso_requerido                NUMERIC(12,3) NOT NULL CHECK (peso_requerido > 0)
);

CREATE TABLE semana (
    id_semana      SERIAL PRIMARY KEY,
    fecha_inicio   DATE NOT NULL UNIQUE,
    fecha_fin      DATE NOT NULL,
    CONSTRAINT ck_semana_inicio_lunes  CHECK (EXTRACT(DOW FROM fecha_inicio) = 1),
    CONSTRAINT ck_semana_fin_domingo   CHECK (EXTRACT(DOW FROM fecha_fin) = 0),
    CONSTRAINT ck_semana_duracion      CHECK (fecha_fin - fecha_inicio = 6)
);

CREATE TABLE proyeccion_tejeduria (
    id_proyeccion  SERIAL PRIMARY KEY,
    id_semana      INT NOT NULL REFERENCES semana (id_semana),
    version        INT NOT NULL CHECK (version > 0),
    fecha_carga    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    estado         VARCHAR(30) NOT NULL
);

CREATE TABLE detalle_proyeccion_tejeduria (
    id_detalle_proyeccion  SERIAL PRIMARY KEY,
    id_proyeccion           INT NOT NULL REFERENCES proyeccion_tejeduria (id_proyeccion),
    id_detalle_pedido       INT NOT NULL REFERENCES detalle_pedido (id_detalle_pedido),
    fecha_proyectada         DATE NOT NULL,
    kg_proyectados            NUMERIC(12,3) NOT NULL CHECK (kg_proyectados > 0)
);

CREATE TABLE maquina_tintoreria (
    id_maquina             SERIAL PRIMARY KEY,
    codigo                  VARCHAR(50) NOT NULL UNIQUE,
    capacidad                NUMERIC(12,3) NOT NULL CHECK (capacidad > 0),
    clasificacion_color      VARCHAR(50) NOT NULL,
    tiempo_procesamiento      NUMERIC(10,2) NOT NULL CHECK (tiempo_procesamiento > 0),
    objetivo_semanal          INT NOT NULL CHECK (objetivo_semanal > 0),
    barca                     VARCHAR(80),
    estado                    VARCHAR(30) NOT NULL
);

CREATE TABLE indisponibilidad_maquina (
    id_indisponibilidad  SERIAL PRIMARY KEY,
    id_maquina            INT NOT NULL REFERENCES maquina_tintoreria (id_maquina),
    fecha_hora_inicio      TIMESTAMP NOT NULL,
    fecha_hora_fin          TIMESTAMP NOT NULL,
    motivo                  VARCHAR(255) NOT NULL,
    CONSTRAINT ck_indisp_fin_mayor_inicio CHECK (fecha_hora_fin > fecha_hora_inicio)
);

CREATE TABLE planificacion_semanal (
    id_planificacion        SERIAL PRIMARY KEY,
    id_semana                INT NOT NULL REFERENCES semana (id_semana),
    id_proyeccion            INT NOT NULL REFERENCES proyeccion_tejeduria (id_proyeccion),
    numero_version            INTEGER NOT NULL CHECK (numero_version > 0),
    fecha_generacion          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    es_vigente                BOOLEAN NOT NULL DEFAULT FALSE,
    tipo_generacion            VARCHAR(20) NOT NULL
                               CHECK (tipo_generacion IN ('INICIAL', 'REPLANIFICACION')),
    fecha_cierre                TIMESTAMP,
    estado                      VARCHAR(30) NOT NULL,
    valor_funcion_objetivo      NUMERIC(12,3) CHECK (valor_funcion_objetivo >= 0),
    desbalance_semanal          NUMERIC(12,3) CHECK (desbalance_semanal >= 0),
    desbalance_diario           NUMERIC(12,3) CHECK (desbalance_diario >= 0),
    numero_lavados              INT CHECK (numero_lavados >= 0),
    evaluaciones                INT CHECK (evaluaciones >= 0),
    tiempo_ejecucion_ms         BIGINT CHECK (tiempo_ejecucion_ms >= 0),
    vigente_semana              INT GENERATED ALWAYS AS (CASE WHEN es_vigente THEN id_semana END) STORED,
    CONSTRAINT uq_vigente_por_semana UNIQUE (vigente_semana)
);

CREATE TABLE planificacion_partida (
    id_planificacion_partida    SERIAL PRIMARY KEY,
    id_planificacion             INT NOT NULL REFERENCES planificacion_semanal (id_planificacion),
    id_partida                   INT NOT NULL REFERENCES partida (id_partida),
    id_maquina                   INT REFERENCES maquina_tintoreria (id_maquina),
    dia_abastecimiento            DATE,
    orden_secuencia                INTEGER,
    clasificacion_resultado        VARCHAR(20) NOT NULL
                                   CHECK (clasificacion_resultado IN ('ASIGNADA', 'BLOQUEADA', 'SOBRANTE')),
    requiere_lavado_previo          BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_conformacion              DATE,
    fecha_disponible                DATE,
    estado_confirmacion_cierre       VARCHAR(20)
                                     CHECK (estado_confirmacion_cierre IN ('COMPLETADA', 'ARRASTRADA')),
    CONSTRAINT uq_partida_por_planificacion UNIQUE (id_planificacion, id_partida),
    CONSTRAINT ck_planpart_bloqueada_sin_maquina
        CHECK ((clasificacion_resultado = 'BLOQUEADA') = (id_maquina IS NULL)),
    CONSTRAINT ck_planpart_fechas
        CHECK (fecha_disponible IS NULL OR fecha_disponible > fecha_conformacion)
);

CREATE TABLE planificacion_maquina (
    id_planificacion_maquina SERIAL PRIMARY KEY,
    id_planificacion     INT NOT NULL REFERENCES planificacion_semanal (id_planificacion),
    id_maquina           INT NOT NULL REFERENCES maquina_tintoreria (id_maquina),
    objetivo_aplicado    INT NOT NULL CHECK (objetivo_aplicado >= 0),
    minimo_diario        INT NOT NULL CHECK (minimo_diario >= 0),
    maximo_diario        INT NOT NULL,
    partidas_asignadas   INT NOT NULL DEFAULT 0 CHECK (partidas_asignadas >= 0),
    partidas_sobrantes   INT NOT NULL DEFAULT 0 CHECK (partidas_sobrantes >= 0),
    diferencia_objetivo  INT NOT NULL,
    numero_lavados       INT NOT NULL DEFAULT 0 CHECK (numero_lavados >= 0),
    CONSTRAINT uq_planificacion_maquina UNIQUE (id_planificacion, id_maquina),
    CONSTRAINT ck_planmaq_rango_diario CHECK (maximo_diario >= minimo_diario),
    CONSTRAINT ck_planmaq_diferencia
        CHECK (diferencia_objetivo = partidas_asignadas + partidas_sobrantes - objetivo_aplicado)
);

CREATE TABLE bolsa_articulo (
    id_bolsa          SERIAL PRIMARY KEY,
    id_planificacion  INT NOT NULL REFERENCES planificacion_semanal (id_planificacion),
    id_articulo       INT NOT NULL REFERENCES articulo (id_articulo),
    fecha             DATE NOT NULL,
    kg_saldo_inicial  NUMERIC(12,3) NOT NULL CHECK (kg_saldo_inicial >= 0),
    kg_ingresados     NUMERIC(12,3) NOT NULL DEFAULT 0 CHECK (kg_ingresados >= 0),
    kg_consumidos     NUMERIC(12,3) NOT NULL DEFAULT 0 CHECK (kg_consumidos >= 0),
    kg_saldo_final    NUMERIC(12,3) NOT NULL CHECK (kg_saldo_final >= 0),
    CONSTRAINT uq_bolsa_articulo_dia UNIQUE (id_planificacion, id_articulo, fecha),
    CONSTRAINT ck_bolsa_balance
        CHECK (kg_saldo_final = kg_saldo_inicial + kg_ingresados - kg_consumidos)
);

CREATE TABLE incidencia (
    id_incidencia               SERIAL PRIMARY KEY,
    id_usuario                   INT NOT NULL REFERENCES usuario (id_usuario),
    id_planificacion_origen       INT NOT NULL REFERENCES planificacion_semanal (id_planificacion),
    id_pedido                     INT REFERENCES pedido (id_pedido),
    id_partida                    INT REFERENCES partida (id_partida),
    id_maquina                    INT REFERENCES maquina_tintoreria (id_maquina),
    tipo_incidencia                VARCHAR(40) NOT NULL
                                   CHECK (tipo_incidencia IN (
                                       'INCIDENCIA_MAQUINA',
                                       'PROBLEMA_COMPLEMENTO',
                                       'CAMBIO_PRIORIDAD'
                                   )),
    fecha_hora_ocurrencia           TIMESTAMP NOT NULL,
    fecha_hora_disponibilidad        TIMESTAMP,
    estado                           VARCHAR(30) NOT NULL,
    CONSTRAINT ck_incidencia_entidad_exclusiva
        CHECK (num_nonnulls(id_pedido, id_partida, id_maquina) = 1)
);

CREATE TABLE planificacion_incidencia (
    id_planificacion_incidencia  SERIAL PRIMARY KEY,
    id_planificacion              INT NOT NULL REFERENCES planificacion_semanal (id_planificacion),
    id_incidencia                  INT NOT NULL REFERENCES incidencia (id_incidencia),
    CONSTRAINT uq_planificacion_incidencia UNIQUE (id_planificacion, id_incidencia)
);

CREATE INDEX idx_partida_estado             ON partida (estado_partida);
CREATE INDEX idx_partida_pedido             ON partida (id_pedido);
CREATE INDEX idx_detproy_fecha              ON detalle_proyeccion_tejeduria (id_detalle_pedido, fecha_proyectada);
CREATE INDEX idx_planpartida_maquina_dia    ON planificacion_partida (id_maquina, dia_abastecimiento, orden_secuencia);
CREATE INDEX idx_incidencia_estado          ON incidencia (estado);
CREATE INDEX idx_bolsa_articulo_fecha       ON bolsa_articulo (id_articulo, fecha);


ALTER TABLE planificacion_partida
    DROP CONSTRAINT ck_planpart_fechas,
    DROP COLUMN fecha_conformacion,
    ADD CONSTRAINT ck_planpart_dia_desde_disponible
        CHECK (dia_abastecimiento IS NULL OR fecha_disponible IS NULL
               OR dia_abastecimiento >= fecha_disponible);

ALTER TABLE incidencia
    ADD COLUMN nueva_fecha_entrega DATE,
    ADD CONSTRAINT ck_incidencia_tipo_entidad CHECK (
        (tipo_incidencia = 'INCIDENCIA_MAQUINA'   AND id_maquina IS NOT NULL AND nueva_fecha_entrega IS NULL) OR
        (tipo_incidencia = 'PROBLEMA_COMPLEMENTO' AND id_partida IS NOT NULL AND nueva_fecha_entrega IS NULL) OR
        (tipo_incidencia = 'CAMBIO_PRIORIDAD'     AND id_pedido  IS NOT NULL AND nueva_fecha_entrega IS NOT NULL
                                                  AND fecha_hora_disponibilidad IS NULL)
    );
