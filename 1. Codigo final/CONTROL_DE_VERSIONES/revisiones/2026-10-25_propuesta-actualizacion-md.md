# Propuesta: actualizar `SPATS_contexto_sistema.md`

Fecha: 25/10/2026 · Versión 2: 02/11/2026 · Estado: **aprobada y aplicada el 02/11/2026** (CHANGELOG 0.2.3). Las filas 14, 15 y 37 se aplicaron con las respuestas de la autora del 02/11. Antes solo se habían aplicado B1–B5 (D-18).

La versión 2 agrega todo lo decidido después del 25/10. Los cambios se aplican solo cuando la autora los aprueba. Las filas marcadas **⚠ por definir** necesitan su respuesta antes de escribirse.

## §2.1 Dentro del alcance

| # | Cambio |
|---|---|
| 1 | «Cierre de semana, partidas arrastradas e incidencias con re-planificación» queda como **implementado**: re-planificación desde el día actual con propuesta, confirmación y descarte (versión 0.2.0). |
| 2 | Recomendaciones de tejeduría (RF30): regla aprobada, se implementa en un incremento posterior (ver §13 vacío 2). |

## §3 Glosario

| # | Cambio |
|---|---|
| 3 | **Bolsa:** tela **cruda** de un artículo, disponible para conformar partidas. Una bolsa por artículo; la clave es `articulo.codigo` (D-25). |

## §6 Estados y clasificaciones

| # | Cambio |
|---|---|
| 4 | Nueva §6.4 `planificacion_semanal.estado`: **ABIERTA** (vigente, semana en curso) · **CERRADA** (vigente, semana cerrada, RF42) · **REEMPLAZADA** (dejó de ser vigente por otra versión, D-14, o por una re-planificación confirmada) · **INVALIDADA** (cambió la proyección, D-06) · **PROPUESTA** (re-planificación sin confirmar, no vigente, D-13b; si se descarta se elimina y no queda en el historial, D-26). |
| 5 | §6.1: quitar «el dominio de `pedido.estado` está pendiente». Es PENDIENTE/COMPLETADO (D-20). |

## §7 Reglas de negocio (nuevas)

| # | Cambio |
|---|---|
| 6 | Cambiar la proyección de una semana con vigente abierta exige confirmación. La vigente pasa a INVALIDADA y el calendario queda en blanco hasta volver a generar. Se puede volver a una versión anterior de la proyección (D-06). |
| 7 | Volver a generar una semana crea una versión nueva vigente; la anterior queda REEMPLAZADA (D-14). La primera semana empieza con stock 0 (D-15). |
| 8 | **Marca de N+1 (D-19):** cualquier cambio en la vigente de N (otra versión, invalidación por la proyección o re-planificación confirmada) deja la vigente abierta de N+1 con `stock_desactualizado = TRUE`. Se guarda en la base y la pantalla sugiere volver a generarla. Cerrar N **no** la activa. |
| 9 | **Cierre en orden (D-19):** no se cierra N+1 mientras N tenga vigente abierta. |
| 10 | **D-16:** se puede planificar N+1 sin cerrar N. Las partidas Asignadas o Sobrantes de la vigente abierta de N no se envían al planificar N+1. |
| 11 | La generación es síncrona, con un tope de 60 s. No se permiten dos generaciones simultáneas de la misma semana (D-22). |
| 12 | Al importar, cada partida debe tener al menos un componente PRINCIPAL (D-23). |
| 13 | Solo se modifican a mano las partidas PENDIENTE; las arrastradas y completadas conservan sus datos (D-24). |
| 14 | **⚠ por definir (D-24):** modificar una partida que figura en la vigente abierta de una semana. Propuesta: igual que D-06, se pide confirmación, esa vigente pasa a INVALIDADA y se marca N+1 (D-19). **Hoy no está implementado ni registrado.** |
| 15 | **⚠ por definir (D-25):** «código limpio» y «aviso de prefijos». Propuesta: al importar, un código de artículo con prefijo entre paréntesis, como `(313811)TW2021AA00P03`, se guarda sin el prefijo (`TW2021AA00P03`) y se muestra un aviso. **Hoy no está implementado ni registrado.** Solo los datos de demostración usan la `clave_articulo` del libro. |

## §8 Algoritmo

| # | Cambio |
|---|---|
| 16 | El contrato de `spats-algoritmo` incluye `Replanificacion` (D, fijas, conservadas y disponibilidades) y `PlanificadorSemanal.evaluar`. La búsqueda del ACO y la función objetivo no cambiaron. |

## §9.1 Tablas y dominios

| # | Cambio |
|---|---|
| 17 | Columnas nuevas o cambiadas: `bitacora_auditoria.id_entidad` · `incidencia.descripcion` · `indisponibilidad_maquina.tipo_indisponibilidad` (D-21) · `bolsa_articulo.kg_devueltos` y `ck_bolsa_balance` = inicial + ingresados + devueltos − consumidos (D-30) · `planificacion_semanal.stock_desactualizado` (D-19). |
| 18 | Dominios con CHECK (D-20, D-07, D-37): los estados de §6.4; `usuario.estado`, `pedido.estado`, `proyeccion_tejeduria.estado`, `maquina_tintoreria.estado` e `incidencia.estado`; `bitacora_auditoria.tipo_operacion` (incluye PROPONER, CONFIRMAR y DESCARTAR); y `clasificacion_color` C/B. |

## §9.2 Entrada

| # | Cambio |
|---|---|
| 19 | La proyección usada es la **versión vigente** de la semana (D-06). |
| 20 | Una máquina sin barca impide generar (D-09). Una partida sin barca o sin componentes no se envía y se avisa (D-09b, **pendiente de confirmar**). |
| 21 | Partidas candidatas: quitar «falta confirmarla»; la regla queda confirmada por D-16. |
| 22 | En la re-planificación, el stock del día D es `bolsa_articulo.kg_saldo_inicial` de D en la vigente. |

## §9.3 Salida

| # | Cambio |
|---|---|
| 23 | `SIN_MAQUINA`: no se registra en la versión; se avisa al cargar la partida y al planificar (D-12). |

## §9.4 Cierre de semana y arrastradas

| # | Cambio |
|---|---|
| 24 | Agregar el cierre en orden (fila 9) y que cerrar no marca N+1 (fila 8). |
| 25 | **Arrastradas (D-27):** lista única de pendientes, independiente de la semana seleccionada, con la semana de cierre y las semanas que lleva arrastrada. Salen al marcarse completadas (HU-16 C4). No se agrega `partida.fecha_completada`: la fecha queda en la bitácora. |

## §9.5 Re-planificación

Reemplazar «definida en negocio, **no implementada en el código**» y el párrafo «Cómo hacerlo no está decidido» por el diseño implementado.

| # | Cambio |
|---|---|
| 26 | Se re-planifica la vigente ABIERTA de la semana en curso desde D, la fecha del servidor. Grupos: **A**, fijas (antes de D, más las del día D que el planificador marca como ya en máquina, D-31); **B**, conservadas (tela separada antes de D y ubicadas desde D: solo se redistribuyen); **C**, el resto, que se vuelve a decidir con la bolsa de D más la proyección desde D. |
| 27 | Incidencias: máquina fuera desde D hasta su fecha prevista (D-08), con su objetivo reducido en 2 por día · complemento: la partida no se ubica antes de la fecha · cambio de prioridad: usa la nueva fecha, y `pedido.fecha_entrega` se actualiza al confirmar (D-13b). |
| 28 | **D-33:** si la barca de una partida del grupo B tiene una sola máquina y esa máquina está caída, la partida se ubica desde el día en que vuelve. Si no vuelve en la semana, o su complemento llega después del domingo, queda BLOQUEADA y su tela vuelve a la bolsa el día D (`kg_devueltos`). |
| 29 | **D-34:** no se puede seleccionar un PROBLEMA_COMPLEMENTO sobre una partida del grupo A, y el sistema explica por qué. |
| 30 | **D-29:** un complemento con fecha prevista a media jornada cuenta desde el día siguiente. |
| 31 | **D-32:** una sola propuesta pendiente por semana. Mientras exista, no se puede re-planificar otra vez, cerrar, volver a generar la semana ni generar la siguiente. Si no se confirma el mismo día D, se descarta. Si cambia la proyección de la semana, la propuesta se elimina. |
| 32 | **D-36:** hay que seleccionar al menos una incidencia PENDIENTE. **D-37:** la bitácora registra PROPONER, CONFIRMAR y DESCARTAR. |
| 33 | Confirmar: la propuesta pasa a vigente, la anterior a REEMPLAZADA, las incidencias a CONSIDERADA, se actualiza `pedido.fecha_entrega` y se marca N+1 (D-19). Descartar: se elimina (D-26). |
| 34 | **Corregir la frase** «la diferencia entre las dos F es la de los días restantes». E_S es cuadrática sobre el total semanal, y una incidencia de máquina cambia el mínimo y el máximo de toda la semana. Lo correcto: las dos F se calculan sobre la semana completa y sobre la misma instancia, con las incidencias aplicadas (D-28, D-35). |

## §12 Historias de usuario

| # | Cambio |
|---|---|
| 35 | HU-18 C2: quitar «⚠ La forma de llevarlo al ACO está pendiente (sección 9.5)». |

## §13 Vacíos

| # | Cambio |
|---|---|
| 36 | **1** Re-planificación: resuelto (§9.5). |
| 37 | **2** RF30: regla aprobada. (a) Déficit por **barca**: Σ max(0, −`diferencia_objetivo`) de sus máquinas. (b) Candidatas: partidas PENDIENTE de la barca que no quedaron Asignadas ni Sobrantes, **incluidas las que no tuvieron ningún kilo**; sin arrastradas. (c) Orden: fecha de entrega más próxima; si empatan, **menos kg por tejer**. (d) `kg_a_tejer` por par pedido-artículo, descontando el saldo del domingo. (e) Sin candidatas, no se recomienda nada (HU-13 C5). (f) Se guarda en la tabla `recomendacion_tejido`. **Se implementa en un incremento posterior.** |
| 38 | **3** resuelto por D-12 · **5** por D-08 · **6** por D-20 · **7** por D-13 y D-13b · **8, 9, 10** por D-14, D-15 y D-16. |
| 39 | **4** (lista de tonalidades) y **11** (tolerancia de tiempo): siguen **abiertos**. |
