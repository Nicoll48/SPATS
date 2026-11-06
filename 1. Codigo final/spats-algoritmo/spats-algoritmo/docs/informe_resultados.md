# Informe de resultados de validación (R2.3)

## Pruebas

Se ejecutaron los 34 casos del [plan de pruebas](plan_de_pruebas.md) sobre la versión final del código: **34 aprobados, 0 fallidos**.

Además, para comprobar que las pruebas detectan errores, se introdujeron a propósito tres fallas en copias del código (quitar la prioridad por fecha de entrega, repartir siempre el mínimo diario y contar los lavados al revés). En los tres casos al menos una prueba falló.

## Ejecución en las doce instancias

Comando: `java -jar spats-algoritmo.jar datos resultados` (parámetros por defecto: 20 hormigas, ρ = 0.90, τ0 = 0.50, a = 1, b = 2, 6 000 evaluaciones, semilla 1).

| Instancia | Conformadas | Sobrantes | Bloqueadas | F regla de referencia | F colonia de hormigas | Tiempo (s) | Factible |
|---|---|---|---|---|---|---|---|
| S37-B | 48 | 0 | 489 | 1387.00 | 1279.75 | 0.78 | Sí |
| S37-I | 48 | 0 | 489 | 1331.75 | 1232.00 | 0.51 | Sí |
| S37-R | 38 | 0 | 499 | 1526.75 | 1471.00 | 0.39 | Sí |
| S37-IR | 38 | 0 | 499 | 1471.50 | 1418.75 | 0.36 | Sí |
| S38-B | 115 | 0 | 375 | 337.25 | 184.00 | 1.59 | Sí |
| S38-I | 114 | 0 | 376 | 341.25 | 170.75 | 1.52 | Sí |
| S38-R | 114 | 0 | 376 | 340.75 | 202.75 | 1.57 | Sí |
| S38-IR | 112 | 0 | 378 | 336.75 | 191.75 | 1.44 | Sí |
| S39-B | 25 | 0 | 349 | 1864.75 | 1766.25 | 0.16 | Sí |
| S39-I | 25 | 0 | 349 | 1804.75 | 1703.75 | 0.14 | Sí |
| S39-R | 25 | 0 | 349 | 1870.00 | 1767.50 | 0.13 | Sí |
| S39-IR | 25 | 0 | 349 | 1810.00 | 1707.50 | 0.13 | Sí |

- 100 % de las soluciones fueron factibles según el validador independiente.
- Tiempo máximo: 1.59 s, por debajo de los 60 s del RNF03 (el tiempo depende del equipo).
- En todas las instancias la función objetivo de la colonia de hormigas fue menor que la de la regla de referencia (regla voraz del R2.2, usada solo en las pruebas).
- La regla de referencia reproduce exactamente los valores del R2.2 y la colonia de hormigas reproduce exactamente 24 corridas del R2.2 con las mismas semillas (PI-03 y PI-04).
- Las semanas 37, 38 y 39 encadenadas con la memoria de la planificación anterior son factibles y ninguna partida se asigna dos veces (PI-06).
