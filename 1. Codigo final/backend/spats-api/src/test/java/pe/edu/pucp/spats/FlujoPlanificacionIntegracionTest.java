package pe.edu.pucp.spats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.GeneracionResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.PlanificacionDetalleResponse;
import pe.edu.pucp.spats.planificacion.entity.Enumeraciones.EstadoPlanificacion;
import pe.edu.pucp.spats.planificacion.service.CierreSemanaService;
import pe.edu.pucp.spats.planificacion.service.ConsultaPlanificacionService;
import pe.edu.pucp.spats.planificacion.service.GeneracionPlanificacionService;

/**
 * Flujo completo contra PostgreSQL: crea la base con {@code BD/BD.sql} y {@code BD/datos_iniciales.sql},
 * carga las semanas 37 y 38 del R2.2 (perfil demo), genera, regenera, cierra y cambia la proyección.
 *
 * <p>Solo corre si está definida {@code SPATS_TEST_DB_URL} (y {@code SPATS_TEST_DB_USUARIO},
 * {@code SPATS_TEST_DB_CONTRASENA}). ¡Debe ser una base de PRUEBA: el script elimina todas las tablas!</p>
 */
@SpringBootTest
@ActiveProfiles({"dev", "demo"})
@EnabledIfEnvironmentVariable(named = "SPATS_TEST_DB_URL", matches = ".+")
class FlujoPlanificacionIntegracionTest {

    static final Path BD = Path.of("..", "..", "BD");
    static final LocalDate S37 = LocalDate.of(2026, 9, 7);
    static final LocalDate S38 = LocalDate.of(2026, 9, 14);

    @DynamicPropertySource
    static void baseDePrueba(DynamicPropertyRegistry registro) throws Exception {
        String url = System.getenv("SPATS_TEST_DB_URL");
        String usuario = System.getenv().getOrDefault("SPATS_TEST_DB_USUARIO", "postgres");
        String contrasena = System.getenv().getOrDefault("SPATS_TEST_DB_CONTRASENA", "");
        try (Connection c = DriverManager.getConnection(url, usuario, contrasena); Statement s = c.createStatement()) {
            s.execute(Files.readString(BD.resolve("BD.sql"), StandardCharsets.UTF_8));
            s.execute(Files.readString(BD.resolve("datos_iniciales.sql"), StandardCharsets.UTF_8));
        }
        registro.add("spring.datasource.url", () -> url);
        registro.add("spring.datasource.username", () -> usuario);
        registro.add("spring.datasource.password", () -> contrasena);
        registro.add("spats.demo.carpeta-datos", () -> "../../spats-algoritmo/spats-algoritmo/datos");
    }

    @Autowired GeneracionPlanificacionService generacion;
    @Autowired ConsultaPlanificacionService consulta;
    @Autowired CierreSemanaService cierre;
    @Autowired CargaService cargas;
    @Autowired PlantillaService plantillas;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void iniciarSesionComoPlanificador() {
        Integer id = jdbc.queryForObject("select id_usuario from usuario where usuario_login = 'carlos'", Integer.class);
        Jwt jwt = Jwt.withTokenValue("prueba").header("alg", "HS256").subject(String.valueOf(id))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600)).build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_PLANIFICADOR"))));
    }

    @AfterEach
    void cerrarSesion() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Flujo semanal completo sobre la base real")
    void flujoCompleto() throws Exception {
        // 1. Semana 37 desde la base: mismo F que el R2.3 con los Excel (regresión de la sección 8.5)
        GeneracionResponse v1 = generacion.generar(S37);
        assertThat(v1.numeroVersion()).isEqualTo(1);
        assertThat(v1.indicadores().valorFuncionObjetivo()).isEqualByComparingTo("1279.75");
        PlanificacionDetalleResponse plan37 = consulta.vigente(S37);
        assertThat(plan37.resumen().asignadas() + plan37.resumen().sobrantes()).isEqualTo(48);
        assertThat(contar("select count(*) from bolsa_articulo where id_planificacion = ?", v1.idPlanificacion())).isPositive();

        // 2. Semana 38 encadenada: su stock inicial sale del saldo del domingo de la 37
        GeneracionResponse s38 = generacion.generar(S38);
        assertThat(s38.indicadores().valorFuncionObjetivo()).isNotNull();
        System.out.println("F semana 38 encadenada desde la base: " + s38.indicadores().valorFuncionObjetivo()
                + " (R2.3 con S38-B.xlsx: 184.00; el stock de ese libro viene de otra corrida del R2.2)");

        // 3. Volver a generar la 37: versión 2 vigente, la 1 se conserva como REEMPLAZADA (D-14)
        GeneracionResponse v2 = generacion.generar(S37);
        assertThat(v2.numeroVersion()).isEqualTo(2);
        assertThat(v2.advertencias()).anyMatch(a -> a.contains(S38.toString()));
        assertThat(consulta.versiones(S37)).extracting(v -> v.estado())
                .containsExactly(EstadoPlanificacion.ABIERTA, EstadoPlanificacion.REEMPLAZADA);
        // D-19: la 38 queda marcada como desactualizada y no se puede cerrar con la 37 abierta
        assertThat(consulta.estadoSemana(S38).stockDesactualizado()).startsWith("Desactualizada");
        assertThat(consulta.estadoSemana(S37).stockDesactualizado()).isNull();
        assertThat(jdbc.queryForObject("select stock_desactualizado from planificacion_semanal where id_planificacion = ?",
                Boolean.class, s38.idPlanificacion())).isTrue();
        assertThat(jdbc.queryForObject("select stock_desactualizado from planificacion_semanal where id_planificacion = ?",
                Boolean.class, v2.idPlanificacion())).isFalse();
        assertThatThrownBy(() -> cierre.cerrar(s38.idPlanificacion(), List.of())).isInstanceOf(ConflictoException.class);

        // 4. Una proyección nueva de la 38 exige confirmación y deja sin vigencia su planificación (D-06)
        byte[] proyeccion38 = plantillas.exportar(TipoDato.PROYECCION_TEJEDURIA, S38).contenido();
        MockMultipartFile archivo = new MockMultipartFile("archivo", "proyeccion_38.xlsx", "application/octet-stream", proyeccion38);
        assertThatThrownBy(() -> cargas.importar(TipoDato.PROYECCION_TEJEDURIA, archivo, S38, false))
                .isInstanceOf(ConflictoException.class);
        cargas.importar(TipoDato.PROYECCION_TEJEDURIA, archivo, S38, true);
        assertThat(consulta.estadoSemana(S38).planificacionVigente()).isNull();
        assertThat(consulta.versiones(S38)).extracting(v -> v.estado()).containsExactly(EstadoPlanificacion.INVALIDADA);

        // 5. Cierre de la 37: confirmadas → COMPLETADA, resto → ARRASTRADA, bloqueadas siguen PENDIENTE
        PlanificacionDetalleResponse vigente37 = consulta.vigente(S37);
        List<String> conformadas = vigente37.maquinas().stream().flatMap(m -> m.dias().stream())
                .flatMap(d -> d.partidas().stream()).map(p -> p.numeroPartida()).toList();
        List<String> completadas = conformadas.subList(0, conformadas.size() - 3);
        var resultado = cierre.cerrar(vigente37.id(), completadas);
        assertThat(resultado.completadas()).isEqualTo(conformadas.size() - 3);
        assertThat(resultado.arrastradas()).isEqualTo(3);
        assertThat(cierre.arrastradas()).hasSize(3);
        assertThat(contar("select count(*) from partida where estado_partida = 'PENDIENTE' and numero_partida in ("
                + "select p.numero_partida from planificacion_partida pp join partida p on p.id_partida = pp.id_partida "
                + "where pp.id_planificacion = ? and pp.clasificacion_resultado = 'BLOQUEADA')", vigente37.id()))
                .isEqualTo(vigente37.resumen().bloqueadas());
        assertThatThrownBy(() -> generacion.generar(S37)).isInstanceOf(ConflictoException.class);

        // 6. Las arrastradas no vuelven a planificarse; una se marca completada (RF49)
        cierre.completarArrastrada(cierre.arrastradas().getFirst().numeroPartida());
        assertThat(cierre.arrastradas()).hasSize(2);
        assertThat(contar("select count(*) from bitacora_auditoria where tipo_operacion in ('GENERAR','CERRAR','INVALIDAR')")).isGreaterThanOrEqualTo(5);
    }

    private int contar(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }
}
