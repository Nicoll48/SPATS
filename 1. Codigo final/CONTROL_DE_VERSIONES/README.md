# Control de versiones de SPATS

Esta carpeta registra **todos los cambios** hechos al proyecto SPATS (backend, frontend, módulo del algoritmo, base de datos y documentación), y las decisiones que los justifican.

## Contenido

| Archivo / carpeta | Qué guarda |
|---|---|
| `CHANGELOG.md` | Registro cronológico de cambios, del más reciente al más antiguo. Una entrada por cada conjunto de cambios entregado. |
| `DECISIONES.md` | Decisiones confirmadas por la autora (vacíos resueltos, inconsistencias aclaradas). Cada una con fecha y alcance. |
| `revisiones/` | Informes de análisis previos a un cambio (inconsistencias encontradas, propuestas, preguntas). |

## Reglas

1. Ningún cambio al **módulo del algoritmo** (`spats-algoritmo`) ni a `SPATS_contexto_sistema.md` se hace sin confirmación previa de la autora. La propuesta se registra primero en `revisiones/` y la confirmación en `DECISIONES.md`.
2. Cada entrada del `CHANGELOG.md` indica: fecha, versión, archivos afectados, qué cambió y por qué, y cómo se verificó (compilación, pruebas).
3. Versionado semántico del sistema: `MAYOR.MENOR.PARCHE`. Se empieza en `0.x` mientras el sistema no esté completo.
