# Plan de pruebas del algoritmo (R2.3)

El plan comprende 34 casos: 28 pruebas unitarias (PU) y 6 pruebas de integración (PI). Cada caso corresponde a un método de prueba JUnit 5 cuyo `@DisplayName` empieza con su código, por lo que el reporte de `mvn test` muestra el resultado de cada uno.

Las pruebas unitarias usan semanas pequeñas construidas con `DatosPrueba` (semana del lunes 14/09/2026), cuyo resultado esperado se calculó a mano. Las pruebas de integración usan los libros de `datos/` y los valores de referencia de la experimentación del R2.2 (`src/test/resources/referencia_r22.csv`).

## Incrementos

| Incremento | Alcance | Pruebas |
|---|---|---|
| 1. Datos de entrada y construcción de la semana | Representación de máquinas, pedidos, partidas, componentes, proyección de tejeduría, tela en almacén e indisponibilidades; cálculo de la fecha de disponibilidad, del objetivo efectivo y del mínimo y máximo diario; acumulación de la tela por artículo e identificación de partidas candidatas y bloqueadas. | PU-01, PU-02, PU-03, PU-04, PU-05 |
| 2. Simulación de la conformación | Conformación día a día de partidas completas, conservación de la tela que no alcanza, prioridad por fecha de entrega y consulta al algoritmo para elegir entre partidas empatadas y entre las máquinas de la barca. | PU-06, PU-07, PU-08, PU-09, PU-10 |
| 3. Calendario y función objetivo | Distribución de 2 o 3 partidas completas por día, clasificación de Sobrantes, desbalance semanal, desbalance diario y lavados. | PU-11, PU-12, PU-13, PU-14, PU-15, PU-16 |
| 4. Validador independiente | Verificación, sobre cualquier solución, de las reglas presentadas en la Tabla 40. | PU-17, PU-18, PU-19, PU-20, PU-21 |
| 5. Colonia de hormigas | Construcción de semanas por las hormigas, feromona, información heurística, evaporación, depósito y condiciones de término. | PU-22, PU-23, PU-24, PU-25, PU-26, PU-27 |
| 6. Punto de entrada y datos de prueba | Planificador semanal, resultado expresado en partidas, máquinas y días, memoria para la semana siguiente y lectura y escritura de libros de Excel. | PU-28, PI-01, PI-02, PI-03, PI-04, PI-05, PI-06 |

## Casos de prueba

| Código | Caso | Resultado esperado | Trazabilidad | Clase de prueba |
|---|---|---|---|---|
| PU-01 | Fecha de disponibilidad de la tela según el día de tejido | Lo tejido de lunes a viernes queda disponible al día siguiente y lo tejido el sábado o domingo, el lunes. | RD-08, RF24 | `nucleo/ConstructorInstanciaTest` |
| PU-02 | Acumulación de la tela por artículo | La tela de un mismo artículo tejida para distintos pedidos forma una sola reserva que parte del stock en almacén y recibe las llegadas de cada día. | RD-03, RF21 | `nucleo/ConstructorInstanciaTest` |
| PU-03 | Objetivo semanal con un día indisponible | La máquina indisponible el miércoles tiene objetivo 14, mínimo 2 y máximo 3 partidas por día y seis días hábiles; la otra conserva el objetivo 16. | RD-01, RF16, RF27 | `nucleo/ConstructorInstanciaTest` |
| PU-04 | Partidas candidatas y bloqueadas | Solo es candidata la partida con tela suficiente en la semana y máquina de su barca; las demás pendientes quedan bloqueadas con su motivo y las no pendientes no se evalúan. | RF21, RF34, RF47 | `nucleo/ConstructorInstanciaTest` |
| PU-05 | Rechazo de datos inválidos | Una semana que no inicia en lunes, una partida repetida o la ausencia de máquinas se rechazan con un mensaje. | RF09 | `nucleo/ConstructorInstanciaTest` |
| PU-06 | Conformación completa y memoria de tela | Con 600 kg y tres partidas de 500 kg se conforma una el lunes y se guardan 100 kg; al llegar 400 kg el miércoles se conforma la segunda. | RD-03, RD-05, RF22 | `nucleo/SimuladorConformacionTest` |
| PU-07 | Partida con complemento | La partida se conforma el jueves, cuando llega la tela del complemento, y no antes. | RD-05, RF21 | `nucleo/SimuladorConformacionTest` |
| PU-08 | Prioridad por fecha de entrega | Se conforma la partida del pedido con fecha de entrega más próxima sin consultar al algoritmo. | RD-07, RF23 | `nucleo/SimuladorConformacionTest` |
| PU-09 | Elección entre partidas con la misma fecha de entrega | La partida que recibe la tela depende de la elección del algoritmo. | RF23, RF36 | `nucleo/SimuladorConformacionTest` |
| PU-10 | Máquina de la barca | La partida se asigna a una máquina de su barca y el algoritmo solo es consultado cuando la barca tiene más de una máquina. | RD-02, RF25 | `nucleo/SimuladorConformacionTest` |
| PU-11 | Distribución diaria | Con 16 partidas disponibles el lunes, la máquina recibe 3, 3, 2, 2, 2, 2 y 2 partidas. | RD-01, RD-08, RF27 | `nucleo/ConstructorCalendarioTest` |
| PU-12 | Sobrantes por exceso del objetivo | Con 18 partidas, las dos últimas en llegar son Sobrantes y se ubican en su primer día disponible. | RF32, RF35 | `nucleo/ConstructorCalendarioTest` |
| PU-13 | Día indisponible en el calendario | Con 14 partidas y el miércoles indisponible, la máquina recibe 3, 3, 0, 2, 2, 2 y 2 partidas. | RF16, RF27 | `nucleo/ConstructorCalendarioTest` |
| PU-14 | Llegada tardía de la tela | Con la tela disponible desde el viernes, la máquina recibe 3 partidas de viernes a domingo y las 7 restantes son Sobrantes del viernes. | RF24, RF35 | `nucleo/ConstructorCalendarioTest` |
| PU-15 | Desbalance semanal y diario | Con 10 partidas y objetivo 16, el desbalance semanal es 36, el diario es 12 y la función objetivo es 48. | RF27, RF29, RF36 | `nucleo/FuncionObjetivoTest` |
| PU-16 | Lavados por cambio de tonalidad | El paso de Oscuro a Claro se cuenta como lavado y se marca en la partida; el paso de Claro a Medio no. | RD-06, RF26 | `nucleo/FuncionObjetivoTest` |
| PU-17 | Validación de una solución factible | El validador no registra incumplimientos en una solución construida por el simulador. | Tabla 40 | `nucleo/ValidadorTest` |
| PU-18 | Máquina fuera de la barca | El validador detecta la partida asignada a una máquina de otra barca. | RD-02, RF25 | `nucleo/ValidadorTest` |
| PU-19 | Prioridad incumplida | El validador detecta la partida conformada antes que otra con fecha de entrega más próxima. | RD-07, RF23 | `nucleo/ValidadorTest` |
| PU-20 | Tela insuficiente | El validador detecta la partida conformada sin tela suficiente para completarla. | RD-03, RF22 | `nucleo/ValidadorTest` |
| PU-21 | Partida completable sin conformar | El validador detecta la partida que la tela permitía completar y quedó fuera de la planificación. | RF32 | `nucleo/ValidadorTest` |
| PU-22 | Parámetros inválidos de la colonia | Se rechazan valores no admisibles de hormigas, fracción de feromona conservada, feromona inicial, evaluaciones y tiempo máximo. | R2.2 | `aco/ColoniaHormigasTest` |
| PU-23 | Reproducibilidad | Dos ejecuciones con la misma semilla devuelven la misma solución. | R2.2 | `aco/ColoniaHormigasTest` |
| PU-24 | Número de evaluaciones | Con 45 evaluaciones y 20 hormigas por iteración se realizan 45 evaluaciones en 3 iteraciones. | R2.2 | `aco/ColoniaHormigasTest` |
| PU-25 | Evaporación y depósito de feromona | La feromona de todos los pares se reduce a 0.45 y el par usado por la mejor hormiga recibe además 0.01. | Fidanova et al. (2025) | `aco/ColoniaHormigasTest` |
| PU-26 | Tiempo máximo de ejecución | La búsqueda se detiene al alcanzar el tiempo máximo y entrega una solución factible. | RNF03 | `aco/ColoniaHormigasTest` |
| PU-27 | Reparto de la carga en una barca | Ocho partidas de una barca con dos máquinas se reparten en 4 y 4. | RF27, RF36 | `aco/ColoniaHormigasTest` |
| PU-28 | Planificación de una semana pequeña | Se conforman cuatro partidas, cada una en una máquina de su barca; las bloqueadas se informan con su motivo y la tela no utilizada pasa a la semana siguiente. | RF25, RF29, RF34, RF37 | `PlanificadorSemanalTest` |
| PI-01 | Lectura de los datos desde Excel | Se leen las máquinas, pedidos, partidas, componentes, proyección, stock, indisponibilidades y parámetros de las instancias. | RF08, RF09 | `excel/LecturaEscrituraExcelTest` |
| PI-02 | Escritura del resultado en Excel | El libro de resultados contiene sus seis hojas y la misma información que la planificación generada. | RF31, RF41 | `excel/LecturaEscrituraExcelTest` |
| PI-03 | Correspondencia con el modelo del R2.2 | La regla de referencia obtiene en las doce instancias la misma función objetivo y componentes que en el R2.2. | R2.2 | `excel/InstanciasSemanalesTest` |
| PI-04 | Correspondencia con las corridas del R2.2 | Con las mismas semillas, la colonia de hormigas obtiene las mismas soluciones que en 24 corridas del R2.2. | R2.2 | `excel/InstanciasSemanalesTest` |
| PI-05 | Factibilidad, tiempo y calidad en las doce instancias | Todas las soluciones son factibles, se obtienen en menos de 60 segundos y su función objetivo es menor o igual que la de la regla de referencia. | Tabla 40, RF28, RNF03 | `excel/InstanciasSemanalesTest` |
| PI-06 | Encadenamiento de semanas | Las semanas 37, 38 y 39, planificadas con la memoria de la anterior, son factibles y ninguna partida se asigna dos veces. | RD-03, RF22, RF47 | `excel/InstanciasSemanalesTest` |

Trazabilidad: RD = reglas de decisión del R1.1; RF y RNF = requisitos del R3.1; Tabla 40 = reglas que debe cumplir una alternativa (R2.2).
