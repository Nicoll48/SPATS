package pe.edu.pucp.spats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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

import pe.edu.pucp.spats.comun.error.ConflictoException;
import pe.edu.pucp.spats.incidencia.dto.IncidenciaDtos.RegistrarIncidenciaRequest;
import pe.edu.pucp.spats.incidencia.entity.TipoIncidencia;
import pe.edu.pucp.spats.incidencia.service.IncidenciaService;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.DiaMaquinaResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.MaquinaPlanResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.PartidaCalendarioResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.PlanificacionDetalleResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.ReplanificacionRequest;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.ReplanificacionResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.ResultadoReplanificacion;
import pe.edu.pucp.spats.planificacion.service.CierreSemanaService;
import pe.edu.pucp.spats.planificacion.service.ConsultaPlanificacionService;
import pe.edu.pucp.spats.planificacion.service.GeneracionPlanificacionService;
import pe.edu.pucp.spats.planificacion.service.ReplanificacionService;

/**
 * Prueba 6 de la propuesta de re-planificación contra PostgreSQL: proponer, descartar y confirmar dejan la base
 * como se describe (HU-18, D-13b, D-26). El reloj empieza el miércoles 16/09/2026, en la semana 38 del R2.2.
 *
 * <p>Solo corre si está definida {@code SPATS_TEST_DB_URL} (y {@code SPATS_TEST_DB_USUARIO},
 * {@code SPATS_TEST_DB_CONTRASENA}). ¡Debe ser una base de PRUEBA: el script elimina todas las tablas!</p>
 */
@SpringBootTest
@ActiveProfiles({"dev", "demo"})
@EnabledIfEnvironmentVariable(named = "SPATS_TEST_DB_URL", matches = ".+")
class ReplanificacionIntegracionTest {

    static final LocalDate S37 = FlujoPlanificacionIntegracionTest.S37;
    static final LocalDate S38 = FlujoPlanificacionIntegracionTest.S38;
    static final LocalDate D = LocalDate.of(2026, 9, 16);

    @DynamicPropertySource
    static void baseDePrueba(DynamicPropertyRegistry registro) throws Exception {
        String url = System.getenv("SPATS_TEST_DB_URL");
        String usuario = System.getenv().getOrDefault("SPATS_TEST_DB_USUARIO", "postgres");
        String contrasena = System.getenv().getOrDefault("SPATS_TEST_DB_CONTRASENA", "");
        try (Connection c = DriverManager.getConnection(url, usuario, contrasena); Statement s = c.createStatement()) {
            s.execute(Files.readString(FlujoPlanificacionIntegracionTest.BD.resolve("BD.sql"), StandardCharsets.UTF_8));
            s.execute(Files.readString(FlujoPlanificacionIntegracionTest.BD.resolve("datos_iniciales.sql"), StandardCharsets.UTF_8));
        }
        registro.add("spring.datasource.url", () -> url);
        registro.add("spring.datasource.username", () -> usuario);
        registro.add("spring.datasource.password", () -> contrasena);
        registro.add("spats.demo.carpeta-datos", () -> "../../spats-algoritmo/spats-algoritmo/datos");
        registro.add("spats.reloj.inicio", () -> D + "T09:00:00");
    }

    @Autowired GeneracionPlanificacionService generacion;
    @Autowired ReplanificacionService replanificacion;
    @Autowired ConsultaPlanificacionService consulta;
    @Autowired CierreSemanaService cierre;
    @Autowired IncidenciaService incidencias;
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
    @DisplayName("6. Proponer, descartar y confirmar una re-planificación sobre la base real")
    void proponerDescartarYConfirmar() {
        generacion.generar(S37);
        generacion.generar(S38);
        PlanificacionDetalleResponse vigente = consulta.vigente(S38);
        Integer idVigente = vigente.id();

        // Incidencias: la máquina con partidas desde hoy queda fuera hasta el domingo, y un pedido cambia de prioridad
        MaquinaPlanResponse maquina = vigente.maquinas().stream()
                .filter(m -> m.dias().stream().anyMatch(d -> !d.fecha().isBefore(D) && !d.partidas().isEmpty()))
                .findFirst().orElseThrow();
        PartidaCalendarioResponse desdeHoy = vigente.maquinas().stream().flatMap(m -> m.dias().stream())
                .filter(d -> !d.fecha().isBefore(D)).flatMap(d -> d.partidas().stream()).findFirst().orElseThrow();
        LocalDate entregaAnterior = desdeHoy.fechaEntrega();
        LocalDate entregaNueva = S38.minusDays(20);
        Integer idMaquina = incidencias.registrar(S38, new RegistrarIncidenciaRequest(TipoIncidencia.INCIDENCIA_MAQUINA,
                maquina.codigo(), null, null, D.atTime(8, 0), S38.plusDays(7).atStartOfDay(), null, "Falla del motor")).id();
        Integer idPrioridad = incidencias.registrar(S38, new RegistrarIncidenciaRequest(TipoIncidencia.CAMBIO_PRIORIDAD,
                null, null, desdeHoy.numeroOp(), D.atTime(8, 30), null, entregaNueva, null)).id();
        assertThat(fechaEntrega(desdeHoy.numeroOp())).isEqualTo(entregaAnterior);           // D-13b: registrar no cambia el pedido

        // Proponer: versión PROPUESTA, no vigente, con todas sus filas
        ReplanificacionResponse r = replanificacion.replanificar(S38, new ReplanificacionRequest(List.of(idMaquina, idPrioridad), List.of()));
        assertThat(r.resultado()).isEqualTo(ResultadoReplanificacion.PROPUESTA);
        assertThat(r.vigenteFactible()).isFalse();                                          // usaba la máquina caída desde hoy
        Integer idPropuesta = r.idPropuesta();
        Map<String, Object> propuesta = jdbc.queryForMap("select estado, es_vigente, tipo_generacion from planificacion_semanal where id_planificacion = ?", idPropuesta);
        assertThat(propuesta).containsEntry("estado", "PROPUESTA").containsEntry("es_vigente", false).containsEntry("tipo_generacion", "REPLANIFICACION");
        assertThat(consulta.vigente(S38).id()).isEqualTo(idVigente);
        assertThat(contar("select count(*) from planificacion_incidencia where id_planificacion = ?", idPropuesta)).isEqualTo(2);
        assertThat(filasAntesDeD(idPropuesta)).isEqualTo(filasAntesDeD(idVigente)).isNotEmpty();         // prueba 2 en la base
        assertThat(contar("select count(*) from planificacion_partida where id_planificacion = ? and id_maquina = "
                + "(select id_maquina from maquina_tintoreria where codigo = ?) and dia_abastecimiento >= ?", idPropuesta, maquina.codigo(), D)).isZero();
        assertThat(contar("select count(*) from bolsa_articulo where id_planificacion = ? and fecha < ?", idPropuesta, D))
                .isEqualTo(contar("select count(*) from bolsa_articulo where id_planificacion = ? and fecha < ?", idVigente, D));
        assertThat(consulta.versiones(S38)).noneMatch(v -> v.id().equals(idPropuesta));        // no está en el historial
        assertThat(consulta.estadoSemana(S38).propuestaPendiente().id()).isEqualTo(idPropuesta);
        assertThat(estadoIncidencias()).containsOnly("PENDIENTE");
        List<DiaMaquinaResponse> diasMaquina = consulta.detalle(idPropuesta).maquinas().stream()
                .filter(m -> m.codigo().equals(maquina.codigo())).findFirst().orElseThrow().dias();
        assertThat(diasMaquina).filteredOn(d -> !d.fecha().isBefore(D)).allMatch(DiaMaquinaResponse::indisponible);

        // Con la propuesta pendiente no se puede re-planificar otra vez, cerrar ni volver a generar
        assertThatThrownBy(() -> replanificacion.replanificar(S38, new ReplanificacionRequest(List.of(idMaquina), List.of())))
                .isInstanceOf(ConflictoException.class);
        assertThatThrownBy(() -> cierre.cerrar(idVigente, List.of())).isInstanceOf(ConflictoException.class);
        assertThatThrownBy(() -> generacion.generar(S38)).isInstanceOf(ConflictoException.class);

        // Descartar: se elimina con todas sus filas; las incidencias siguen PENDIENTE (D-26)
        replanificacion.descartar(idPropuesta);
        for (String tabla : List.of("planificacion_semanal", "planificacion_partida", "planificacion_maquina", "bolsa_articulo", "planificacion_incidencia"))
            assertThat(contar("select count(*) from " + tabla + " where id_planificacion = ?", idPropuesta)).as(tabla).isZero();
        assertThat(estadoIncidencias()).containsOnly("PENDIENTE");
        assertThat(consulta.vigente(S38).id()).isEqualTo(idVigente);
        assertThat(contar("select count(*) from bitacora_auditoria where tipo_operacion = 'DESCARTAR' and id_entidad = ?", idPropuesta)).isEqualTo(1);

        // Confirmar: la propuesta pasa a vigente, la anterior a REEMPLAZADA, las incidencias a CONSIDERADA y el pedido cambia
        ReplanificacionResponse r2 = replanificacion.replanificar(S38, new ReplanificacionRequest(List.of(idMaquina, idPrioridad), List.of()));
        assertThat(r2.resultado()).isEqualTo(ResultadoReplanificacion.PROPUESTA);
        var confirmacion = replanificacion.confirmar(r2.idPropuesta());
        assertThat(confirmacion.incidenciasConsideradas()).isEqualTo(2);
        assertThat(confirmacion.pedidosActualizados()).isEqualTo(1);
        assertThat(consulta.vigente(S38).id()).isEqualTo(r2.idPropuesta());
        assertThat(jdbc.queryForObject("select estado from planificacion_semanal where id_planificacion = ?", String.class, idVigente))
                .isEqualTo("REEMPLAZADA");
        assertThat(estadoIncidencias()).containsOnly("CONSIDERADA");
        assertThat(fechaEntrega(desdeHoy.numeroOp())).isEqualTo(entregaNueva);
        assertThat(jdbc.queryForObject("select valor_anterior from bitacora_auditoria where entidad_afectada = 'pedido' "
                + "and campo_modificado = 'fecha_entrega' order by id_bitacora desc limit 1", String.class)).isEqualTo(entregaAnterior.toString());
        assertThat(consulta.versiones(S38)).anyMatch(v -> v.id().equals(r2.idPropuesta()) && v.esVigente());
        assertThat(consulta.estadoSemana(S38).propuestaPendiente()).isNull();
    }

    private List<Map<String, Object>> filasAntesDeD(Integer idPlanificacion) {
        return jdbc.queryForList("select id_partida, id_maquina, dia_abastecimiento, orden_secuencia, clasificacion_resultado, "
                + "requiere_lavado_previo, fecha_disponible from planificacion_partida where id_planificacion = ? "
                + "and dia_abastecimiento < ? order by id_partida", idPlanificacion, D);
    }

    private List<String> estadoIncidencias() {
        return jdbc.queryForList("select estado from incidencia", String.class);
    }

    private LocalDate fechaEntrega(String numeroOp) {
        return jdbc.queryForObject("select fecha_entrega from pedido where numero_op = ?", LocalDate.class, numeroOp);
    }

    private int contar(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

}
