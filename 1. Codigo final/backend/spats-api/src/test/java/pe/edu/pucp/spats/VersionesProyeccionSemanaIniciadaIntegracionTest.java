package pe.edu.pucp.spats;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import pe.edu.pucp.spats.carga.entity.TipoDato;
import pe.edu.pucp.spats.carga.service.CargaService;
import pe.edu.pucp.spats.carga.service.PlantillaService;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.CambioVigenteResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.ContextoCambioVigenteResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.GeneracionResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.ModoCambioVigente;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.ResultadoReplanificacion;
import pe.edu.pucp.spats.planificacion.service.CambioVigenteService;
import pe.edu.pucp.spats.planificacion.service.ConsultaPlanificacionService;
import pe.edu.pucp.spats.planificacion.service.GeneracionPlanificacionService;
import pe.edu.pucp.spats.planificacion.service.ReplanificacionService;
import pe.edu.pucp.spats.tejeduria.entity.EstadoProyeccion;

/**
 * D-52 contra PostgreSQL con la semana 38 ya empezada (el reloj está en el miércoles 16/09/2026): «Usar como vigente»
 * re-planifica desde hoy con la otra versión de la proyección y deja una propuesta. Hasta confirmarla, la vigente no
 * cambia; al confirmarla, lo anterior a hoy se conserva y la versión de la proyección pasa a ser la VIGENTE.
 *
 * <p>Solo corre si está definida {@code SPATS_TEST_DB_URL}. ¡Debe ser una base de PRUEBA!</p>
 */
@SpringBootTest
@ActiveProfiles({"dev", "demo"})
@EnabledIfEnvironmentVariable(named = "SPATS_TEST_DB_URL", matches = ".+")
class VersionesProyeccionSemanaIniciadaIntegracionTest {

    static final LocalDate S37 = FlujoPlanificacionIntegracionTest.S37;
    static final LocalDate S38 = FlujoPlanificacionIntegracionTest.S38;
    static final LocalDate HOY = LocalDate.of(2026, 9, 16);

    @DynamicPropertySource
    static void baseDePrueba(DynamicPropertyRegistry registro) throws Exception {
        VersionesProyeccionIntegracionTest.prepararBase(registro, HOY);
    }

    @Autowired GeneracionPlanificacionService generacion;
    @Autowired ConsultaPlanificacionService consulta;
    @Autowired CambioVigenteService cambioVigente;
    @Autowired ReplanificacionService replanificacion;
    @Autowired CargaService cargas;
    @Autowired PlantillaService plantillas;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void iniciarSesionComoPlanificador() {
        VersionesProyeccionIntegracionTest.iniciarSesion(jdbc);
    }

    @AfterEach
    void cerrarSesion() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("D-52: usar otra versión como vigente con la semana empezada re-planifica desde hoy")
    void semanaIniciada() throws Exception {
        generacion.generar(S37);
        GeneracionResponse v1 = generacion.generar(S38);
        cargas.importar(TipoDato.PROYECCION_TEJEDURIA, VersionesProyeccionIntegracionTest.otraVersion(plantillas, S38), S38);
        Integer idV2 = consulta.versionesProyeccion(S38).versiones().get(1).idProyeccion();
        GeneracionResponse g2 = generacion.generar(S38, idV2);
        assertThat(g2.vigente()).isFalse();

        ContextoCambioVigenteResponse ctx = cambioVigente.contexto(g2.idPlanificacion());
        assertThat(ctx.semanaIniciada()).isTrue();
        assertThat(ctx.permitido()).isTrue();
        assertThat(ctx.dia()).isEqualTo(HOY);

        // Usar como vigente: propuesta desde hoy; la vigente sigue igual hasta confirmar
        CambioVigenteResponse cambio = cambioVigente.usar(g2.idPlanificacion(), List.of());
        assertThat(cambio.modo()).isEqualTo(ModoCambioVigente.REPLANIFICACION);
        assertThat(cambio.replanificacion().resultado()).isEqualTo(ResultadoReplanificacion.PROPUESTA);
        Integer idPropuesta = cambio.replanificacion().idPropuesta();
        assertThat(consulta.vigente(S38).id()).isEqualTo(v1.idPlanificacion());
        assertThat(estado(idPropuesta)).isEqualTo("PROPUESTA");
        assertThat(jdbc.queryForObject("select id_proyeccion from planificacion_semanal where id_planificacion = ?",
                Integer.class, idPropuesta)).isEqualTo(idV2);

        // Confirmar: la propuesta pasa a vigente, conserva lo anterior a hoy y la V2 pasa a ser la VIGENTE
        replanificacion.confirmar(idPropuesta);
        assertThat(consulta.vigente(S38).id()).isEqualTo(idPropuesta);
        assertThat(estado(v1.idPlanificacion())).isEqualTo("REEMPLAZADA");
        assertThat(estado(g2.idPlanificacion())).isEqualTo("REEMPLAZADA");   // la candidata de semana completa ya no se usa
        assertThat(consulta.versionesProyeccion(S38).versiones()).extracting(v -> v.estado())
                .containsExactly(EstadoProyeccion.CARGADA, EstadoProyeccion.VIGENTE);
        String antesDeHoy = "select p.numero_partida || '|' || m.codigo || '|' || pp.dia_abastecimiento || '|' || pp.clasificacion_resultado "
                + "from planificacion_partida pp join partida p on p.id_partida = pp.id_partida "
                + "join maquina_tintoreria m on m.id_maquina = pp.id_maquina "
                + "where pp.id_planificacion = ? and pp.dia_abastecimiento < ? order by 1";
        List<String> anteriores = jdbc.queryForList(antesDeHoy, String.class, v1.idPlanificacion(), HOY);
        assertThat(anteriores).isNotEmpty();
        assertThat(jdbc.queryForList(antesDeHoy, String.class, idPropuesta, HOY)).isEqualTo(anteriores);
        assertThat(contar("select count(*) from bitacora_auditoria where tipo_operacion = 'CAMBIAR_VIGENTE' and id_entidad = ?",
                idPropuesta)).isEqualTo(1);
    }

    private String estado(Integer idPlanificacion) {
        return jdbc.queryForObject("select estado from planificacion_semanal where id_planificacion = ?", String.class, idPlanificacion);
    }

    private int contar(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }
}
