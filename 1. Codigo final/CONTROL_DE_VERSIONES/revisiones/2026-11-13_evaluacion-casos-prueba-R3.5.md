# Evaluación del sistema frente a los casos de prueba del R3.5

**Fecha:** 13/11/2026 · **Documento evaluado:** `R3.5 Casos de prueba (3).docx` (104 casos, CP001–CP104, HU-01 a HU-21) · **Sistema:** rama `CAMBIO_FRONT`, base `tintoreria` local.

## Cómo se evaluó

Cada caso se contrastó con el sistema por uno o más de estos medios:

- **[V] Verificado en vivo** el 13/11 contra la API en ejecución y la base `tintoreria` (login, permisos, cargas inválidas, búsqueda de máquinas, generación sin proyección, alertas, bitácora). Las pruebas con archivos inválidos no registraron nada (537 partidas y 24 cargas antes y después).
- **[A] Prueba automática** del código (JUnit en `spats-algoritmo` y `spats-api`). Se cita su identificador (PU-xx, PI-xx) o su nombre.
- **[R] Revisión del código** o de la estructura de la base, cuando no hay prueba específica.

Estado de las pruebas automáticas al 13/11: algoritmo 63/63; API 78 pruebas, 77 pasan. Falla `FlujoPlanificacionIntegracionTest` (F = 1274.75 frente a 1279.75 esperado): es la regresión pendiente de la D-48 (el reparto diario cambió y las cifras del R2.2 se deben recalcular), no un error de funcionamiento.

**Escala:** ✅ Cumple · 🟡 Cumple con observación (el comportamiento es correcto, pero el dato o la redacción del caso no coincide exactamente con el sistema) · 🟠 Cumple en parte · ❌ No cumple.

## Resumen

| Resultado | Casos | Cantidad |
|---|---|---|
| ✅ Cumple | Todos los demás | **88** |
| 🟡 Cumple con observación | CP023, CP027, CP028, CP039, CP046, CP047, CP048, CP052, CP053, CP056, CP092, CP095 | **12** |
| 🟠 Cumple en parte | CP019, CP080, CP081 | **3** |
| ❌ No cumple | CP079 | **1** |
| **Total** | | **104** |

Lo que no cumple o cumple en parte se concentra en dos temas:

1. **Tipos de incidencia** (CP079, CP080, CP081): el sistema tiene 3 tipos (Incidencia de máquina, Problema de complemento, Cambio de prioridad). Los casos usan «Falta o demora de receta» y «Retraso de tejeduría», que quedaron fuera del alcance (nota ⚠ de RF50 y HU-17 C1 en el contexto). **El sistema está bien según los requisitos corregidos; los casos de prueba son los que no se actualizaron.**
2. **Registro de máquina** (CP019): el sistema no guarda el **tiempo de procesamiento por partida** (RF15 y HU-07 C1 lo piden) y la «clasificación de color» de la máquina es C/B, no «Oscuros»; la tonalidad que acepta se registra aparte (D-51).

## Detalle por caso

### HU-01 — Iniciar sesión

| ID | Caso | Resultado | Evidencia |
|---|---|---|---|
| CP001 | Inicio de sesión exitoso | ✅ | [V] `POST /api/auth/login` con la cuenta de prueba → 200 con token. |
| CP002 | Contraseña incorrecta | ✅ | [V] 401 «Usuario o contraseña incorrectos. Verifica los datos e inténtalo de nuevo.» |
| CP003 | Usuario inexistente | ✅ | [V] 401 con el mismo mensaje (no revela si el usuario existe). |
| CP004 | Cierre de sesión | ✅ | [R] «Cerrar sesión» en la barra lateral descarta el token y vuelve al inicio de sesión (RF03). |
| CP005 | Cierre por inactividad | ✅ | [R][V] `duracion-inactividad: 30m`; sin actividad el token vence y la pantalla vuelve al inicio de sesión (se observó dos veces el 13/11). |

### HU-02 — Acceso según el perfil

| ID | Caso | Resultado | Evidencia |
|---|---|---|---|
| CP006 | Acceso del Planificador | ✅ | [A] `PermisosPorPerfilTest` «Planificador puede modificar»; [V] genera, elimina y carga. |
| CP007 | Acceso del perfil Consulta | ✅ | [A] «Consulta puede leer», «Consulta no puede modificar: 403»; [V] Consulta lee la planificación (200); generar y eliminar → 403 «Tu perfil es Consulta: puedes ver la información, pero no ejecutar operaciones que modifiquen datos ni la planificación.» |

### HU-03 — Modo de interacción

| ID | Caso | Resultado | Evidencia |
|---|---|---|---|
| CP008 | Cambio a Modo Guiado | ✅ | [R] `PUT /auth/yo/modo`; `GuidedWorkflow` muestra los pasos en secuencia; los permisos no cambian. [V] bitácora registra EXPERTO → GUIADO. |
| CP009 | Cambio a Modo Experto | ✅ | [V] bitácora registra GUIADO → EXPERTO. Consulta no puede cambiar de modo (422 «Solo el Planificador alterna…»). |

### HU-04 — Importar mediante plantillas

| ID | Caso | Resultado | Evidencia |
|---|---|---|---|
| CP010 | Importación exitosa | ✅ | [A] «Flujo semanal completo sobre la base real» importa pedidos, partidas y proyección; historial de cargas con 24 cargas. |
| CP011 | Conservación de registros existentes | ✅ | [R] Los registros ya existentes se conservan sin cambios y se avisa («ya estaba registrada; se conservó sin cambios»); los nuevos se agregan. |
| CP012 | Registro de la carga | ✅ | [R][V] `carga_datos.fecha_hora` y usuario; «Historial de cargas» (RF14). |
| CP013 | Cabecera obligatoria faltante | ✅ | [V] 422 «El archivo presenta errores y no se importó ningún registro.» · «Falta la cabecera obligatoria «numero_partida».» |
| CP014 | Plantilla no reconocida | ✅ | [V] Plantilla de pedidos subida como partidas → 422 «Falta la hoja «Partidas» de la plantilla.» |
| CP015 | Campo obligatorio vacío | ✅ | [V] 422 · fila 2, `numero_op`: «El campo es obligatorio.» |
| CP016 | Formato de dato incorrecto | ✅ | [V] 422 · fila 2, `peso_total`: «ABC» no es un número válido. |

### HU-05 / HU-06 — Descargar y modificar datos

| ID | Caso | Resultado | Evidencia |
|---|---|---|---|
| CP017 | Descarga de información | ✅ | [V] `GET /api/exportaciones/PARTIDAS` → 200, Excel de 49 KB en el formato de la plantilla; botón «Descargar en Excel» en cada sección. |
| CP018 | Modificación manual | ✅ | [R] Editar partida (peso total, color, barca, componentes); si está en la vigente pide confirmación e invalida la vigente (D-24), así la siguiente generación usa el dato nuevo. |

### HU-07 — Configurar las máquinas

| ID | Caso | Resultado | Evidencia |
|---|---|---|---|
| CP019 | Registro de máquina | 🟠 | [R] Se guardan código, capacidad, clasificación de color (C/B), objetivo semanal, estado y tonalidades aceptadas. **No existe el campo «tiempo de procesamiento por partida»** (ni en `maquina_tintoreria` ni en el formulario); D-25 lo fija en 8 h como valor de demostración y el sistema no trabaja con horas (§2.2). Además, el dato del caso «Clasificación: Oscuros» no es un valor válido: la clasificación es C/B y «Oscuro» es una tonalidad aceptada. |
| CP020 | Búsqueda por código | ✅ | [V] Búsqueda «M03» → M03 con su estado actual (Operativa). |
| CP021 | Búsqueda por clasificación de color | ✅ | **Corregido el 13/11.** [V] «Oscuros» → las 9 máquinas que aceptan Oscuro, con su estado (queda fuera la M05, que solo acepta Blanco); «C» y «B» siguen buscando por clasificación. [A] `TonalidadesAceptadasMaquinaTest` «CP021: la búsqueda encuentra por tonalidad aceptada…». |
| CP022 | Edición de máquina | ✅ | [R] «Modificar máquina» (objetivo, capacidad, estado, tonalidades); [A] pruebas de D-51. |

### HU-08 — Indisponibilidad de máquinas

| ID | Caso | Resultado | Evidencia |
|---|---|---|---|
| CP023 | Registro de indisponibilidad | 🟡 | [A] «Un día tocado en parte por una indisponibilidad cuenta como indisponible (D-08)», PU-13. **Observación:** con 08:00–16:00 el sistema excluye **todo el día**, no solo el intervalo, porque no planifica por horas. |
| CP024 | Estado «En mantenimiento» | ✅ | [R] Indisponibilidad de tipo MANTENIMIENTO vigente → `estadoActual = EN_MANTENIMIENTO` («En mantenimiento»). |
| CP025 | Estado «Indisponible» | ✅ | [R] Tipo INDISPONIBLE vigente → «Indisponible». |

### HU-09 — Partidas candidatas

| ID | Caso | Resultado | Evidencia |
|---|---|---|---|
| CP026 | Composición completada | ✅ | [A] PU-04, PU-06. |
| CP027 | Partida bloqueada | 🟡 | [A] PU-04, prueba «Bloqueada: la tela priorizada…». **Observación:** según §6.2, es Bloqueada si al menos uno de sus artículos tuvo kilos pero no alcanzaron; si ningún artículo tuvo kilos sigue Pendiente y no se lista. El dato del caso (250 de 300 kg) sí da Bloqueada. |
| CP028 | Disponible desde el día operativo siguiente | 🟡 | [A] PU-01, «Ingresos: lo tejido el sábado anterior llega el lunes y lo del viernes, el sábado». **Observación:** «día operativo siguiente» = día siguiente al de tejido (nota ⚠ de HU-09 C3); el domingo sí es día de abastecimiento. |
| CP029 | Exclusión de arrastradas | ✅ | [A] PU-04; RecomendacionesIntegracionTest. |
| CP030 | Complemento incompleto | ✅ | [A] PU-07 «Una partida con complemento espera a que llegue la tela de todos sus artículos». |
| CP031 | Partida sin complemento | ✅ | [A] PU-06. |
| CP032 | Sábado → lunes siguiente | ✅ | [A] PU-01 y prueba de ingresos (sábado → lunes). |

### HU-10 — Seleccionar las partidas a conformar

| ID | Caso | Resultado | Evidencia |
|---|---|---|---|
| CP033 | Según cantidades disponibles | ✅ | [A] PU-02, PU-06; «Bolsa reconstruida: el saldo cumple el balance día a día». |
| CP034 | Prioridad por fecha de entrega | ✅ | [A] PU-08; PU-19 detecta adelantar una partida de entrega posterior. |
| CP035 | Misma fecha: menor desviación | ✅ | [A] PU-09 «Con la misma fecha de entrega, la elección la toma el algoritmo»; PI-05. |
| CP036 | No exceder la disponibilidad | ✅ | [A] PU-20 «Detecta una partida conformada sin tela suficiente»; «Ninguna tela se consume dos veces». |
| CP037 | Disponibilidad insuficiente | ✅ | [A] PU-08, PU-21. |
| CP038 | Descarte de mayor desviación | ✅ | [A] PU-09, PI-05 (función objetivo minimizada por la colonia). |

### HU-11 — Generar la planificación semanal

| ID | Caso | Resultado | Evidencia |
|---|---|---|---|
| CP039 | Solicitud de planificación | 🟡 | [A] PU-28, «Flujo semanal completo»; [V] planificaciones 31 y 32 generadas. **Observación:** el dato del caso usa la semana 14/09–19/09 (lunes a sábado); el sistema trabaja de **lunes a domingo** (prueba «La semana va de lunes a domingo»). |
| CP040 | Objetivo semanal | ✅ | [A] PU-03, PU-11. |
| CP041 | No se alcanza el objetivo | ✅ | [A] PU-17 (solución factible); [V] alertas «faltan N partidas». |
| CP042 | Selección entre máquinas compatibles | ✅ | [A] PU-27 «La colonia reparte la carga entre las máquinas de una misma barca». |
| CP043 | Solo la asignación final | ✅ | [R] Se guarda y se muestra una sola solución por generación. |
| CP044 | Respeto de la fecha de disponibilidad | ✅ | [A] PU-01, PU-06; restricción `ck_planpart_dia_desde_disponible` en la base. |
| CP045 | Exclusión de arrastradas | ✅ | [A] PU-04; RN-18. |
| CP046 | Restricción de capacidad | 🟡 | [A] PU-10, PU-40–45. **Observación:** la compatibilidad es por capacidad igual a la barca de la partida (400 → máquinas de 400), no «capacidad mayor o igual». La partida de 400 kg tampoco iría a la de 600 salvo que su barca sea 600. |
| CP047 | Restricción de clasificación de color | 🟡 | [A] PU-41, PU-42. **Observación:** desde D-51 la restricción es por **tonalidades aceptadas** de la máquina; la letra C/B es informativa. El comportamiento pedido (no asignar) se cumple. |
| CP048 | Máquina indisponible | 🟡 | [A] PU-13. Igual que CP023: el miércoles completo queda sin partidas. |
| CP049 | Inclusión de Sobrante | ✅ | [A] PU-12. |
| CP050 | Restricciones de la Sobrante | ✅ | [A] PU-12, PU-14 (primer día disponible y hábil de una máquina compatible). |
| CP051 | Datos insuficientes | ✅ | [V] Semana 23/11 sin proyección → 422 «La semana no tiene proyección de tejeduría registrada. Cárgala en Carga de datos.»; no se creó planificación. |
| CP052 | Tiempo máximo 60 s | 🟡 | [A] PI-05, PU-26; [V] tiempos reales 1,7 s a 3,5 s (planificaciones 24–32). **Observación:** la tolerancia real del jefe de planeamiento sigue pendiente (§13, punto 11). |
| CP053 | Fallo durante la generación | 🟡 | [A] «Una solución con incumplimientos del validador es un fallo y no se traduce»; [R] la generación es una sola transacción. **Observación:** no hay una prueba que simule el fallo con una vigente existente y compruebe que queda igual. |
| CP054 | Todas las candidatas factibles | ✅ | [A] PU-12, PU-21. |
| CP055 | Menor desviación | ✅ | [A] PU-15, PI-03, PI-05. |

### HU-12 — Visualizar la planificación

| ID | Caso | Resultado | Evidencia |
|---|---|---|---|
| CP056 | Calendario semanal | 🟡 | [R] Una pantalla con máquinas por día. Observación: 7 días (lunes a domingo), ver CP039. |
| CP057 | Clasificación de las partidas | ✅ | [R] Pestañas Partidas asignadas, sobrantes y bloqueadas; leyenda del calendario. |
| CP058 | Consulta por clasificación | ✅ | [R] Una pestaña por clasificación. |
| CP059 | Sobrante diferenciada | ✅ | [R] Tarjeta roja con «X» de Sobrante. |
| CP060 | Indicador de lavado | ✅ | [A] PU-16; [R] ícono de lavado en la transición (paso a una tonalidad más clara). |
| CP061 | Distribución por tonalidad | ✅ | [R] «Distribución por tonalidad» con Asignadas y Sobrantes por separado. Los valores del caso (Claro, Medio, Oscuro) son tonalidades, así que coincide. |

### HU-13 — Desviaciones y recomendaciones

| ID | Caso | Resultado | Evidencia |
|---|---|---|---|
| CP062 | Carga faltante | ✅ | [V] «M03: faltan 4 partidas para su objetivo semanal (12 de 16).» |
| CP063 | Carga excedente | ✅ | [R] «<máquina>: excede en N partidas su objetivo semanal (… de …).» |
| CP064 | Recomendación de producción | ✅ | [A] `CalculoRecomendacionesTest` (14), `RecomendacionesIntegracionTest`; [V] pestaña Recomendaciones por artículo (21 artículos, 22 513 kg, 83 partidas en la semana 9–15). |
| CP065 | Arrastradas fuera de los indicadores | ✅ | [A] RecomendacionesIntegracionTest «arrastradas fuera»; RF48. |
| CP066 | Objetivo alcanzado | ✅ | [R] «Desviación 0» en Partidas asignadas; [A] «HU-13 C4: la máquina que llegó a su objetivo no recibe nada». |
| CP067 | Sin pedidos para recomendar | ✅ | [A] «HU-13 C5: sin candidatas no se recomienda nada». |

### HU-14 — Exportar

| ID | Caso | Resultado | Evidencia |
|---|---|---|---|
| CP068 | Exportación | ✅ | [V] `GET /api/planificaciones/32/exportacion` (vigente) → 200, Excel de 36 KB; con una versión no vigente → 422 «Solo se exporta la planificación vigente de la semana.» |

### HU-15 — Cerrar la semana

| ID | Caso | Resultado | Evidencia |
|---|---|---|---|
| CP069 | Confirmar completadas | ✅ | [A] «Flujo semanal completo», D-52 «…y cerrar»; [R] diálogo de cierre con Asignadas y Sobrantes. |
| CP070 | Confirmadas → Completada | ✅ | [A] mismas pruebas; bitácora COMPLETAR. |
| CP071 | No confirmadas → Arrastrada | ✅ | [A] mismas pruebas. |
| CP072 | Bloqueadas al cierre | ✅ | [R] §9.4: las Bloqueadas siguen Pendientes. |
| CP073 | Ninguna confirmada | ✅ | [R] Todas las Asignadas y Sobrantes pasan a Arrastrada. |

### HU-16 — Partidas arrastradas

| ID | Caso | Resultado | Evidencia |
|---|---|---|---|
| CP074 | Visualización | ✅ | [R] Pestaña «Partidas arrastradas». |
| CP075 | Permanencia entre semanas | ✅ | [R] Se listan mientras sigan Arrastradas, sin importar la semana. |
| CP076 | Sin participación | ✅ | [A] PU-04, RecomendacionesIntegracionTest; RN-18. |
| CP077 | Confirmación posterior | ✅ | [R] `POST /partidas/arrastradas/{numero}/completado`; deja de listarse. |

### HU-17 — Registrar incidencias

| ID | Caso | Resultado | Evidencia |
|---|---|---|---|
| CP078 | Registro por categoría | ✅ | [R] 3 categorías: Incidencia de máquina, Problema de complemento, Cambio de prioridad. |
| CP079 | Detalle de la incidencia | ❌ | [R] **El tipo «Falta o demora de receta» no existe** (quedó fuera del alcance: nota ⚠ de RF50 y HU-17 C1; la receta no bloquea la planificación, §2.2). Con un tipo válido sí se guardan tipo, entidad, ocurrencia y disponibilidad prevista (tabla `incidencia`). **Hay que cambiar el dato del caso.** |
| CP080 | Varias incidencias | 🟠 | [A] «4f. Varias incidencias juntas», PU-37. INC-002 (máquina) e INC-003 (cambio de prioridad) se registran; **INC-001 «Retraso de tejeduría» no existe como tipo**. |
| CP081 | Entidad según el tipo: Partida | 🟠 | [R] La regla «tipo → una sola entidad» se cumple (restricción `ck_incidencia_tipo_entidad`), pero con **Problema de complemento**; el tipo del caso, «Retraso de tejeduría», no existe. |
| CP082 | Entidad: Máquina | ✅ | [R] Incidencia de máquina solo con máquina (`ck_incidencia_entidad_exclusiva`). |
| CP083 | Entidad: Pedido | ✅ | [R] Cambio de prioridad solo con pedido. |
| CP084 | Entidad no registrada | ✅ | [R] `FALTA_ENTIDAD_AFECTADA` «Indica la partida afectada.» / «Indica la máquina afectada.» |
| CP085 | Cambio de prioridad | ✅ | [R] Pide la nueva fecha (`FALTA_NUEVA_FECHA`) y no la disponibilidad; se guarda en `incidencia.nueva_fecha_entrega`. |

### HU-18 — Re-planificar

| ID | Caso | Resultado | Evidencia |
|---|---|---|---|
| CP086 | Conserva los días transcurridos | ✅ | [A] «2. Las filas anteriores a D quedan idénticas a las de la vigente», PU-30. |
| CP087 | Redistribuye desde hoy | ✅ | [A] PU-29–PU-32, «6. Proponer, descartar y confirmar una re-planificación». |
| CP088 | Nueva versión vinculada | ✅ | [R] `tipo_generacion = REPLANIFICACION` y tabla `planificacion_incidencia`. |
| CP089 | Sin alternativa de mejora | ✅ | [A] «5b. RF55: si la vigente es factible y la nueva no la mejora, se conserva la vigente». |
| CP090 | Exclusión de arrastradas | ✅ | [A] RN-18; PU-04. |
| CP091 | Múltiples incidencias | ✅ | [A] «4f. Varias incidencias juntas», PU-37. |
| CP092 | Incidencia de máquina | 🟡 | [A] «4a. Incidencia de máquina: sin partidas en el periodo afectado», PU-32. Observación: jueves 10:00 a viernes 18:00 bloquea jueves y viernes completos (D-08). |
| CP093 | Incidencia sobre una partida | ✅ | [A] «4b. Problema de complemento: no se ubica… antes de la fecha», PU-33. |
| CP094 | Cambio de prioridad | ✅ | [A] «4e. Cambio de prioridad: la nueva fecha de entrega se usa desde D», PU-36. |
| CP095 | Fallo durante la re-planificación | 🟡 | [R] La re-planificación queda como propuesta y se guarda en una transacción; un fallo no deja versión. Igual que CP053: falta una prueba con fallo simulado. |

### HU-19 — Historial y versiones

| ID | Caso | Resultado | Evidencia |
|---|---|---|---|
| CP096 | Conservación acumulativa | ✅ | [R][V] Cargas, versiones (REEMPLAZADA/CANDIDATA) e incidencias se conservan; bitácora con 493 registros. |
| CP097 | Consulta y comparación | ✅ | [R] Historial de versiones; comparación de dos versiones en el calendario (D-52). |
| CP098 | Un solo plan vigente | ✅ | [R] Índice único `uq_vigente_por_semana` en la base. |

### HU-20 — Confirmación de acciones críticas

| ID | Caso | Resultado | Evidencia |
|---|---|---|---|
| CP099 | Solicitud de confirmación | ✅ | [R] «Cerrar semana» abre `DialogoCierre`; también hay confirmación al generar, eliminar y re-planificar. |
| CP100 | Confirmar | ✅ | [R] Al confirmar se ejecuta el cierre (`POST /planificaciones/{id}/cierre`). |
| CP101 | Cancelar | ✅ | [R] «Cancelar» cierra el diálogo sin llamar a la API. |

### HU-21 — Trazabilidad

| ID | Caso | Resultado | Evidencia |
|---|---|---|---|
| CP102 | Creación o modificación | ✅ | [V] `bitacora_auditoria`: usuario, fecha y hora, campo, valor anterior y nuevo (279 de 280 MODIFICAR con valor anterior). |
| CP103 | Correcciones manuales | ✅ | [R] Las correcciones de partidas y de la proyección se registran como MODIFICAR. |
| CP104 | Consulta del historial | ✅ | [V] `GET /api/bitacora` paginado; pantalla Historial con Cuándo, Quién, Operación, Entidad, Campo, Valor anterior y Valor nuevo. |

## Qué corregir

### En los casos de prueba (el sistema ya está bien según los requisitos corregidos)

| Caso | Cambio sugerido |
|---|---|
| CP079 | Cambiar «Falta o demora de receta» por **Problema de complemento** (partida, con disponibilidad prevista). |
| CP080 | Cambiar INC-001 «Retraso tejeduría / PAR-702» por **Problema de complemento / PAR-702**. |
| CP081 | Cambiar «Retraso de tejeduría» por **Problema de complemento**. |
| CP039, CP056 y otros con 14/09–19/09 | Usar la semana de **lunes a domingo** (14/09–20/09). |
| CP019 (y la redacción de CP021) | «Clasificación de color: Oscuros» no es un valor: la clasificación es **Color (C) o Blanco (B)**; «Oscuro» es una **tonalidad aceptada**. |
| CP023, CP048, CP092 | Aclarar que el día tocado en parte se excluye completo (D-08). |
| CP046, CP047 | Aclarar que la compatibilidad es por **capacidad de la barca** y **tonalidades aceptadas** (D-51). |

### En el sistema (decisión de la autora)

| Tema | Opciones |
|---|---|
| **Tiempo de procesamiento por partida** (CP019, RF15, HU-07 C1) | (a) agregar el campo a la máquina, solo informativo, porque el sistema no planifica por horas; o (b) quitarlo de RF15, HU-07 C1 y CP019. |
| **Fallo simulado** (CP053, CP095) | Agregar una prueba de integración que fuerce un fallo del algoritmo con una vigente existente y compruebe que queda igual y sin versión nueva. |
| **Regresión de la D-48** | Repetir la experimentación para actualizar las cifras de `FlujoPlanificacionIntegracionTest`, PI-03 y PI-04. |
