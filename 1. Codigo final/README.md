# SPATS — Sistema de Planificación de Abastecimiento de Tintorería

Sistema de **asignación de partidas a máquinas de tintorería**. Recibe la proyección de tejeduría, decide qué partidas se conforman con la tela de cada artículo y a qué máquina de su barca va cada una, y construye un calendario de 7 días por máquina lo más equilibrado posible. Para eso usa el algoritmo de **colonia de hormigas (ACO)** seleccionado en el R2.2.

El contexto funcional completo (reglas, estados, requisitos e historias de usuario) está en [`SPATS_contexto_sistema.md`](SPATS_contexto_sistema.md).

## Estructura

| Carpeta | Contenido |
|---|---|
| `BD/` | `BD.sql` (crea las 22 tablas; se puede volver a ejecutar) y `datos_iniciales.sql` (perfiles y cuentas). |
| `spats-algoritmo/` | Módulo del algoritmo (R2.3), Java puro. El 01/11/2026 se amplió su contrato de entrada para la re-planificación desde D (aprobado por la autora). La búsqueda del ACO y la función objetivo no cambiaron (ver `docs/integracion-algoritmo.md`). El backend lo usa como dependencia. |
| `backend/` | API REST en Java 21 + Spring Boot 4.1 (agregador Maven: `spats-algoritmo` + `spats-api`). |
| `FRONT/frontend/` | Aplicación web en React 19 + Vite + TypeScript + Tailwind CSS v4. |
| `docs/` | Documentación técnica: arquitectura, API, plantillas, integración con el algoritmo y pruebas. |
| `CONTROL_DE_VERSIONES/` | Registro de cambios, decisiones confirmadas, revisiones y propuestas pendientes. |
| `Experimentacion_R2.2/` | Experimentación del R2.2 (solo referencia). |
| `SKILLS/` | Guías de backend y frontend que sigue el código. |

## Puesta en marcha (desarrollo local)

Requisitos: JDK 21, Maven 3.9, Node 20 o superior, PostgreSQL 16 o superior.

```bash
# 1. Base de datos (una sola vez). OJO: BD.sql elimina y vuelve a crear las tablas.
createdb -U postgres tintoreria
psql -U postgres -d tintoreria -f BD/BD.sql
psql -U postgres -d tintoreria -f BD/datos_iniciales.sql

# 2. API (puerto 8080). Con el perfil "demo" carga las semanas 37 y 38 del R2.2 si la base no tiene máquinas.
cd backend
mvn -DskipTests install                                   # una vez, y cada vez que cambie spats-algoritmo
cd spats-api
set SPATS_DB_CONTRASENA=<contraseña de postgres>          # PowerShell: $env:SPATS_DB_CONTRASENA="..."
mvn spring-boot:run "-Dspring-boot.run.profiles=dev,demo"

# 3. Aplicación web (puerto 5173; /api se reenvía a la API)
cd FRONT/frontend
npm install
npm run dev
```

Cuentas iniciales: `carlos` / `planificar` (Planificador) y `consulta` / `consulta` (Consulta). **Cámbialas antes de usar datos reales.**

Con los datos de demostración, elige la semana del 7 de setiembre de 2026 y genera su planificación. Después haz lo mismo con la del 14 de setiembre, que toma como stock inicial el saldo de las bolsas de la anterior.

## Documentación

- [Arquitectura del sistema](docs/arquitectura.md)
- [Integración del algoritmo con la base de datos](docs/integracion-algoritmo.md)
- [API REST](docs/api.md)
- [Plantillas de carga](docs/plantillas.md)
- [Pruebas](docs/pruebas.md)
- [Cambios y decisiones](CONTROL_DE_VERSIONES/CHANGELOG.md)
