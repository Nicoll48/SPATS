package pe.edu.pucp.spats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import pe.edu.pucp.spats.carga.entity.TipoDato;
import pe.edu.pucp.spats.carga.service.CargaService;
import pe.edu.pucp.spats.carga.service.PlantillaService;
import pe.edu.pucp.spats.comun.error.ConflictoException;
import pe.edu.pucp.spats.comun.error.ReglaNegocioException;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.CambioVigenteResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.ContextoCambioVigenteResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.GeneracionResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.ModoCambioVigente;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.VersionesSemanaResponse;
import pe.edu.pucp.spats.planificacion.service.CambioVigenteService;
import pe.edu.pucp.spats.planificacion.service.CierreSemanaService;
import pe.edu.pucp.spats.planificacion.service.ConsultaPlanificacionService;
import pe.edu.pucp.spats.planificacion.service.ExportacionPlanificacionService;
import pe.edu.pucp.spats.planificacion.service.GeneracionPlanificacionService;
import pe.edu.pucp.spats.tejeduria.entity.EstadoProyeccion;

/**
 * D-52 contra PostgreSQL con la semana 37 todavía sin empezar (el reloj está en el viernes 04/09/2026): subir una
 * versión nueva de la proyección no toca la vigente, generar esa versión no la vuelve vigente, «Usar como vigente»
 * cambia directamente (y se puede volver) y, al cerrar la semana, todo queda en solo lectura.
 *
 * <p>Solo corre si está definida {@code SPATS_TEST_DB_URL} (y {@code SPATS_TEST_DB_USUARIO},
 * {@code SPATS_TEST_DB_CONTRASENA}). ¡Debe ser una base de PRUEBA: el script elimina todas las tablas!</p>
 */
@SpringBootTest
@ActiveProfiles({"dev", "demo"})
@EnabledIfEnvironmentVariable(named = "SPATS_TEST_DB_URL", matches = ".+")
class VersionesProyeccionIntegracionTest {

    static final LocalDate S37 = FlujoPlanificacionIntegracionTest.S37;
    static final LocalDate S38 = FlujoPlanificacionIntegracionTest.S38;
    static final LocalDate HOY = LocalDate.of(2026, 9, 4);

    @DynamicPropertySource
    static void baseDePrueba(DynamicPropertyRegistry registro) throws Exception {
        prepararBase(registro, HOY);
    }

    /** Crea la base de prueba (BD.sql, datos iniciales, parque del R2.2) y fija el reloj. */
    static void prepararBase(DynamicPropertyRegistry registro, LocalDate hoy) throws Exception {
        String url = System.getenv("SPATS_TEST_DB_URL");
        String usuario = System.getenv().getOrDefault("SPATS_TEST_DB_USUARIO", "postgres");
        String contrasena = System.getenv().getOrDefault("SPATS_TEST_DB_CONTRASENA", "");
        try (Connection c = DriverManager.getConnection(url, usuario, contrasena); Statement s = c.createStatement()) {
            s.execute(Files.readString(FlujoPlanificacionIntegracionTest.BD.resolve("BD.sql"), StandardCharsets.UTF_8));
            s.execute(Files.readString(FlujoPlanificacionIntegracionTest.BD.resolve("datos_iniciales.sql"), StandardCharsets.UTF_8));
            s.execute("DELETE FROM maquina_tonalidad_aceptada; DELETE FROM maquina_tintoreria;");
        }
        registro.add("spring.datasource.url", () -> url);
        registro.add("spring.datasource.username", () -> usuario);
        registro.add("spring.datasource.password", () -> contrasena);
        registro.add("spats.demo.carpeta-datos", () -> "../../spats-algoritmo/spats-algoritmo/datos");
        registro.add("spats.reloj.inicio", () -> hoy + "T09:00:00");
    }

    @Autowired GeneracionPlanificacionService generacion;
    @Autowired ConsultaPlanificacionService consulta;
    @Autowired CambioVigenteService cambioVigente;
    @Autowired ExportacionPlanificacionService exportacion;
    @Autowired CierreSemanaService cierre;
    @Autowired CargaService cargas;
    @Autowired PlantillaService plantillas;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void iniciarSesionComoPlanificador() {
        iniciarSesion(jdbc);
    }

    @AfterEach
    void cerrarSesion() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("D-52: subir una versión, generarla, usarla como vigente antes de empezar la semana y cerrar")
    void semanaSinEmpezar() throws Exception {
        GeneracionResponse v1 = generacion.generar(S37);
        assertThat(v1.vigente()).isTrue();
        assertThat(v1.versionProyeccion()).isEqualTo(1);

        // 1. Subir la V2 no borra la V1 ni cambia la vigente
        cargas.importar(TipoDato.PROYECCION_TEJEDURIA, otraVersion(plantillas, S37), S37);
        assertThat(consulta.estadoSemana(S37).planificacionVigente().id()).isEqualTo(v1.idPlanificacion());
        assertThat(consulta.estadoSemana(S37).proyeccionDesactualizada()).isTrue();
        assertThat(estado(v1.idPlanificacion())).isEqualTo("ABIERTA");
        VersionesSemanaResponse versiones = consulta.versionesProyeccion(S37);
        assertThat(versiones.versiones()).extracting(v -> v.version()).containsExactly(1, 2);
        assertThat(versiones.versiones()).extracting(v -> v.estado()).containsExactly(EstadoProyeccion.VIGENTE, EstadoProyeccion.CARGADA);
        assertThat(versiones.versiones().get(0).planificacion().id()).isEqualTo(v1.idPlanificacion());
        assertThat(versiones.versiones().get(1).planificacion()).isNull();
        assertThat(contar("select count(*) from bitacora_auditoria where tipo_operacion = 'IMPORTAR' "
                + "and entidad_afectada = 'proyeccion_tejeduria'")).isEqualTo(1);
        Integer idV2 = versiones.versiones().get(1).idProyeccion();

        // 2. Generar la V2 no la vuelve vigente: queda CANDIDATA y no se exporta
        generacion.generar(S38);
        GeneracionResponse g2 = generacion.generar(S37, idV2);
        assertThat(g2.vigente()).isFalse();
        assertThat(g2.versionProyeccion()).isEqualTo(2);
        assertThat(estado(g2.idPlanificacion())).isEqualTo("CANDIDATA");
        assertThat(consulta.vigente(S37).id()).isEqualTo(v1.idPlanificacion());
        assertThat(consulta.versionesProyeccion(S37).versiones().get(1).planificacion().id()).isEqualTo(g2.idPlanificacion());
        assertThat(stockDesactualizado(consulta.vigente(S38).id())).isFalse();     // generar una candidata no marca N+1
        assertThatThrownBy(() -> exportacion.exportar(g2.idPlanificacion())).isInstanceOf(ReglaNegocioException.class);
        assertThat(contar("select count(*) from bitacora_auditoria where tipo_operacion = 'GENERAR' "
                + "and id_entidad = ? and valor_nuevo like '%no vigente%'", g2.idPlanificacion())).isEqualTo(1);

        // 3. Usar como vigente con la semana sin empezar: cambio directo; la anterior vuelve a ser candidata de la V1
        ContextoCambioVigenteResponse ctx = cambioVigente.contexto(g2.idPlanificacion());
        assertThat(ctx.semanaIniciada()).isFalse();
        assertThat(ctx.permitido()).isTrue();
        assertThat(ctx.incidenciasPendientes() + ctx.incidenciasConsideradas() + ctx.replanificaciones()).isZero();
        CambioVigenteResponse cambio = cambioVigente.usar(g2.idPlanificacion(), List.of());
        assertThat(cambio.modo()).isEqualTo(ModoCambioVigente.DIRECTO);
        assertThat(consulta.vigente(S37).id()).isEqualTo(g2.idPlanificacion());
        assertThat(estado(v1.idPlanificacion())).isEqualTo("CANDIDATA");
        assertThat(consulta.versionesProyeccion(S37).versiones()).extracting(v -> v.estado())
                .containsExactly(EstadoProyeccion.CARGADA, EstadoProyeccion.VIGENTE);
        assertThat(contar("select count(*) from planificacion_semanal where id_semana = "
                + "(select id_semana from semana where fecha_inicio = ?) and es_vigente", S37)).isEqualTo(1);
        assertThat(stockDesactualizado(consulta.vigente(S38).id())).isTrue();      // D-19: cambió la vigente de N
        assertThat(contar("select count(*) from bitacora_auditoria where tipo_operacion = 'CAMBIAR_VIGENTE'")).isEqualTo(1);

        // ... y se puede volver a la V1
        cambioVigente.usar(v1.idPlanificacion(), List.of());
        assertThat(consulta.vigente(S37).id()).isEqualTo(v1.idPlanificacion());
        assertThat(estado(g2.idPlanificacion())).isEqualTo("CANDIDATA");

        // 4. Al cerrar la semana todo queda en solo lectura; las demás versiones se conservan
        cierre.cerrar(v1.idPlanificacion(), List.of());
        assertThat(consulta.versionesProyeccion(S37).cerrada()).isTrue();
        assertThatThrownBy(() -> cargas.importar(TipoDato.PROYECCION_TEJEDURIA, otraVersion(plantillas, S37), S37))
                .isInstanceOf(ConflictoException.class);
        assertThatThrownBy(() -> generacion.generar(S37, idV2)).isInstanceOf(ConflictoException.class);
        assertThatThrownBy(() -> cambioVigente.usar(g2.idPlanificacion(), List.of())).isInstanceOf(ReglaNegocioException.class);
        assertThat(cambioVigente.contexto(g2.idPlanificacion()).permitido()).isFalse();
        assertThat(estado(g2.idPlanificacion())).isEqualTo("CANDIDATA");
        assertThat(consulta.versiones(S37)).hasSize(2);
    }

    // ------------------------------------------------------------------ apoyo (también para la semana iniciada)

    static void iniciarSesion(JdbcTemplate jdbc) {
        Integer id = jdbc.queryForObject("select id_usuario from usuario where usuario_login = 'carlos'", Integer.class);
        Jwt jwt = Jwt.withTokenValue("prueba").header("alg", "HS256").subject(String.valueOf(id))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600)).build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_PLANIFICADOR"))));
    }

    /**
     * Otra versión de la proyección vigente de la semana: la misma plantilla con un tercio de los kilos en una de
     * cada tres filas, para que la planificación cambie.
     */
    static MockMultipartFile otraVersion(PlantillaService plantillas, LocalDate lunes) throws Exception {
        byte[] original = plantillas.exportar(TipoDato.PROYECCION_TEJEDURIA, lunes).contenido();
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(original));
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.getSheet("Proyeccion");
            int columnaKg = -1, filaCabecera = -1;
            for (Row fila : hoja) {
                for (Cell celda : fila)
                    if (celda.getCellType() == CellType.STRING && celda.getStringCellValue().trim().equals("kg_proyectados")) {
                        columnaKg = celda.getColumnIndex();
                        filaCabecera = fila.getRowNum();
                    }
                if (columnaKg >= 0) break;
            }
            assertThat(columnaKg).as("columna kg_proyectados").isNotNegative();
            for (int i = filaCabecera + 1; i <= hoja.getLastRowNum(); i += 3) {
                Row fila = hoja.getRow(i);
                Cell kg = fila == null ? null : fila.getCell(columnaKg);
                if (kg != null && kg.getCellType() == CellType.NUMERIC)
                    kg.setCellValue(BigDecimal.valueOf(kg.getNumericCellValue()).divide(BigDecimal.valueOf(3), 3,
                            java.math.RoundingMode.HALF_UP).max(new BigDecimal("0.001")).doubleValue());
            }
            libro.write(salida);
            return new MockMultipartFile("archivo", "proyeccion_" + lunes + "_v2.xlsx", "application/octet-stream", salida.toByteArray());
        }
    }

    private String estado(Integer idPlanificacion) {
        return jdbc.queryForObject("select estado from planificacion_semanal where id_planificacion = ?", String.class, idPlanificacion);
    }

    private boolean stockDesactualizado(Integer idPlanificacion) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select stock_desactualizado from planificacion_semanal where id_planificacion = ?",
                Boolean.class, idPlanificacion));
    }

    private int contar(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }
}
