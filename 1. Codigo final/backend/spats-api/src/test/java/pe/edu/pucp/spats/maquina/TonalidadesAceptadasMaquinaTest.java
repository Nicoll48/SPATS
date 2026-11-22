package pe.edu.pucp.spats.maquina;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import pe.edu.pucp.spats.auditoria.service.BitacoraService;
import pe.edu.pucp.spats.comun.dominio.ClasificacionColor;
import pe.edu.pucp.spats.comun.error.ConflictoException;
import pe.edu.pucp.spats.comun.error.ReglaNegocioException;
import pe.edu.pucp.spats.comun.error.SpatsException;
import pe.edu.pucp.spats.comun.fecha.SemanaPlanificacion;
import pe.edu.pucp.spats.maquina.dto.MaquinaDtos.GuardarMaquinaRequest;
import pe.edu.pucp.spats.maquina.entity.EstadoMaquina;
import pe.edu.pucp.spats.maquina.entity.MaquinaTintoreria;
import pe.edu.pucp.spats.maquina.mapper.MaquinaMapper;
import pe.edu.pucp.spats.maquina.repository.IndisponibilidadRepository;
import pe.edu.pucp.spats.maquina.repository.MaquinaRepository;
import pe.edu.pucp.spats.maquina.service.MaquinaService;
import pe.edu.pucp.spats.partida.entity.Color;
import pe.edu.pucp.spats.partida.entity.EstadoPartida;
import pe.edu.pucp.spats.partida.entity.Partida;
import pe.edu.pucp.spats.partida.entity.TonalidadColor;
import pe.edu.pucp.spats.partida.repository.PartidaRepository;
import pe.edu.pucp.spats.pedido.entity.Cliente;
import pe.edu.pucp.spats.pedido.entity.Pedido;
import pe.edu.pucp.spats.planificacion.entity.Enumeraciones.ClasificacionResultado;
import pe.edu.pucp.spats.planificacion.entity.Enumeraciones.TipoGeneracion;
import pe.edu.pucp.spats.planificacion.entity.PlanificacionPartida;
import pe.edu.pucp.spats.planificacion.entity.PlanificacionSemanal;
import pe.edu.pucp.spats.planificacion.repository.PlanificacionPartidaRepository;
import pe.edu.pucp.spats.tejeduria.entity.Semana;

/** D-51: tonalidades aceptadas en el registro y la modificación de máquinas. */
class TonalidadesAceptadasMaquinaTest {

    static final LocalDate LUNES = LocalDate.of(2026, 11, 9);
    static final LocalDate HOY = LUNES.plusDays(2);   // miércoles: la semana se puede re-planificar

    final List<MaquinaTintoreria> registradas = new ArrayList<>();
    final List<Partida> pendientes = new ArrayList<>();
    final List<PlanificacionPartida> enVigente = new ArrayList<>();
    MaquinaRepository maquinas;
    BitacoraService bitacora;
    MaquinaService servicio;
    PlanificacionSemanal vigente;

    @BeforeEach
    void preparar() {
        maquinas = mock(MaquinaRepository.class);
        PartidaRepository partidas = mock(PartidaRepository.class);
        PlanificacionPartidaRepository filas = mock(PlanificacionPartidaRepository.class);
        bitacora = mock(BitacoraService.class);
        when(maquinas.findAll()).thenReturn(registradas);
        when(maquinas.save(any())).thenAnswer(i -> i.getArgument(0));
        when(partidas.findByEstadoPartidaOrderByNumeroPartida(EstadoPartida.PENDIENTE)).thenReturn(pendientes);
        when(filas.enVigentesAbiertasDeLaMaquina(any())).thenReturn(enVigente);
        Clock reloj = Clock.fixed(HOY.atTime(9, 0).atZone(ZoneId.of("America/Lima")).toInstant(), ZoneId.of("America/Lima"));
        servicio = new MaquinaService(maquinas, mock(IndisponibilidadRepository.class), partidas, filas, new MaquinaMapper(),
                bitacora, reloj);
        vigente = new PlanificacionSemanal(new Semana(new SemanaPlanificacion(LUNES)), null, 1, LUNES.atTime(7, 0),
                TipoGeneracion.INICIAL, null, null, null, 0, 0, 0);
    }

    MaquinaTintoreria registrada(int id, String codigo, int capacidad, Set<TonalidadColor> aceptadas) {
        MaquinaTintoreria m = new MaquinaTintoreria(codigo, BigDecimal.valueOf(capacidad), ClasificacionColor.B, 16,
                capacidad + " B", EstadoMaquina.OPERATIVA, aceptadas);
        ReflectionTestUtils.setField(m, "id", id);
        registradas.add(m);
        when(maquinas.findById(id)).thenReturn(Optional.of(m));
        return m;
    }

    Partida partida(String numero, String barca, TonalidadColor t) {
        Color color = new Color("C-" + numero, "Color " + numero, ClasificacionColor.B, t);
        return new Partida(new Pedido(new Cliente("Cliente"), "OP-1", LUNES.plusDays(10)), color, numero, barca,
                BigDecimal.valueOf(250), LocalDateTime.of(2026, 11, 6, 8, 0));
    }

    static GuardarMaquinaRequest solicitud(String codigo, int capacidad, Set<TonalidadColor> aceptadas) {
        return new GuardarMaquinaRequest(codigo, BigDecimal.valueOf(capacidad), ClasificacionColor.B, 16, EstadoMaquina.OPERATIVA, aceptadas);
    }

    static String codigo(Throwable e) { return ((SpatsException) e).codigo(); }

    @Test
    @DisplayName("CP021: la búsqueda encuentra por tonalidad aceptada («Oscuros»), y «C»/«B» siguen siendo la clasificación")
    void busquedaPorTonalidad() {
        when(maquinas.findAllByOrderByCodigoAsc()).thenReturn(registradas);
        registrada(1, "M300-1", 300, EnumSet.of(TonalidadColor.BLANCO, TonalidadColor.CLARO));
        registrada(2, "M300-2", 300, EnumSet.of(TonalidadColor.MEDIO, TonalidadColor.OSCURO));
        registrada(3, "M600-1", 600, EnumSet.allOf(TonalidadColor.class));
        assertThat(servicio.buscar("Oscuros")).extracting(r -> r.codigo()).containsExactly("M300-2", "M600-1");
        assertThat(servicio.buscar("oscuro")).extracting(r -> r.codigo()).containsExactly("M300-2", "M600-1");
        assertThat(servicio.buscar("cla")).extracting(r -> r.codigo()).containsExactly("M300-1", "M600-1");
        assertThat(servicio.buscar("C")).isEmpty();   // las tres son clasificación B: «C» no busca la tonalidad Claro
        assertThat(servicio.buscar("M300")).hasSize(2);
    }

    @Test
    @DisplayName("Guardar una máquina con las cuatro tonalidades desmarcadas: error con el mensaje pedido")
    void sinTonalidades() {
        assertThatThrownBy(() -> servicio.registrar(solicitud("M300-3", 300, Set.of())))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Marca al menos una tonalidad. Si la máquina acepta todos los colores, deja todas marcadas.")
                .extracting(TonalidadesAceptadasMaquinaTest::codigo).isEqualTo("TONALIDADES_REQUERIDAS");
        verify(maquinas, never()).save(any());
        MaquinaTintoreria m = registrada(1, "M300-1", 300, EnumSet.of(TonalidadColor.BLANCO));
        assertThatThrownBy(() -> servicio.modificar(1, solicitud(null, 300, Set.of()), true)).isInstanceOf(ReglaNegocioException.class);
        assertThat(m.getTonalidadesAceptadas()).containsExactly(TonalidadColor.BLANCO);
    }

    @Test
    @DisplayName("Una máquina nueva sin tonalidades indicadas acepta las cuatro")
    void porDefectoTodas() {
        assertThat(servicio.registrar(solicitud("M300-3", 300, null)).tonalidadesAceptadas())
                .containsExactly(TonalidadColor.BLANCO, TonalidadColor.CLARO, TonalidadColor.MEDIO, TonalidadColor.OSCURO);
    }

    @Test
    @DisplayName("Si una partida PENDIENTE se queda sin máquina compatible, pide confirmación con la lista")
    void pendienteSinCompatible() {
        MaquinaTintoreria m300 = registrada(2, "M300-2", 300, EnumSet.of(TonalidadColor.BLANCO, TonalidadColor.CLARO));
        registrada(1, "M300-1", 300, EnumSet.of(TonalidadColor.BLANCO));
        pendientes.add(partida("P-CLARA", "300 B", TonalidadColor.CLARO));     // solo M300-2 la aceptaba
        pendientes.add(partida("P-BLANCA", "300 B", TonalidadColor.BLANCO));   // M300-1 la sigue aceptando
        GuardarMaquinaRequest soloBlanco = solicitud(null, 300, EnumSet.of(TonalidadColor.BLANCO));

        assertThatThrownBy(() -> servicio.modificar(2, soloBlanco, false))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("P-CLARA").hasMessageNotContaining("P-BLANCA")
                .satisfies(e -> {
                    assertThat(codigo(e)).isEqualTo("CONFIRMACION_REQUERIDA");
                    assertThat(((ConflictoException) e).datos().get("sinMaquinaCompatible")).isEqualTo(List.of("P-CLARA"));
                });
        assertThat(m300.getTonalidadesAceptadas()).contains(TonalidadColor.CLARO);

        servicio.modificar(2, soloBlanco, true);
        assertThat(m300.getTonalidadesAceptadas()).containsExactly(TonalidadColor.BLANCO);
        verify(bitacora).registrarCambio("maquina_tintoreria", 2, "tonalidades_aceptadas", "BLANCO,CLARO", "BLANCO");
    }

    @Test
    @DisplayName("Quitar una tonalidad con partidas de la vigente en esa máquina: aviso con lista y la vigente no cambia")
    void partidasEnLaVigente() {
        MaquinaTintoreria m300 = registrada(2, "M300-2", 300, EnumSet.of(TonalidadColor.BLANCO, TonalidadColor.CLARO));
        registrada(1, "M300-1", 300, EnumSet.of(TonalidadColor.BLANCO, TonalidadColor.CLARO));
        PlanificacionPartida clara = PlanificacionPartida.ubicada(vigente, partida("P-CLARA", "300 B", TonalidadColor.CLARO), m300,
                HOY.plusDays(1), 1, ClasificacionResultado.ASIGNADA, false, HOY);
        PlanificacionPartida ayer = PlanificacionPartida.ubicada(vigente, partida("P-AYER", "300 B", TonalidadColor.CLARO), m300,
                HOY.minusDays(1), 1, ClasificacionResultado.ASIGNADA, false, HOY.minusDays(1));
        PlanificacionPartida blanca = PlanificacionPartida.ubicada(vigente, partida("P-BLANCA", "300 B", TonalidadColor.BLANCO), m300,
                HOY, 2, ClasificacionResultado.ASIGNADA, false, HOY);
        enVigente.addAll(List.of(ayer, clara, blanca));
        GuardarMaquinaRequest soloBlanco = solicitud(null, 300, EnumSet.of(TonalidadColor.BLANCO));

        assertThatThrownBy(() -> servicio.modificar(2, soloBlanco, false))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("P-CLARA").hasMessageNotContaining("P-AYER").hasMessageNotContaining("P-BLANCA")
                .satisfies(e -> {
                    Map<String, Object> datos = ((ConflictoException) e).datos();
                    assertThat(codigo(e)).isEqualTo("CONFIRMACION_PLAN_VIGENTE");
                    assertThat((List<?>) datos.get("enPlanificacionVigente")).hasSize(1);
                });

        servicio.modificar(2, soloBlanco, true);   // «Guardar y aplicar desde la próxima planificación»
        assertThat(m300.getTonalidadesAceptadas()).containsExactly(TonalidadColor.BLANCO);
        assertThat(clara.getMaquina()).isSameAs(m300);                              // la vigente no cambia sola
        assertThat(clara.getClasificacionResultado()).isEqualTo(ClasificacionResultado.ASIGNADA);
    }
}
