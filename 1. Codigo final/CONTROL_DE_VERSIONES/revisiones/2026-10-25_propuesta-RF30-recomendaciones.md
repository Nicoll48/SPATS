# Propuesta: recomendaciones de tejeduría (RF30, HU-13 C2 y C5)

Fecha: 25 de octubre de 2026 · Estado: **propuesta, pendiente de aprobación** (no implementada)

## Qué pide el requisito

> RF30: cuando una máquina no alcance su objetivo semanal, recomendar artículos y pedidos pendientes cuya producción permita completar la carga semanal.
> HU-13 C5: si no hay pedidos pendientes que ayuden, no se recomienda nada para esa máquina.

El algoritmo no calcula recomendaciones (y no debe hacerlo: RN-17 y la regla de no tocar el ACO). Se proponen como **post-proceso** de una versión ya guardada, con datos que ya están en la base.

## Regla de cálculo propuesta

Al guardar una versión, en la misma transacción:

1. **Déficit por barca.** Se suman las diferencias negativas de `planificacion_maquina.diferencia_objetivo` de las máquinas de cada barca: `faltan(barca) = Σ max(0, −diferencia)`. Se agrupa por barca porque una partida puede ir a cualquier máquina de su barca (RN-06). Si `faltan = 0`, esa barca no recibe recomendaciones.
2. **Partidas que podrían cubrirlo.** Partidas `PENDIENTE` de esa barca que no quedaron como Asignadas ni Sobrantes en la versión (son las Bloqueadas y las que no tuvieron ningún kilo). Las arrastradas no entran (RF48).
3. **Orden de prioridad.** Fecha de entrega más próxima y, después, orden de registro (RN-04). Se toman las primeras `faltan(barca)` partidas.
4. **Qué tejer.** Por cada componente de esas partidas: `kg_a_tejer = max(0, kg_requeridos − saldo final del domingo de la bolsa del artículo)`. Como varias partidas pueden pedir el mismo artículo, el saldo se va descontando en ese orden. Se recomienda el par pedido-artículo (`detalle_pedido`) con sus kilos.
5. **Sin candidatas** (HU-13 C5): no se registra nada para esa barca.

Es una regla determinista y explicable. No reproduce la conformación día a día: la tela que se teja la semana siguiente tiene su propia fecha de disponibilidad.

## Dónde se guarda (regla 4 del contexto: todo resultado va a la base)

Tabla nueva (requiere tu aprobación porque cambia `BD/BD.sql`):

```sql
CREATE TABLE recomendacion_tejido (
    id_recomendacion   SERIAL PRIMARY KEY,
    id_planificacion   INT NOT NULL REFERENCES planificacion_semanal (id_planificacion),
    barca              VARCHAR(80) NOT NULL,
    partidas_faltantes INT NOT NULL CHECK (partidas_faltantes > 0),
    id_partida         INT NOT NULL REFERENCES partida (id_partida),
    id_detalle_pedido  INT NOT NULL REFERENCES detalle_pedido (id_detalle_pedido),
    kg_a_tejer         NUMERIC(12,3) NOT NULL CHECK (kg_a_tejer > 0),
    prioridad          INT NOT NULL CHECK (prioridad > 0),
    CONSTRAINT uq_recomendacion UNIQUE (id_planificacion, id_partida, id_detalle_pedido)
);
```

En la interfaz se mostraría dentro de la pestaña **Alertas**, junto a cada barca con faltantes. Columnas: pedido, artículo, kg a tejer, partida que se completaría y fecha de entrega.

## Preguntas para la autora

1. ¿El déficit se agrupa por **barca** (propuesto) o por **máquina**?
2. ¿Se recomiendan también las partidas sin ningún kilo en la semana, o solo las Bloqueadas?
3. ¿Hace falta una fecha límite de tejido? Para abastecer el lunes, lo tejido tiene que estar listo el sábado anterior.
