-- =====================================================================
-- SPATS — Sistema de Planificación de Abastecimiento de Tintorería
-- Script de creación de base de datos — PostgreSQL (base "tintoreria")
--
-- Base: modelo físico del R3.3 con los dominios confirmados (22 tablas).
-- Ejecutar conectado a la base "tintoreria" (psql o pgAdmin). El script
-- es re-ejecutable: primero elimina las 22 tablas y luego las crea.
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
--   * bitacora_auditoria: id_entidad (referencia polimórfica a la fila
--     afectada) y valores anterior/nuevo de hasta 255 caracteres.
-- Correcciones del script (2026-10-25, ver CONTROL_DE_VERSIONES):
--   * S1: el bloque DROP incluye las 22 tablas (faltaban
--     planificacion_maquina y bolsa_articulo).
--   * S2: comentarios corregidos; los ALTER finales se integraron en las
--     sentencias CREATE (mismo resultado, sin fecha_conformacion).
--   * S3: bitacora_auditoria.id_entidad.
--   * S4: bitacora_auditoria.valor_anterior/valor_nuevo VARCHAR(255).
--   * P4: incidencia.descripcion VARCHAR(255).
-- =====================================================================

-- ---------------------------------------------------------------------
-- Eliminación de las tablas (orden inverso de dependencias)
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS planificacion_incidencia CASCADE;
DROP TABLE IF EXISTS incidencia CASCADE;
DROP TABLE IF EXISTS bolsa_articulo CASCADE;
DROP TABLE IF EXISTS planificacion_maquina CASCADE;
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
-- ---------------------------------------------------------------------
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

-- ---------------------------------------------------------------------
-- carga_datos
-- ---------------------------------------------------------------------
CREATE TABLE carga_datos (
    id_carga       SERIAL PRIMARY KEY,
    id_usuario     INT NOT NULL REFERENCES usuario (id_usuario),
    tipo_dato      VARCHAR(40) NOT NULL
                   CHECK (tipo_dato IN ('PEDIDOS', 'PARTIDAS', 'PROYECCION_TEJEDURIA')),
    nombre_archivo VARCHAR(255) NOT NULL,
    fecha_hora     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---------------------------------------------------------------------
-- bitacora_auditoria
-- id_entidad: id de la fila afectada en la tabla nombrada por
--   entidad_afectada (referencia polimórfica, por eso sin FK).
-- ---------------------------------------------------------------------
CREATE TABLE bitacora_auditoria (
    id_bitacora       SERIAL PRIMARY KEY,
    id_usuario        INT NOT NULL REFERENCES usuario (id_usuario),
    fecha_hora        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    entidad_afectada  VARCHAR(80) NOT NULL,
    id_entidad        INT,
    campo_modificado  VARCHAR(100),
    valor_anterior    VARCHAR(255),
    valor_nuevo       VARCHAR(255),
    tipo_operacion    VARCHAR(30) NOT NULL
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
-- ---------------------------------------------------------------------
CREATE TABLE pedido (
    id_pedido     SERIAL PRIMARY KEY,
    id_cliente    INT NOT NULL REFERENCES cliente (id_cliente),
    numero_op     VARCHAR(50) NOT NULL UNIQUE,
    fecha_entrega DATE NOT NULL,
    estado        VARCHAR(30) NOT NULL
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
    peso_total_requerido  NUMERIC(10,2) NOT NULL CHECK (peso_total_requerido > 0)
);

-- ---------------------------------------------------------------------
-- color
-- clasificacion_color: tipo de color de la barca (C color, B blanco);
--   decide a qué máquinas puede ir la partida.
-- tonalidad: tono del color dentro de una misma máquina. Pasar de una
--   tonalidad más oscura a una más clara en la misma máquina requiere
--   lavado (RF26). Orden de más clara a más oscura:
--   BLANCO < CLARO < MEDIO < OSCURO.
--   SUPUESTO: los cuatro valores vienen de la experimentación (R2.2);
--   la lista formal de tonalidades se confirmará con el jefe de
--   planeamiento.
-- ---------------------------------------------------------------------
CREATE TABLE color (
    id_color             SERIAL PRIMARY KEY,
    codigo_color         VARCHAR(50)  NOT NULL UNIQUE,
    nombre                VARCHAR(100) NOT NULL,
    clasificacion_color   VARCHAR(50)  NOT NULL,
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
    estado_partida  VARCHAR(20) NOT NULL
                    CHECK (estado_partida IN ('PENDIENTE', 'ARRASTRADA', 'COMPLETADA'))
);

-- ---------------------------------------------------------------------
-- partida_componente
-- Sin peso_disponible_acumulado: una partida nunca se llena a medias;
-- la tela disponible se registra en bolsa_articulo.
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
-- Cada carga de la proyección de una semana crea una versión nueva; la
-- de mayor número es la que se usa para planificar. Las anteriores se
-- conservan como historial (RF56).
-- ---------------------------------------------------------------------
CREATE TABLE proyeccion_tejeduria (
    id_proyeccion  SERIAL PRIMARY KEY,
    id_semana      INT NOT NULL REFERENCES semana (id_semana),
    version        INT NOT NULL CHECK (version > 0),
    fecha_carga    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    estado         VARCHAR(30) NOT NULL
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
    clasificacion_color      VARCHAR(50) NOT NULL,
    tiempo_procesamiento      NUMERIC(10,2) NOT NULL CHECK (tiempo_procesamiento > 0),
    objetivo_semanal          INT NOT NULL CHECK (objetivo_semanal > 0),
    barca                     VARCHAR(80),
    estado                    VARCHAR(30) NOT NULL
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

-- ---------------------------------------------------------------------
-- planificacion_partida
-- clasificacion_resultado (resultado dentro de esta versión):
--   ASIGNADA  — conformada y ubicada en máquina y día.
--   SOBRANTE  — conformada; excede el objetivo semanal de su máquina o
--               ya no hay espacio. Se muestra en su primer día disponible.
--   BLOQUEADA — la bolsa de al menos uno de sus artículos tuvo kilos,
--               pero no alcanzaron para completar toda la partida
--               (principal o complemento). No tiene máquina ni consume
--               tela; sigue PENDIENTE para la semana siguiente.
-- fecha_disponible: día en que la tela completa de la partida queda
--   disponible en tintorería (lo tejido de lunes a viernes, al día
--   siguiente; lo tejido el sábado, el lunes). El día de abastecimiento
--   nunca es anterior.
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
    fecha_disponible                DATE,
    estado_confirmacion_cierre       VARCHAR(20)
                                     CHECK (estado_confirmacion_cierre IN ('COMPLETADA', 'ARRASTRADA')),
    CONSTRAINT uq_partida_por_planificacion UNIQUE (id_planificacion, id_partida),
    CONSTRAINT ck_planpart_bloqueada_sin_maquina
        CHECK ((clasificacion_resultado = 'BLOQUEADA') = (id_maquina IS NULL)),
    CONSTRAINT ck_planpart_dia_desde_disponible
        CHECK (dia_abastecimiento IS NULL OR fecha_disponible IS NULL
               OR dia_abastecimiento >= fecha_disponible)
);

-- ---------------------------------------------------------------------
-- planificacion_maquina
-- Resultado de cada máquina en cada versión de planificación.
--   objetivo_aplicado: objetivo semanal usado en esa versión (16 de
--     referencia; baja a 14 si la máquina está indisponible un día).
--   minimo_diario / maximo_diario: reparto diario en partidas
--     completas (2 y 3 con objetivo 16).
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
-- planificación. La bolsa es del artículo: reúne lo tejido para
-- cualquier pedido y no pertenece a ninguna partida.
--   kg_saldo_inicial: lo que quedó del día anterior (el primer día de
--     la semana, el saldo final del domingo anterior de la versión
--     vigente).
--   kg_ingresados: tela que llega ese día según la proyección.
--   kg_consumidos: tela de las partidas que se completaron ese día.
--   kg_saldo_final: lo que pasa al día siguiente. El del domingo pasa
--     a la semana siguiente.
-- ---------------------------------------------------------------------
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

-- ---------------------------------------------------------------------
-- incidencia
-- Exactamente una entidad afectada, la que corresponde a su tipo:
--   INCIDENCIA_MAQUINA   → máquina, con fecha y hora prevista de
--                          disponibilidad.
--   PROBLEMA_COMPLEMENTO → partida, con fecha y hora prevista de
--                          disponibilidad.
--   CAMBIO_PRIORIDAD     → pedido, con nueva_fecha_entrega y sin fecha
--                          prevista de disponibilidad.
-- descripcion: detalle libre que registra el planificador.
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
    estado                           VARCHAR(30) NOT NULL,
    CONSTRAINT ck_incidencia_entidad_exclusiva
        CHECK (num_nonnulls(id_pedido, id_partida, id_maquina) = 1),
    CONSTRAINT ck_incidencia_tipo_entidad CHECK (
        (tipo_incidencia = 'INCIDENCIA_MAQUINA'   AND id_maquina IS NOT NULL AND nueva_fecha_entrega IS NULL) OR
        (tipo_incidencia = 'PROBLEMA_COMPLEMENTO' AND id_partida IS NOT NULL AND nueva_fecha_entrega IS NULL) OR
        (tipo_incidencia = 'CAMBIO_PRIORIDAD'     AND id_pedido  IS NOT NULL AND nueva_fecha_entrega IS NOT NULL
                                                  AND fecha_hora_disponibilidad IS NULL)
    )
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
-- Índices de apoyo
-- ---------------------------------------------------------------------
CREATE INDEX idx_partida_estado             ON partida (estado_partida);
CREATE INDEX idx_partida_pedido             ON partida (id_pedido);
CREATE INDEX idx_detproy_fecha              ON detalle_proyeccion_tejeduria (id_detalle_pedido, fecha_proyectada);
CREATE INDEX idx_planpartida_maquina_dia    ON planificacion_partida (id_maquina, dia_abastecimiento, orden_secuencia);
CREATE INDEX idx_incidencia_estado          ON incidencia (estado);
CREATE INDEX idx_bolsa_articulo_fecha       ON bolsa_articulo (id_articulo, fecha);
