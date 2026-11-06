# Integración del algoritmo con la base de datos

El backend construye la entrada del módulo `spats-algoritmo` (R2.3) desde la base y guarda su salida (secciones 9.2 y 9.3 del contexto). El código está en `backend/spats-api/src/main/java/pe/edu/pucp/spats/planificacion/motor/`.

Para la generación de la semana completa, el módulo **funciona igual que el R2.3**. El 01/11/2026 se amplió su contrato de entrada para re-planificar desde el día D (ver el final de este documento). La búsqueda del ACO y la función objetivo no cambiaron, y con `replanificacion = null` cada clase sigue el camino original: las 12 instancias del R2.2 dan el mismo F.

## Entrada: de la base a `DatosSemana` (`LectorDatosPlanificacion`)

| Objeto del algoritmo | Origen en la base |
|---|---|
| `ParametrosPlanificacion(lunes)` | `semana.fecha_inicio`. Los pesos α, β, γ quedan con sus valores del código. |
| `Maquina` | Todas las de `maquina_tintoreria`: `codigo`, `capacidad`, `clasificacion_color`, `barca`, `objetivo_semanal`. Si alguna no tiene barca, no se genera (RN-23, D-09). |
| `Indisponibilidad` | Un registro por cada día de la semana que toca un intervalo de `indisponibilidad_maquina`. Un día tocado en parte cuenta entero (D-08). |
| `Pedido` | `numero_op`, `cliente.nombre`, `null`, `fecha_entrega` de los pedidos de las partidas candidatas. |
| `Partida` | Candidatas (ver abajo): `numero_partida`, `numero_op`, `color.codigo_color`, `color.nombre`, `peso_total`, `color.tonalidad`, `barca`, orden por (`fecha_registro`, `id_partida`), `PENDIENTE`. |
| `Componente` | `partida_componente` → `detalle_pedido` → `articulo`. `claveArticulo = articulo.codigo`; rol `Principal`/`Complemento`; `peso_requerido`. |
| `RegistroProyeccion` | Detalles de la **versión vigente** de la proyección de la semana (D-06) con `fecha_proyectada` del sábado anterior al viernes. `maquinaTejeduria` va vacía. |
| `StockArticulo` | `bolsa_articulo.kg_saldo_final` del domingo anterior en la versión vigente de la semana anterior, si es > 0. Sin versión vigente: 0 (D-15). |

**Partidas candidatas** (`CandidatasRepository`):

- `estado_partida = 'PENDIENTE'`;
- que no figuren como Asignada ni Sobrante en la versión vigente abierta de otra semana (D-16);
- con barca y con componentes. Si no los tienen, no se envían y se avisa (D-09).

## Ejecución (`EjecutorAlgoritmo`)

`new PlanificadorSemanal().planificar(datos, new ParametrosACO())`: 20 hormigas, ρ = 0.90, τ0 = 0.50, a = 1, b = 2, 6000 evaluaciones, 60 s, semilla 1.

- Un `IllegalArgumentException` del algoritmo (datos rechazados) se devuelve como 422.
- Cualquier otro error, como 500 `FALLO_PLANIFICACION`.

## Salida: de `ResultadoPlanificacion` a la base (`TraductorResultado` + `GuardadoPlanificacion`)

| Tabla | Cómo se llena |
|---|---|
| `planificacion_semanal` | Versión siguiente de la semana, `INICIAL`, proyección usada, F, E_S, E_D, lavados, evaluaciones y tiempo. Pasa a vigente; la anterior queda `REEMPLAZADA`. |
| `planificacion_partida` | Una fila por `AsignacionPartida`: máquina, `dia_abastecimiento = fecha`, `ASIGNADA`/`SOBRANTE`, `fecha_disponible`, `requiere_lavado_previo` y `orden_secuencia`. El orden es la posición en (máquina, día), primero Asignadas y luego Sobrantes, cada grupo por orden de conformación (RN-15). |
| `planificacion_partida` (BLOQUEADA) | Partidas `TELA_PRIORIZADA`, y `SIN_TELA` en las que algún artículo tuvo kilos en la semana (stock inicial o llegadas). Las `SIN_TELA` sin ningún kilo no se registran. Las `SIN_MAQUINA` no se registran y generan un aviso (D-12). |
| `planificacion_maquina` | Una fila por `ResumenMaquina`, con `numero_lavados` = partidas de la máquina que requieren lavado. |
| `bolsa_articulo` | Siete filas por artículo (ver abajo). |

### Reconstrucción de la bolsa (D-02)

Por cada artículo y día:

- `kg_saldo_inicial`: el stock inicial el lunes; después, el saldo final del día anterior.
- `kg_ingresados`: la proyección cuya `RegistroProyeccion.fechaDisponible(fecha_tejido)` es ese día. Es el **mismo método del algoritmo**, no una copia de la regla.
- `kg_consumidos`: los componentes de las partidas cuyo `AsignacionPartida.fechaDisponible` (día de conformación) es ese día.
- `kg_saldo_final = inicial + ingresados − consumidos`, con `BigDecimal` a 3 decimales, como exige `ck_bolsa_balance`.

**Control:** el saldo del domingo debe coincidir con `EstadoSiguiente.stockFinal` (tolerancia 0.001 kg). Si no coincide, o si algún saldo da negativo, la corrida es un fallo y no se guarda.

### Lo que no se toca al generar

`partida.estado_partida` solo cambia al cerrar la semana (§9.4).

## Re-planificación desde D (HU-18)

Propuesta aprobada: `CONTROL_DE_VERSIONES/revisiones/2026-10-25_propuesta-replanificacion.md` (versión 2). Decisiones: D-13b, D-26, D-28 a D-33.

### Cambios en `spats-algoritmo`

| Clase | Cambio |
|---|---|
| `entrada/Replanificacion`, `AsignacionFija`, `ConformacionConservada`, `DisponibilidadPartida` (nuevas) | Contrato explícito: día D, partidas fijas, conformaciones conservadas y disponibilidad de partidas. |
| `entrada/DatosSemana` | Componente opcional `replanificacion`. El constructor de 8 argumentos lo deja en `null`. |
| `nucleo/InstanciaSemana` | `diaInicio`, `fija*`, `conservada`, `diaConformacionPrevia`, `desde`, `consumoFijasInicio`. |
| `nucleo/ConstructorInstancia` | Reparte las fijas y las conservadas. El stock es el saldo al iniciar D; la tela de las fijas conformadas el día D sale ese día; la tela de las conservadas sin día hábil vuelve a la bolsa (`TELA_DEVUELTA`). Desde D, `compat` solo admite máquinas de la barca con algún día hábil. |
| `nucleo/SimuladorConformacion` | Empieza en D. La carga parte con las fijas, la estrategia elige la máquina de las conservadas y una partida no se conforma antes de su día `desde`. |
| `nucleo/ConstructorCalendario` | Las fijas van a su día y descuentan del objetivo restante. Desde D se aplica la misma regla de cuotas y nada se ubica antes de D ni de `desde`. |
| `nucleo/Validador` | Rehace el consumo desde D, comprueba que las fijas no cambien y revisa las reglas de distribución sobre lo decidido. |
| `nucleo/PartidaExcluida` | Motivos `SIN_DIA_DISPONIBLE` y `TELA_DEVUELTA`. |
| `PlanificadorSemanal` | Salida de la semana completa (fijas + nuevas) y `evaluar(datos, alternativa)` para RF55. |
| **Sin cambios** | `aco/*` (feromona, heurística, ruleta, actualización y parada), `FuncionObjetivo`, `Solucion`, `Evaluacion`, `Calendario`, `EstrategiaDecision`, `salida/*` y `excel/*`. |

### Entrada desde la base (`LectorDatosReplanificacion` + `ArmadoReplanificacion`)

| Grupo | Filas de la vigente | Al algoritmo |
|---|---|---|
| A — fijas | `dia_abastecimiento < D` y las de hoy marcadas como ya en máquina (D-31) | `AsignacionFija` |
| B — conservadas | resto con `fecha_disponible < D` | `ConformacionConservada` (ubicable desde máx(D, complemento)) |
| C — se vuelven a decidir | candidatas de §9.2 que no son A ni B | partidas normales; `DisponibilidadPartida` si tienen complemento |

- **Stock:** `kg_saldo_inicial` del día D en la vigente. La proyección disponible antes de D ya está dentro de ese saldo.
- **INCIDENCIA_MAQUINA:** días indisponibles desde D hasta la fecha prevista (D-08).
- **PROBLEMA_COMPLEMENTO:** «disponible desde» el primer día completo después de la fecha prevista (D-29). No se permite sobre una fija.
- **CAMBIO_PRIORIDAD:** el pedido va con `nueva_fecha_entrega`.

### Comparación (RF55) y guardado

- **RF55:** la vigente se evalúa sobre la misma instancia con `PlanificadorSemanal.evaluar`, usando sus decisiones (máquina, día de conformación y orden reconstruido). Se compara con la nueva (`DecisionReplanificacion`):
  - si la vigente no es factible, o si la F nueva es menor, se propone la nueva;
  - si no, se conserva la vigente y no se guarda nada.
- **Indicadores:** los de la semana completa (D-28).
- **Proponer** (`GuardadoReplanificacion`): `planificacion_semanal` REPLANIFICACION/PROPUESTA no vigente, con `planificacion_partida` y `planificacion_maquina` de la semana completa, y `bolsa_articulo` (`TraductorResultado.traducirReplanificacion`).
  - La bolsa se copia hasta D-1 y desde D se reconstruye: saldo de D + proyección + `kg_devueltos` (D-30) − consumo de lo conformado desde D.
  - El domingo debe coincidir con `stockFinal`.
- **Confirmar:** la propuesta pasa a vigente, la anterior a REEMPLAZADA, las incidencias a CONSIDERADA y se actualiza `pedido.fecha_entrega` (con la bitácora).
- **Descartar:** se elimina con todas sus filas.
- **Vencimiento:** una propuesta no confirmada el mismo día se elimina (D-32).

## Verificación

- `RegresionInstanciasR22Test`: con los 12 libros del R2.2, el motor da exactamente el F de `docs/resumen_ejecuciones.csv` del R2.3, sin incumplimientos. La traducción cumple todas las restricciones del script: balance de la bolsa, saldo del domingo igual a `stockFinal`, día ≥ disponible, secuencias 1..n, diferencia por máquina y unicidad por versión.
- `ReplanificacionTest` (módulo) y `ReplanificacionMotorTest` (backend): pruebas 1 a 5 de la propuesta de re-planificación.
- `FlujoPlanificacionIntegracionTest` y `ReplanificacionIntegracionTest` (con PostgreSQL):
  - cargan la semana 37 en la base, generan desde la base y obtienen el mismo F que el R2.3 (1279.75);
  - prueba 6: proponer, descartar y confirmar.
