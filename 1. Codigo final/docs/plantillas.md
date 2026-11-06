# Plantillas de carga (.xlsx)

Se descargan desde **Carga de datos → Descargar plantilla** (`GET /api/plantillas/{tipo}`); cada una trae filas de ejemplo. Los nombres de hoja y de columna son obligatorios (no importan las mayúsculas). Las columnas salen del script `BD/BD.sql` y de lo que el algoritmo necesita (sección 9.2 del contexto).

**Orden de carga:** 1. Pedidos → 2. Partidas → 3. Proyección de tejeduría.

## Reglas comunes (RF09, RF10, RF11)

- El archivo se valida **completo**. Si hay un error bloqueante, no se importa ningún registro y se informa la hoja, la fila, el campo y el motivo.
- La carga es **acumulable**: lo que ya existe (mismo código) se conserva sin reemplazarlo y se avisa. Para corregir un dato se usa **Datos registrados** (RF13).
- Fechas en `aaaa-mm-dd` (también se acepta `d/m/aaaa`). Números con punto o coma decimal, siempre mayores que cero.
- Cada carga exitosa queda en `carga_datos` con su fecha y hora (RF14) y en la trazabilidad.
- **Eliminar una carga** (Carga de datos → Historial de cargas) quita solo lo que agregó ese archivo. Se eliminan en orden inverso: 3. Proyección → 2. Partidas → 1. Pedidos. Si otros datos usan esos registros, o ya hay una planificación generada con ellos, no se elimina nada y se informa el motivo (D-40).
- **La proyección no desaparece:** su versión queda como `Eliminada` en el historial, no se usa al planificar y se puede volver a ella con «Volver a esta versión», que crea una versión nueva (D-46).

## 1. Pedidos (`PEDIDOS`) — hoja `Pedidos`

Una fila por artículo de cada pedido (`detalle_pedido`).

| Columna | Obligatoria | Formato | Tabla |
|---|---|---|---|
| `numero_op` | sí | texto ≤ 50 | `pedido.numero_op` |
| `cliente` | sí | texto ≤ 150 | `cliente.nombre` |
| `fecha_entrega` | sí | fecha | `pedido.fecha_entrega` |
| `codigo_articulo` | sí | texto ≤ 80 | `articulo.codigo` (clave de la bolsa del artículo). En las tres plantillas, un prefijo numérico entre paréntesis al inicio se quita, con un aviso que muestra el código original y el limpio: `(313811)TW2021AA00P03` → `TW2021AA00P03` (D-25). |
| `nombre_articulo` | sí | texto ≤ 150 | `articulo.nombre` |
| `peso_total_requerido` | sí | > 0, 2 decimales | `detalle_pedido.peso_total_requerido` |

Errores: un mismo pedido con otro cliente u otra fecha en el archivo, o un artículo repetido en el mismo pedido.

## 2. Partidas (`PARTIDAS`) — hojas `Partidas` y `Componentes`

**Hoja `Partidas`** (una fila por partida; toda partida nueva entra como PENDIENTE):

| Columna | Obligatoria | Formato | Tabla |
|---|---|---|---|
| `numero_partida` | sí | texto ≤ 50 | `partida.numero_partida` |
| `numero_op` | sí | pedido ya registrado | `partida.id_pedido` |
| `codigo_color` | sí | texto ≤ 50 | `color.codigo_color` |
| `nombre_color` | sí | texto ≤ 100 | `color.nombre` |
| `clasificacion_color` | sí | `C` o `B` (también `Color` o `Blanco`) | `color.clasificacion_color` |
| `tonalidad` | sí | `BLANCO`, `CLARO`, `MEDIO` u `OSCURO` | `color.tonalidad` |
| `barca` | no* | `capacidad letra`, p. ej. `400 C` | `partida.barca` |
| `peso_total` | sí | > 0, 3 decimales | `partida.peso_total` |

\* Una partida sin barca se registra con un aviso, pero no se planifica hasta que se le registre una.

**Hoja `Componentes`** (una fila por tela de la partida):

| Columna | Obligatoria | Formato | Tabla |
|---|---|---|---|
| `numero_partida` | sí | partida de la hoja Partidas | `partida_componente.id_partida` |
| `codigo_articulo` | sí | artículo del pedido de la partida | `partida_componente.id_detalle_pedido` |
| `tipo_componente` | sí | `PRINCIPAL` o `COMPLEMENTO` | `tipo_componente` |
| `tubular_abierto` | sí | `SI` o `NO` | `tubular_abierto` |
| `peso_requerido` | sí | > 0, 3 decimales | `peso_requerido` |

Validaciones:

- La letra de la barca coincide con la clasificación del color (D-07).
- Cada partida tiene al menos un componente PRINCIPAL.
- Un color ya registrado no puede cambiar de clasificación. Si cambian su nombre o tonalidad, se conserva lo registrado y se avisa.
- **Aviso** si la barca no tiene ninguna máquina registrada: «la partida X tiene una barca sin máquina registrada» (D-12).

## 3. Proyección de tejeduría (`PROYECCION_TEJEDURIA`) — hoja `Proyeccion`

Se carga para la semana elegida en la pantalla. Cada carga crea una **versión nueva** que pasa a ser la vigente; las anteriores quedan en el historial y se pueden restaurar (D-06).

| Columna | Obligatoria | Formato | Tabla |
|---|---|---|---|
| `numero_op` | sí | pedido registrado | `detalle_pedido` |
| `codigo_articulo` | sí | artículo de ese pedido | `detalle_pedido` |
| `fecha_proyectada` | sí | fecha de **tejido** | `detalle_proyeccion_tejeduria.fecha_proyectada` |
| `kg_proyectados` | sí | > 0, 3 decimales | `kg_proyectados` |

- La ventana de la semana va del **sábado anterior al viernes**. Lo que cae fuera se guarda, pero no se considera al planificar, y se avisa.
- La tela tejida de lunes a viernes queda disponible en tintorería al día siguiente; la del sábado, el lunes.
- Si la semana tiene planificación vigente abierta, la carga pide confirmación: esa planificación deja de ser vigente y hay que volver a generarla.
