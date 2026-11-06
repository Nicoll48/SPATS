# Análisis inicial: algoritmo, contexto, frontend y skills

Fecha: 25 de octubre de 2026 · Estado: **pendiente de respuesta de la autora**

Revisé `SPATS_contexto_sistema.md`, el módulo `spats-algoritmo` (R2.3), `Experimentacion_R2.2` (solo como referencia), el frontend de `FRONT/frontend`, `FRONT/Especificacion/CLAUDE (1).md` y las skills de backend y frontend de `SKILLS/`.

Línea base: el módulo `spats-algoritmo` compila y sus 34 pruebas pasan con JDK 21 (`mvn test`, 25/10/2026).

---

## A. Lo que falta para empezar

### A1. No existe el script de la base de datos (R3.3)

El `.md` dice que la base se crea "con el script" y que Hibernate solo valida (`ddl-auto=validate`), pero en el proyecto no hay ningún `.sql`; la carpeta `Especificaciones/` está vacía. El `.md` nombra las 22 tablas y los dominios de algunas columnas, pero **no todas las columnas, ni sus tipos, ni sus claves**.

### A2. No existe un backend

El `.md` dice que el proyecto "ya contiene el código (backend, frontend y el módulo del algoritmo)", pero no hay backend. Se crea desde cero siguiendo la skill de backend.

---

## B. Inconsistencias entre el código del algoritmo y el `.md`

Ninguna cambia **lo que hace** el algoritmo; son diferencias de nombres o de redacción. Propongo corregir el `.md`, no el código.

| # | `.md` dice | El código tiene | Propuesta |
|---|---|---|---|
| B1 | §8.5 y §9.3: `EstadoAsignacion` = `REGULAR`, `SOBRANTE`; "`REGULAR` → `ASIGNADA`" | `EstadoAsignacion { ASIGNADA, SOBRANTE }` | Corregir el `.md`: el mapeo es directo (`ASIGNADA` → `ASIGNADA`). |
| B2 | §8.5: `salida.PartidaNoConformada` | `salida.PartidaBloqueada` | Corregir el `.md`. |
| B3 | §8.5: `nucleo.PartidaBloqueada` | `nucleo.PartidaExcluida` (motivos `SIN_MAQUINA`, `SIN_TELA`) | Corregir el `.md`. |
| B4 | §8.5: `ReglaReferencia` en `spats.algoritmo` | Está en `src/test` (solo pruebas) | Corregir el `.md`. |
| B5 | §6.1: el algoritmo usa `PENDIENTE / PLANIFICADA / COMPLETADA` | El enum también tiene `ARRASTRADA` | Corregir el `.md` (no afecta: el backend solo envía `PENDIENTE`). |
| B6 | §10.2: API en Java 21 | El módulo compila con `release 17` | Sin cambio: un jar Java 17 corre en Java 21. Solo dejarlo anotado. |

## C. El algoritmo frente a la base de datos: ¿hay que modificar el código?

### C1. `bolsa_articulo` (una fila por artículo y día): **no hace falta modificar el código**

`PlanificadorSemanal.planificar` solo devuelve `ResultadoPlanificacion`; la instancia y la solución quedan internas, y la salida no trae el saldo diario de cada bolsa. Aun así, el backend puede reconstruirlo **exactamente** con datos que ya tiene:

- `kg_saldo_inicial` del lunes = el `StockArticulo` que el backend envió.
- `kg_ingresados` del día = suma de la proyección cuya `RegistroProyeccion.fechaDisponible(fechaTejido)` cae ese día (se usa el **mismo método público del algoritmo**; la regla no se duplica).
- `kg_consumidos` del día = suma de los componentes de las partidas cuyo `AsignacionPartida.fechaDisponible` (= día de conformación) es ese día.
- Control: el saldo final del domingo debe coincidir con `EstadoSiguiente.stockFinal`; si no coincide, la corrida se trata como fallo (RN-19).

Alternativa: agregar al módulo una salida aditiva (`BolsaDiaria`) sin tocar la lógica. Implica modificar el código del R2.3, así que no la recomiendo.

### C2. Re-planificación (HU-18, §9.5): **sí exige modificar el algoritmo**

El código planifica siempre una semana completa desde el lunes. Para conservar lo ubicado hasta D-1 hay que darle al ACO una carga inicial fija como dato de entrada adicional. Eso cambia el contrato de entrada y la construcción de la instancia y del calendario. El `.md` pide no hacerlo sin confirmación.

### C3. Otros puntos del contrato que el backend resuelve sin tocar el código

- `orden_secuencia`: el algoritmo ya ordena las asignaciones por máquina, día, `ASIGNADA` antes que `SOBRANTE` y orden de conformación, que es el mismo orden que usa para contar lavados (RN-15). El backend toma la posición dentro de (máquina, día).
- `Indicadores.incumplimientos` no vacío → fallo, sin guardar nada (RN-19, §9.3).
- `PartidaBloqueada.motivo` es texto libre. El backend lo traduce a la clasificación `BLOQUEADA` según §9.3 (`TELA_PRIORIZADA` y `SIN_TELA` con algún kilo en la semana).

## D. Inconsistencias entre el frontend existente y el `.md`

El frontend se construyó con `FRONT/Especificacion/CLAUDE (1).md`, que es **anterior** a `SPATS_contexto_sistema.md` y lo contradice en varios puntos.

| # | Frontend actual | `.md` (25/10/2026) |
|---|---|---|
| D1 | 5 tipos de incidencia (incluye Retraso de tejeduría y Falta de receta). "Cambio de prioridad" afecta a una **partida** con "Adelantar/Postergar". | 3 tipos. `CAMBIO_PRIORIDAD` afecta a un **pedido** con `nueva_fecha_entrega`. |
| D2 | El calendario ubica bloques con `horaInicio`/`horaFin`, y la exportación incluye horas. | Sin horas: cola por máquina y día con `orden_secuencia`. |
| D3 | Botón "Marcar como vigente" y paso "Publicar" en el modo guiado. | Cada generación exitosa pasa a vigente (vacío 8). |
| D4 | Botón "Limpiar planificación" (borra el plan). | No existe: hay versiones, no borrado. |
| D5 | Historial: "Volver a versión anterior". | RF57 solo pide consultar y comparar. |
| D6 | Parámetros "horizonte congelado" y "tiempo de lavado" en horas. | La re-planificación conserva hasta D-1; no hay horas. |
| D7 | Máquina con `restriccionTonalidad`, sin `barca`. Partida con `maquinaCompatible`. | La compatibilidad es por **barca**. |
| D8 | `MotivoBloqueo` con 3 literales (Composición incompleta, Sin proyección, Sin máquina compatible). | Bloqueada = la tela no alcanzó (§6.2); `SIN_MAQUINA` es un vacío. |
| D9 | Sobrante con `clasificacion: 'Color' \| 'Blanco' \| 'Medio'` (mezcla clasificación y tonalidad). | Clasificación C/B y tonalidad son campos distintos. |
| D10 | Datos registrados permite **eliminar** pedidos, artículos, partidas y proyección. | RF13 solo pide modificar. |
| D11 | Hay archivos de páginas `EscenariosPage`, `ReportesPage`, `ParametrosPage` y `ProyeccionDisponibilidadPage` sin ruta. | Escenarios y Reportes quedan fuera del alcance. |
| D12 | Sugerencias de tejido (RF30) con datos simulados. | Vacío 2: no hay regla ni tabla. |
| D13 | Cuentas y contraseñas en `mockData.ts`; arrastradas y cierre en `localStorage`. | Todo en la base, con autenticación en la API. |

## E. Vacíos del `.md` (§13) que afectan la primera entrega

Para cada uno propongo un comportamiento por defecto. No lo aplico hasta que la autora lo confirme:

| Vacío | Propuesta por defecto |
|---|---|
| 3. `SIN_MAQUINA` | No se registra en la planificación (sigue `PENDIENTE`) y se muestra como advertencia. |
| 5. Indisponibilidad de parte de un día | Si el intervalo toca un día, ese día cuenta como indisponible. |
| 6. Dominios sin enumerar | `usuario.estado`: ACTIVO/INACTIVO · `pedido.estado`: PENDIENTE/COMPLETADO · `planificacion_semanal.estado`: ABIERTA/CERRADA · `proyeccion_tejeduria.estado`: REGISTRADA · `maquina_tintoreria.estado`: OPERATIVA/INDISPONIBLE/EN_MANTENIMIENTO · `incidencia.estado`: PENDIENTE/CONSIDERADA · `bitacora_auditoria.tipo_operacion`: CREAR/MODIFICAR/IMPORTAR/GENERAR/CERRAR/COMPLETAR/INICIAR_SESION. |
| 7. Cambio de prioridad | Se guarda solo en la incidencia, sin tocar `pedido.fecha_entrega`. |
| 8. Volver a generar la inicial | Se crea una versión nueva `INICIAL`, que pasa a vigente. |
| 9. Primera semana | Stock inicial 0. |
| 10. Planificar sin cerrar la anterior | Se permite y se excluyen las Asignadas/Sobrantes de la vigente sin cerrar (§9.2). |
| 2. Recomendaciones RF30 | Fuera de la primera entrega. |
