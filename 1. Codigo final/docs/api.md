# API REST

Prefijo `/api`, JSON con nombres en camelCase y fechas ISO (`2026-09-14`, `2026-09-14T08:00:00`, zona America/Lima).

- **Autenticación:** cabecera `Authorization: Bearer <token>`.
- **Errores:** `application/problem+json` con `title`, `status`, `detail` (en español, se muestra tal cual) y `codigo` (estable). Según el caso también traen `errores` (400), `problemas` (archivo con errores) o `detalles` (fallo de la planificación).
- **Permisos:** cualquier usuario autenticado lee (`GET`); todo lo demás exige el perfil **Planificador** (403 para Consulta).

## Sesión

| Método y ruta | Descripción |
|---|---|
| `POST /auth/login` | `{usuario, contrasena}` → `{token, expiraEn, usuario}`. 401 si las credenciales no son válidas. |
| `POST /auth/renovacion` | Token nuevo mientras hay actividad (RF04). |
| `GET /auth/yo` | Usuario autenticado. |
| `PUT /auth/yo/modo` | `{modoInteraccion: GUIADO\|EXPERTO}`. Solo el Planificador (RF07). |

## Planificación

| Método y ruta | Descripción |
|---|---|
| `GET /semanas/{lunes}/estado` | Proyección vigente, planificación vigente, número de versiones del historial, `stockDesactualizado` (texto del aviso si la columna `stock_desactualizado` está activa, o `null`; D-19) y `propuestaPendiente`. |
| `GET /semanas/{lunes}/planificacion` | Planificación vigente completa (calendario, resumen, bloqueadas, distribución y alertas). 404 si no hay. |
| `GET /semanas/{lunes}/versiones-proyeccion` | Solo Planificador (D-52). Versiones no eliminadas de la proyección con su planificación en uso (vigente o candidata, o null) → `{lunes, cerrada, idVigente, versiones: [{idProyeccion, version, fechaCarga, estado, kgTotales, planificacion}]}`. |
| `POST /semanas/{lunes}/planificaciones?proyeccion=` | Genera una versión con el algoritmo → 201 `{idPlanificacion, numeroVersion, vigente, versionProyeccion, indicadores, detenidaPorTiempo, advertencias}`. Sin `proyeccion` usa la versión VIGENTE de la proyección. Si la semana ya tiene una vigente generada con otra versión, la nueva queda `CANDIDATA` (`vigente=false`) y la vigente no cambia (D-52). Puede responder 422 (faltan datos, RN-23), 409 (semana cerrada, generación en curso, datos cambiaron) o 500 `FALLO_PLANIFICACION` (nada se guarda). |
| `GET /semanas/{lunes}/versiones` | Versiones de la semana (RF57). |
| `GET /planificaciones/{id}` | Cualquier versión. |
| `GET /planificaciones/{id}/bolsas` | `bolsa_articulo` de la versión. |
| `GET /planificaciones/comparacion?a=&b=` | Comparación de dos versiones. |
| `GET /planificaciones/{id}/exportacion` | `.xlsx` (RF41). Solo la vigente: 422 `SOLO_VIGENTE` con otra (D-52). |
| `GET /planificaciones/{id}/recomendaciones` | Tejido adicional recomendado para la misma semana, por máquina (RF30, D-56). Consulta: solo la vigente. |
| `GET /semanas/{lunes}/recomendaciones/exportacion` | `.xlsx` para tejeduría de la vigente, ordenado por día de tejido. Solo Planificador. |
| `GET /planificaciones/{id}/cambio-vigente` | Solo Planificador (D-52). Avisos antes de usar una candidata como vigente: `semanaIniciada`, `permitido`, `motivo`, incidencias pendientes y consideradas, re-planificaciones, `stockDesactualizado` y partidas de hoy. |
| `POST /planificaciones/{id}/cambio-vigente` | `{partidasEnMaquina?: [numero]}` → `{modo, idVigente, numeroVersion, replanificacion}`. `DIRECTO` si la semana no empezó (la candidata pasa a vigente). `REPLANIFICACION` si ya empezó: `replanificacion` es una propuesta desde hoy que se confirma con `POST /planificaciones/{id}/confirmacion`. 422 `CAMBIO_NO_PERMITIDO` si la semana se cerró o ya terminó, o hay una propuesta pendiente. |
| `POST /planificaciones/{id}/cierre` | `{partidasCompletadas: [...]}` → cierre de semana (HU-15). 409 `SEMANA_ANTERIOR_ABIERTA` si la semana anterior sigue abierta (D-19). |
| `GET /partidas/arrastradas` | Lista única de partidas ARRASTRADA de cualquier semana, con `semanaCierre` y `semanasArrastrada` (RF45, D-27). |
| `POST /partidas/arrastradas/{numero}/completado` | Marca una arrastrada como completada (RF49). |

## Carga y datos

| Método y ruta | Descripción |
|---|---|
| `POST /cargas/{PEDIDOS\|PARTIDAS\|PROYECCION_TEJEDURIA}` | Multipart `archivo` (.xlsx); para la proyección, `?semana=<lunes>&confirmado=`. 422 con `problemas` si el archivo tiene errores (no se guarda nada); 409 `CONFIRMACION_REQUERIDA` si la semana tiene planificación vigente. |
| `GET /cargas` | Historial de cargas (RF14). |
| `DELETE /cargas/{id}` | Elimina lo que agregó ese archivo → `{tipo, archivo, eliminados}` (D-40). 409 `CARGA_EN_USO` si otros datos lo usan: no se elimina nada. En la proyección, la versión y la carga quedan como eliminadas (D-46). |
| `GET /plantillas/{tipo}` | Plantilla vacía con filas de ejemplo. |
| `GET /exportaciones/{tipo}?semana=` | Datos registrados en el formato de la plantilla (RF12). |
| `GET /pedidos` · `PUT /pedidos/{id}` · `PUT /detalles-pedido/{id}` | Pedidos con sus artículos; modificación manual. |
| `DELETE /pedidos/{id}` · `DELETE /articulos/{id}` · `DELETE /partidas/{id}` · `DELETE /colores/{id}` | Eliminan una fila (D-42). 409 si otros datos la usan. |
| `GET /articulos` · `PUT /articulos/{id}` | Artículos. |
| `GET /partidas[?estado=]` · `PUT /partidas/{id}` · `PUT /componentes/{id}` | Partidas con componentes; solo se modifican las PENDIENTE. Si la partida está Asignada o Sobrante en una vigente abierta, responde 409 `CONFIRMACION_REQUERIDA`; con `?confirmado=true` esa vigente pasa a INVALIDADA y se marca la semana siguiente (D-24). |
| `GET /colores` · `PUT /colores/{id}` | Colores (clasificación C/B y tonalidad). |
| `GET /semanas/{lunes}/proyecciones` | Versiones de la proyección. |
| `GET /proyecciones/{id}/detalles` | Registros de una versión, con su fecha de disponibilidad. |
| `DELETE /proyecciones/{id}?confirmado=` | Marca una versión como `ELIMINADA` y borra las planificaciones generadas con ella → `{version, registros, planificaciones}` (D-44, D-46). Se vuelve a ella con `POST /proyecciones/{id}/restauracion`. Sin `confirmado`, 409 `CONFIRMACION_REQUERIDA` si tiene planificaciones. 409 si la semana se cerró o tiene incidencias. |
| `PUT /detalles-proyeccion/{id}?confirmado=` | Corrige un registro de la versión vigente. |
| `DELETE /detalles-proyeccion/{id}?confirmado=` | Elimina un registro de la versión vigente. |
| `POST /proyecciones/{id}/restauracion` | Vuelve a una versión eliminada: la copia en una versión nueva. No cambia la planificación vigente (D-52). |

## Máquinas

| Método y ruta | Descripción |
|---|---|
| `GET /maquinas?busqueda=` | Búsqueda por código o clasificación, con el estado actual (RF18). |
| `POST /maquinas` · `PUT /maquinas/{id}` | Registro y modificación (RF15, RF19). La barca no se envía: sale de la capacidad y la clasificación (D-41). |
| `DELETE /maquinas/{id}` | Elimina la máquina y sus indisponibilidades. 409 `MAQUINA_CON_HISTORIAL` si aparece en una planificación o incidencia (D-42). |
| `GET /maquinas/indisponibilidades` · `DELETE /maquinas/indisponibilidades/{id}` | Intervalos de todas las máquinas; eliminar uno. |
| `GET /maquinas/{id}/indisponibilidades` · `POST` | Intervalos de indisponibilidad (RF16). `tipo` INDISPONIBLE o MANTENIMIENTO (columna `tipo_indisponibilidad`) define el estado (RF17). |

## Incidencias y trazabilidad

| Método y ruta | Descripción |
|---|---|
| `GET /semanas/{lunes}/incidencias` | Incidencias de la semana. |
| `POST /semanas/{lunes}/incidencias` | `{tipo, codigoMaquina\|numeroPartida\|numeroOp, fechaHoraOcurrencia, fechaHoraDisponibilidad?, nuevaFechaEntrega?, descripcion?}`. CAMBIO_PRIORIDAD guarda `nuevaFechaEntrega`; el pedido no cambia hasta que se confirme la re-planificación (D-13b). |
| `GET /semanas/{lunes}/replanificacion` | Día D (fecha del servidor), si se puede re-planificar y por qué no, partidas de hoy en la vigente y propuesta pendiente. |
| `POST /semanas/{lunes}/replanificacion` | `{incidencias: [id], partidasEnMaquina?: [numero]}`. Respuesta `resultado`: `PROPUESTA` (guarda una versión PROPUESTA no vigente) o `SE_CONSERVA_VIGENTE` (no guarda nada, RF55). Incluye la F nueva y la de la vigente evaluada sobre la misma instancia nueva. 409 `PROPUESTA_PENDIENTE` si ya hay una. |
| `POST /planificaciones/{id}/confirmacion` | Confirma la propuesta: pasa a vigente, incidencias CONSIDERADA, `pedido.fecha_entrega` actualizado. 409 `PROPUESTA_VENCIDA` si no es del mismo día (se elimina). |
| `DELETE /planificaciones/{id}` | Descarta una propuesta (solo PROPUESTA): se elimina con todas sus filas. |
| `GET /bitacora?pagina=&tamanio=&entidad=` | Trazabilidad paginada (RF60). |
| `GET /actuator/health` | Estado del servicio (sin autenticación). |
