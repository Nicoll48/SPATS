# SPATS — Algoritmo de asignación de partidas a máquinas de tintorería

Componente algorítmico del Sistema de Planificación de Abastecimiento de Tintorería (SPATS). Implementa la **optimización por colonia de hormigas** (Fidanova et al., 2025), seleccionada en la experimentación numérica del R2.2, para decidir de forma conjunta:

1. **Qué partidas conformar**: una partida solo recibe tela cuando la tela acumulada de sus artículos alcanza para completarla; los kg que no alcanzan quedan guardados. Entre las partidas que la tela puede completar, primero va la de fecha de entrega más próxima; con la misma fecha, decide el algoritmo.
2. **A qué máquina de su barca va cada partida**, buscando que las máquinas queden balanceadas semanal y diariamente (2 o 3 partidas por día para un objetivo de 16).

El componente no depende de librerías externas. El paquete `excel` es solo un adaptador de prueba: en el sistema, los datos vendrán de la base de datos.

## Requisitos

- Java 17 o superior.
- Maven (opcional; también se puede compilar con `javac`).

## Ejecutar con los libros de Excel

Cada libro de `datos/` contiene una semana de las doce instancias del R2.2: semanas 37, 38 y 39 en las condiciones base (B), máquina indisponible (I), retraso de tejeduría (R) y ambas (IR).

```bash
mvn package                                   # compila, ejecuta las pruebas y crea target/spats-algoritmo.jar
java -jar target/spats-algoritmo.jar datos/S38-B.xlsx           # una semana
java -jar target/spats-algoritmo.jar datos resultados           # todas las semanas de la carpeta
java -jar target/spats-algoritmo.jar datos/S38-B.xlsx resultados --semilla 7
```

Por cada libro se muestra el calendario en la consola y se guarda `resultados/<semana>_resultado.xlsx` con las hojas:

| Hoja | Contenido |
|---|---|
| Calendario | Partidas de cada máquina en cada día (`S:` = Sobrante, `(L)` = requiere lavado) |
| Asignaciones | Máquina, día, clasificación (Asignada o Sobrante), día disponible, orden de conformación, tonalidad y lavado de cada partida |
| Resumen por maquina | Objetivo, mínimo y máximo diario, partidas por día, Asignadas, Sobrantes y diferencia con el objetivo |
| Bloqueadas | Partidas que no pudieron conformarse y el motivo |
| Indicadores | Función objetivo, desbalance semanal y diario, lavados, evaluaciones, tiempo y validación |
| StockFinal | Tela de cada artículo que pasa a la semana siguiente |

Con una carpeta, además se escribe `resultados/resumen_ejecuciones.csv`.

### Sin Maven

```bash
javac -encoding UTF-8 -d clases $(find src/main/java -name "*.java")
java -cp clases spats.algoritmo.excel.Aplicacion datos/S38-B.xlsx
```

En Windows (PowerShell): `javac -encoding UTF-8 -d clases (Get-ChildItem -Recurse src/main/java -Filter *.java).FullName`.

## Libro de entrada

| Hoja | Columnas |
|---|---|
| Parametros | `parametro`, `valor` (fecha_inicio_semana, dias_semana, reduccion_objetivo_por_dia_indisponible, pesos de la función objetivo, hormigas, rho, feromona_inicial, peso_feromona, peso_heuristica, evaluaciones, tiempo_maximo_segundos, semilla) |
| Maquinas | `maquina`, `capacidad`, `tipo`, `barca`, `objetivo_semanal` |
| Indisponibilidad | `maquina`, `fecha`, `motivo` |
| Pedidos | `pedido`, `cliente`, `fecha_pedido`, `fecha_entrega` |
| Partidas | `partida`, `pedido`, `cod_color`, `color`, `peso`, `tonalidad` (Blanco, Claro, Medio, Oscuro), `barca`, `orden_registro`, `estado` (Pendiente, Planificada, Completada, Arrastrada) |
| Componentes | `partida`, `cod_articulo`, `articulo`, `clave_articulo`, `rol`, `kg_requeridos` |
| Proyeccion | `maquina_tejeduria`, `pedido`, `articulo`, `clave_articulo`, `fecha_tejido`, `kg` |
| StockInicial | `clave_articulo`, `articulo`, `kg` |

Lo tejido de lunes a viernes queda disponible al día siguiente y lo tejido el sábado o domingo, el lunes. Solo se evalúan las partidas en estado Pendiente.

Los libros se generan desde el proyecto de experimentación del R2.2 con `datos/generar_excel.R` (ver el encabezado del script).

## Pruebas

```bash
mvn test
```

34 casos (28 unitarios y 6 de integración). El detalle está en [`docs/plan_de_pruebas.md`](docs/plan_de_pruebas.md) y los resultados en [`docs/informe_resultados.md`](docs/informe_resultados.md). Las pruebas de integración se ejecutan desde la raíz del proyecto porque leen la carpeta `datos/`.

Desde el 01/11/2026 hay además 20 casos de re-planificación en `ReplanificacionTest` (PU-29 a PU-37).

## Uso desde el sistema

```java
DatosSemana datos = ...;                       // construido desde la base de datos
ParametrosACO aco = new ParametrosACO();       // valores calibrados en el R2.2
ResultadoPlanificacion r = new PlanificadorSemanal().planificar(datos, aco);

r.asignaciones();      // partida, máquina, día, Asignada o Sobrante, lavado
r.maquinas();          // carga por máquina y día, diferencia con el objetivo
r.bloqueadas();        // partidas que no pudieron conformarse y el motivo
r.indicadores();       // función objetivo, tiempo, factibilidad
r.estadoSiguiente();   // tela no utilizada y partidas planificadas (memoria para la semana siguiente)
```

### Re-planificación desde el día D (agregado el 01/11/2026)

```java
DatosSemana re = datos.conReplanificacion(new Replanificacion(D, fijas, conservadas, disponibilidades));
ResultadoPlanificacion r = new PlanificadorSemanal().planificar(re, aco);       // semana completa: fijas + nuevas
Indicadores vigente = new PlanificadorSemanal().evaluar(re, asignacionesVigentes); // RF55
```

- `stockInicial` es el saldo de cada bolsa al iniciar D.
- Las **fijas** no cambian.
- Las **conservadas** ya tienen su tela separada: solo se elige su máquina y su día desde D.
- Una partida con **disponibilidad** no se conforma antes de esa fecha.
- Con `replanificacion = null` (constructor de 8 argumentos), el algoritmo es exactamente el del R2.3.
- La búsqueda del ACO y la función objetivo no cambiaron.

## Estructura

```
src/main/java/spats/algoritmo/
├── PlanificadorSemanal.java      punto de entrada
├── entrada/                      datos de la semana y parámetros operativos
├── nucleo/                       construcción de la semana, simulación de la conformación,
│                                 calendario, función objetivo y validador
├── aco/                          colonia de hormigas
├── salida/                       resultado de la planificación
└── excel/                        adaptador de prueba (lectura y escritura de Excel)
```

## Función objetivo

`F = α·E_S + β·E_D + γ·Λ` (por defecto α = 1, β = 1, γ = 0.25), donde E_S es el desbalance semanal (cuadrado de la diferencia entre las partidas de cada máquina y su objetivo), E_D el desbalance diario (cuadrado de lo que falta para el mínimo o sobra del máximo diario) y Λ la cantidad de pasos de una tonalidad a otra más clara en una misma máquina. Un valor menor es mejor.
