# Propuesta: re-planificación desde D (HU-18, RF53–RF55, RN-25, §9.5)

Versión 2 · 1 de noviembre de 2026 · Estado: **aprobada e implementada el 01/11/2026 (versión 0.2.0)**. Las respuestas de la autora a la §5 están en `DECISIONES.md` (D-28 a D-35).
Reemplaza la versión del 25/10. Parte de la regla de negocio que la autora confirmó el 01/11 y del código real de `spats-algoritmo` (R2.3) y de `backend/spats-api`.

---

## 0. Respuesta corta: sí hay que modificar `spats-algoritmo`

**No se puede resolver solo armando la entrada desde el backend.** El módulo tiene tres supuestos que la entrada actual no puede cambiar sin trucos:

| Supuesto del código actual | Dónde | Por qué impide la re-planificación |
|---|---|---|
| La simulación empieza el lunes: `for (int d = 1; d <= I.ND; d++)` | `SimuladorConformacion.simular` | Conformaría partidas en días anteriores a D. |
| El calendario reparte el objetivo completo desde el primer día hábil, sin carga previa | `ConstructorCalendario.construir` | No sabe que antes de D ya hay partidas ubicadas, así que calcula mal la cuota diaria y el objetivo restante. |
| El validador repite el consumo de tela desde el lunes con `stockInicial` | `Validador.validar` | Con el saldo del día D como stock inicial, contaría la tela dos veces o marcaría errores que no lo son. |

Resolverlo desde el backend exigiría lo que pediste evitar: marcar como indisponibles los días ya pasados, inflar objetivos o crear bolsas ficticias. Tu sección «Implementación» ya pide ampliar el contrato de entrada. Eso es modificar el módulo, y abajo detallo exactamente qué cambia.

**No cambian** la búsqueda del ACO (`aco/ColoniaHormigas`, `ParametrosACO`: feromona, heurística, ruleta, actualización y parada), `FuncionObjetivo`, `Solucion`, `Evaluacion`, `Calendario`, `EstrategiaDecision`, `salida/*` ni `excel/*`. Además, con D = lunes y sin datos fijos, cada clase modificada sigue exactamente el camino actual.

---

## 1. Grupos de partidas (cómo arma la entrada el backend)

D es la fecha del servidor (`Clock`, America/Lima). Solo se re-planifica la versión vigente ABIERTA de la semana que contiene D; si no, se rechaza.

| Grupo | Qué partidas son (en la vigente) | Qué hace el algoritmo con ellas |
|---|---|---|
| **A — Fijas** | `dia_abastecimiento < D` | Nada: se copian con la misma máquina, día, clasificación y orden. Cuentan en la carga, el calendario y la F. |
| **B — Conformación conservada** | `fecha_disponible < D` y `dia_abastecimiento ≥ D` | No consumen tela, porque ya se descontó antes de D. El ACO solo elige la máquina dentro de su barca; el calendario les asigna un día ≥ máx(D, disponibilidad). |
| **C — Se vuelven a decidir** | Las candidatas de la §9.2 (PENDIENTE, sin arrastradas ni Asignadas o Sobrantes de la vigente de otra semana, D-16) que no están en A ni en B: las conformadas desde D en la vigente, las Bloqueadas, las que no tuvieron kilos y las partidas nuevas | Simulación normal desde D: nunca a medias, prioridad por fecha de entrega y desempate por el ACO. |

**Tela desde D:**
- `kg_saldo_inicial` del día D en la vigente (ya descuenta lo que A y B consumieron antes de D);
- más la proyección con `fechaDisponible ≥ D`;
- más la tela devuelta (PROBLEMA_COMPLEMENTO, ver abajo).

La proyección disponible antes de D ya está dentro de ese saldo. El módulo la ignora y lo advierte, igual que hoy hace con la proyección anterior al lunes.

**Por tipo de incidencia** (todo dato real, ningún truco):

| Incidencia | Qué envía el backend |
|---|---|
| INCIDENCIA_MAQUINA | Días indisponibles desde D hasta la fecha prevista de disponibilidad (D-08, un día parcial cuenta entero), como `Indisponibilidad`. El módulo ya baja el objetivo en 2 por día perdido y recalcula el mínimo y máximo diario. Desde D, una partida solo puede ir a máquinas de su barca con algún día hábil desde que puede ubicarse (filtro nuevo, solo al re-planificar). |
| PROBLEMA_COMPLEMENTO, fecha ≤ domingo | Partida del grupo B: «ubicable desde» esa fecha. Partida del grupo C: «disponible desde» esa fecha (no se conforma antes, así su tela queda libre para otras). |
| PROBLEMA_COMPLEMENTO, fecha > domingo | La partida no se envía al algoritmo y se guarda **BLOQUEADA**. Si era del grupo B, sus kg vuelven a la bolsa de cada artículo el día D. |
| CAMBIO_PRIORIDAD | El `Pedido` se envía con `nueva_fecha_entrega`. Cambia la prioridad de C y el orden del calendario de B y C. `pedido.fecha_entrega` solo cambia al confirmar (D-13b). |

---

## 2. Cambios en `spats-algoritmo` (clase por clase)

| Clase | Cambio | Por qué |
|---|---|---|
| `entrada/Replanificacion` (**nueva**) | `record Replanificacion(LocalDate diaInicio, List<AsignacionFija> fijas, List<ConformacionConservada> conservadas, List<DisponibilidadPartida> disponibleDesde)` | Contrato explícito pedido. |
| `entrada/AsignacionFija` (**nueva**) | `(partida, maquina, fecha, EstadoAsignacion estado, fechaDisponible, int ordenSecuencia, boolean requiereLavado)` | Grupo A, tal como está en la vigente. |
| `entrada/ConformacionConservada` (**nueva**) | `(partida, LocalDate fechaConformacion, LocalDate ubicableDesde)` | Grupo B. |
| `entrada/DisponibilidadPartida` (**nueva**) | `(partida, LocalDate disponibleDesde)` | Grupo C con PROBLEMA_COMPLEMENTO. **No estaba en tu lista**, pero sin ella no se cumple HU-18 C8 para una partida que todavía no se conformó. |
| `entrada/DatosSemana` | Nuevo componente `Replanificacion replanificacion` (puede ser `null`). Se conserva el constructor de 8 argumentos, que lo deja en `null`, y `conMemoria` lo copia. | El código y las pruebas actuales no cambian. |
| `nucleo/InstanciaSemana` | Campos nuevos: `diaInicio` (por defecto 1); `fija[]`, `fijaDia[]`, `fijaSobrante[]`; `conservada[]`; `desde[]` (día mínimo de cada partida, por defecto 1); `diaConformacionPrevia[]`. | Para que la simulación, el calendario y el validador conozcan lo fijo. |
| `nucleo/ConstructorInstancia` | Si hay `replanificacion`: valida los datos; agrega las fijas y las conservadas a `I.partidas`, sin requerimientos de tela; filtra `compat` a las máquinas de la barca con algún día hábil desde máx(D, desde); ignora la proyección disponible antes de D (con advertencia); las demás partidas pasan por el filtro actual (SIN_MAQUINA, SIN_TELA). | Construcción de la instancia. |
| `nucleo/SimuladorConformacion` | Empieza en `diaInicio`; `carga[m]` parte con las fijas de cada máquina; antes de la tela del día D, elige con `estrategia.elegirMaquina` la máquina de cada conservada (en su orden); `completables` recibe el día y excluye las partidas con `desde > d`. Las fijas y las conservadas nunca están pendientes. | Simulación. |
| `nucleo/ConstructorCalendario` | Las fijas van a su día y con su clasificación, y cuentan en `partidasPorDia`. En los días hábiles anteriores a D, `restante` baja por las fijas asignadas; desde D se aplica la misma regla de cuotas. Para B y C, el primer día disponible es `sigHabil[m][máx(diaConformación, D, desde)]`, y una Sobrante sin día hábil nunca queda antes de D. | Calendario. |
| `nucleo/Validador` | Repite el consumo de tela desde D sin las fijas ni las conservadas. Comprueba que las fijas no cambiaron, que ninguna partida de B o C quedó antes de máx(D, desde) y que las reglas de distribución (máximo diario, objetivo, Sobrantes) se cumplen en lo decidido desde D. Lo fijo se toma como dado (ver §5, punto 4). | Validador. |
| `PlanificadorSemanal` | `construirResultado` incluye las fijas en `asignaciones` y en el resumen de máquinas (semana completa). Nuevo método `evaluar(DatosSemana, List<AsignacionPartida> alternativa)`, que devuelve los `Indicadores` (F y lista de incumplimientos) de una solución dada, construida con `ConstructorInstancia`, `FuncionObjetivo` y `Validador`. | Comparación de RF55. |

**`FuncionObjetivo` no cambia ni una línea.** Recorre todas las partidas conformadas de la instancia: como las fijas están dentro (con máquina, día y orden), E_S, E_D y Λ quedan calculados sobre la semana completa con la misma fórmula. El lavado de la primera partida desde D depende de la última fija de la máquina, como corresponde.

**Regresión:** con `replanificacion == null`, se cumple `diaInicio = 1`, `desde[] = 1`, no hay fijas ni conservadas y `compat` no se filtra. Cada condición nueva da el mismo valor que hoy. Deben seguir pasando las 34 pruebas y dar el mismo F en las 12 instancias.

---

## 3. Backend (`spats-api`)

| Clase | Cambio |
|---|---|
| `Enumeraciones.EstadoPlanificacion` | Agrega `PROPUESTA`. `planificacion_semanal.estado` es VARCHAR sin CHECK: **no cambia la base**. |
| `motor/LectorDatosReplanificacion` (**nueva**) | Lee la vigente, D y las incidencias seleccionadas, arma los grupos A, B y C y la tela desde D, y construye `DatosSemana` con `Replanificacion`. Solo lectura. |
| `motor/TraductorResultado` | Variante para re-planificar: copia las filas de `bolsa_articulo` anteriores a D y reconstruye desde D (saldo de D, proyección, devoluciones y consumo de C). El domingo debe cuadrar con `stockFinal`. Las filas de A se copian; las de B y C salen del resultado. |
| `motor/GuardadoReplanificacion` (**nueva**) | Tres transacciones separadas: proponer, confirmar y descartar. |
| `PlanificacionIncidencia` (**nueva**) | Entidad y repositorio de la tabla que ya existe. |
| `service/ReplanificacionService` (**nueva**) | Orquesta: leer → algoritmo → evaluar la vigente (RF55) → guardar la propuesta o informar que se conserva la vigente. |
| `PlanificacionController` | `POST /semanas/{lunes}/replanificacion {incidencias: [ids]}` · `POST /planificaciones/{id}/confirmacion` · `DELETE /planificaciones/{id}` (solo una PROPUESTA). |
| Consulta, cierre y generación | El historial y la comparación no muestran la PROPUESTA; el detalle sí la muestra para revisarla. Mientras haya una PROPUESTA pendiente, no se puede cerrar la semana, volver a generarla ni generar la semana N+1 (ver §5, punto 12). |
| Frontend: Incidencias | Se seleccionan incidencias PENDIENTE y se elige «Re-planificar desde hoy». Si hay propuesta, se muestra su comparación con la vigente (F de cada una, cambios por partida y calendario) con los botones **Confirmar** y **Descartar**. Si no, aparece la alerta de RF55. |

### Guardado

- **Proponer** (una transacción):
  - `planificacion_semanal`: REPLANIFICACION, PROPUESTA, `es_vigente = false`, versión = última + 1;
  - `planificacion_partida` de la semana completa (A copiadas; B, C y Bloqueadas del resultado);
  - `planificacion_maquina` de la semana completa;
  - `bolsa_articulo` (copiada hasta D-1 y resultante desde D);
  - `planificacion_incidencia`.
- **Confirmar** (una transacción): se verifica que la vigente siga siendo la misma, que siga ABIERTA y que la fecha siga siendo D. Luego:
  - la vigente pasa a REEMPLAZADA (con `flush`, por `uq_vigente_por_semana`) y la propuesta a ABIERTA y vigente;
  - las incidencias pasan a CONSIDERADA;
  - `pedido.fecha_entrega` se actualiza con `cambiarFechaEntregaPorPrioridad`, que ya existe y registra el valor anterior en la bitácora;
  - si N+1 está planificada, queda desactualizada (D-19).
- **Descartar** (una transacción): se borran la propuesta y todas sus filas (D-26). Las incidencias siguen PENDIENTE y en la bitácora queda solo el registro de la operación.
- **Fallo** del algoritmo o del traductor: no se guarda nada (RN-19).

**Sobre D-19 al confirmar:** *(actualizado el 02/11)* la marca se guarda en `planificacion_semanal.stock_desactualizado`. Al confirmar, se activa en la vigente abierta de N+1. Si la vigente reemplazada estaba marcada, la nueva hereda la marca. Mientras haya una propuesta pendiente sigue bloqueado generar la N+1 (punto 12).

---

## 4. Comparación (RF55) e indicadores

1. Se construye **una sola instancia** con las incidencias aplicadas y desde D.
2. **Nueva:** resultado del ACO sobre esa instancia, con F_nueva e incumplimientos (si los hay, es un fallo).
3. **Vigente:** sus decisiones se trasladan a la misma instancia con `PlanificadorSemanal.evaluar`:
   - las fijas, igual;
   - B, con la máquina que tenían en la vigente;
   - C, con la máquina, el día de conformación y el orden de la vigente.

   Se obtienen F_vigente y su factibilidad.
4. Decisión:
   - **la vigente no es factible** → se propone la nueva;
   - **es factible y F_nueva < F_vigente** → se propone la nueva;
   - **es factible y F_nueva ≥ F_vigente** → se conserva la vigente, se muestra la alerta y no se guarda nada.

**Indicadores de la versión nueva (propuesta D-28): semana completa.** `valor_funcion_objetivo`, `desbalance_semanal`, `desbalance_diario` y `numero_lavados` son los de la F de la instancia, que incluye las fijas. Motivos:
- E_S eleva al cuadrado el total semanal de cada máquina, así que no se puede separar «desde D»;
- así se comparan con la vigente sobre la misma base;
- y siguen siendo comparables con las versiones INICIAL en el historial.

`evaluaciones` y `tiempo_ejecucion_ms` son los de la corrida. `planificacion_maquina` refleja la semana completa (lo fijo más lo nuevo), con el objetivo ya reducido por la incidencia.

---

## 5. Lo que se pierde o no se puede hacer exactamente (decisiones abiertas)

1. **El orden global de conformación de la vigente no está guardado.** `orden_secuencia` es la posición dentro de la máquina y el día. Para evaluar la vigente se reconstruye ordenando por día de conformación y luego por fecha de entrega original. Es un orden válido: dentro de un día, la tela solo baja y el algoritmo nunca adelanta a una partida con fecha posterior. Así que se repite el mismo consumo sin falsos incumplimientos. Hacerlo exacto exigiría una columna nueva; no la propongo.
2. **La vigente se evalúa por sus decisiones, no por sus días guardados.** En el módulo, el día de cada partida sale del calendario, que se recalcula con el nuevo objetivo y los nuevos días hábiles. Así funciona la F del artículo; no hay otra forma sin cambiar `FuncionObjetivo`.
3. **En la práctica, casi toda incidencia que toque la vigente desde D la deja no factible.** Una máquina caída con partidas desde D, un complemento con partidas conformadas antes de la fecha o un cambio de prioridad que invierte el orden: en esos casos se propone la nueva aunque su F sea mayor. «Se conserva la vigente» ocurre cuando las incidencias no afectan lo decidido desde D.
4. **Lo fijo puede quedar por encima del objetivo reducido.** Con un objetivo semanal bajo, el que baja 2 por día puede quedar por debajo de las partidas ya asignadas antes de D, o el nuevo máximo diario por debajo de lo ubicado en un día pasado. El validador no lo marca como error, porque no es una decisión. E_S y E_D sí lo penalizan, y por igual en las dos versiones.
5. **§9.5 del `.md` no es exacta.** Dice que «la diferencia entre las dos F es la de los días restantes». No es así: E_S es cuadrática sobre el total semanal, y con una incidencia de máquina cambian el mínimo y el máximo de toda la semana. La comparación sigue siendo correcta porque las dos F se calculan sobre la misma instancia. Propongo corregir esa frase (después de consultarte).
6. **El día D se vuelve a decidir completo.** Si una partida de hoy ya entró a la máquina en la mañana, la re-planificación podría moverla. Lo dejo como lo definiste; solo te lo advierto.
7. **❓ Barca con una sola máquina, caída desde D.** Una partida del grupo B (con su tela ya separada) no tiene máquina disponible. Propuesta: tratarla igual que el complemento fuera de la semana, es decir, BLOQUEADA y con su tela devuelta a la bolsa el día D.
8. **❓ PROBLEMA_COMPLEMENTO sobre una partida del grupo A** (ubicada antes de D). Como lo anterior a D no cambia, la incidencia no tendría efecto. Propuesta: no permitir seleccionarla para la re-planificación y explicar por qué.
9. **La indisponibilidad de una incidencia de máquina no se guarda en `indisponibilidad_maquina`.** El calendario de la versión nueva marca esos días leyendo `planificacion_incidencia`, sin crear filas nuevas.
10. **Cómo se registra la tela devuelta (resuelto en D-30: columna `kg_devueltos`).** Como las filas anteriores a D se copian, la devolución se registra en `kg_ingresados` del día D (el balance de `ck_bolsa_balance` se cumple). Entonces `kg_ingresados` deja de ser solo proyección ese día. ¿Te parece bien así?
11. **Bloqueadas.** Se mantiene la §6.2. Para decidir si un artículo «tuvo kilos» se considera la semana completa: lo copiado antes de D más lo que llega desde D.
12. **❓ Una sola PROPUESTA por semana mientras está pendiente.** Mientras exista, no se puede cerrar la semana, volver a generarla ni generar la N+1. La confirmación solo vale el mismo día D y con la misma vigente; si cambió algo, hay que descartarla y re-planificar.

---

## 6. Pruebas

| # | Prueba | Dónde |
|---|---|---|
| 1 | Re-planificar con D = lunes y sin incidencias da las mismas filas y la misma F que la generación normal con la misma semilla. | `spats-algoritmo` y backend |
| 1b | Las 12 instancias del R2.2 dan el mismo F (con `replanificacion = null`) y las 34 pruebas actuales siguen pasando. | `spats-algoritmo` |
| 2 | Las filas anteriores a D son idénticas a las de la vigente: partida, máquina, día, clasificación, orden y lavado. | backend |
| 3 | Las bolsas cuadran día por día (`ck_bolsa_balance`), el domingo coincide con `stockFinal` y las conservadas no consumen tela nueva. | backend |
| 4 | Un caso de cada tipo de incidencia y uno con las tres juntas. | `spats-algoritmo` (instancia chica) y backend |
| 5 | RF55: vigente no factible → propuesta; vigente factible sin mejora → se conserva y no se guarda nada. | backend |
| 6 | Confirmar (vigente, REEMPLAZADA, CONSIDERADA, `fecha_entrega` y bitácora) y descartar (sin filas, incidencias PENDIENTE). | backend, integración con PostgreSQL |

---

## 7. Para aprobar

- **Modificar `spats-algoritmo`** en las clases de la §2. La búsqueda del ACO y la función objetivo no cambian.
- **El dato extra `DisponibilidadPartida`** (§2), que no estaba en tu lista.
- **D-28:** indicadores de la semana completa (§4).
- **Puntos abiertos:** respuestas a 7, 8, 10 y 12 de la §5.
- **El `.md`:** después de implementar, propondré actualizar la §9.5, la HU-18 C2 y el vacío 1 de la §13, incluida la frase del punto 5.
