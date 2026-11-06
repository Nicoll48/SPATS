# Revisión de `BD/BD.sql` frente al `.md` y al algoritmo

Fecha: 25 de octubre de 2026 · Estado: **pendiente de respuesta de la autora**

El script crea las 22 tablas del `.md` §9.1 y cumple todas las restricciones de §9.1 (semana de lunes a domingo, `vigente_semana`, `ck_planpart_bloqueada_sin_maquina`, `dia_abastecimiento ≥ fecha_disponible`, entidad única por tipo de incidencia, balance de `planificacion_maquina` y de `bolsa_articulo`).

Cruzado con el algoritmo: `planificacion_partida.fecha_disponible` = `AsignacionPartida.fechaDisponible` (el día en que la tela de la partida queda completa en tintorería), y la restricción `dia_abastecimiento ≥ fecha_disponible` siempre se cumple con la salida del ACO.

## S. Errores del script (propongo corregirlos; no los toco sin confirmación)

| # | Problema | Efecto | Propuesta |
|---|---|---|---|
| S1 | Los `DROP` del inicio no incluyen `planificacion_maquina` ni `bolsa_articulo`. | Al ejecutar el script por segunda vez falla con "relation already exists". | Agregar los dos `DROP TABLE IF EXISTS … CASCADE`. |
| S2 | El encabezado menciona "las dos líneas siguientes (CREATE DATABASE y \c)", que no existen. También describe `fecha_conformacion` como nueva, pero el `ALTER` final la elimina. | Solo confunde. | Corregir los comentarios. |
| S3 | `bitacora_auditoria` no tiene columna para el id de la entidad afectada. El `.md` §10.4 habla de una "referencia polimórfica". | No se puede saber **qué** pedido o partida se modificó (RF60). | Agregar `id_entidad INT` (sin FK, porque es polimórfica). |
| S4 | `bitacora_auditoria.valor_anterior` y `valor_nuevo` son `VARCHAR(80)`. | Un motivo de indisponibilidad (255) o el nombre de un cliente (150) no caben. | Ampliar a `VARCHAR(255)`. |

## P. Preguntas que el script no responde

| # | Pregunta | Propuesta por defecto |
|---|---|---|
| P1 | `proyeccion_tejeduria` tiene `version`. Si se carga dos veces la proyección de una semana, ¿se usa solo la última versión o se acumulan? | Se usa la **última versión** de la semana (la carga nueva reemplaza la anterior **de esa semana**; las versiones viejas se conservan, RF56). Sus detalles se filtran del sábado anterior al viernes. |
| P2 | `clasificacion_color` (en `color` y en `maquina_tintoreria`) no tiene dominio. | `'C'` y `'B'`, igual que la letra de la barca. Al importar se valida que la letra de `partida.barca` coincida con la clasificación de su color. |
| P3 | `partida.barca` y `maquina_tintoreria.barca` aceptan `NULL`, pero el algoritmo lanza una excepción con una partida o máquina sin barca. | Partida sin barca: no se envía y se avisa (§9.2). Máquina sin barca: no se genera la planificación por datos incompletos (RN-23), con un mensaje que nombra la máquina. |
| P4 | `incidencia` no tiene columna de descripción, pero el frontend tiene un campo "Detalle". | Quitar "Detalle" del frontend; o bien agregar `descripcion VARCHAR(255)` al script. |
| P5 | Plantillas de importación: las del frontend (`public/plantillas/*.csv`) no coinciden con la base (no traen barca, color con tonalidad, componentes con tipo y tubular abierto, ni la proyección por pedido). | Defino 3 plantillas `.xlsx` a partir de las columnas del script: **Pedidos** (pedidos, clientes, artículos y detalle de pedido), **Partidas** (partidas, colores y componentes) y **Proyección de tejeduría**. Las documento en `docs/plantillas.md`. |
| P6 | El script usa `SERIAL` y no tiene columnas `@Version`. | Uso `IDENTITY` en JPA, sin bloqueo optimista. El volumen por versión es pequeño (unas 150 filas). |

## Vacíos del `.md` §13: los aplico como en el análisis inicial (§E), salvo indicación contraria

`SIN_MAQUINA` no se registra · día con indisponibilidad parcial = día indisponible · dominios propuestos · cambio de prioridad solo en la incidencia · volver a generar la inicial crea una versión nueva vigente · primera semana con stock 0 · se permite planificar sin cerrar la semana anterior · recomendaciones RF30 fuera de esta entrega.
