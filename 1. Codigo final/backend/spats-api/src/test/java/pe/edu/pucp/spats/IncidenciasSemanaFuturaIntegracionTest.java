package pe.edu.pucp.spats;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

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

import pe.edu.pucp.spats.incidencia.dto.IncidenciaDtos.RegistrarIncidenciaRequest;
import pe.edu.pucp.spats.incidencia.entity.TipoIncidencia;
import pe.edu.pucp.spats.incidencia.service.IncidenciaService;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.GeneracionResponse;
import pe.edu.pucp.spats.planificacion.service.GeneracionPlanificacionService;

/**
 * Volver a generar una semana que aún no empieza aplica sus incidencias pendientes (reloj en el viernes 04/09/2026,
 * semana 37 desde el lunes 07/09): la máquina caída no recibe partidas esos días, la incidencia queda vinculada a la
 * nueva vigente y pasa a CONSIDERADA.
 *
 * <p>Solo corre si está definida {@code SPATS_TEST_DB_URL}. ¡Debe ser una base de PRUEBA!</p>
 */
@SpringBootTest
@ActiveProfiles({"dev", "demo"})
@EnabledIfEnvironmentVariable(named = "SPATS_TEST_DB_URL", matches = ".+")
class IncidenciasSemanaFuturaIntegracionTest {

    static final LocalDate S37 = FlujoPlanificacionIntegracionTest.S37;
    static final LocalDate HOY = LocalDate.of(2026, 9, 4);

    @DynamicPropertySource
    static void baseDePrueba(DynamicPropertyRegistry registro) throws Exception {
        VersionesProyeccionIntegracionTest.prepararBase(registro, HOY);
    }

    @Autowired GeneracionPlanificacionService generacion;
    @Autowired IncidenciaService incidencias;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void iniciarSesion() {
        VersionesProyeccionIntegracionTest.iniciarSesion(jdbc);
    }

    @AfterEach
    void cerrarSesion() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Semana futura: volver a generar aplica la incidencia de máquina pendiente y la deja considerada")
    void incidenciaDeMaquinaEnSemanaFutura() {
        GeneracionResponse v1 = generacion.generar(S37);
        // La máquina y el día con más partidas en la V1; la incidencia la deja fuera ese día y el siguiente
        var elegida = jdbc.queryForMap("""
                select m.codigo, pp.dia_abastecimiento as dia from planificacion_partida pp join maquina_tintoreria m using (id_maquina)
                where pp.id_planificacion = ? and pp.dia_abastecimiento is not null and pp.dia_abastecimiento < ?
                group by m.codigo, pp.dia_abastecimiento order by count(*) desc, m.codigo limit 1""",
                v1.idPlanificacion(), S37.plusDays(6));
        String maquina = (String) elegida.get("codigo");
        LocalDate martes = ((java.sql.Date) elegida.get("dia")).toLocalDate(), miercoles = martes.plusDays(1);
        var registrada = incidencias.registrar(S37, new RegistrarIncidenciaRequest(TipoIncidencia.INCIDENCIA_MAQUINA, maquina,
                null, null, martes.atStartOfDay(), martes.plusDays(2).atStartOfDay(), null, "Mantenimiento"));

        GeneracionResponse v2 = generacion.generar(S37);
        assertThat(v2.vigente()).isTrue();
        assertThat(v2.advertencias()).anyMatch(a -> a.startsWith("Se aplicaron 1 incidencia "));
        assertThat(jdbc.queryForObject("""
                select count(*) from planificacion_partida pp join maquina_tintoreria m using (id_maquina)
                where pp.id_planificacion = ? and m.codigo = ? and pp.dia_abastecimiento in (?, ?)""",
                Integer.class, v2.idPlanificacion(), maquina, martes, miercoles)).isZero();
        assertThat(jdbc.queryForObject("select estado from incidencia where id_incidencia = ?", String.class, registrada.id()))
                .isEqualTo("CONSIDERADA");
        assertThat(jdbc.queryForObject("select count(*) from planificacion_incidencia where id_planificacion = ? and id_incidencia = ?",
                Integer.class, v2.idPlanificacion(), registrada.id())).isEqualTo(1);

        // D-57: ya considerada, volver a generar la sigue aplicando (no se «pierde») y la vincula a la versión nueva
        GeneracionResponse v3 = generacion.generar(S37);
        assertThat(v3.advertencias()).anyMatch(a -> a.startsWith("Se aplicaron 1 incidencia"));
        assertThat(jdbc.queryForObject("select count(*) from planificacion_incidencia where id_planificacion = ?",
                Integer.class, v3.idPlanificacion())).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*) from planificacion_partida pp join maquina_tintoreria m using (id_maquina)
                where pp.id_planificacion = ? and m.codigo = ? and pp.dia_abastecimiento in (?, ?)""",
                Integer.class, v3.idPlanificacion(), maquina, martes, miercoles)).isZero();

        // D-57: anulada, volver a generar ya no la aplica
        var anulada = incidencias.anular(S37, registrada.id(), "Se registró por error");
        assertThat(anulada.estado().name()).isEqualTo("ANULADA");
        GeneracionResponse v4 = generacion.generar(S37);
        assertThat(v4.advertencias()).noneMatch(a -> a.startsWith("Se aplicaron"));
        assertThat(jdbc.queryForObject("select count(*) from planificacion_incidencia where id_planificacion = ?",
                Integer.class, v4.idPlanificacion())).isZero();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> incidencias.anular(S37, registrada.id(), null))
                .isInstanceOf(pe.edu.pucp.spats.comun.error.ConflictoException.class);
    }

    @Test
    @DisplayName("D-57: anular un cambio de prioridad considerado devuelve al pedido su fecha de entrega anterior")
    void anularCambioDePrioridad() {
        generacion.generar(S37);
        String op = jdbc.queryForObject("""
                select pe.numero_op from partida p join pedido pe using (id_pedido) where p.estado_partida = 'PENDIENTE'
                order by pe.numero_op limit 1""", String.class);
        LocalDate antes = jdbc.queryForObject("select fecha_entrega from pedido where numero_op = ?", LocalDate.class, op);
        var registrada = incidencias.registrar(S37, new RegistrarIncidenciaRequest(TipoIncidencia.CAMBIO_PRIORIDAD, null, null, op,
                S37.minusDays(3).atStartOfDay(), null, antes.minusDays(10), null));
        generacion.generar(S37);   // la aplica: el pedido toma la fecha nueva
        assertThat(jdbc.queryForObject("select fecha_entrega from pedido where numero_op = ?", LocalDate.class, op)).isEqualTo(antes.minusDays(10));
        incidencias.anular(S37, registrada.id(), null);
        assertThat(jdbc.queryForObject("select fecha_entrega from pedido where numero_op = ?", LocalDate.class, op)).isEqualTo(antes);
    }

    @Test
    @DisplayName("D-54: una incidencia cerrada sin re-planificar queda ATENDIDA y ya no se aplica al generar")
    void incidenciaCerradaSinReplanificar() {
        generacion.generar(S37);
        String maquina = jdbc.queryForObject("select codigo from maquina_tintoreria order by codigo limit 1", String.class);
        var registrada = incidencias.registrar(S37, new RegistrarIncidenciaRequest(TipoIncidencia.INCIDENCIA_MAQUINA, maquina,
                null, null, S37.atStartOfDay(), S37.plusDays(2).atStartOfDay(), null, null));
        var atendida = incidencias.atender(S37, registrada.id(), "Se tiñeron en otra máquina");
        assertThat(atendida.estado().name()).isEqualTo("ATENDIDA");
        GeneracionResponse g = generacion.generar(S37);
        assertThat(g.advertencias()).noneMatch(a -> a.startsWith("Se aplicaron"));
        assertThat(jdbc.queryForObject("select count(*) from planificacion_incidencia where id_incidencia = ?", Integer.class, registrada.id())).isZero();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> incidencias.atender(S37, registrada.id(), null))
                .isInstanceOf(pe.edu.pucp.spats.comun.error.ConflictoException.class);
    }
}
