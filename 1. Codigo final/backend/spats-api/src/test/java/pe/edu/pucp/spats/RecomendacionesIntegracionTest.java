package pe.edu.pucp.spats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import pe.edu.pucp.spats.comun.error.ReglaNegocioException;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.GeneracionResponse;
import pe.edu.pucp.spats.planificacion.recomendacion.RecomendacionesTejidoService;
import pe.edu.pucp.spats.planificacion.recomendacion.RecomendacionesTejidoService.RecomendacionFila;
import pe.edu.pucp.spats.planificacion.service.CierreSemanaService;
import pe.edu.pucp.spats.planificacion.service.ConsultaPlanificacionService;
import pe.edu.pucp.spats.planificacion.service.GeneracionPlanificacionService;

/**
 * RF30 (D-56) contra PostgreSQL, semana 37 sin empezar: cada versión planificada tiene sus propias recomendaciones; las
 * arrastradas no entran; «Crear versión con las recomendaciones» agrega las marcadas a una versión nueva de la
 * proyección, sin planificación y no vigente; se exportan de la vigente; Consulta solo ve las de la vigente; al cerrar
 * solo quedan las de la vigente.
 */
@SpringBootTest
@ActiveProfiles({"dev", "demo"})
@EnabledIfEnvironmentVariable(named = "SPATS_TEST_DB_URL", matches = ".+")
class RecomendacionesIntegracionTest {

    static final LocalDate S37 = FlujoPlanificacionIntegracionTest.S37;

    @DynamicPropertySource
    static void baseDePrueba(DynamicPropertyRegistry registro) throws Exception {
        VersionesProyeccionIntegracionTest.prepararBase(registro, LocalDate.of(2026, 9, 4));
    }

    @Autowired GeneracionPlanificacionService generacion;
    @Autowired ConsultaPlanificacionService consulta;
    @Autowired CierreSemanaService cierre;
    @Autowired RecomendacionesTejidoService recomendaciones;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void sesion() { VersionesProyeccionIntegracionTest.iniciarSesion(jdbc); }

    @AfterEach
    void cerrarSesion() { SecurityContextHolder.clearContext(); }

    int filas(Integer idPlan) {
        return jdbc.queryForObject("select count(*) from recomendacion_tejido where id_planificacion = ?", Integer.class, idPlan);
    }

    @Test
    @DisplayName("D-56: por versión, arrastradas fuera, sin crear versiones, exportación, Consulta y cierre")
    void tejidoAdicional() {
        GeneracionResponse v1 = generacion.generar(S37);
        var r1 = recomendaciones.consultar(v1.idPlanificacion());
        assertThat(filas(v1.idPlanificacion())).isPositive();
        assertThat(r1.maquinas()).allSatisfy(m -> {
            assertThat(m.seCubren()).isLessThanOrEqualTo(Math.min(m.faltan(), m.espacioLibre()));
            assertThat(m.recomendaciones()).allSatisfy(f -> assertThat(f.diaTejido()).isBefore(f.diaLlena()));
        });
        List<RecomendacionFila> todas = r1.maquinas().stream().flatMap(m -> m.recomendaciones().stream()).toList();

        // HU-13 C3: una partida Arrastrada no se recomienda
        String arrastrada = todas.get(0).partida();
        jdbc.update("update partida set estado_partida = 'ARRASTRADA' where numero_partida = ?", arrastrada);
        jdbc.update("delete from recomendacion_tejido where id_planificacion = ?", v1.idPlanificacion());
        var sinArrastrada = recomendaciones.consultar(v1.idPlanificacion());
        assertThat(sinArrastrada.maquinas()).flatExtracting(m -> m.recomendaciones()).extracting(RecomendacionFila::partida).doesNotContain(arrastrada);
        jdbc.update("update partida set estado_partida = 'PENDIENTE' where numero_partida = ?", arrastrada);
        jdbc.update("delete from recomendacion_tejido where id_planificacion = ?", v1.idPlanificacion());
        r1 = recomendaciones.consultar(v1.idPlanificacion());
        todas = r1.maquinas().stream().flatMap(m -> m.recomendaciones().stream()).toList();

        // Las recomendaciones solo se muestran: no crean versiones de la proyección (corrección de la autora, 13/11)
        assertThat(jdbc.queryForObject("select count(*) from bitacora_auditoria where tipo_operacion = 'CREAR' and entidad_afectada = 'proyeccion_tejeduria'",
                Integer.class)).isZero();

        // Otra versión de la proyección (copia de la de origen, como si se subiera otra vez)
        Integer idOrigen = jdbc.queryForObject("select id_proyeccion from planificacion_semanal where id_planificacion = ?", Integer.class, v1.idPlanificacion());
        Integer idOtra = jdbc.queryForObject("""
                insert into proyeccion_tejeduria (id_semana, version, estado)
                select id_semana, (select max(version) + 1 from proyeccion_tejeduria q where q.id_semana = p.id_semana), 'CARGADA'
                from proyeccion_tejeduria p where id_proyeccion = ? returning id_proyeccion""", Integer.class, idOrigen);
        jdbc.update("""
                insert into detalle_proyeccion_tejeduria (id_proyeccion, id_detalle_pedido, fecha_proyectada, kg_proyectados)
                select ?, id_detalle_pedido, fecha_proyectada, kg_proyectados from detalle_proyeccion_tejeduria where id_proyeccion = ?""", idOtra, idOrigen);

        // La otra versión, al generarla, tiene sus propias recomendaciones y no toca las de la V1
        int antesV1 = filas(v1.idPlanificacion());
        GeneracionResponse g2 = generacion.generar(S37, idOtra);
        assertThat(g2.vigente()).isFalse();
        assertThat(filas(v1.idPlanificacion())).isEqualTo(antesV1);
        recomendaciones.consultar(g2.idPlanificacion());

        // Exportar: siempre de la vigente
        assertThat(recomendaciones.exportar(S37).nombre()).isEqualTo("tejido_adicional_" + S37 + "_v" + r1.numeroVersion() + ".xlsx");

        // Consulta solo ve las de la vigente
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                Jwt.withTokenValue("c").header("alg", "HS256").subject("2").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build(),
                List.of(new SimpleGrantedAuthority("ROLE_CONSULTA"))));
        assertThat(recomendaciones.consultar(v1.idPlanificacion()).esVigente()).isTrue();
        assertThatThrownBy(() -> recomendaciones.consultar(g2.idPlanificacion())).isInstanceOf(ReglaNegocioException.class);
        VersionesProyeccionIntegracionTest.iniciarSesion(jdbc);

        // Al cerrar solo quedan las de la vigente
        cierre.cerrar(v1.idPlanificacion(), List.of());
        assertThat(filas(g2.idPlanificacion())).isZero();
        assertThat(filas(v1.idPlanificacion())).isPositive();
    }
}
