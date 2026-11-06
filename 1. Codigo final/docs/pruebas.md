# Pruebas

## Backend

```bash
cd backend
mvn test        # algoritmo (34 + 20) + API
```

| Prueba | Qué verifica |
|---|---|
| Las 34 de `spats-algoritmo` | Las pruebas originales del R2.3 (unitarias e integración con los 12 libros). |
| `ReplanificacionTest` (módulo, 20) | PU-29: con D = lunes y sin datos fijos, en los 12 libros, mismo resultado que la planificación completa y `evaluar` da la misma F. PU-30 a PU-37: lo anterior a D queda igual, partidas de hoy ya en máquina, incidencia de máquina, complemento (dentro y fuera de la semana), barca sin máquina, cambio de prioridad y las tres juntas. Además verifica que la tela cuadra. |
| `ReplanificacionMotorTest` (backend, 12) | Pruebas 1 a 5 de la propuesta, con el mismo camino del backend (armado → algoritmo → filas → RF55) sobre la semana 38: misma salida con D = lunes, filas anteriores a D idénticas, tela sin doble consumo y `kg_devueltos`, cada incidencia, complemento sobre una fija rechazado, y RF55 (vigente no factible → propuesta; sin mejora → se conserva). |
| `RegresionInstanciasR22Test` (12 casos) | Regresión de §8.5: mismo F que el R2.3 con semilla 1 en las 12 instancias, sin incumplimientos. La traducción a filas cumple las restricciones de `BD.sql` y el saldo del domingo coincide con `stockFinal`. |
| `TraductorResultadoTest` | Regla de Bloqueada (§6.2), aviso `SIN_MAQUINA` (D-12), reconstrucción de la bolsa día a día, regla de disponibilidad (sábado → lunes), `orden_secuencia` y rechazo de una solución no factible. |
| `DominioComunTest` | Semana lunes a domingo, ventana de la proyección, indisponibilidad parcial (D-08) y barca (D-07). |
| `PermisosPorPerfilTest` | 401 sin sesión, Consulta lee pero recibe 403 al modificar, Planificador modifica, 400 con errores por campo. |
| `FlujoPlanificacionIntegracionTest` | Flujo completo contra **PostgreSQL** (ver abajo). |
| `ReplanificacionIntegracionTest` | Prueba 6 contra **PostgreSQL**, con el reloj en el miércoles 16/09/2026: proponer, bloqueos mientras hay propuesta, descartar (sin filas, incidencias PENDIENTE) y confirmar (vigente nueva, REEMPLAZADA, CONSIDERADA, `fecha_entrega` y bitácora). |

### Prueba de integración con PostgreSQL

Corre solo si están definidas las variables de entorno. Usa una base **de prueba**, porque ejecuta `BD.sql`, que elimina las tablas:

```powershell
createdb -U postgres tintoreria_test
$env:SPATS_TEST_DB_URL="jdbc:postgresql://localhost:5432/tintoreria_test"
$env:SPATS_TEST_DB_USUARIO="postgres"
$env:SPATS_TEST_DB_CONTRASENA="<contraseña>"
mvn -pl spats-api -am test "-Dtest=FlujoPlanificacionIntegracionTest,ReplanificacionIntegracionTest" -Dsurefire.failIfNoSpecifiedTests=false
```

Recorre el flujo completo:

1. Crea la base y carga las semanas 37 y 38 del R2.2.
2. Genera la 37 desde la base, con el mismo F que el R2.3 (1279.75).
3. Genera la 38 encadenada con el saldo de las bolsas.
4. Vuelve a generar la 37: hay versiones y queda una sola vigente.
5. Carga una proyección nueva de la 38: exige confirmación y deja su planificación sin vigencia.
6. Cierra la 37: completadas, arrastradas y bloqueadas sin cambios.
7. Completa una arrastrada.

## Frontend

```bash
cd FRONT/frontend
npm run build   # tsc + Vite
npm run lint    # oxlint sobre src/
```
