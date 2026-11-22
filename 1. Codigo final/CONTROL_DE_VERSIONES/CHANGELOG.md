# CHANGELOG — SPATS

Formato: una entrada por entrega, de la más reciente a la más antigua.

## [0.11.0] — 2026-11-13 — D-57 anular una incidencia

Pedido de la autora. Respaldos: `local/respaldo_tintoreria_2026-11-13_antes_D57.sql` y `respaldos/SPATS_contexto_sistema_2026-11-13_antes_D57.md`.

- Base: `incidencia.estado` admite `ANULADA` (`BD.sql` y `BD/migracion_2026-11-13_incidencia_anulada.sql`, aplicada en `tintoreria`).
- Backend: `IncidenciaService.anular` (`POST /api/semanas/{lunes}/incidencias/{id}/anulacion`), devuelve la fecha de entrega anterior de un cambio de prioridad considerado; re-planificación `porAnulacion` (`LectorDatosReplanificacion.leerPorAnulacion`); al volver a generar una semana sin empezar se aplican también las incidencias consideradas.
- Frontend: botón «Anular» en cada incidencia (no anulada) con motivo; si está considerada, «Anular y volver a generar la semana» o «Anular y re-planificar desde hoy» (abre el panel de re-planificación).
- Pruebas: `IncidenciasSemanaFuturaIntegracionTest` (anular y volver a generar; anular un cambio de prioridad) y `ReplanificacionIntegracionTest` (anular y re-planificar desde hoy). Algoritmo: 63 pruebas sin cambios. API: 76; solo falla `FlujoPlanificacionIntegracionTest` (D-48).

## [0.10.0] — 2026-11-13 — D-56 RF30: tejido adicional para la misma semana

Pedido de la autora. Respaldos: `local/respaldo_tintoreria_2026-11-13_antes_D56.sql` y `respaldos/SPATS_contexto_sistema_2026-11-13_antes_D56.md`.

- Reversión completa del primer RF30 (D-55): código, pantalla, pruebas, documentos y la tabla (`BD/migracion_2026-11-13_revertir_recomendaciones_d55.sql`, aplicada en `tintoreria`).
- Base: tabla `recomendacion_tejido` nueva (una fila por componente a tejer) en `BD.sql` y `BD/migracion_2026-11-13_recomendaciones_tejido_adicional.sql` (aplicada en `tintoreria`).
- Backend: `planificacion/recomendacion/CalculoRecomendaciones` (cálculo puro: espacio libre, déficit, candidatas compatibles, reparto por día, tela sin usar, día de tejido) y `RecomendacionesTejidoService` (calcula al generar y al proponer o confirmar una re-planificación; al cerrar deja las de la vigente; consulta; «Crear versión con las recomendaciones»; Excel para tejeduría). Endpoints `GET /api/planificaciones/{id}/recomendaciones`, `POST /api/planificaciones/{id}/recomendaciones/version` y `GET /api/semanas/{lunes}/recomendaciones/exportacion`.
- Frontend: pestaña «Recomendaciones» (`components/planning/RecomendacionesTejido.tsx`): total para llegar al objetivo, resumen por máquina desplegable, filas con casilla (principal y complemento juntos), «Crear versión con las recomendaciones» y «Exportar para tejeduría»; con dos versiones, un bloque por versión con su color.
- Vista previa en el calendario (pedido de la autora): «Ver en el calendario» muestra el calendario de la versión con las partidas recomendadas (o solo las marcadas) en el día que llenan, con borde punteado y el contador actualizado; es una estimación (el resultado real sale al generar la versión creada). Cada recomendación trae el color, la tonalidad y los kg de su partida.
- Día de las recomendaciones con la regla del calendario (D-48, corrección de la autora): antes se elegía el día con más espacio libre y, al empatar, el más temprano, lo que dejaba 3, 3, 2, 2, 2, 2, 2 en la vista previa y adelantaba el tejido. Ahora `CalculoRecomendaciones.cuotaPorDia` usa la misma cuota y el mismo desfase que `ConstructorCalendario` (no se tocó `spats-algoritmo`). Se recalcularon las recomendaciones de las planificaciones 31 y 32 (respaldo `local/respaldo_tintoreria_2026-11-13_recomendaciones_antes_D48.sql`). Pruebas nuevas: `diaComoElCalendario`, `desfaseComoElAlgoritmo`, `repartoFinalParejo`.
- Detalle de la partida en cualquier calendario (pedido de la autora): «Disponible desde <día>» (`planificacion_partida.fecha_disponible`, el día en que su tela ya está en tintorería) y, si se ubicó después, «ubicada N días después». En la vista previa de recomendaciones, el día disponible es el siguiente al de tejido (sábado → lunes).
- Máquinas (CP021 del R3.5, pedido de la autora): la búsqueda también encuentra por tonalidad aceptada desde 3 letras («Oscuros», «oscuro», «cla»); «C» y «B» siguen siendo la clasificación. `MaquinaService.buscar`, prueba en `TonalidadesAceptadasMaquinaTest`.
- Datos registrados → Pedidos (pedido de la autora): la columna «Relacionados» ya no lleva a la proyección; solo a Artículos y Partidas.
- Recomendaciones por artículo (pedido de la autora): `components/planning/RecomendacionesPorArticulo.tsx`. Tres números arriba (kg, artículos, partidas); tabla de artículos (tipo, kg, partidas que completa, «se necesita desde») ordenada por partidas; al desplegar, kg por día de tejido y las partidas con máquina, día que llena, «también necesita» y entrega. Selector «Por artículo / Por máquina». Selección por artículo o por partida; la vista previa solo muestra partidas con todas sus telas seleccionadas y la barra avisa las incompletas con «Agregar lo que falta». Solo frontend; el Excel no cambia.
- Las recomendaciones solo se muestran (corrección de la autora): se quitó «Crear versión con las recomendaciones» (botón, `POST /planificaciones/{id}/recomendaciones/version`, `RecomendacionesTejidoService.crearVersion`, `RecomendacionTejidoRepository.conIds`). La selección sirve para la vista previa en el calendario; la barra dice «N partidas seleccionadas · Seleccionar las N · Ver en el calendario · Cancelar». Las proyecciones V3 y V4 de la semana del 09/11, creadas con ese botón, se marcaron ELIMINADA con `DELETE /proyecciones/{id}` (sin planificaciones). `RecomendacionesIntegracionTest` ahora comprueba que no se crean versiones.
- Selección como en una tabla web: casilla en el encabezado de cada tabla de máquina (selecciona o quita todas sus recomendaciones; queda a medias si solo hay algunas). Al seleccionar aparece una barra fija con «N partidas seleccionadas», «Seleccionar las N» (todas las de la versión), «Crear versión con las recomendaciones» y «Cancelar».
- Pruebas: `CalculoRecomendacionesTest` (14 casos) y `RecomendacionesIntegracionTest`. Algoritmo: 63 pruebas con las mismas F (no se tocó `spats-algoritmo`). API: 75 pruebas; solo falla `FlujoPlanificacionIntegracionTest` (D-48, ya pendiente).
- Prueba con `Especificaciones/Carga_semana_2026-11-09` (partidas + proyección V1) en una copia de la base, ya borrada: V1 con 87 recomendaciones (51 partidas, 13 691 kg); la versión creada con todas (V2, +48 filas) baja el déficit total de 51 a 5 partidas.

## [0.9.0] — 2026-11-12 — D-53 incidencias y re-planificación reorganizadas

Pedido de la autora. Respaldo: `respaldos/SPATS_contexto_sistema_2026-11-12_antes_D53.md`.

- Frontend: Incidencias solo lista las incidencias; nuevo panel `components/incidencias/DialogoIncidencia.tsx` en 3 pasos (qué pasó; partidas de hoy por máquina; solo registrar / registrar y re-planificar desde hoy / registrar y volver a generar), con modo guiado paso a paso. La re-planificación toma todas las incidencias pendientes. La propuesta se revisa en Planificación semanal (`PropuestaReplanificacion.tsx`); se elimina `PanelReplanificacion.tsx`.
- Backend: al generar una semana que aún no empieza se aplican sus incidencias pendientes (`LectorDatosPlanificacion`); si la versión queda vigente pasan a `CONSIDERADA`, se vinculan y actualizan la fecha de entrega (`GuardadoPlanificacion`). `EntradaPlanificacion.idsIncidencias`. Con la semana empezada se avisa que no se aplican.
- Tonalidades: Máquinas ofrece «Guardar y volver a generar la semana» (semana sin empezar) en lugar de «re-planificar desde hoy»; Planificación semanal avisa si cambiaron tonalidades después de generar (`EstadoSemanaResponse.maquinasCambiadas`, consulta en la bitácora).
- Pruebas: nueva `IncidenciasSemanaFuturaIntegracionTest`. Backend: 61 pruebas del API y 63 del algoritmo; todas pasan salvo `FlujoPlanificacionIntegracionTest` (F 1274,75 vs 1279,75, ya pendiente por D-48). Frontend: 9 pruebas, tsc, lint y build.
- Propuesta de re-planificación: la tabla «Vigente → Propuesta» se reemplaza por un resumen (`ResumenCambios.tsx`): pestañas como las de Planificación semanal: «Afectadas por la incidencia», «Ahora sí entran», «Ahora quedan bloqueadas», «Reubicadas por equilibrio», «Carga por máquina» y «Calendario de la propuesta»; cada partida con su tonalidad, color, pedido y «en la vigente → con la propuesta» (o Bloqueada con su motivo al pasar el puntero).
- Limpieza: se quitó la re-planificación por cambio de máquinas del backend (`ReplanificacionRequest.cambioMaquinas`, `LectorDatosReplanificacion.leer(..., cambioMaquinas)`, `semanaReplanificable` del aviso de Máquinas) y del frontend.
- D-54: «Cerrar sin re-planificar» en cada incidencia pendiente (estado nuevo `ATENDIDA`, con motivo opcional en la bitácora). Migración `BD/migracion_2026-11-12_incidencia_atendida.sql` (respaldo previo `local/respaldo_tintoreria_2026-11-12_antes_D54.sql`). Prueba en `IncidenciasSemanaFuturaIntegracionTest`.
- No cambia `spats-algoritmo` ni la base de datos.

## [0.8.0] — 2026-11-11 — D-52 versiones de la proyección de una semana; se quitan los restos de Escenarios

Pedido de la autora (lo pidió su asesor). Respaldos:
- `respaldos/SPATS_contexto_sistema_2026-11-11_antes_D52.md`
- `local/respaldo_tintoreria_2026-11-11_antes_D52.sql`

**Limpieza (fuera del alcance)**
- Eliminados `FRONT/frontend/_fuera_de_alcance/pages/EscenariosPage.tsx` y `.../components/planning/ScenarioBanner.tsx`, la sección de escenarios de `_fuera_de_alcance/data/mockData.ts`, el token de color `scenario` de `index.css` y el tono `scenario` de `ConfirmDialog`.
- Textos: `README.md` del frontend, `docs/arquitectura.md`, `SPATS_contexto_sistema.md` (sección 2.2), `Tabs.tsx`, y «escenario» → «instancia» en comentarios de la re-planificación (backend, `tipos.ts`, `docs/api.md`) y en `FRONT/generar_proyecciones.py`.

**Base de datos**
- `proyeccion_tejeduria.estado`: `VIGENTE`, `CARGADA`, `ELIMINADA` (`CARGADA` reemplaza a `REEMPLAZADA`).
- `planificacion_semanal.estado` suma `CANDIDATA`; `bitacora_auditoria.tipo_operacion` suma `CAMBIAR_VIGENTE`.
- `BD.sql` y nueva `BD/migracion_2026-11-11_versiones_proyeccion.sql` (aplicada en `tintoreria`). En la semana del 16/11 se quitaron las tres versiones que ya estaban `ELIMINADA`, para que la prueba empiece en V1.

**Backend** (no cambia `spats-algoritmo`)
- Cargar o restaurar una proyección crea una versión sin tocar la vigente ni pedir confirmación (`ProyeccionService.registrarVersion`, puerto `PlanificacionesDeLaSemana`). Sale `confirmado` de `POST /cargas/{tipo}` y de la restauración.
- `POST /semanas/{lunes}/planificaciones?proyeccion=` genera la planificación de una versión; con otra vigente queda `CANDIDATA`. La respuesta suma `vigente` y `versionProyeccion`.
- `GET /semanas/{lunes}/versiones-proyeccion` (panel), `GET` y `POST /planificaciones/{id}/cambio-vigente` («Usar como vigente»: directo o re-planificación propuesta desde hoy). Nuevos `CambioVigenteService` y `GuardadoCambioVigente`.
- Re-planificación con otra versión: `LectorDatosReplanificacion.leerCambioVersion` (incidencias heredadas), `ReplanificacionService.replanificarConVersion` (siempre propone) y, al confirmar, cambio de la versión vigente de la proyección.
- D-06 acotada a correcciones (invalida la vigente y su candidata); D-24 invalida también candidatas; D-19 marca también las candidatas de N+1.
- Exportar solo acepta la vigente.

**Frontend**
- Panel de versiones en la fila de «Visualización», banner de proyección nueva, generar y «Usar como vigente» con sus avisos, y resultado de la re-planificación (confirmar, descartar o decidir después).
- Avisos flotantes (`src/avisos/`): el resultado de las acciones sale abajo a la derecha, con su X, y se cierra solo (6 s el éxito, 10 s las advertencias; la cuenta se detiene con el puntero encima). Aplica a: carga importada, carga y versión de proyección eliminadas (Datos semanales); planificación generada, sus avisos y los demás mensajes de Planificación semanal; mensajes de Incidencias y de la re-planificación. Los errores y las advertencias por fila de una carga siguen en la pantalla porque hay que revisarlos.
- Indicadores de la planificación (asignadas, sobrantes, bloqueadas, máquinas con carga completa, lavados): todas las tarjetas con el color de «Partidas asignadas», en un tono más claro (`--color-indicador` #EBF1FA, borde del acento al 20 %); etiquetas y cifras casi negras (`--color-indicador-texto` #111827), también en las tarjetas por versión; en las tarjetas por versión, las cifras en el color de texto normal.
- Máximo 5 versiones no eliminadas de la proyección por semana (una por color): `ProyeccionService.registrarVersion` rechaza la sexta (`MAXIMO_VERSIONES_PROYECCION`) y Datos semanales avisa antes de subir o restaurar que hay que eliminar alguna. El color de cada versión va por su lugar entre las no eliminadas (`asignarColoresVersiones`), no por su número. V4: oliva claro → ciruela #6B3A5E.
- Carga de datos: los íconos de Pedidos, Partidas y Proyección van en el azul de los íconos de los indicadores (`text-accent`); su fondo de color no cambia.
- Datos registrados: navegación entre datos relacionados. Clic en un pedido → sus artículos (con «Kg en este pedido»); cada código de artículo, pedido o color en las tablas es un enlace; columna «Relacionados» (Pedido → Artículos, Partidas, Proyección; Artículo → Pedidos, Partidas; Color → Partidas). El filtro viaja en la URL (`?pedido=`, `?articulo=`, `?color=`) y se muestra en una franja con «Volver», «Ver todos» y una etiqueta por filtro para quitarlo.
- Indicadores: el nombre de la tarjeta puede ir en dos líneas para que «Máquinas con carga completa» se lea completo.
- Panel de versiones: en una fila debajo de «Visualización» (con «Solo diferencias»), y cada casilla muestra solo «V1», «V2»…; la fecha de carga pasa a la ayuda del puntero.
- Partidas asignadas: la tabla «Distribución por clasificación de color» pasa a ser «Distribución por tonalidad», un gráfico de barras horizontales (Blanco, Claro, Medio, Oscuro, con el color de la muestra de tonalidad), con cantidad y porcentaje; se cuenta en el frontend desde el calendario guardado. Debajo, «Asignadas por día»: columnas de lunes a domingo con las partidas asignadas de cada día sumando todas las máquinas y el promedio diario. Pendiente: RF40 dice «según su clasificación de color».
- Contraste: en el tema claro, `--color-success` #12925a → #0e7a4b y `--color-warning` #b25e09 → #a35407 (4.5:1 sobre sus fondos suaves, WCAG/Carbon).
- Calendario: celdas en gris y contador con color de carga. Con dos versiones marcadas, las partidas de cada celda van en cajas con el color de su versión (diseño de imagenes/colores.png: cuadrito «V1» #234F79 / «V2» #5B663A con su contador «2/3» al lado, borde claro y fondo suave; primero V1, luego V2 y al final «V1·V2» con las iguales en ambas); con una sola, sin colores. Comparación de dos versiones (casos a–d), «Solo diferencias», una tarjeta de indicadores por versión (sin la función objetivo) y leyenda nueva. Sobrantes y Bloqueadas con columna Versión al comparar. Lógica en `src/lib/versiones.ts` (`compararVersiones`, `agruparPorVersion`).
- Carga de datos: la proyección se sube sin confirmación; «Volver a esta versión» solo para eliminadas; estados V1/V2.

**Pruebas**
- Backend: `VersionesProyeccionIntegracionTest` (subir sin tocar la vigente, generar sin volverla vigente, usar como vigente antes de empezar la semana, volver y bloquear todo al cerrar) y `VersionesProyeccionSemanaIniciadaIntegracionTest` (usar como vigente con la semana empezada: propuesta, confirmar, lo anterior a hoy igual). Pasan contra PostgreSQL (`tintoreria_test`). `FlujoPlanificacionIntegracionTest` actualizado (paso 4); sigue fallando solo en la F de la semana 37 (1274,75 contra 1279,75), pendiente por D-48 como antes.
- Frontend: `npm test` (`node --test`, sin dependencias nuevas), 9 pruebas de `tests/versiones.test.ts`: casos a, b, c y d, y el orden de las cajas por versión.

## [0.7.0] — 2026-11-10 — D-51 compatibilidad por capacidad y tonalidades aceptadas (reemplaza a D-49 y D-50)

Aprobada por la autora, que la pidió como «D-50»; se numeró D-51 porque D-50 ya existía. Respaldos:
- `respaldos/spats-algoritmo_2026-11-10_antes_D51/`
- `respaldos/SPATS_contexto_sistema_2026-11-10_antes_D51.md`
- `local/respaldo_tintoreria_2026-11-10_antes_D51.sql`

**Base de datos**
- Nueva tabla `maquina_tonalidad_aceptada (id_maquina, tonalidad)`, con clave compuesta; sale `maquina_tintoreria.tonalidad_maxima` (D-50).
- `planificacion_partida.motivo_bloqueo`.
- `BD.sql` (23 tablas), nueva `BD/migracion_2026-11-10_tonalidades_aceptadas.sql` (aplicada en `tintoreria`) y parque oficial de 10 máquinas en `datos_iniciales.sql`.

**spats-algoritmo** (no cambian la búsqueda del ACO, el calendario, la función objetivo, el validador ni los parámetros)
- `Maquina.tonalidadesAceptadas` (`Set<Tonalidad>`; si es null, las cuatro).
- Nueva `nucleo.Compatibilidad`: capacidad = número de la barca y tonalidad aceptada.
- `ConstructorInstancia` arma `compat[p]` y `tieneDiaHabil` con esa regla.
- `PartidaExcluida.SIN_MAQUINA_COMPATIBLE` reemplaza a `SIN_MAQUINA`.
- `LectorDatosExcel`: columna opcional `tonalidades_aceptadas`.
- Nuevas PU-40 a PU-46. PI-03 y PI-04 dan la misma F.

**Backend**
- `MaquinaTintoreria.tonalidadesAceptadas` (`@ElementCollection`).
- `MaquinaService`:
  - Al menos una tonalidad (`TONALIDADES_REQUERIDAS`); al registrar sin tonalidades, las cuatro.
  - Antes de modificar: `CONFIRMACION_REQUERIDA` (pendientes sin máquina compatible) o `CONFIRMACION_PLAN_VIGENTE` (partidas de la vigente, desde hoy, en esa máquina), con las listas en el problem+json.
  - Bitácora de `tonalidades_aceptadas`.
- `ConflictoException` lleva datos.
- Nueva `CompatibilidadMaquinas`; sale `CoherenciaBarca`.
- `ImportadorPartidas` y `PartidaService`:
  - La barca debe empezar con un número.
  - Ya no se valida la letra (ni contra la tonalidad ni contra la clasificación).
  - Aviso si no hay máquina compatible.
  - Sale el aviso de partida blanca con barca C.
- `LectorDatosPlanificacion` envía las tonalidades al algoritmo y ya no filtra partidas.
- `TraductorResultado` registra las `SIN_MAQUINA_COMPATIBLE` como BLOQUEADA y guarda el motivo.
- `GET /partidas/sin-maquina-compatible` reemplaza a `/partidas/incoherentes`.
- La re-planificación acepta `cambioMaquinas` sin incidencias.
- Exportación: columna «Motivo» en Bloqueadas.

**Frontend**
- Máquinas:
  - Cuatro casillas «Tonalidades que acepta», con el mensaje de mínimo una y la ayuda del modo guiado.
  - Columna «Acepta».
  - Aviso con listas y las opciones «Guardar y re-planificar desde hoy» (lleva a Incidencias con el cambio de máquinas marcado) y «Guardar y aplicar desde la próxima planificación».
- Re-planificación: casilla «Aplicar los cambios de tonalidades aceptadas de las máquinas».
- Partidas: aviso y filtro «Sin máquina compatible».
- Bloqueadas: columna «Motivo».

**Pruebas**
- Algoritmo: `CompatibilidadTest` (300 blanca, 300 clara, 300 media Bloqueada, 540 kg barca 600, la letra no influye, barca sin número) y `TonalidadesAceptadasExcelTest`.
- Backend:
  - `CompatibilidadMaquinasTest`.
  - `TonalidadesAceptadasMaquinaTest`: cuatro desmarcadas, por defecto todas, pendiente sin compatible y vigente sin cambios.
  - `ReplanificacionMotorTest` D-51: tras quitar la tonalidad, la vigente deja de ser factible y la re-planificación ya no usa esa máquina para esas partidas.
  - `TraductorResultadoTest` actualizada.
- Salen las pruebas de D-49 y D-50.

**Verificación**
- `mvn test` en `backend/`:
  - spats-algoritmo: 63 pruebas sin fallos, incluidas PI-03 y PI-04.
  - API: 74 sin fallos; 2 omitidas (integración con PostgreSQL).
- Integración, en una base temporal ya eliminada:
  - `ReplanificacionIntegracionTest` pasa.
  - `FlujoPlanificacionIntegracionTest` sigue fallando en la F de la semana 37 (1274,75 contra 1279,75), con el mismo valor que antes de D-51: pendiente por D-48.
- `npm run build` y `npm run lint` sin errores.

## [0.6.0] — 2026-11-10 — D-50 tonalidad máxima admitida por máquina de blanco (modifica D-49)

Aprobada por la autora, que la pidió como «D-49 (modifica D-39)»; se numeró D-50 porque D-49 ya existía (su «D-39» es la D-49). `spats-algoritmo` no cambia. Respaldos: `respaldos/SPATS_contexto_sistema_2026-11-10_antes_D50.md` y `local/respaldo_tintoreria_2026-11-10_antes_tonalidad_maxima.sql`.

**Base de datos**
- `maquina_tintoreria.tonalidad_maxima` (`BLANCO`, `CLARO`, `MEDIO`, `OSCURO`), NOT NULL, con una restricción: C solo `OSCURO`; B, `BLANCO`, `CLARO` o `MEDIO`.
- `BD.sql` actualizado. Nueva `BD/migracion_2026-11-10_tonalidad_maxima.sql`, que deja C → `OSCURO` y B → `CLARO`. Se aplicó en `tintoreria`.

**Backend**
- `CoherenciaBarca`: la regla usa la tonalidad máxima de la barca (`maximasPorBarca`). Si la barca no tiene máquinas, sigue la de D-49 (B → Claro). El mensaje indica partida, color, tonalidad, barca y tonalidad máxima.
- Se aplica en `ImportadorPartidas`, `PartidaService` (`modificarPartida`, `modificarColor`, `incoherentes`) y `LectorDatosPlanificacion`.
- `MaquinaService`:
  - Una máquina B exige tonalidad máxima (`TONALIDAD_MAXIMA_REQUERIDA`) y no admite `OSCURO`. Una C guarda `OSCURO`.
  - Otra máquina de la barca con otro valor → `TONALIDAD_BARCA_DISTINTA`, que nombra esa máquina.
  - Si la tonalidad máxima de la barca baja y hay PENDIENTE más oscuras, responde `CONFIRMACION_REQUERIDA` con la cantidad y los números.
- `POST /maquinas` acepta `confirmado`. `PUT /maquinas/{id}` acepta `confirmado` y `aplicarABarca` (iguala la barca; pendiente de confirmación, ver D-50).
- `CargadorDatosDemo`: B → `CLARO`, C → `OSCURO`.

**Frontend**
- Máquinas:
  - Campo obligatorio «Tonalidades que acepta» para clasificación B, con ayuda en modo guiado. No se puede guardar sin elegir.
  - Columna «Tonalidades que acepta» en la lista.
  - Aviso de confirmación al reducir la tonalidad máxima.
  - Al modificar, botón «Aplicar a toda la barca» cuando otra máquina de la barca tiene otro valor.
- Datos registrados → Partidas: las incoherentes se piden a `GET /partidas/incoherentes`, en vez de calcularse en el navegador con la regla fija de D-49.

**Pruebas**
- `CoherenciaBarcaTest` reescrita: 300 B con MEDIO, 200 B con CLARO, máquina C, barca sin máquinas, valores distintos y mensaje.
- Nueva `TonalidadMaximaMaquinaTest`: B sin tonalidad, B con Oscuro, C, misma barca con otro valor, aplicar a la barca, aviso al reducir y sin aviso.
- Nueva `FiltroTonalidadMaximaTest`: tras reducir la barca, la partida más oscura no se envía al algoritmo y se avisa.
- `PermisosPorPerfilTest` adaptada a las firmas nuevas.

**Documentación**
- `SPATS_contexto_sistema.md`:
  - Glosario: Barca y la nueva entrada «Tonalidad máxima admitida».
  - RN-06, dominios y columnas agregadas (9.1), partidas candidatas (9.2), RF15, HU-07 C1 y sección 13 (ítems 4 y 13).
- `DECISIONES.md`: D-50, y D-49 marcada como modificada.

**Verificación**
- `mvn test` en `backend/`:
  - spats-algoritmo: 56 pruebas sin fallos.
  - API: 62 pruebas sin fallos; 2 omitidas (integración con PostgreSQL).
- Integración, en una base temporal ya eliminada:
  - `ReplanificacionIntegracionTest` pasa.
  - `FlujoPlanificacionIntegracionTest` falla en la comparación de F de la semana 37 del R2.2 (1274,75 contra 1279,75 esperado). Es la regresión contra el R2.2 que ya estaba pendiente por D-48: con los datos demo, las máquinas B quedan en `CLARO`, igual que con D-49.
- `npm run build` y `npm run lint` sin errores.

## [0.5.0] — 2026-11-07 — D-48 reparto diario proporcional y escalonado; D-49 coherencia barca–tonalidad

Aprobadas por la autora, que las pidió como D-38 y D-39; se numeraron D-48 y D-49 porque esos números ya existían. Respaldos: `respaldos/spats-algoritmo_2026-11-07_antes_D48/` y `respaldos/SPATS_contexto_sistema_2026-11-07_antes_D48_D49.md`.

**spats-algoritmo** (solo `nucleo.ConstructorCalendario`; no cambian la búsqueda del ACO, la función objetivo, el validador ni los parámetros)
- Nueva cuota diaria: `min(máximo, floor(meta·j/H + desfase) − ubicadas)`, con `meta = min(objetivo, conformadas)` y `desfase = ((m + semana ISO) mod M + 0.5)/M`. La re-planificación la aplica desde D.
- Pruebas:
  - PU-11, 12, 13, 15 y 16 actualizadas, con el cálculo explicado.
  - PU-14 sin cambios.
  - PU-27 y PU-28 sin cambios, con un comentario de por qué.
  - Nuevas PU-38 (planta pareja) y PU-39 (rotación semanal). Se numeraron así porque PU-29 a PU-37 ya existían.
  - PI-03 y PI-04 quedan pendientes por D-48.

**Backend**
- `partida/service/CoherenciaBarca`: barca B solo con Blanco o Claro. Se rechaza en `ImportadorPartidas`, `PartidaService.modificarPartida` y `modificarColor`; una partida blanca con barca C da aviso.
- `LectorDatosPlanificacion` no envía al algoritmo las PENDIENTE incoherentes y las avisa.
- Nuevo `GET /partidas/incoherentes`.
- `RegresionInstanciasR22Test`: la comparación de F y de conformadas queda pendiente por D-48; sigue revisando factibilidad y restricciones de la base.
- Nueva `CoherenciaBarcaTest`.

**Frontend**
- Datos registrados → Partidas: filtro «Barca y tonalidad» y aviso con el número de partidas incoherentes.

**Documentación**
- `SPATS_contexto_sistema.md`: RN-06, RN-08, RN-13, sección 8.3 paso 3 y sección 13 (ítems 12 y 13).
- `DECISIONES.md`: D-48 y D-49.
- `spats-algoritmo/docs/plan_de_pruebas.md`.

**Verificación**
- `mvn test` en `backend/`:
  - spats-algoritmo: 56 pruebas sin fallos; 2 omitidas (PI-03 y PI-04, pendientes por D-48). PI-05 confirma que las 12 instancias del R2.2 siguen dando soluciones factibles según el validador.
  - API: 48 pruebas sin fallos; 2 omitidas (integración con PostgreSQL, como antes).
- `npm run build` y `npm run lint` sin errores.
- Semana del 09/11/2026 regenerada en `tintoreria` (versión 2):
  - Total de la planta por día: 12, 14, 14, 16, 17, 15 y 14, con 5 entre el día con más y el día con menos. Antes era 17, 17, 18, 15, 24, 18 y 1, con 23 de diferencia.
  - Quedaron fuera 34 partidas de tonalidad media en `200 B` (D-49).
## [0.4.0] — 2026-11-06 — Nuevo diseño de indicadores y calendario de Planificación semanal (solo frontend)

**Agregado** (pedido de la autora, a partir de su imagen de referencia)
- `IndicadoresPlan`: tarjetas con color para asignadas (con % del objetivo), sobrantes, bloqueadas, máquinas con carga completa ("X de 11") y lavados.
- `CalendarioPlanificacion`:
  - Visualización Semana, Día y Máquina.
  - Máquinas agrupadas por barca, con grupos que se pliegan.
  - Celdas coloreadas según la carga del día frente al reparto diario: completa, normal, baja o sin carga.
  - Partidas en etiquetas de una línea: tonalidad, código y gota de lavado.
- Panel "Detalle de planificación" al elegir una celda: secuencia numerada con lavados, kg, partidas, % del máximo diario e información adicional. Con el panel abierto, las partidas se reducen a puntos.
- Se usa también en la propuesta de re-planificación.
- Token de color `lavado` (celeste) en `index.css`: el violeta sigue reservado para escenarios.

**Sin usar**
- `PlanningGrid.tsx` y `SummaryBar.tsx` se conservan, pero ya no se importan.
- No se incluyó la columna de carga semanal, a pedido de la autora. Tampoco los botones, filtros y tiempos de la imagen que el sistema no tiene (editar planificación, tiempo estimado, búsqueda).

**Verificación**
- `npm run build` y `npm run lint` sin errores.
- En el navegador, con una base temporal ya eliminada: las tres vistas, el detalle y la reducción a puntos, en modo claro y oscuro.
## [0.3.7] — 2026-11-05 — Ajuste de filtros en Datos registrados (solo frontend)

**Cambiado** (pedido de la autora)
- Artículos: nuevos filtros por tipo de tela, sacado del nombre («JERSEY», «RIB», «FRENCH TERRY»…), y por licra («LYC» en el nombre).
- Partidas: solo barca, tonalidad y estado.
- Proyección: solo el mes de tejido.
- Las fechas se filtran por mes, con los meses que tienen datos: pedidos por mes de entrega y proyección por mes de tejido.

## [0.3.6] — 2026-11-05 — Paginación de 15 registros en todos los listados (solo frontend)

**Agregado**
- `components/ui/Paginacion.tsx`: muestra 15 registros por página, con flechas, «Página X de Y» y el rango de registros. Las flechas se deshabilitan en la primera y en la última página. Al cambiar un filtro vuelve a la página 1.
- Se aplica en Datos registrados (5 pestañas), Carga de datos (versiones, historial y advertencias), Máquinas (máquinas e indisponibilidades) y Planificación (asignadas, sobrantes, bloqueadas, arrastradas, alertas, bolsas y cierre). También en Historial (versiones, comparación, cambios y trazabilidad), Incidencias, Re-planificación (incidencias, partidas de hoy y cambios) y la tabla de errores de importación.
- Trazabilidad: la API se consulta de 15 en 15 (antes, de 50 en 50).
- No se pagina el calendario, porque no es un listado.

**Verificación**
- `npm run build` y `npm run lint` sin errores.
- En el navegador, con una base temporal ya eliminada:
  - Partidas: 537 registros en 36 páginas; con el filtro 200 B, 83 en 6 páginas y 8 en la última. Las flechas se deshabilitan en los extremos y el filtro vuelve a la página 1.
  - Bloqueadas: 310 en 21 páginas. Alertas: 51 en 4. Asignadas: 110 en 8. Bolsas: 203 en 14.
  - Trazabilidad: la página 2 muestra los registros 16 y 17 de 17.
  - Historial de cargas: 3 registros en 1 página.

## [0.3.5] — 2026-11-05 — La función objetivo deja de mostrarse al usuario

**Cambiado** (pedido de la autora; sin cambios en `BD/BD.sql` ni en `spats-algoritmo`)
- Se quitó F de Planificación semanal, Historial, Re-planificación, la exportación a Excel, los mensajes y la trazabilidad. La exportación tampoco muestra desbalances, evaluaciones ni tiempo de ejecución.
- F se sigue calculando y guardando: el algoritmo la usa para decidir, RF55 para comparar propuestas, y las pruebas de regresión para validarla. La API la sigue devolviendo.

## [0.3.4] — 2026-11-05 — Reversión del cierre de prueba en `tintoreria` (solo datos)

A pedido de la autora, con respaldo en `local/respaldo_tintoreria_2026-11-05_antes_revertir_cierre.sql`: semana del 09/11/2026 sin planificación, proyección v1 como `ELIMINADA`, 110 partidas y 3 pedidos de vuelta a pendiente. Se conservó el cambio de fecha de entrega hecho en el pedido 176.

## [0.3.3] — 2026-11-05 — Filtros en el cierre y en las listas de partidas (solo frontend)

**Agregado**
- `components/common/Filtros.tsx` (lo que estaba en Datos registrados) y `components/planning/FiltrosPartidasPlan.tsx`.
- Cerrar planificación: filtros por búsqueda, selección, clasificación, máquina, día, tipo de color, tonalidad, lavado y fecha de entrega. Marcar y desmarcar actúan solo sobre las partidas filtradas; el cierre usa la selección completa.
- Partidas asignadas y Partidas sobrantes: los mismos filtros sobre su lista.

**Verificación**
- `npm run build` y `npm run lint` sin errores.
- En el navegador, con una base temporal ya eliminada: filtros de Partidas asignadas (2 de 110) y de Datos registrados visibles; en el cierre, desmarcar 10 partidas filtradas y cerrar da 100 completadas y 10 arrastradas. El filtro «Selección» muestra esas 10.
- No se probó en navegador: Partidas sobrantes (la prueba no tuvo ninguna).

## [0.3.2] — 2026-11-05 — Filtros en Datos registrados y proyección eliminada recuperable

**Base de datos** (respaldo: `respaldos/BD_2026-11-05_antes_proyeccion_eliminada.sql`; pedido de la autora)
- `BD/BD.sql` y `BD/migracion_2026-11-05_proyeccion_eliminada.sql`: `proyeccion_tejeduria.estado` admite `ELIMINADA`.

**Cambiado**
- Eliminar una proyección ya no borra la versión ni su carga: quedan como eliminadas, no se usan al planificar y se puede volver a ellas (D-46). Las planificaciones generadas con esa versión sí se borran.
- Eliminar un pedido borra del todo las versiones eliminadas que lo usan, porque ya no se podría volver a ellas.
- Datos registrados: filtros por sección, búsqueda también en proyección, contador de resultados y lo más reciente primero.

## [0.3.1] — 2026-11-05 — Eliminar una versión de la proyección con sus planificaciones

**Agregado** (pedido de la autora, D-44; sin cambios en `BD/BD.sql` ni en `spats-algoritmo`)
- `DELETE /proyecciones/{id}` y botón «Eliminar» en Carga de datos → Versiones de la proyección.
- Borra la versión, sus registros, su carga del historial y las planificaciones generadas con ella. Si era la vigente, vuelve a serlo la anterior. Si la vigente eliminada dejó saldo en las bolsas, la semana siguiente queda marcada como desactualizada (D-19).
- No se elimina si una de esas planificaciones está cerrada o tiene incidencias.

## [0.3.0] — 2026-11-05 — Eliminar cargas, filas, máquinas e indisponibilidades; barca automática

**Base de datos** (respaldo: `respaldos/BD_2026-11-05_antes_id_carga.sql`; pedido expreso de la autora)
- `BD/BD.sql`: `id_carga` en `pedido`, `detalle_pedido`, `partida` y `proyeccion_tejeduria`; sale `maquina_tintoreria.tiempo_procesamiento`; `tipo_operacion` suma `ELIMINAR`.
- `BD/migracion_2026-11-05_eliminar_cargas.sql`: aplica lo mismo a una base existente sin borrar sus datos. Aplicada en `tintoreria` el 05/11, a pedido de la autora, con respaldo en `local/respaldo_tintoreria_2026-11-05_antes_migracion.sql`.

**Backend**
- `EliminacionCargaService` y `DELETE /cargas/{id}` (D-40). Eliminación de pedidos, artículos, partidas, colores y registros de proyección (D-42).
- Máquinas: `DELETE`, barca calculada (D-41), sin tiempo de procesamiento (D-43). Indisponibilidades: listar todas y eliminar.

**Frontend**
- Máquinas: el botón Indisponibilidad abre directamente el formulario; ya no se crea un apartado vacío. La tabla «Indisponibilidades registradas» sale de la base, solo aparece si hay alguna y permite eliminarlas. Cada máquina tiene Indisponibilidad, Modificar y Eliminar. La barca se completa sola.
- Datos registrados: Modificar y Eliminar en cada fila. Carga de datos: «Eliminar carga» en el historial.

**Verificación**
- `mvn test`: sin fallos (las 2 pruebas de integración siguen omitidas). `npm run build` y `npm run lint` sin errores.
- Flujo completo por API en una base temporal y en una copia migrada de `tintoreria`, ya eliminadas. En la copia: borrar la carga vieja de partidas y subir la corregida da 83 partidas en `200 B`; luego genera 110 asignadas, F = 590.25.
- **Sin verificar:** las pantallas en el navegador, porque la API de `tintoreria` no se reinició.

**Pendiente**
- `SPATS_contexto_sistema.md` no se modificó: RF15 y HU-07 C1 aún nombran el tiempo de procesamiento (D-43).

## [0.2.5] — 2026-11-05 — Máquinas 200 B en los archivos de la semana del 09/11/2026 (sin cambios de código)

**Cambiado**
- `2_partidas_semana_2026-11-09.xlsx` y `generar_carga.py`: hasta 200 kg, los colores claros, medios y blancos van a `200 B` y los oscuros a `200 C` (D-38, D-39). Resultado: 83 partidas en `200 B` y 105 en `200 C`. Pedidos, componentes y proyección no cambian.

**Agregado**
- `actualizar_barcas_200B.sql`: aplica el cambio en la base donde ya se cargó el archivo anterior, porque la carga acumulable conserva las partidas registradas. Probado en una copia de `tintoreria`, ya eliminada; sin aplicar en `tintoreria`.

**Verificación**
- Importación y generación con la API en una base temporal, ya eliminada, con 11 máquinas (3 `200 B`, 2 `200 C`, 2 de cada una de las demás capacidades): sin errores ni advertencias de importación; 110 partidas asignadas, 310 bloqueadas, F = 590.25.

## [0.2.4] — 2026-11-05 — Archivos de carga con datos reales para la semana del 09/11/2026 (sin cambios de código)

**Agregado**
- `Especificaciones/Carga_semana_2026-11-09/`: plantillas de pedidos, partidas y proyección de tejeduría, más `generar_carga.py`, que las produce.
- La proyección es la ventana real del 12 al 18/09 (la S38 del R2.2, 36 305 kg). Todas las fechas, incluidas las de entrega, se mueven 56 días: se conservan los días de la semana y la distancia entre pedidos y proyección.
- Fecha de entrega, tonalidad y barca: las del R2.2 (`instancias/datos_*.csv`), porque los archivos reales no las traen.

**Verificación**
- Importación y generación con la API (`spats-api.jar`) en una base temporal, ya eliminada: 88 pedidos, 537 partidas y 115 filas de proyección, sin errores ni advertencias de importación. Resultado: 104 partidas asignadas, 316 bloqueadas, F = 367.75. Las bolsas empiezan en 0 kg (D-15). La base `tintoreria` no se modificó.

## [0.2.3] — 2026-11-02 — `SPATS_contexto_sistema.md` actualizado (39 cambios aprobados)

**Respaldo:** `respaldos/SPATS_contexto_sistema_2026-11-02_antes_39_cambios.md`.

Se aplicaron los 39 cambios de `revisiones/2026-10-25_propuesta-actualizacion-md.md` (v2), aprobados por la autora, con sus respuestas del 02/11:
- Encabezado: fecha de actualización y referencia a `DECISIONES.md`.
- §2.1: re-planificación implementada; RF30 con regla aprobada para un incremento posterior.
- §3: la bolsa es tela cruda, una por artículo (D-25).
- §6.1: dominio de `pedido.estado` (D-20). Nueva §6.4 con los estados de `planificacion_semanal`.
- §7: nuevos bloques «Vigencia y encadenamiento de semanas» (D-06, D-14, D-15, D-16, D-19, D-22) y «Datos» (D-23, D-24, D-25).
- §8.5: contrato de re-planificación, `evaluar` y motivos `SIN_DIA_DISPONIBLE` y `TELA_DEVUELTA`. La búsqueda del ACO y la función objetivo no cambiaron.
- §9.1: dominios con CHECK, columnas agregadas y `ck_bolsa_balance` con `kg_devueltos`.
- §9.2: proyección vigente, código sin prefijo, stock de D, D-16 confirmada, D-09 y D-09b.
- §9.3: `SIN_MAQUINA` (D-12), marca de N+1 y `kg_devueltos`.
- §9.4: cierre en orden, cerrar no marca N+1 y lista única de arrastradas (D-27).
- §9.5: diseño implementado (grupos A, B y C, incidencias, D-29, D-31 a D-37, propuesta, confirmación y descarte). Se corrigió la frase sobre la F: las dos se calculan sobre la semana completa y la misma instancia (D-28, D-35).
- HU-18 C2: se quitó la nota de pendiente.
- §13: abiertos los vacíos 4 y 11; los demás quedan como resueltos. El 2 lleva la regla de RF30, para la proyección de la semana siguiente (sábado a viernes) y sin fecha límite guardada.

## [0.2.2] — 2026-11-02 — D-24 y D-25 ampliadas; decisiones confirmadas

**Backend**
- D-24: `PartidaPorCambiarEvent` (se publica solo si la partida o su componente cambian) y `InvalidacionPorPartida`. Si la partida está Asignada o Sobrante en una vigente abierta: sin `confirmado` → 409 `CONFIRMACION_REQUERIDA`; con `confirmado=true` → la vigente pasa a INVALIDADA, se eliminan sus propuestas y se marca N+1 (D-19). Consulta `PlanificacionSemanalRepository.vigentesAbiertasConPartidaUbicada`. `PUT /partidas/{id}` y `PUT /componentes/{id}` aceptan `?confirmado=`.
- D-25: `ValidacionLibro.codigoArticulo` quita un prefijo numérico entre paréntesis al inicio del código y avisa con el código original y el limpio. Lo usan las tres plantillas.
- Pruebas nuevas: `CodigoArticuloTest` (3) e `InvalidacionPorPartidaTest` (3).

**Frontend**
- Datos registrados → Partidas: diálogo de confirmación cuando la partida está en la vigente (D-24).

**Documentación**
- `DECISIONES.md`: D-09b, D-22 a D-25, D-29 y D-32 a D-37 pasan a confirmadas (sección A); D-24 y D-25 ampliadas.
- `docs/api.md` y `docs/plantillas.md`.
- `SPATS_contexto_sistema.md`: los 39 cambios se aplicaron en la entrega 0.2.3.

**Verificación**
- `mvn test`: módulo **54 sin fallos**; API **46 sin fallos y 2 omitidas** (integración, necesitan PostgreSQL).
- `npm run build` y `npm run lint` sin errores.

## [0.2.1] — 2026-11-02 — Marca de N+1 guardada (D-19) y dominios con CHECK (D-20)

**Respaldo:** `respaldos/BD_2026-11-02_antes_D19_D20.sql`.

**Base de datos (`BD/BD.sql`, sigue siendo re-ejecutable)**
- `planificacion_semanal.stock_desactualizado BOOLEAN NOT NULL DEFAULT FALSE` (D-19). Reemplaza la marca calculada con `fecha_generacion`.
- CHECK de dominio (D-20) en `usuario.estado`, `pedido.estado`, `planificacion_semanal.estado` (incluye PROPUESTA), `proyeccion_tejeduria.estado`, `maquina_tintoreria.estado`, `incidencia.estado` y `bitacora_auditoria.tipo_operacion` (incluye PROPONER/CONFIRMAR/DESCARTAR, D-37).
- CHECK `clasificacion_color IN ('C','B')` en `color` y `maquina_tintoreria` (D-07).
- Comentario con los estados de `planificacion_semanal`.

**Backend**
- `PlanificacionSemanal`: campo `stockDesactualizado` y `marcarStockDesactualizado()`.
- `ReglasVigencia.marcarSemanaSiguiente(lunes)`: marca la vigente abierta de N+1 y lo registra en la bitácora. Se llama al:
  - generar otra versión de N (`GuardadoPlanificacion`);
  - invalidar N por la proyección (`InvalidacionPorProyeccion`);
  - confirmar una re-planificación de N (`GuardadoReplanificacion`). La versión confirmada hereda la marca de la que reemplaza.
- Cerrar N no marca N+1.
- `ReglasVigencia.stockDesactualizado` solo lee la columna. Se quitó `PlanificacionSemanalRepository.existsBySemanaFechaInicio`, que ya no se usa.
- `FlujoPlanificacionIntegracionTest` comprueba el valor de la columna.

**Documentación**
- `DECISIONES.md`: D-19 cambiada a columna; D-20 pasa a confirmada (sección A).
- `revisiones/2026-10-25_propuesta-actualizacion-md.md` v2: 39 cambios con todo lo decidido después del 25/10. Los cambios 14 y 15 quedan **por definir**. El `.md` **no** se modificó.
- `revisiones/2026-10-25_propuesta-replanificacion.md` y `docs/api.md`: la marca de D-19 se lee de la columna.

**Verificación**
- `mvn test`: módulo **54 sin fallos**; API **38 sin fallos y 2 omitidas** (integración, necesitan PostgreSQL).
- **Sin verificar:** el script contra PostgreSQL y las pruebas de integración.

## [0.2.0] — 2026-11-01 — Re-planificación desde D (HU-18, RF53–RF55, RN-25)

Implementa la propuesta de re-planificación v2 aprobada por la autora, con sus respuestas a la §5 (D-28 a D-35).

**Respaldos (antes de cambiar)**
- `respaldos/spats-algoritmo_R2.3_2026-11-01/`: `src/` y `pom.xml` del módulo tal como estaban.
- `respaldos/BD_2026-11-01_antes_kg_devueltos.sql`.

**Base de datos (`BD/BD.sql`, sigue siendo re-ejecutable)**
- `bolsa_articulo.kg_devueltos NUMERIC(12,3) NOT NULL DEFAULT 0 CHECK (kg_devueltos >= 0)`.
- `ck_bolsa_balance` = saldo inicial + ingresados + devueltos − consumidos (D-30).
- Encabezado del script: cambios D-21 y D-30.

**`spats-algoritmo` (aprobado; la búsqueda del ACO y la función objetivo no cambian)**
- Nuevas: `entrada/Replanificacion`, `AsignacionFija`, `ConformacionConservada`, `DisponibilidadPartida`.
- Cambian: `DatosSemana` (componente opcional `replanificacion`, se mantiene el constructor de 8 argumentos), `InstanciaSemana`, `ConstructorInstancia`, `SimuladorConformacion`, `ConstructorCalendario`, `Validador`, `PartidaExcluida` (`SIN_DIA_DISPONIBLE`, `TELA_DEVUELTA`) y `PlanificadorSemanal` (salida de la semana completa y `evaluar`).
- Sin cambios: `aco/*`, `FuncionObjetivo`, `Solucion`, `Evaluacion`, `Calendario`, `EstrategiaDecision`, `salida/*`, `excel/*`.
- Nueva prueba `ReplanificacionTest` (20 casos, PU-29 a PU-37).

**Backend**
- Motor:
  - `ArmadoReplanificacion` (grupos A/B/C e incidencias, Java puro);
  - `LectorDatosReplanificacion`;
  - `TraductorResultado.traducirReplanificacion` (bolsa copiada hasta D-1 y reconstruida desde D con `kg_devueltos`);
  - `DecisionReplanificacion` (RF55);
  - `GuardadoReplanificacion` (proponer, confirmar y descartar, una transacción por paso);
  - `PropuestasPendientes` (D-32);
  - `ReglasReplanificacion`;
  - `EjecutorAlgoritmo.evaluar`.
- `GuardadoPlanificacion.guardarFilas` se comparte entre la generación y la re-planificación.
- Entidades: `EstadoPlanificacion.PROPUESTA`, `PlanificacionSemanal.propuesta()`/`confirmar()`, `BolsaArticulo.kgDevueltos`, `PlanificacionIncidencia` (nueva), `Incidencia.considerar()`, `TipoOperacion` PROPONER/CONFIRMAR/DESCARTAR.
- API: `GET`/`POST /semanas/{lunes}/replanificacion`, `POST /planificaciones/{id}/confirmacion`, `DELETE /planificaciones/{id}` (`ReplanificacionController`, `ReplanificacionService`).
- Bloqueos con una propuesta pendiente: generar la semana o la siguiente, cerrar y re-planificar otra vez.
- Si cambia la proyección de la semana, se eliminan sus propuestas.
- El historial y el número de versiones no incluyen la PROPUESTA. El estado de la semana informa la propuesta pendiente.
- El calendario de una re-planificación marca los días de la incidencia de máquina.
- `RelojConfig`: propiedad `spats.reloj.inicio`, solo para pruebas.

**Frontend**
- Incidencias, nuevo `PanelReplanificacion`:
  - se seleccionan las incidencias pendientes y se marcan las partidas de hoy que ya están en máquina;
  - se re-planifica desde hoy;
  - si se conserva la vigente, aparece la alerta de RF55;
  - la propuesta se revisa (F de la propuesta y de la vigente con las incidencias, cambios por partida y calendario) y se confirma o descarta.
- Planificación semanal: aviso de propuesta pendiente (Generar y Cerrar quedan deshabilitados) y columna «Devuelto» en las bolsas.
- `api/cliente.ts`: método `eliminar`. Tipos y etiquetas para PROPUESTA.

**Documentación**
- `DECISIONES.md`: D-28, D-30 a D-35 confirmadas; D-29, D-36 y D-37 pendientes; D-03 marcada como implementada.
- Propuesta de re-planificación marcada como aprobada e implementada. Propuesta de actualización del `.md` ampliada (§9.1, §9.5 con la corrección de la frase sobre F, HU-18 C2, §8). El `.md` **no** se modificó.
- `README.md`, `docs/arquitectura.md`, `docs/integracion-algoritmo.md`, `docs/api.md`, `docs/pruebas.md`, `backend/README.md`, `FRONT/frontend/README.md` y `spats-algoritmo/.../README.md`.

**Verificación**
- `mvn test` (backend, con el módulo):
  - `spats-algoritmo`: **54 pruebas sin fallos**, las 34 originales más 20 de re-planificación;
  - API: **38 sin fallos y 2 omitidas** (las dos de integración, que necesitan PostgreSQL);
  - `RegresionInstanciasR22Test`: las 12 instancias siguen dando el F del R2.3.
- `npm run build` y `npm run lint` sin errores.
- **Sin verificar:**
  - `ReplanificacionIntegracionTest` (prueba 6) y `FlujoPlanificacionIntegracionTest` contra PostgreSQL;
  - el arranque de la API con los beans y consultas nuevas, que se validan al conectarse a la base;
  - la pantalla en el navegador.

## [0.1.5] — 2026-11-01 — Propuesta de re-planificación, versión 2 (sin cambios de código)

**Documentación**
- `revisiones/2026-10-25_propuesta-replanificacion.md` reescrita con la regla de negocio confirmada el 01/11 y revisada contra el código real:
  - grupos A, B y C;
  - las clases de `spats-algoritmo` que cambian y las que no;
  - los cambios del backend;
  - la comparación de RF55;
  - lo que se pierde o queda abierto;
  - las pruebas.
- Conclusión: sí exige modificar `spats-algoritmo`. Cambian el contrato de entrada, `ConstructorInstancia`, `SimuladorConformacion`, `ConstructorCalendario`, `Validador` y `PlanificadorSemanal`. No cambian `ColoniaHormigas` ni `FuncionObjetivo`.
- `DECISIONES.md`: D-28 propuesta (indicadores de la semana completa).

## [0.1.4] — 2026-11-01 — D-27: lista única de partidas arrastradas

**Backend**
- `GET /partidas/arrastradas`: `semanaOrigen` se renombra a `semanaCierre` y se agrega `semanasArrastrada`, que cuenta las semanas desde la de cierre hasta la actual con el reloj del sistema.
- Sin cambios en la base: no se agrega `partida.fecha_completada`. Al completar una arrastrada, la fecha queda en la bitácora.

**Frontend**
- Partidas arrastradas: cada tarjeta muestra «Cerrada en la semana del … · lleva N semanas arrastrada». El texto explica que la lista no depende de la semana seleccionada y que la historia de cada semana está en el historial.

**Documentación**
- `DECISIONES.md`: D-27.

**Verificación**
- `mvn test`: 34 pruebas del algoritmo y 26 de la API sin fallos, más 1 omitida, la de integración. `npm run build` y `npm run lint` sin errores.
- **Sin verificar** contra PostgreSQL.

## [0.1.3] — 2026-11-01 — Corrección de la orden para arrancar la API

**Documentación**
- `README.md` y `backend/README.md`: `mvn -pl spats-api -am spring-boot:run` fallaba con «No plugin found for prefix 'spring-boot'», porque el módulo del algoritmo no tiene ese plugin. La orden correcta es `mvn -DskipTests install` en `backend/` y luego `mvn spring-boot:run` dentro de `backend/spats-api`.

**Verificación**
- Con esa orden, la API arranca hasta la conexión con PostgreSQL y se detiene por la falta de contraseña. Es lo esperado: falta definir `SPATS_DB_CONTRASENA`.

## [0.1.2] — 2026-10-31 — D-19: cierre en orden y semana desactualizada

**Backend**
- `ReglasVigencia.exigirSemanaAnteriorCerrada`: el cierre devuelve 409 `SEMANA_ANTERIOR_ABIERTA` («Cierra primero la semana del d/M.») si la semana anterior tiene planificación vigente abierta.
- `ReglasVigencia.stockDesactualizado`: `GET /semanas/{lunes}/estado` devuelve `stockDesactualizado` con el motivo si la vigente de la semana anterior se generó después que la de esta, o si la anterior quedó sin vigente. Solo compara `fecha_generacion` guardadas; **no cambia la base**.
- `PlanificacionSemanalRepository`: `findBySemanaFechaInicioAndEsVigenteTrue` y `existsBySemanaFechaInicio`.

**Frontend**
- Planificación semanal: aviso «Desactualizada…» con el botón Generar (solo para el Planificador).

**Documentación**
- `DECISIONES.md`: D-19 confirmada y D-26 nueva (una re-planificación descartada no queda en el historial).
- Propuesta de re-planificación: punto de la versión sin confirmar resuelto. `docs/api.md` actualizado.

**Pruebas**
- `FlujoPlanificacionIntegracionTest`: comprueba que la 38 queda desactualizada al volver a generar la 37 y que no se puede cerrar la 38 con la 37 abierta.

**Verificación**
- `mvn test`: 34 pruebas del algoritmo y 26 de la API sin fallos, más 1 omitida, la de integración.
- `npm run build` y `npm run lint` sin errores.
- **Sin verificar:** las consultas nuevas y la prueba de integración contra PostgreSQL.

## [0.1.1] — 2026-10-31 — D-13b y D-21 según la revisión de la autora

**Base de datos**
- `BD/BD.sql` no estaba en `BD/`. Se restauró la versión corregida del 25/10 (D-05) y se le agregó `indisponibilidad_maquina.tipo_indisponibilidad`. La columna va dentro del `CREATE TABLE`, con la restricción `ck_indisp_tipo`, así que el script se sigue pudiendo ejecutar varias veces (D-21).

**Backend**
- `TipoIndisponibilidad` pasa a `INDISPONIBLE`/`MANTENIMIENTO` y se guarda en la columna nueva. `motivo` guarda solo el texto, sin prefijo.
- Registrar un `CAMBIO_PRIORIDAD` ya no cambia `pedido.fecha_entrega` (D-13b). La respuesta devuelve `fechaEntregaActual` en lugar de `fechaEntregaAnterior`.

**Frontend**
- Máquinas: el selector «Tipo de indisponibilidad» ofrece Indisponible (por defecto) o Mantenimiento.
- Incidencias: el mensaje indica que el pedido conserva su fecha hasta que se confirme la re-planificación.

**Documentación**
- `DECISIONES.md`: D-13b y D-21 confirmadas; D-19 en revisión.
- Propuestas de re-planificación y de actualización del `.md`: se agregó el paso de confirmación y la columna nueva.
- Nueva `revisiones/2026-10-31_revision-D19.md`.

**Verificación**
- `mvn test`: 34 pruebas del algoritmo y 26 de la API sin fallos, más 1 omitida, la de integración con PostgreSQL.
- `npm run build` y `npm run lint` sin errores.
- **Sin verificar:** que el mapeo de la columna nueva coincida con la base. Eso lo comprueba `ddl-auto=validate` al arrancar contra PostgreSQL o la prueba de integración.

## [0.1.0] — 2026-10-25 — Sistema base: backend, algoritmo integrado y frontend conectado

**Base de datos**
- `BD/BD.sql` corregido en el mismo archivo (D-05, D-10): re-ejecutable con DROP de las 22 tablas; `bitacora_auditoria.id_entidad`; `valor_anterior`/`valor_nuevo` de 255; `incidencia.descripcion`; comentarios corregidos; ALTER finales integrados en los CREATE (mismo resultado). Respaldo del original en `respaldos/BD_original_2026-10-25.sql`.
- Nuevo `BD/datos_iniciales.sql`: perfiles PLANIFICADOR y CONSULTA, y dos cuentas con contraseñas BCrypt.

**Algoritmo**
- `spats-algoritmo`: **sin cambios de código**. Se incorpora como módulo del agregador `backend/pom.xml`.

**Backend (nuevo: `backend/spats-api`)**
- Java 21, Spring Boot 4.1.2, Spring Data JPA, Spring Security (JWT), Apache POI 5.5.1. Módulos: comun, seguridad, auditoria, pedido, partida, tejeduria, carga, maquina, planificacion (con `motor`), incidencia, demo.
- Generación (HU-11):
  - lectura de §9.2 → ACO con los parámetros del R2.2 → traducción de §9.3 → guardado en una transacción;
  - `bolsa_articulo` se reconstruye y se verifica contra `stockFinal` (D-02);
  - la regla de Bloqueada de §6.2;
  - versiones con una sola vigente (D-14).
- Consulta (HU-12, HU-13): calendario con `orden_secuencia`, distribución por color, desviaciones, bolsas. Exportación .xlsx (HU-14). Cierre de semana y arrastradas (HU-15, HU-16).
- Carga de datos (HU-04 a HU-06): plantillas .xlsx (D-11), validación completa, carga acumulable, versiones de la proyección con invalidación confirmada (D-06), modificación manual con bitácora, descargas.
- Máquinas e indisponibilidades (HU-07, HU-08). Incidencias (HU-17, D-13). Historial, comparación y trazabilidad (HU-19, HU-21). Autenticación con dos perfiles y modo de interacción (HU-01 a HU-03).
- Perfil `demo`: carga las semanas 37 y 38 del R2.2.

**Frontend (`FRONT/frontend`)**
- Conectado a la API: `src/api/` (cliente, tipos, servicios), sesión real con renovación e inactividad, semana compartida.
- Ajustado al `.md` (D-04):
  - 3 tipos de incidencia; calendario como cola por día sin horas;
  - sin «Marcar como vigente», «Limpiar planificación», eliminación de registros ni «volver a versión»;
  - compatibilidad por barca;
  - re-planificación deshabilitada con aviso (D-03).
- Pantallas fuera del alcance (Escenarios, Reportes, Parámetros, Proyección simulada) y `mockData.ts` movidos a `_fuera_de_alcance/` (no se compilan; nada se borró). Plantillas CSV anteriores movidas a `_fuera_de_alcance/plantillas_csv_anteriores/`.
- `npm run lint` limitado a `src/`: antes también revisaba `node_modules`.

**Documentación**
- `README.md` (raíz), `docs/` (arquitectura, integración del algoritmo, API, plantillas, pruebas), `backend/README.md`, `FRONT/frontend/README.md`.
- `SPATS_contexto_sistema.md`: B1–B5 (D-18), solo nombres de clases y valores que no coincidían con el código.
- Propuestas pendientes en `revisiones/`: RF30, re-planificación y actualización del `.md`.

**Verificación**
- `mvn test` en `backend/`: 34 pruebas del algoritmo sin fallos; 26 de la API sin fallos y 1 omitida. Las 12 instancias del R2.2 reproducen exactamente el F del R2.3.
- `npm run build` y `npm run lint` del frontend sin errores.
- **Sin verificar todavía:** `FlujoPlanificacionIntegracionTest` contra PostgreSQL (es la omitida: necesita la contraseña de la base local). Tampoco se probó el arranque de la API ni el uso de la interfaz contra la base real.

## [0.0.2] — 2026-10-25 — Decisiones D-01 a D-04 y revisión del script de BD (sin cambios de código)

**Agregado**
- `DECISIONES.md`: D-01 a D-04.
- `revisiones/2026-10-25_revision-script-BD.md`: errores S1 a S4 y preguntas P1 a P6 sobre `BD/BD.sql`.

**Sin cambios**
- `BD/BD.sql` queda intacto hasta que la autora confirme.

## [0.0.1] — 2026-10-25 — Análisis inicial (sin cambios de código)

**Agregado**
- `CONTROL_DE_VERSIONES/` con `README.md`, `CHANGELOG.md`, `DECISIONES.md` y `revisiones/2026-10-25_analisis-inicial.md`.

**Sin cambios**
- `spats-algoritmo/`, `FRONT/frontend/` y `SPATS_contexto_sistema.md` quedan intactos.

**Verificación**
- `spats-algoritmo`: `mvn test` con JDK 21 da 34 pruebas sin fallos. La ejecución creó `spats-algoritmo/spats-algoritmo/target/`, que es salida de compilación y está excluida por `.gitignore`.
