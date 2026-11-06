# Experimentación numérica del R2.2 — asignación de partidas a máquinas de tintorería

Código en R (procesamiento de datos, experimentación y análisis) y en Java (misma lógica, para comprobar la ejecución en el lenguaje del sistema y medir el tiempo frente al RNF03).

## Cómo ejecutarlo en RStudio

1. Instalar los paquetes una sola vez: `install.packages(c("readxl", "ggplot2"))`.
2. Abrir la carpeta `R_experimentacion` como directorio de trabajo (`Session > Set Working Directory`).
3. Ejecutar `source("ejecutar_todo.R", encoding = "UTF-8")`.

El script corre en paralelo con todos los núcleos menos uno. Cada corrida se guarda en `resultados/corridas/` apenas termina, así que si se interrumpe se retoma sin perder lo avanzado. Para una prueba rápida, baja `CONFIG$corridas` y `CONFIG$evaluaciones` en `R/00_configuracion.R`.

## Resultados incluidos

`R_experimentacion/resultados/` trae la corrida completa con la que se redactó el R2.2: `corridas.csv` (480 corridas), `voraz.csv`, `resumen_por_instancia.csv`, `indicadores_globales.csv`, `calibracion_*.csv`, `convergencia.csv`, las figuras y los calendarios de la mejor semana 38. En `resultados/java/` están los tiempos de las mismas corridas en Java. Si vuelves a ejecutar `ejecutar_todo.R`, borra antes `resultados/corridas/` y `resultados/calibracion.rds`; si no, el script reutiliza lo ya calculado.

## Qué hace cada archivo

| Archivo | Contenido |
|---|---|
| `R/00_configuracion.R` | Parámetros: máquinas (barcas), objetivo semanal, pesos de la función objetivo, presupuesto, parámetros del AG y del ACO |
| `R/01_preparar_datos.R` | Lee `PARTIDAS.xlsx`, `PROG_SETIEMBRE.xlsx` y `PROG_TEJIDO.xlsx` y arma pedidos, partidas, componentes y proyección |
| `R/02_instancias.R` | Construye una semana (bolsas de tela, partidas candidatas y bloqueadas) y la memoria que pasa a la semana siguiente |
| `R/03_modelo.R` | Simulación día a día de la conformación, calendario 2–3 partidas por día, función objetivo y validador |
| `R/04_algoritmos.R` | Regla voraz (línea base), algoritmo genético y colonia de hormigas |
| `R/05_construir_instancias.R` | Semanas S37, S38 y S39 con las condiciones B, I, R e IR |
| `R/06_experimento.R` | 20 corridas por algoritmo e instancia con el mismo presupuesto |
| `R/07_calibracion.R` | Diseño factorial 2^3 para los parámetros de cada algoritmo |
| `R/08_exportar_java.R` | Exporta las instancias a CSV para la versión en Java |
| `R/09_caracterizacion.R` | Tamaño del problema (bolsas con competencia, espacio de búsqueda) |
| `R/10_analisis.R` | RPD, Mann-Whitney, A12, Wilcoxon y figuras |

## Lógica del modelo

**Tela acumulada por artículo, con memoria.** La tela de cada artículo forma una "bolsa", sin importar para qué pedido se tejió, porque un mismo artículo puede abastecer partidas de distintos pedidos. Cada día llega a la bolsa lo que tejeduría dejó disponible: lo tejido de lunes a viernes queda disponible al día siguiente y lo del sábado, el lunes. Si la bolsa alcanza para completar una partida entera (con su complemento, que puede venir de otro artículo), se le asigna toda su tela de una vez. Lo que no alcanza queda guardado en la bolsa y se suma a la tela de los días siguientes. Por ejemplo, con 600 kg se completa una partida de 500 y quedan 100 kg guardados; si al día siguiente llegan 400 kg, la bolsa tiene 500 y vuelve a decidirse qué partida los recibe.

**Primera decisión: qué partida recibe la tela.** Entre las partidas que la tela acumulada permite completar enteras, primero va la de fecha de entrega más próxima. Entre las que tienen la misma fecha, sean del mismo pedido o de pedidos distintos, decide el algoritmo, y cada elección produce un escenario distinto que se evalúa con la semana completa. Para restringir la tela al pedido para el que se tejió, cambia `agrupar_tela_por` a `"pedido_articulo"` en `R/00_configuracion.R`.

**Segunda decisión: a qué máquina va.** Cada partida tiene su barca (por ejemplo, `400 C`: máquina de 400 kg para color). Entre las máquinas de esa barca decide el algoritmo. Ambas decisiones se evalúan juntas, porque la máquina elegida cambia el balance y el balance decide qué conformación conviene.

**Calendario.** Con objetivo de 16 partidas en 7 días, cada máquina recibe 2 o 3 partidas completas por día (3, 3, 2, 2, 2, 2, 2). Si un día hay más partidas disponibles que espacio, pasan al día siguiente más cercano. Las que exceden el objetivo semanal se muestran como Sobrante en su primer día disponible. Una máquina indisponible un día baja su objetivo a 14.

**Función objetivo** (menor es mejor): `F = 1·E_S + 1·E_D + 0.25·Λ`, donde:

- `E_S`: desequilibrio semanal (cuadrado de la diferencia entre las partidas de cada máquina y su objetivo).
- `E_D`: desequilibrio diario (cuadrado de lo que falta para 2 o sobra de 3 cada día).
- `Λ`: pasos de una tonalidad oscura a una más clara, que requieren lavado.

**Memoria entre semanas.** S38 parte del estado que deja la planificación de S37, y S39 del que deja S38: la tela no usada de cada artículo y las partidas pendientes. Una partida nunca se llena a medias: solo recibe tela cuando la tela acumulada alcanza para completarla entera.

## Versión en Java

Requiere Java 17 o superior. En la carpeta `java/` (junto a `R_experimentacion/`) está el código fuente (`src/main/java/tintoreria`) y el ejecutable `tintoreria.jar`. Primero corre `ejecutar_todo.R` (o al menos los pasos 1 y 2), que exporta las instancias a `instancias/csv`. Luego:

```
java -jar tintoreria.jar experimento ../R_experimentacion/instancias/csv salida_java 20 6000
java -jar tintoreria.jar planificar ../R_experimentacion/instancias/csv/S38-B
```

El primer comando ejecuta la regla voraz y las 20 corridas de cada algoritmo con los parámetros calibrados, y escribe `voraz_java.csv` y `corridas_java.csv` con el tiempo de cada corrida. El segundo muestra el calendario de la mejor semana encontrada por la colonia de hormigas.

Para recompilar: `javac -encoding UTF-8 -d clases src/main/java/tintoreria/*.java`.

La regla voraz es determinista y da exactamente el mismo valor de F en R y en Java para las 12 instancias. Los generadores aleatorios de R y de Java son distintos, así que las corridas del AG y del ACO no coinciden una a una, aunque siguen la misma lógica.
