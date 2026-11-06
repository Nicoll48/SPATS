# SPATS — Backend

Agregador Maven:

- `../spats-algoritmo/spats-algoritmo`: algoritmo de colonia de hormigas del R2.3. Se amplió su contrato de entrada para la re-planificación; la búsqueda y la función objetivo no cambiaron.
- `spats-api`: API REST en Java 21 + Spring Boot 4.1.2.

```bash
mvn test                                              # pruebas del algoritmo y de la API
mvn -DskipTests install                               # instala spats-algoritmo en el repositorio local
cd spats-api && mvn spring-boot:run "-Dspring-boot.run.profiles=dev,demo"   # API en http://localhost:8080
mvn -DskipTests package                               # spats-api/target/spats-api.jar
```

## Variables de entorno

| Variable | Uso | Valor por defecto |
|---|---|---|
| `SPATS_DB_URL` | URL JDBC | `jdbc:postgresql://localhost:5432/tintoreria` |
| `SPATS_DB_USUARIO` / `SPATS_DB_CONTRASENA` | Credenciales de PostgreSQL | `postgres` / vacío |
| `SPATS_JWT_SECRETO` | Clave para firmar los tokens (≥ 32 caracteres). **Obligatoria fuera del perfil `dev`.** | solo en `dev` |
| `SPATS_ORIGENES` | Orígenes permitidos por CORS | `http://localhost:5173` |
| `SPATS_DEMO_DATOS` | Carpeta de los libros del R2.2 (perfil `demo`) | `../../spats-algoritmo/spats-algoritmo/datos` |

Perfiles:

- `dev`: clave de desarrollo.
- `demo`: si la base no tiene máquinas, carga las semanas 37 y 38 del R2.2.

La base se crea con `../BD/BD.sql` y `../BD/datos_iniciales.sql`.

Más detalle en [`../docs/`](../docs/arquitectura.md).
