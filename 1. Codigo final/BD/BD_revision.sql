-- =====================================================================
-- SPATS — Sistema de Planificación de Abastecimiento de Tintorería
-- Script de creación de base de datos — PostgreSQL (base "tintoreria")
--
-- Modelo físico del R3.3: 27 tablas de negocio en el esquema public y
-- 8 bitácoras en el esquema auditoria.
-- Ejecutar conectado a la base "tintoreria" (psql o pgAdmin). El script
-- es re-ejecutable: elimina el esquema auditoria y las tablas, funciones
-- y disparadores del esquema public, y luego vuelve a crearlos. Todo se
-- ejecuta en una sola transacción: si algo falla, no se aplica nada.
-- ¡Atención! Al eliminar las tablas se pierden todos los datos.
--
-- Cambios respecto del R3.3 (2026-10-25):
--   * color.tonalidad: tono del color para detectar lavados.
--   * partida_componente sin peso_disponible_acumulado: la tela pertenece
--     a la bolsa del artículo, nunca a una partida.
--   * planificacion_semanal: indicadores del resultado del algoritmo.
--   * planificacion_partida: fecha_disponible, regla BLOQUEADA = sin
--     máquina y dia_abastecimiento >= fecha_disponible.
--   * planificacion_maquina (nueva tabla): resultado por máquina.
--   * bolsa_articulo (nueva tabla): saldo de tela por artículo y día.
--   * incidencia: nueva_fecha_entrega, descripcion y entidad afectada
--     según el tipo.
-- Cambios confirmados por la autora:
--   * D-21 (2026-10-31): indisponibilidad_maquina.tipo_indisponibilidad.
--   * D-30 (2026-11-01): bolsa_articulo.kg_devueltos y ck_bolsa_balance
--     con la tela devuelta (re-planificación).
--   * D-19 (2026-11-02): planificacion_semanal.stock_desactualizado.
--   * D-20 (2026-11-02): CHECK con los dominios de los estados;
--     clasificacion_color C/B (D-07).
--   * D-51 (2026-11-10): maquina_tonalidad_aceptada (nueva tabla).
--   * D-52 (2026-11-11): versiones de la proyección de una semana.
--     proyeccion_tejeduria.estado VIGENTE/CARGADA/ELIMINADA;
--     planificacion_semanal.estado suma CANDIDATA.
--   * D-56 (2026-11-13): recomendacion_tejido (nueva tabla).
-- Cambios por la revisión del modelo de base de datos (2026-11-21, D-58):
--   * ejecucion_algoritmo (nueva tabla): algoritmo y parámetros con los
--     que se generó cada versión de planificación.
--   * motivo_bloqueo (nueva tabla, catálogo): planificacion_partida
--     referencia el motivo con id_motivo_bloqueo y guarda el detalle de
--     la opción OTRO en detalle_motivo_bloqueo.
--   * Auditoría: bitacora_auditoria se reemplaza por una bitácora por
--     entidad en el esquema auditoria, llenadas por disparadores. El
--     usuario se toma de la variable de sesión spats.id_usuario.
--   * carga_datos: estado ACTIVA/ELIMINADA, fecha_eliminacion e
--     id_usuario_eliminacion. Las cargas ya no se borran, por lo que
--     id_carga deja de pasar a NULL.
--   * historial_estado_pedido e historial_estado_partida (nuevas
--     tablas): cada cambio de estado del pedido y de la partida, con
--     fecha, hora y usuario, llenadas por disparadores.
--   * usuario.fecha_ultimo_acceso; incidencia.motivo_cierre y
--     incidencia.fecha_entrega_anterior (antes se leían de la bitácora).
--   * Índices de las claves foráneas y de las búsquedas del algoritmo.
-- =====================================================================

SET client_min_messages = warning;

BEGIN;

-- ---------------------------------------------------------------------
-- Eliminación (orden inverso de dependencias)
-- ---------------------------------------------------------------------
DROP SCHEMA IF EXISTS auditoria CASCADE;

DROP TABLE IF EXISTS recomendacion_tejido CASCADE;
DROP TABLE IF EXISTS planificacion_incidencia CASCADE;
DROP TABLE IF EXISTS incidencia CASCADE;
DROP TABLE IF EXISTS bolsa_articulo CASCADE;
DROP TABLE IF EXISTS planificacion_maquina CASCADE;
DROP TABLE IF EXISTS planificacion_partida CASCADE;
DROP TABLE IF EXISTS motivo_bloqueo CASCADE;
DROP TABLE IF EXISTS ejecucion_algoritmo CASCADE;
DROP TABLE IF EXISTS planificacion_semanal CASCADE;
DROP TABLE IF EXISTS indisponibilidad_maquina CASCADE;
DROP TABLE IF EXISTS maquina_tonalidad_aceptada CASCADE;
DROP TABLE IF EXISTS maquina_tintoreria CASCADE;
DROP TABLE IF EXISTS detalle_proyeccion_tejeduria CASCADE;
DROP TABLE IF EXISTS proyeccion_tejeduria CASCADE;
DROP TABLE IF EXISTS semana CASCADE;
DROP TABLE IF EXISTS partida_componente CASCADE;
DROP TABLE IF EXISTS historial_estado_partida CASCADE;
DROP TABLE IF EXISTS partida CASCADE;
DROP TABLE IF EXISTS color CASCADE;
DROP TABLE IF EXISTS detalle_pedido CASCADE;
DROP TABLE IF EXISTS articulo CASCADE;
DROP TABLE IF EXISTS historial_estado_pedido CASCADE;
DROP TABLE IF EXISTS pedido CASCADE;
DROP TABLE IF EXISTS cliente CASCADE;
DROP TABLE IF EXISTS bitacora_auditoria CASCADE;   -- tabla de versiones anteriores
DROP TABLE IF EXISTS carga_datos CASCADE;
DROP TABLE IF EXISTS usuario CASCADE;
DROP TABLE IF EXISTS rol CASCADE;

DROP FUNCTION IF EXISTS fn_registrar_estado_pedido() CASCADE;
DROP FUNCTION IF EXISTS fn_registrar_estado_partida() CASCADE;

-- ---------------------------------------------------------------------
-- rol
-- ---------------------------------------------------------------------
CREATE TABLE rol (
    id_rol      SERIAL PRIMARY KEY,
    nombre      VARCHAR(50)  NOT NULL UNIQUE,
    descripcion VARCHAR(255)
);

-- ---------------------------------------------------------------------
-- usuario
-- fecha_ultimo_acceso: se actualiza en cada inicio de sesión; el cambio
--   queda registrado en auditoria.bitacora_usuario.
-- ---------------------------------------------------------------------
CREATE TABLE usuario (
    id_usuario          SERIAL PRIMARY KEY,
    id_rol              INT NOT NULL REFERENCES rol (id_rol),
    nombre_completo     VARCHAR(150) NOT NULL,
    usuario_login       VARCHAR(80)  NOT NULL UNIQUE,
    contrasena          VARCHAR(255) NOT NULL,
    modo_interaccion    VARCHAR(20)  NOT NULL
                        CHECK (modo_interaccion IN ('GUIADO', 'EXPERTO')),
    fecha_creacion      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_ultimo_acceso TIMESTAMP,
    estado              VARCHAR(30)  NOT NULL
                        CHECK (estado IN ('ACTIVO', 'INACTIVO'))
);

-- ---------------------------------------------------------------------
-- carga_datos
-- Una carga no se borra: al eliminarla se quitan los registros que creó
-- (D-40) y la carga queda con estado ELIMINADA, con la fecha y el usuario
-- que la eliminó. Así id_carga siempre apunta a una carga existente.
-- ---------------------------------------------------------------------
CREATE TABLE carga_datos (
    id_carga               SERIAL PRIMARY KEY,
    id_usuario             INT NOT NULL REFERENCES usuario (id_usuario),
    tipo_dato              VARCHAR(40) NOT NULL
                           CHECK (tipo_dato IN ('PEDIDOS', 'PARTIDAS', 'PROYECCION_TEJEDURIA')),
    nombre_archivo         VARCHAR(255) NOT NULL,
    fecha_hora             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    estado                 VARCHAR(20) NOT NULL DEFAULT 'ACTIVA'
                           CHECK (estado IN ('ACTIVA', 'ELIMINADA')),
    fecha_eliminacion      TIMESTAMP,
    id_usuario_eliminacion INT REFERENCES usuario (id_usuario),
    CONSTRAINT ck_carga_eliminacion CHECK (
        (estado = 'ELIMINADA') = (fecha_eliminacion IS NOT NULL AND id_usuario_eliminacion IS NOT NULL)
    )
);

-- ---------------------------------------------------------------------
-- cliente
-- ---------------------------------------------------------------------
CREATE TABLE cliente (
    id_cliente SERIAL PRIMARY KEY,
    nombre     VARCHAR(150) NOT NULL
);

-- ---------------------------------------------------------------------
-- pedido
-- id_carga: carga que creó el registro; permite quitar lo que agregó un
--   archivo. NULL solo si el registro no vino de una carga. Igual en
--   detalle_pedido, partida y proyeccion_tejeduria (D-40, D-58).
-- ---------------------------------------------------------------------
CREATE TABLE pedido (
    id_pedido     SERIAL PRIMARY KEY,
    id_cliente    INT NOT NULL REFERENCES cliente (id_cliente),
    numero_op     VARCHAR(50) NOT NULL UNIQUE,
    fecha_entrega DATE NOT NULL,
    id_carga      INT REFERENCES carga_datos (id_carga),
    estado        VARCHAR(30) NOT NULL
                  CHECK (estado IN ('PENDIENTE', 'COMPLETADO'))
);

-- ---------------------------------------------------------------------
-- historial_estado_pedido (D-58)
-- Un registro al crear el pedido (estado_anterior NULL) y uno por cada
-- cambio de estado. Lo llena el disparador tg_pedido_historial_estado;
-- id_usuario es el usuario de la sesión (NULL si lo hizo el sistema).
-- ---------------------------------------------------------------------
CREATE TABLE historial_estado_pedido (
    id_historial_estado SERIAL PRIMARY KEY,
    id_pedido           INT NOT NULL REFERENCES pedido (id_pedido) ON DELETE CASCADE,
    estado_anterior     VARCHAR(30)
                        CHECK (estado_anterior IN ('PENDIENTE', 'COMPLETADO')),
    estado_nuevo        VARCHAR(30) NOT NULL
                        CHECK (estado_nuevo IN ('PENDIENTE', 'COMPLETADO')),
    fecha_hora          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_usuario          INT REFERENCES usuario (id_usuario),
    CONSTRAINT ck_historial_cambio CHECK (estado_anterior IS DISTINCT FROM estado_nuevo)
);

-- ---------------------------------------------------------------------
-- articulo
-- ---------------------------------------------------------------------
CREATE TABLE articulo (
    id_articulo SERIAL PRIMARY KEY,
    codigo      VARCHAR(80)  NOT NULL UNIQUE,
    nombre      VARCHAR(150) NOT NULL
);

-- ---------------------------------------------------------------------
-- detalle_pedido
-- ---------------------------------------------------------------------
CREATE TABLE detalle_pedido (
    id_detalle_pedido    SERIAL PRIMARY KEY,
    id_pedido             INT NOT NULL REFERENCES pedido (id_pedido),
    id_articulo           INT NOT NULL REFERENCES articulo (id_articulo),
    peso_total_requerido  NUMERIC(10,2) NOT NULL CHECK (peso_total_requerido > 0),
    id_carga              INT REFERENCES carga_datos (id_carga)
);

-- ---------------------------------------------------------------------
-- color
-- clasificacion_color: tipo de color de la barca (C color, B blanco).
-- tonalidad: tono del color dentro de una misma máquina. Pasar de una
--   tonalidad más oscura a una más clara en la misma máquina requiere
--   lavado (RF26). Orden de más clara a más oscura:
--   BLANCO < CLARO < MEDIO < OSCURO.
-- ---------------------------------------------------------------------
CREATE TABLE color (
    id_color             SERIAL PRIMARY KEY,
    codigo_color         VARCHAR(50)  NOT NULL UNIQUE,
    nombre                VARCHAR(100) NOT NULL,
    clasificacion_color   VARCHAR(50)  NOT NULL
                          CHECK (clasificacion_color IN ('C', 'B')),
    tonalidad             VARCHAR(10)  NOT NULL
                          CHECK (tonalidad IN ('BLANCO', 'CLARO', 'MEDIO', 'OSCURO'))
);

-- ---------------------------------------------------------------------
-- partida
-- estado_partida (estado general en el tiempo):
--   PENDIENTE  — aún no se tiñe; es la única que puede ser candidata.
--   ARRASTRADA — estuvo en una planificación cerrada como Asignada o
--                Sobrante y no se confirmó como completada. Solo se
--                muestra; no entra a nuevas planificaciones (RF47).
--   COMPLETADA — confirmada como completada por el Planificador.
-- ---------------------------------------------------------------------
CREATE TABLE partida (
    id_partida      SERIAL PRIMARY KEY,
    id_pedido       INT NOT NULL REFERENCES pedido (id_pedido),
    id_color        INT NOT NULL REFERENCES color (id_color),
    numero_partida  VARCHAR(50) NOT NULL UNIQUE,
    barca           VARCHAR(80),
    peso_total      NUMERIC(12,3) NOT NULL CHECK (peso_total > 0),
    fecha_registro  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_carga        INT REFERENCES carga_datos (id_carga),
    estado_partida  VARCHAR(20) NOT NULL
                    CHECK (estado_partida IN ('PENDIENTE', 'ARRASTRADA', 'COMPLETADA'))
);

-- ---------------------------------------------------------------------
-- historial_estado_partida (D-58)
-- Un registro al crear la partida (estado_anterior NULL) y uno por cada
-- cambio de estado: al cerrar la semana (COMPLETADA o ARRASTRADA) y al
-- marcar como completada una partida arrastrada. Lo llena el disparador
-- tg_partida_historial_estado; id_usuario es el usuario de la sesión.
-- ---------------------------------------------------------------------
CREATE TABLE historial_estado_partida (
    id_historial_estado SERIAL PRIMARY KEY,
    id_partida          INT NOT NULL REFERENCES partida (id_partida) ON DELETE CASCADE,
    estado_anterior     VARCHAR(20)
                        CHECK (estado_anterior IN ('PENDIENTE', 'ARRASTRADA', 'COMPLETADA')),
    estado_nuevo        VARCHAR(20) NOT NULL
                        CHECK (estado_nuevo IN ('PENDIENTE', 'ARRASTRADA', 'COMPLETADA')),
    fecha_hora          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_usuario          INT REFERENCES usuario (id_usuario),
    CONSTRAINT ck_historial_partida_cambio CHECK (estado_anterior IS DISTINCT FROM estado_nuevo)
);

-- ---------------------------------------------------------------------
-- partida_componente
-- Una partida nunca se llena a medias; la tela disponible se registra en
-- bolsa_articulo.
-- ---------------------------------------------------------------------
CREATE TABLE partida_componente (
    id_partida_componente      SERIAL PRIMARY KEY,
    id_partida                  INT NOT NULL REFERENCES partida (id_partida),
    id_detalle_pedido           INT NOT NULL REFERENCES detalle_pedido (id_detalle_pedido),
    tipo_componente              VARCHAR(20) NOT NULL
                                 CHECK (tipo_componente IN ('PRINCIPAL', 'COMPLEMENTO')),
    tubular_abierto              BOOLEAN NOT NULL,
    peso_requerido                NUMERIC(12,3) NOT NULL CHECK (peso_requerido > 0)
);

-- ---------------------------------------------------------------------
-- semana
-- Semana de planificación: lunes a domingo. La ventana de datos de la
-- proyección de tejeduría (sábado anterior a viernes) es regla de
-- negocio sobre detalle_proyeccion_tejeduria.fecha_proyectada.
-- ---------------------------------------------------------------------
CREATE TABLE semana (
    id_semana      SERIAL PRIMARY KEY,
    fecha_inicio   DATE NOT NULL UNIQUE,
    fecha_fin      DATE NOT NULL,
    CONSTRAINT ck_semana_inicio_lunes  CHECK (EXTRACT(DOW FROM fecha_inicio) = 1),
    CONSTRAINT ck_semana_fin_domingo   CHECK (EXTRACT(DOW FROM fecha_fin) = 0),
    CONSTRAINT ck_semana_duracion      CHECK (fecha_fin - fecha_inicio = 6)
);

-- ---------------------------------------------------------------------
-- proyeccion_tejeduria
-- Cada carga de la proyección de una semana crea una versión nueva (V1,
-- V2, ...) y no cambia la planificación vigente (D-52).
-- estado:
--   VIGENTE   — la versión de la planificación vigente de la semana; si la
--               semana no tiene vigente, la última cargada.
--   CARGADA   — cualquier otra versión que se puede planificar.
--   ELIMINADA — se conserva con sus registros para poder volver a ella,
--               pero no se planifica (D-46).
-- ---------------------------------------------------------------------
CREATE TABLE proyeccion_tejeduria (
    id_proyeccion  SERIAL PRIMARY KEY,
    id_semana      INT NOT NULL REFERENCES semana (id_semana),
    version        INT NOT NULL CHECK (version > 0),
    fecha_carga    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_carga       INT REFERENCES carga_datos (id_carga),
    estado         VARCHAR(30) NOT NULL
                   CHECK (estado IN ('VIGENTE', 'CARGADA', 'ELIMINADA'))
);

-- ---------------------------------------------------------------------
-- detalle_proyeccion_tejeduria
-- Kilos tejidos por pedido-artículo y fecha (dato de entrada).
-- ---------------------------------------------------------------------
CREATE TABLE detalle_proyeccion_tejeduria (
    id_detalle_proyeccion  SERIAL PRIMARY KEY,
    id_proyeccion           INT NOT NULL REFERENCES proyeccion_tejeduria (id_proyeccion),
    id_detalle_pedido       INT NOT NULL REFERENCES detalle_pedido (id_detalle_pedido),
    fecha_proyectada         DATE NOT NULL,
    kg_proyectados            NUMERIC(12,3) NOT NULL CHECK (kg_proyectados > 0)
);

-- ---------------------------------------------------------------------
-- maquina_tintoreria
-- ---------------------------------------------------------------------
CREATE TABLE maquina_tintoreria (
    id_maquina             SERIAL PRIMARY KEY,
    codigo                  VARCHAR(50) NOT NULL UNIQUE,
    capacidad                NUMERIC(12,3) NOT NULL CHECK (capacidad > 0),
    clasificacion_color      VARCHAR(50) NOT NULL
                             CHECK (clasificacion_color IN ('C', 'B')),
    objetivo_semanal          INT NOT NULL CHECK (objetivo_semanal > 0),
    barca                     VARCHAR(80),
    estado                    VARCHAR(30) NOT NULL
                              CHECK (estado IN ('OPERATIVA', 'INDISPONIBLE', 'EN_MANTENIMIENTO'))
);

-- ---------------------------------------------------------------------
-- maquina_tonalidad_aceptada (D-51)
-- Tonalidades que puede teñir cada máquina. Una partida puede ir a una
-- máquina de su misma capacidad (número de la barca) que acepte su
-- tonalidad. El backend exige al menos una tonalidad por máquina.
-- ---------------------------------------------------------------------
CREATE TABLE maquina_tonalidad_aceptada (
    id_maquina  INT NOT NULL REFERENCES maquina_tintoreria (id_maquina) ON DELETE CASCADE,
    tonalidad   VARCHAR(10) NOT NULL
                CHECK (tonalidad IN ('BLANCO', 'CLARO', 'MEDIO', 'OSCURO')),
    PRIMARY KEY (id_maquina, tonalidad)
);

-- ---------------------------------------------------------------------
-- indisponibilidad_maquina
-- ---------------------------------------------------------------------
CREATE TABLE indisponibilidad_maquina (
    id_indisponibilidad  SERIAL PRIMARY KEY,
    id_maquina            INT NOT NULL REFERENCES maquina_tintoreria (id_maquina),
    fecha_hora_inicio      TIMESTAMP NOT NULL,
    fecha_hora_fin          TIMESTAMP NOT NULL,
    motivo                  VARCHAR(255) NOT NULL,
    tipo_indisponibilidad   VARCHAR(20) NOT NULL DEFAULT 'INDISPONIBLE',
    CONSTRAINT ck_indisp_fin_mayor_inicio CHECK (fecha_hora_fin > fecha_hora_inicio),
    CONSTRAINT ck_indisp_tipo CHECK (tipo_indisponibilidad IN ('INDISPONIBLE', 'MANTENIMIENTO'))
);

-- ---------------------------------------------------------------------
-- planificacion_semanal
-- vigente_semana garantiza una sola versión vigente por semana.
-- Columnas de indicadores: resultado del algoritmo guardado para
-- mostrarlo y para comparar versiones (RF55, RF57) sin recalcular.
--   valor_funcion_objetivo = desbalance_semanal + desbalance_diario
--                            + peso de lavados × numero_lavados
-- estado:
--   ABIERTA     — vigente, la semana sigue en curso.
--   CERRADA     — vigente, la semana se cerró (RF42).
--   REEMPLAZADA — dejó de ser vigente por una versión nueva (D-14) o
--                 por una re-planificación confirmada.
--   INVALIDADA  — dejó de ser vigente porque cambió la proyección (D-06).
--   PROPUESTA   — re-planificación pendiente de confirmar, no vigente
--                 (D-13b); si se descarta se elimina (D-26).
--   CANDIDATA   — generada con otra versión de la proyección, no vigente
--                 (D-52). El planificador decide si la usa como vigente.
-- stock_desactualizado (D-19): TRUE cuando, después de generar esta
--   versión, cambió la planificación vigente de la semana anterior.
-- ---------------------------------------------------------------------
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
    estado                      VARCHAR(30) NOT NULL
                                CHECK (estado IN ('ABIERTA', 'CERRADA', 'REEMPLAZADA', 'INVALIDADA', 'PROPUESTA', 'CANDIDATA')),
    stock_desactualizado        BOOLEAN NOT NULL DEFAULT FALSE,
    valor_funcion_objetivo      NUMERIC(12,3) CHECK (valor_funcion_objetivo >= 0),
    desbalance_semanal          NUMERIC(12,3) CHECK (desbalance_semanal >= 0),
    desbalance_diario           NUMERIC(12,3) CHECK (desbalance_diario >= 0),
    numero_lavados              INT CHECK (numero_lavados >= 0),
    evaluaciones                INT CHECK (evaluaciones >= 0),
    tiempo_ejecucion_ms         BIGINT CHECK (tiempo_ejecucion_ms >= 0),
    vigente_semana              INT GENERATED ALWAYS AS (CASE WHEN es_vigente THEN id_semana END) STORED,
    CONSTRAINT uq_vigente_por_semana UNIQUE (vigente_semana)
);

-- ---------------------------------------------------------------------
-- ejecucion_algoritmo (D-58)
-- Algoritmo y parámetros con los que se generó cada versión de
-- planificación (generación, candidata o re-planificación). Los valores
-- siguen definidos en el código (ParametrosACO y ParametrosPlanificacion);
-- aquí se guarda la copia de los que se usaron, para comparar por qué dos
-- versiones dieron soluciones distintas. Los resultados de la ejecución
-- (evaluaciones y tiempo) están en planificacion_semanal.
-- ---------------------------------------------------------------------
CREATE TABLE ejecucion_algoritmo (
    id_ejecucion                 SERIAL PRIMARY KEY,
    id_planificacion             INT NOT NULL UNIQUE
                                 REFERENCES planificacion_semanal (id_planificacion) ON DELETE CASCADE,
    algoritmo                    VARCHAR(60) NOT NULL,
    version_algoritmo            VARCHAR(20) NOT NULL,
    hormigas_por_iteracion       INT NOT NULL CHECK (hormigas_por_iteracion >= 1),
    fraccion_feromona_conservada NUMERIC(5,4) NOT NULL
                                 CHECK (fraccion_feromona_conservada BETWEEN 0 AND 1),
    feromona_inicial             NUMERIC(10,4) NOT NULL CHECK (feromona_inicial > 0),
    peso_feromona                NUMERIC(6,3) NOT NULL,
    peso_heuristica              NUMERIC(6,3) NOT NULL,
    evaluaciones_maximas         INT NOT NULL CHECK (evaluaciones_maximas >= 1),
    tiempo_maximo_ms             BIGINT NOT NULL CHECK (tiempo_maximo_ms >= 0),
    semilla                      BIGINT NOT NULL,
    peso_desbalance_semanal      NUMERIC(6,3) NOT NULL CHECK (peso_desbalance_semanal >= 0),
    peso_desbalance_diario       NUMERIC(6,3) NOT NULL CHECK (peso_desbalance_diario >= 0),
    peso_lavados                 NUMERIC(6,3) NOT NULL CHECK (peso_lavados >= 0)
);

-- ---------------------------------------------------------------------
-- motivo_bloqueo (D-58)
-- Catálogo de los motivos por los que una partida queda BLOQUEADA. Los
-- tres primeros vienen del algoritmo (MotivoNoConformacion); los dos
-- siguientes, de la re-planificación (D-33). OTRO exige el detalle en
-- planificacion_partida.detalle_motivo_bloqueo.
-- ---------------------------------------------------------------------
CREATE TABLE motivo_bloqueo (
    id_motivo_bloqueo SERIAL PRIMARY KEY,
    codigo            VARCHAR(40)  NOT NULL UNIQUE,
    descripcion       VARCHAR(150) NOT NULL,
    requiere_detalle  BOOLEAN NOT NULL DEFAULT FALSE
);

INSERT INTO motivo_bloqueo (codigo, descripcion, requiere_detalle) VALUES
    ('SIN_MAQUINA_COMPATIBLE',      'Ninguna máquina de su capacidad acepta su tonalidad', FALSE),
    ('SIN_TELA',                    'La tela de la semana no alcanza para completar alguno de sus componentes', FALSE),
    ('TELA_PRIORIZADA',             'La tela disponible se destinó a partidas con mayor prioridad', FALSE),
    ('MAQUINA_NO_DISPONIBLE',       'La máquina de su barca no vuelve a estar disponible en la semana', FALSE),
    ('COMPLEMENTO_FUERA_DE_SEMANA', 'El complemento llega después del domingo', FALSE),
    ('OTRO',                        'Otro motivo', TRUE);

-- ---------------------------------------------------------------------
-- planificacion_partida
-- clasificacion_resultado (resultado dentro de esta versión):
--   ASIGNADA  — conformada y ubicada en máquina y día.
--   SOBRANTE  — conformada; excede el objetivo semanal de su máquina o
--               ya no hay espacio. Se muestra en su primer día disponible.
--   BLOQUEADA — no se pudo conformar o ubicar en la semana; no tiene
--               máquina ni consume tela y sigue PENDIENTE para la semana
--               siguiente. Su motivo es obligatorio (id_motivo_bloqueo).
-- fecha_disponible: día en que la tela completa de la partida queda
--   disponible en tintorería. El día de abastecimiento nunca es anterior.
-- estado_confirmacion_cierre: NULL mientras la semana sigue abierta;
--   al cerrar, COMPLETADA o ARRASTRADA.
-- ---------------------------------------------------------------------
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
    id_motivo_bloqueo               INT REFERENCES motivo_bloqueo (id_motivo_bloqueo),
    detalle_motivo_bloqueo          VARCHAR(255),
    fecha_disponible                DATE,
    estado_confirmacion_cierre       VARCHAR(20)
                                     CHECK (estado_confirmacion_cierre IN ('COMPLETADA', 'ARRASTRADA')),
    CONSTRAINT uq_partida_por_planificacion UNIQUE (id_planificacion, id_partida),
    CONSTRAINT ck_planpart_bloqueada_sin_maquina
        CHECK ((clasificacion_resultado = 'BLOQUEADA') = (id_maquina IS NULL)),
    CONSTRAINT ck_planpart_motivo_solo_bloqueada
        CHECK ((clasificacion_resultado = 'BLOQUEADA') = (id_motivo_bloqueo IS NOT NULL)),
    CONSTRAINT ck_planpart_detalle_con_motivo
        CHECK (detalle_motivo_bloqueo IS NULL OR id_motivo_bloqueo IS NOT NULL),
    CONSTRAINT ck_planpart_dia_desde_disponible
        CHECK (dia_abastecimiento IS NULL OR fecha_disponible IS NULL
               OR dia_abastecimiento >= fecha_disponible)
);

-- ---------------------------------------------------------------------
-- planificacion_maquina
-- Resultado de cada máquina en cada versión de planificación.
--   diferencia_objetivo: asignadas + sobrantes − objetivo_aplicado
--     (negativo = faltante, positivo = excedente; RF29).
-- ---------------------------------------------------------------------
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

-- ---------------------------------------------------------------------
-- bolsa_articulo
-- Saldo de tela de cada artículo, día por día, en cada versión de
-- planificación. La bolsa es del artículo y no pertenece a ninguna
-- partida.
-- ---------------------------------------------------------------------
CREATE TABLE bolsa_articulo (
    id_bolsa          SERIAL PRIMARY KEY,
    id_planificacion  INT NOT NULL REFERENCES planificacion_semanal (id_planificacion),
    id_articulo       INT NOT NULL REFERENCES articulo (id_articulo),
    fecha             DATE NOT NULL,
    kg_saldo_inicial  NUMERIC(12,3) NOT NULL CHECK (kg_saldo_inicial >= 0),
    kg_ingresados     NUMERIC(12,3) NOT NULL DEFAULT 0 CHECK (kg_ingresados >= 0),
    kg_consumidos     NUMERIC(12,3) NOT NULL DEFAULT 0 CHECK (kg_consumidos >= 0),
    kg_devueltos      NUMERIC(12,3) NOT NULL DEFAULT 0 CHECK (kg_devueltos >= 0),
    kg_saldo_final    NUMERIC(12,3) NOT NULL CHECK (kg_saldo_final >= 0),
    CONSTRAINT uq_bolsa_articulo_dia UNIQUE (id_planificacion, id_articulo, fecha),
    CONSTRAINT ck_bolsa_balance
        CHECK (kg_saldo_final = kg_saldo_inicial + kg_ingresados + kg_devueltos - kg_consumidos)
);

-- ---------------------------------------------------------------------
-- recomendacion_tejido (RF30, HU-13, D-56)
-- Tejido adicional para la MISMA semana de una versión de planificación.
-- ---------------------------------------------------------------------
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

-- ---------------------------------------------------------------------
-- incidencia
-- Exactamente una entidad afectada, la que corresponde a su tipo:
--   INCIDENCIA_MAQUINA   → máquina, con fecha y hora prevista de
--                          disponibilidad.
--   PROBLEMA_COMPLEMENTO → partida, con fecha y hora prevista de
--                          disponibilidad.
--   CAMBIO_PRIORIDAD     → pedido, con nueva_fecha_entrega y sin fecha
--                          prevista de disponibilidad.
-- motivo_cierre (D-58): motivo al cerrarla sin re-planificar (ATENDIDA,
--   D-54) o al anularla (ANULADA, D-57).
-- fecha_entrega_anterior (D-58): fecha de entrega que tenía el pedido
--   antes de aplicar el cambio de prioridad; permite devolverla si la
--   incidencia se anula (D-57). Solo en CAMBIO_PRIORIDAD.
-- ---------------------------------------------------------------------
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
    descripcion                      VARCHAR(255),
    fecha_hora_ocurrencia           TIMESTAMP NOT NULL,
    fecha_hora_disponibilidad        TIMESTAMP,
    nueva_fecha_entrega              DATE,
    fecha_entrega_anterior           DATE,
    estado                           VARCHAR(30) NOT NULL
                                     CHECK (estado IN ('PENDIENTE', 'CONSIDERADA', 'ATENDIDA', 'ANULADA')),
    motivo_cierre                    VARCHAR(255),
    CONSTRAINT ck_incidencia_entidad_exclusiva
        CHECK (num_nonnulls(id_pedido, id_partida, id_maquina) = 1),
    CONSTRAINT ck_incidencia_tipo_entidad CHECK (
        (tipo_incidencia = 'INCIDENCIA_MAQUINA'   AND id_maquina IS NOT NULL AND nueva_fecha_entrega IS NULL) OR
        (tipo_incidencia = 'PROBLEMA_COMPLEMENTO' AND id_partida IS NOT NULL AND nueva_fecha_entrega IS NULL) OR
        (tipo_incidencia = 'CAMBIO_PRIORIDAD'     AND id_pedido  IS NOT NULL AND nueva_fecha_entrega IS NOT NULL
                                                  AND fecha_hora_disponibilidad IS NULL)
    ),
    CONSTRAINT ck_incidencia_fecha_anterior
        CHECK (fecha_entrega_anterior IS NULL OR tipo_incidencia = 'CAMBIO_PRIORIDAD'),
    CONSTRAINT ck_incidencia_motivo_cierre
        CHECK (motivo_cierre IS NULL OR estado IN ('ATENDIDA', 'ANULADA'))
);

-- ---------------------------------------------------------------------
-- planificacion_incidencia
-- ---------------------------------------------------------------------
CREATE TABLE planificacion_incidencia (
    id_planificacion_incidencia  SERIAL PRIMARY KEY,
    id_planificacion              INT NOT NULL REFERENCES planificacion_semanal (id_planificacion),
    id_incidencia                  INT NOT NULL REFERENCES incidencia (id_incidencia),
    CONSTRAINT uq_planificacion_incidencia UNIQUE (id_planificacion, id_incidencia)
);

-- ---------------------------------------------------------------------
-- Índices (D-58)
-- Las claves primarias y las restricciones UNIQUE ya crean su índice.
-- Estos cubren las claves foráneas que se recorren en consultas y
-- eliminaciones, y las búsquedas del algoritmo al construir la semana.
-- ---------------------------------------------------------------------
CREATE INDEX idx_carga_estado_fecha          ON carga_datos (estado, fecha_hora);
CREATE INDEX idx_pedido_cliente              ON pedido (id_cliente);
CREATE INDEX idx_pedido_carga                ON pedido (id_carga);
CREATE INDEX idx_historial_pedido            ON historial_estado_pedido (id_pedido, fecha_hora);
CREATE INDEX idx_detalle_pedido_pedido       ON detalle_pedido (id_pedido);
CREATE INDEX idx_detalle_pedido_articulo     ON detalle_pedido (id_articulo);
CREATE INDEX idx_detalle_pedido_carga        ON detalle_pedido (id_carga);
CREATE INDEX idx_partida_estado              ON partida (estado_partida, fecha_registro);
CREATE INDEX idx_partida_pedido              ON partida (id_pedido);
CREATE INDEX idx_partida_color               ON partida (id_color);
CREATE INDEX idx_partida_carga               ON partida (id_carga);
CREATE INDEX idx_historial_partida           ON historial_estado_partida (id_partida, fecha_hora);
CREATE INDEX idx_componente_partida          ON partida_componente (id_partida);
CREATE INDEX idx_componente_detalle          ON partida_componente (id_detalle_pedido);
CREATE INDEX idx_proyeccion_semana           ON proyeccion_tejeduria (id_semana, version);
CREATE INDEX idx_proyeccion_carga            ON proyeccion_tejeduria (id_carga);
CREATE INDEX idx_detproy_proyeccion          ON detalle_proyeccion_tejeduria (id_proyeccion, fecha_proyectada);
CREATE INDEX idx_detproy_fecha               ON detalle_proyeccion_tejeduria (id_detalle_pedido, fecha_proyectada);
CREATE INDEX idx_indisponibilidad_maquina    ON indisponibilidad_maquina (id_maquina, fecha_hora_inicio, fecha_hora_fin);
CREATE INDEX idx_planificacion_semana        ON planificacion_semanal (id_semana, estado);
CREATE INDEX idx_planificacion_proyeccion    ON planificacion_semanal (id_proyeccion);
CREATE INDEX idx_planpartida_partida         ON planificacion_partida (id_partida);
CREATE INDEX idx_planpartida_maquina_dia     ON planificacion_partida (id_maquina, dia_abastecimiento, orden_secuencia);
CREATE INDEX idx_bolsa_articulo_fecha        ON bolsa_articulo (id_articulo, fecha);
CREATE INDEX idx_recomendacion_planificacion ON recomendacion_tejido (id_planificacion);
CREATE INDEX idx_incidencia_estado           ON incidencia (estado);
CREATE INDEX idx_incidencia_planificacion    ON incidencia (id_planificacion_origen);
CREATE INDEX idx_planinc_incidencia          ON planificacion_incidencia (id_incidencia);

-- ---------------------------------------------------------------------
-- Historial de estados del pedido (D-58)
-- ---------------------------------------------------------------------
CREATE FUNCTION fn_registrar_estado_pedido() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' OR NEW.estado IS DISTINCT FROM OLD.estado THEN
        INSERT INTO historial_estado_pedido (id_pedido, estado_anterior, estado_nuevo, id_usuario)
        VALUES (NEW.id_pedido,
                CASE WHEN TG_OP = 'UPDATE' THEN OLD.estado END,
                NEW.estado,
                NULLIF(current_setting('spats.id_usuario', TRUE), '')::INT);
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_pedido_historial_estado
    AFTER INSERT OR UPDATE OF estado ON pedido
    FOR EACH ROW EXECUTE FUNCTION fn_registrar_estado_pedido();

-- ---------------------------------------------------------------------
-- Historial de estados de la partida (D-58)
-- ---------------------------------------------------------------------
CREATE FUNCTION fn_registrar_estado_partida() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' OR NEW.estado_partida IS DISTINCT FROM OLD.estado_partida THEN
        INSERT INTO historial_estado_partida (id_partida, estado_anterior, estado_nuevo, id_usuario)
        VALUES (NEW.id_partida,
                CASE WHEN TG_OP = 'UPDATE' THEN OLD.estado_partida END,
                NEW.estado_partida,
                NULLIF(current_setting('spats.id_usuario', TRUE), '')::INT);
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_partida_historial_estado
    AFTER INSERT OR UPDATE OF estado_partida ON partida
    FOR EACH ROW EXECUTE FUNCTION fn_registrar_estado_partida();

-- =====================================================================
-- Esquema auditoria (D-58)
-- Una bitácora por entidad, separada de las tablas de negocio. Cada una
-- guarda las altas, modificaciones y eliminaciones de su entidad y de las
-- tablas que dependen de ella (tabla_origen). Las llenan los disparadores
-- con la función auditoria.fn_registrar_cambio, de modo que el registro
-- no depende de la aplicación. El backend indica el usuario con
--   SELECT set_config('spats.id_usuario', '<id>', true);
-- al inicio de cada transacción; si no lo indica, id_usuario queda NULL
-- (operación del sistema o de un script).
--   id_registro: id de la entidad principal de la bitácora (por ejemplo,
--     id_pedido también para las filas de detalle_pedido).
--   valores_anteriores / valores_nuevos: fila completa al crear o
--     eliminar; solo los campos que cambiaron al modificar. Nunca se
--     guarda la contraseña.
-- =====================================================================
CREATE SCHEMA auditoria;

-- Las ocho bitácoras tienen la misma estructura, por lo que se crean en un
-- solo bloque, cada una con su clave primaria, su CHECK e índices por
-- entidad, por usuario y por fecha.
DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['bitacora_usuario', 'bitacora_carga', 'bitacora_pedido', 'bitacora_partida',
                             'bitacora_proyeccion', 'bitacora_maquina', 'bitacora_planificacion',
                             'bitacora_incidencia'] LOOP
        EXECUTE format('CREATE TABLE auditoria.%I (
            id_bitacora        BIGSERIAL PRIMARY KEY,
            id_registro        INT NOT NULL,
            tabla_origen       VARCHAR(60) NOT NULL,
            operacion          VARCHAR(10) NOT NULL CHECK (operacion IN (''CREAR'', ''MODIFICAR'', ''ELIMINAR'')),
            id_usuario         INT,
            fecha_hora         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            valores_anteriores JSONB,
            valores_nuevos     JSONB,
            CONSTRAINT %I CHECK (
                (operacion = ''CREAR''     AND valores_anteriores IS NULL     AND valores_nuevos IS NOT NULL) OR
                (operacion = ''MODIFICAR'' AND valores_anteriores IS NOT NULL AND valores_nuevos IS NOT NULL) OR
                (operacion = ''ELIMINAR''  AND valores_anteriores IS NOT NULL AND valores_nuevos IS NULL)))',
            t, 'ck_' || t || '_valores');
        EXECUTE format('CREATE INDEX %I ON auditoria.%I (id_registro, fecha_hora)', 'idx_' || t || '_registro', t);
        EXECUTE format('CREATE INDEX %I ON auditoria.%I (id_usuario, fecha_hora)',  'idx_' || t || '_usuario', t);
        EXECUTE format('CREATE INDEX %I ON auditoria.%I (fecha_hora)',              'idx_' || t || '_fecha', t);
    END LOOP;
END;
$$;

-- Función de los disparadores.
--   TG_ARGV[0]: bitácora de destino (por ejemplo, 'bitacora_pedido').
--   TG_ARGV[1]: columna con el id de la entidad principal.
CREATE FUNCTION auditoria.fn_registrar_cambio() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    v_anterior JSONB;
    v_nuevo    JSONB;
    v_id       INT;
BEGIN
    IF TG_OP = 'INSERT' THEN
        v_nuevo := to_jsonb(NEW) - 'contrasena';
        v_id    := (v_nuevo ->> TG_ARGV[1])::INT;
    ELSIF TG_OP = 'DELETE' THEN
        v_anterior := to_jsonb(OLD) - 'contrasena';
        v_id       := (v_anterior ->> TG_ARGV[1])::INT;
    ELSE
        SELECT jsonb_object_agg(a.key, a.value), jsonb_object_agg(a.key, n.value)
          INTO v_anterior, v_nuevo
          FROM jsonb_each(to_jsonb(OLD) - 'contrasena') a
          JOIN jsonb_each(to_jsonb(NEW) - 'contrasena') n USING (key)
         WHERE a.value IS DISTINCT FROM n.value;
        IF v_anterior IS NULL THEN
            RETURN NEW;            -- no cambió ningún campo registrable
        END IF;
        v_id := (to_jsonb(NEW) ->> TG_ARGV[1])::INT;
    END IF;

    EXECUTE format('INSERT INTO auditoria.%I (id_registro, tabla_origen, operacion, id_usuario,
                                              valores_anteriores, valores_nuevos)
                    VALUES ($1, $2, $3, $4, $5, $6)', TG_ARGV[0])
      USING v_id, TG_TABLE_NAME,
            CASE TG_OP WHEN 'INSERT' THEN 'CREAR' WHEN 'UPDATE' THEN 'MODIFICAR' ELSE 'ELIMINAR' END,
            NULLIF(current_setting('spats.id_usuario', TRUE), '')::INT,
            v_anterior, v_nuevo;
    RETURN NULL;                    -- disparador AFTER
END;
$$;

-- Disparadores: tabla de negocio → bitácora (columna de la entidad principal)
CREATE TRIGGER tg_aud_usuario AFTER INSERT OR UPDATE OR DELETE ON usuario
    FOR EACH ROW EXECUTE FUNCTION auditoria.fn_registrar_cambio('bitacora_usuario', 'id_usuario');
CREATE TRIGGER tg_aud_carga_datos AFTER INSERT OR UPDATE OR DELETE ON carga_datos
    FOR EACH ROW EXECUTE FUNCTION auditoria.fn_registrar_cambio('bitacora_carga', 'id_carga');
CREATE TRIGGER tg_aud_pedido AFTER INSERT OR UPDATE OR DELETE ON pedido
    FOR EACH ROW EXECUTE FUNCTION auditoria.fn_registrar_cambio('bitacora_pedido', 'id_pedido');
CREATE TRIGGER tg_aud_detalle_pedido AFTER INSERT OR UPDATE OR DELETE ON detalle_pedido
    FOR EACH ROW EXECUTE FUNCTION auditoria.fn_registrar_cambio('bitacora_pedido', 'id_pedido');
CREATE TRIGGER tg_aud_partida AFTER INSERT OR UPDATE OR DELETE ON partida
    FOR EACH ROW EXECUTE FUNCTION auditoria.fn_registrar_cambio('bitacora_partida', 'id_partida');
CREATE TRIGGER tg_aud_partida_componente AFTER INSERT OR UPDATE OR DELETE ON partida_componente
    FOR EACH ROW EXECUTE FUNCTION auditoria.fn_registrar_cambio('bitacora_partida', 'id_partida');
CREATE TRIGGER tg_aud_proyeccion AFTER INSERT OR UPDATE OR DELETE ON proyeccion_tejeduria
    FOR EACH ROW EXECUTE FUNCTION auditoria.fn_registrar_cambio('bitacora_proyeccion', 'id_proyeccion');
CREATE TRIGGER tg_aud_maquina AFTER INSERT OR UPDATE OR DELETE ON maquina_tintoreria
    FOR EACH ROW EXECUTE FUNCTION auditoria.fn_registrar_cambio('bitacora_maquina', 'id_maquina');
CREATE TRIGGER tg_aud_maquina_tonalidad AFTER INSERT OR UPDATE OR DELETE ON maquina_tonalidad_aceptada
    FOR EACH ROW EXECUTE FUNCTION auditoria.fn_registrar_cambio('bitacora_maquina', 'id_maquina');
CREATE TRIGGER tg_aud_indisponibilidad AFTER INSERT OR UPDATE OR DELETE ON indisponibilidad_maquina
    FOR EACH ROW EXECUTE FUNCTION auditoria.fn_registrar_cambio('bitacora_maquina', 'id_maquina');
CREATE TRIGGER tg_aud_planificacion AFTER INSERT OR UPDATE OR DELETE ON planificacion_semanal
    FOR EACH ROW EXECUTE FUNCTION auditoria.fn_registrar_cambio('bitacora_planificacion', 'id_planificacion');
CREATE TRIGGER tg_aud_incidencia AFTER INSERT OR UPDATE OR DELETE ON incidencia
    FOR EACH ROW EXECUTE FUNCTION auditoria.fn_registrar_cambio('bitacora_incidencia', 'id_incidencia');
CREATE TRIGGER tg_aud_planificacion_incidencia AFTER INSERT OR UPDATE OR DELETE ON planificacion_incidencia
    FOR EACH ROW EXECUTE FUNCTION auditoria.fn_registrar_cambio('bitacora_incidencia', 'id_incidencia');

-- Vista con todas las operaciones (trazabilidad, RF60): permite ver en una
-- sola consulta lo que hizo un usuario o lo que ocurrió en un periodo.
CREATE VIEW auditoria.v_operaciones AS
          SELECT 'USUARIO'       AS entidad, b.* FROM auditoria.bitacora_usuario b
UNION ALL SELECT 'CARGA',                    b.* FROM auditoria.bitacora_carga b
UNION ALL SELECT 'PEDIDO',                   b.* FROM auditoria.bitacora_pedido b
UNION ALL SELECT 'PARTIDA',                  b.* FROM auditoria.bitacora_partida b
UNION ALL SELECT 'PROYECCION',               b.* FROM auditoria.bitacora_proyeccion b
UNION ALL SELECT 'MAQUINA',                  b.* FROM auditoria.bitacora_maquina b
UNION ALL SELECT 'PLANIFICACION',            b.* FROM auditoria.bitacora_planificacion b
UNION ALL SELECT 'INCIDENCIA',               b.* FROM auditoria.bitacora_incidencia b;

COMMIT;
