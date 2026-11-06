# Arquitectura

Sigue el modelo C4 del R3.6 (sección 10 del contexto): una aplicación web y una API REST separadas que se comunican por JSON, y PostgreSQL como base de datos. El algoritmo vive dentro de la API como módulo Java puro.

```
Navegador (React)  ──HTTPS/JSON──►  API Spring Boot  ──JPA/JDBC──►  PostgreSQL "tintoreria"
                                        │
                                        └── spats-algoritmo (Java puro, sin Spring)
```

## Backend (`backend/`)

Agregador Maven con dos módulos:

- `../spats-algoritmo/spats-algoritmo`: el módulo del R2.3, en su carpeta original. Su único cambio es la ampliación del contrato para re-planificar desde D (01/11/2026; respaldo del original en `CONTROL_DE_VERSIONES/respaldos/`). Se compila y se prueba con sus 34 pruebas originales y 20 de re-planificación.
- `spats-api`: la API (Java 21, Spring Boot 4.1.2, Spring Data JPA, Hibernate 7, Spring Security con JWT, Apache POI). Depende de `spats-algoritmo`.

### Paquetes (por módulo de negocio y, dentro, por responsabilidad)

| Paquete | Componente C4 | Contenido |
|---|---|---|
| `comun` | — | Excepciones de dominio y manejador global de errores (`ProblemDetail`), semana de planificación, barca, lectura y escritura de `.xlsx`, reloj del negocio. |
| `seguridad` | Autenticación y autorización | Usuarios y perfiles, emisión de JWT, `SecurityFilterChain`, CORS. |
| `auditoria` | Historial y trazabilidad | `bitacora_auditoria`: quién, cuándo, qué fila y valores anterior y nuevo. |
| `pedido`, `partida`, `tejeduria`, `carga` | Gestión de datos de producción | Pedidos, artículos, partidas, colores, componentes, proyección con versiones; importación, plantillas y descargas. |
| `maquina` | Gestión de máquinas | Máquinas e indisponibilidades. |
| `planificacion` | Planificación semanal + Motor de reglas | Coordina la generación, el guardado, la consulta, el cierre y la comparación de versiones. El subpaquete `motor` es el adaptador entre la base y el algoritmo. |
| `incidencia` | Incidencias y replanificación | Registro de incidencias. La re-planificación (proponer, confirmar y descartar) está en `planificacion` (`ReplanificacionService`, `motor/*Replanificacion`). |
| `demo` | — | Perfil `demo`: carga las semanas del R2.2 en una base vacía. |

En cada módulo: `controller` (REST, sin lógica), `service` (reglas y transacciones), `repository` (Spring Data), `entity` (JPA), `dto` (records) y `mapper`.

### Generación de una planificación

`GeneracionPlanificacionService.generar(lunes)` hace tres pasos con transacciones separadas:

1. **Leer** (`LectorDatosPlanificacion`, solo lectura): arma `DatosSemana` desde la base según §9.2.
2. **Optimizar** (`EjecutorAlgoritmo`): `new PlanificadorSemanal().planificar(datos, new ParametrosACO())`, con los parámetros calibrados del R2.2, sin transacción ni conexión abierta.
3. **Traducir y guardar**:
   - `TraductorResultado` es Java puro: convierte la salida en filas y reconstruye `bolsa_articulo`.
   - `GuardadoPlanificacion` guarda en **una sola transacción**: la versión nueva pasa a vigente y la anterior queda `REEMPLAZADA`. Antes verifica que la vigente y la proyección no cambiaron desde la lectura.

Si el algoritmo falla, la solución tiene incumplimientos del validador o el saldo de las bolsas no coincide con `stockFinal`, no se guarda nada y la vigente queda intacta (RN-19).

La solicitud HTTP espera el resultado: el algoritmo tiene un tope de 60 s (RNF03) y en las semanas del R2.2 tarda de 0.1 a 1.6 s. No se permiten dos generaciones simultáneas de la misma semana (409). La base no tiene una tabla de ejecuciones, por eso no hay una cola asíncrona.

### Cambio de proyección (D-06)

Al cargar, corregir o restaurar la proyección de una semana se publica `ProyeccionPorCambiarEvent` dentro de la transacción. `InvalidacionPorProyeccion` lo escucha:

- Sin `confirmado=true`, responde 409 `CONFIRMACION_REQUERIDA` (RF59).
- Con confirmación, deja la planificación vigente abierta como `INVALIDADA`: se conserva en el historial y el calendario queda en blanco.
- Si la semana ya se cerró, rechaza el cambio.

El evento evita que el módulo de tejeduría dependa del de planificación.

### Seguridad

- Token Bearer JWT (HMAC-SHA256) firmado con `SPATS_JWT_SECRETO`. El token vence a los 30 min (`spats.seguridad.duracion-inactividad`) y el frontend lo renueva mientras hay actividad, lo que cumple RF04.
- `GET /api/**` está abierto a cualquier usuario autenticado. Toda escritura exige el rol `PLANIFICADOR` (filtro de URL y `@PreAuthorize` en generar y cerrar).
- Contraseñas con BCrypt. CORS solo para los orígenes de `SPATS_ORIGENES`.

### Persistencia

- La base se crea con `BD/BD.sql`; Hibernate solo valida (`ddl-auto=validate`).
- El script usa `SERIAL`, por eso las entidades usan `GenerationType.IDENTITY`.
- La columna generada `vigente_semana` no se mapea: la base garantiza una sola vigente por semana.
- `open-in-view=false`. Las listas se cargan con `join fetch` / `@EntityGraph` para evitar N+1.

## Frontend (`FRONT/frontend/`)

React 19 + Vite + TypeScript + Tailwind CSS v4, sobre el diseño que ya existía.

| Carpeta | Contenido |
|---|---|
| `src/api/` | `cliente.ts` (componente "Cliente de API REST": token, errores `problem+json`, descargas), `tipos.ts` (espejo de los DTO) y `servicios.ts` (una función por endpoint). |
| `src/auth/` | Sesión con la API; renovación del token por actividad y cierre por inactividad. |
| `src/experience/` | Modo Guiado o Experto, guardado en la cuenta (`usuario.modo_interaccion`). |
| `src/semana/` | Semana seleccionada (lunes), compartida por todas las pantallas. |
| `src/pages/` | Planificación semanal, Carga de datos, Datos registrados, Máquinas, Incidencias y replanificación, Historial. |
| `src/components/planning/` | Calendario por máquina y día (cola en `orden_secuencia`, sin horas), tablas de Sobrantes, Bloqueadas y Alertas, flujo guiado. |
| `_fuera_de_alcance/` | Pantallas y datos simulados que quedaron fuera del alcance (Escenarios, Reportes, parámetros en horas, mock). No se compilan. |

Las pantallas solo muestran lo guardado: no recalculan indicadores, saldos ni clasificaciones (regla 4 del contexto).
