# Informe comparativo: regla de calendario anterior vs. regla nueva (D-48)

Fecha: 2026-11-08. Solo resultados, sin interpretación.

- Regla anterior: reparto 3, 3, 2, 2, 2, 2, 2 (resultados en `R_experimentacion/resultados_regla_anterior/`).
- Regla nueva: reparto proporcional y escalonado de D-48, la misma del sistema (resultados en `R_experimentacion/resultados/`).
- Se mantienen los datos, las instancias, las semillas, el presupuesto (6000 evaluaciones), los algoritmos, la función objetivo, el diseño de calibración y las pruebas estadísticas.

## Verificaciones

- R y Java dan la misma F con la regla voraz en las 12 instancias.
- Las soluciones son factibles según el validador: 480 de 480 corridas en R y 480 de 480 en Java.
- En `spats-algoritmo`, PI-03 y PI-04 pasan con la nueva línea base (`src/test/resources/referencia_r22.csv`): el sistema da la misma F que la experimentación con la misma semilla. `mvn test`: 56 pruebas, 0 fallos y 0 omitidas.

## Parámetros elegidos en la calibración

| Algoritmo | Anterior | Nueva |
|---|---|---|
| AG | población 50, p_mutación 0,3, elitismo 0,10 (p_cruce 0,90) | **población 30**, p_mutación 0,3, elitismo 0,10 (p_cruce 0,90) |
| ACO | hormigas 20, rho 0,9, b 2 (tau0 0,5, a 1) | hormigas 20, rho 0,9, b 2 (tau0 0,5, a 1), sin cambios |

Mejor F promedio de la calibración: en el AG pasó de 347,35 a 318,60 y en el ACO, de 293,20 a 265,95.

## Regla voraz (F)

| Instancia | Anterior | Nueva |
|---|---|---|
| S37-B | 1387,00 | 1382,00 |
| S37-I | 1331,75 | 1326,75 |
| S37-R | 1526,75 | 1523,75 |
| S37-IR | 1471,50 | 1468,50 |
| S38-B | 337,25 | 329,00 |
| S38-I | 341,25 | 332,75 |
| S38-R | 340,75 | 329,50 |
| S38-IR | 336,75 | 327,25 |
| S39-B | 1864,75 | 1857,00 |
| S39-I | 1804,75 | 1797,00 |
| S39-R | 1870,00 | 1857,00 |
| S39-IR | 1810,00 | 1797,00 |

## AG por instancia (F promedio / mejor F / desviación estándar / RPD %)

| Instancia | Anterior | Nueva |
|---|---|---|
| S37-B | 1293,05 / 1280,75 / 9,13 / 1,14 | 1287,44 / 1273,50 / 8,47 / 1,25 |
| S37-I | 1244,53 / 1233,25 / 5,57 / 1,16 | 1242,08 / 1227,25 / 10,61 / 1,46 |
| S37-R | 1474,63 / 1467,50 / 5,90 / 0,49 | 1467,35 / 1460,50 / 6,11 / 0,52 |
| S37-IR | 1423,38 / 1415,25 / 4,34 / 0,57 | 1416,11 / 1408,00 / 6,95 / 0,59 |
| S38-B | 235,51 / 209,25 / 13,72 / 31,57 | 210,26 / 194,00 / 11,74 / 34,57 |
| S38-I | 222,24 / 197,25 / 14,48 / 34,28 | 206,16 / 180,00 / 12,93 / 41,94 |
| S38-R | 252,64 / 231,25 / 11,67 / 27,43 | 231,63 / 207,25 / 9,49 / 33,89 |
| S38-IR | 245,15 / 228,25 / 10,42 / 33,05 | 223,76 / 188,50 / 14,72 / 38,55 |
| S39-B | 1766,94 / 1765,50 / 1,04 / 0,10 | 1760,04 / 1760,00 / 0,09 / 0,002 |
| S39-I | 1705,13 / 1703,50 / 1,11 / 0,10 | 1698,28 / 1698,25 / 0,08 / 0,001 |
| S39-R | 1767,76 / 1767,25 / 0,53 / 0,03 | 1760,05 / 1760,00 / 0,10 / 0,003 |
| S39-IR | 1707,94 / 1707,25 / 0,65 / 0,04 | 1698,39 / 1698,25 / 0,45 / 0,008 |

## ACO por instancia (F promedio / mejor F / desviación estándar / RPD %) y pruebas

| Instancia | ACO anterior | ACO nueva | p Mann-Whitney (anterior → nueva) | A12 del AG (anterior → nueva) | Resultado (anterior → nueva) |
|---|---|---|---|---|---|
| S37-B | 1279,60 / 1278,50 / 0,80 / 0,09 | 1272,84 / 1271,50 / 0,81 / 0,11 | 9,6e-8 → 1,9e-7 | 0,006 → 0,018 | Gana ACO → Gana ACO |
| S37-I | 1231,28 / 1230,25 / 0,67 / 0,08 | 1225,13 / 1224,25 / 0,55 / 0,07 | 6,5e-8 → 6,5e-8 | 0,000 → 0,000 | Gana ACO → Gana ACO |
| S37-R | 1470,34 / 1470,00 / 0,30 / 0,19 | 1460,36 / 1459,75 / 0,24 / 0,04 | 0,033 → 7,2e-7 | 0,304 → 0,048 | Gana ACO → Gana ACO |
| S37-IR | 1418,49 / 1417,75 / 0,36 / 0,23 | 1408,33 / 1407,75 / 0,29 / 0,04 | 1,5e-4 → 2,9e-5 | 0,150 → 0,115 | Gana ACO → Gana ACO |
| S38-B | 182,09 / 179,00 / 2,12 / 1,72 | 159,61 / 156,25 / 2,18 / 2,15 | 6,7e-8 → 6,7e-8 | 0 → 0 | Gana ACO → Gana ACO |
| S38-I | 170,29 / 165,50 / 2,06 / 2,89 | 148,54 / 145,25 / 1,61 / 2,26 | 6,6e-8 → 6,7e-8 | 0 → 0 | Gana ACO → Gana ACO |
| S38-R | 203,09 / 198,25 / 2,38 / 2,44 | 178,14 / 173,00 / 2,20 / 2,97 | 6,7e-8 → 6,7e-8 | 0 → 0 | Gana ACO → Gana ACO |
| S38-IR | 189,58 / 184,25 / 2,29 / 2,89 | 167,45 / 161,50 / 2,58 / 3,68 | 6,7e-8 → 6,8e-8 | 0 → 0 | Gana ACO → Gana ACO |
| S39-B | 1765,95 / 1765,25 / 0,38 / 0,04 | 1760,00 / 1760,00 / 0,00 / 0,00 | 2,9e-4 → 0,080 | 0,171 → 0,425 | Gana ACO → Sin diferencia significativa |
| S39-I | 1704,40 / 1703,50 / 0,40 / 0,05 | 1698,25 / 1698,25 / 0,00 / 0,00 | 0,019 → 0,162 | 0,285 → 0,450 | Gana ACO → Sin diferencia significativa |
| S39-R | 1767,43 / 1767,25 / 0,14 / 0,01 | 1760,00 / 1760,00 / 0,00 / 0,00 | 1,0e-3 → 0,040 | 0,224 → 0,400 | Gana ACO → Gana ACO |
| S39-IR | 1707,45 / 1707,25 / 0,13 / 0,01 | 1698,26 / 1698,25 / 0,06 / 0,001 | 4,7e-4 → 0,157 | 0,195 → 0,424 | Gana ACO → Sin diferencia significativa |

## Resultados globales

| Indicador | Anterior | Nueva |
|---|---|---|
| Wilcoxon global (ACO vs. AG) | V = 0, p = 0,00253 | V = 0, p = 0,00253 |
| F promedio: ACO / AG | 1090,83 / 1111,57 | 1078,08 / 1100,13 |
| Mejora del ACO frente al AG en F promedio | 1,87 % | 2,00 % |
| Mejora del ACO frente al AG en RPD promedio | 91,80 % (0,89 vs. 10,83) | 92,58 % (0,94 vs. 12,73) |
| Mejora del ACO frente al AG en desviación promedio | 84,69 % (1,00 vs. 6,55) | 87,14 % (0,88 vs. 6,81) |
| Reducción promedio de F frente a la voraz: ACO | 18,73 % | 20,57 % |
| Reducción promedio de F frente a la voraz: AG | 13,33 % | 14,78 % |

## Tiempos en Java (480 corridas por regla)

| Algoritmo | Promedio anterior | Promedio nuevo | Máximo anterior | Máximo nuevo |
|---|---|---|---|---|
| ACO | 733 ms | 891 ms | 2986 ms | 2087 ms |
| AG | 505 ms | 573 ms | 2611 ms | 1336 ms |

Nota: el tiempo promedio por corrida en R subió de 17,9 a 35,1 s en el ACO y de 13,6 a 29,8 s en el AG.

## Conclusión del R2.2

- El ACO tiene la mejor F promedio en las 12 instancias, con las dos reglas.
- El Wilcoxon global es significativo con las dos reglas (p = 0,00253).
- Criterio del 10 % frente al AG: se cumple en los mismos indicadores que antes, RPD (92,58 %) y desviación (87,14 %). En F promedio no se cumplía antes (1,87 %) ni ahora (2,00 %).
- Diferencia por instancia: «Gana ACO» en 12 de 12 con la regla anterior y en 9 de 12 con la nueva. En S39-B, S39-I y S39-IR, la diferencia no es significativa.

## S38-B: total de partidas por día en toda la planta (mejor solución)

En ninguna de las cuatro soluciones hay Sobrantes.

| Solución | Lun | Mar | Mié | Jue | Vie | Sáb | Dom | Total | Diferencia entre el día con más y el día con menos |
|---|---|---|---|---|---|---|---|---|---|
| ACO, regla anterior | 19 | 20 | 15 | 17 | 19 | 19 | 6 | 115 | 14 |
| ACO, regla nueva | 14 | 14 | 16 | 18 | 18 | 16 | 18 | 114 | 4 |
| AG, regla anterior | 18 | 20 | 16 | 16 | 19 | 20 | 6 | 115 | 14 |
| AG, regla nueva | 15 | 16 | 15 | 16 | 18 | 16 | 16 | 112 | 3 |

## Archivos de origen

- `R_experimentacion/resultados/` y `R_experimentacion/resultados_regla_anterior/`: `resumen_por_instancia.csv`, `indicadores_globales.csv`, `calibracion_*.csv`, `calendario_S38-B_*.csv`, `voraz.csv`.
- `R_experimentacion/resultados/java/` y `R_experimentacion/resultados_regla_anterior/java/`: `corridas_java.csv` y `voraz_java.csv`.
- La regla nueva en Java se ejecutó con `java -jar tintoreria.jar experimento ../R_experimentacion/instancias/csv ../R_experimentacion/resultados/java 20 6000 ag=30,0.10,0.90,0.30 aco=20,0.9,0.5,1,2`.
