package pe.edu.pucp.spats.planificacion.motor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pe.edu.pucp.spats.auditoria.service.BitacoraService;
import pe.edu.pucp.spats.comun.error.ConflictoException;
import pe.edu.pucp.spats.partida.service.PartidaPorCambiarEvent;
import pe.edu.pucp.spats.planificacion.entity.Enumeraciones.EstadoPlanificacion;
import pe.edu.pucp.spats.planificacion.entity.Enumeraciones.TipoGeneracion;
import pe.edu.pucp.spats.planificacion.entity.PlanificacionSemanal;
import pe.edu.pucp.spats.planificacion.repository.PlanificacionSemanalRepository;
import pe.edu.pucp.spats.tejeduria.entity.Semana;

/** D-24: modificar una partida Asignada o Sobrante de una vigente abierta, como D-06. */
class InvalidacionPorPartidaTest {

    private static final LocalDate S37 = LocalDate.of(2026, 9, 7);

    private PlanificacionSemanalRepository planificaciones;
    private PropuestasPendientes propuestas;
    private ReglasVigencia reglasVigencia;
    private InvalidacionPorPartida invalidacion;
    private PlanificacionSemanal vigente;

    @BeforeEach
    void preparar() throws Exception {
        planificaciones = mock(PlanificacionSemanalRepository.class);
        propuestas = mock(PropuestasPendientes.class);
        reglasVigencia = mock(ReglasVigencia.class);
        invalidacion = new InvalidacionPorPartida(planificaciones, mock(BitacoraService.class), propuestas, reglasVigencia);
        Semana semana = mock(Semana.class);
        when(semana.getId()).thenReturn(37);
        when(semana.getFechaInicio()).thenReturn(S37);
        vigente = new PlanificacionSemanal(semana, null, 1, LocalDateTime.of(2026, 9, 7, 8, 0), TipoGeneracion.INICIAL,
                null, null, null, 0, 0, 0);
        Field id = PlanificacionSemanal.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(vigente, 10);
    }

    @Test
    void sinConfirmarSePideConfirmacionYNoCambiaNada() {
        when(planificaciones.vigentesAbiertasConPartidaUbicada(5)).thenReturn(List.of(vigente));
        assertThatThrownBy(() -> invalidacion.alCambiarPartida(new PartidaPorCambiarEvent(5, "314618", false)))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("314618");
        assertThat(vigente.isEsVigente()).isTrue();
        verify(reglasVigencia, never()).marcarSemanaSiguiente(any());
    }

    @Test
    void confirmadoInvalidaLaVigenteEliminaPropuestasYMarcaLaSiguiente() {
        when(planificaciones.vigentesAbiertasConPartidaUbicada(5)).thenReturn(List.of(vigente));
        invalidacion.alCambiarPartida(new PartidaPorCambiarEvent(5, "314618", true));
        assertThat(vigente.isEsVigente()).isFalse();
        assertThat(vigente.getEstado()).isEqualTo(EstadoPlanificacion.INVALIDADA);
        verify(propuestas).eliminarTodas(org.mockito.ArgumentMatchers.eq(37), anyString());
        verify(reglasVigencia).marcarSemanaSiguiente(S37);
    }

    @Test
    void unaPartidaQueNoEstaUbicadaEnUnaVigenteAbiertaNoInvalidaNada() {
        when(planificaciones.vigentesAbiertasConPartidaUbicada(5)).thenReturn(List.of());
        invalidacion.alCambiarPartida(new PartidaPorCambiarEvent(5, "314618", false));
        verify(reglasVigencia, never()).marcarSemanaSiguiente(any());
    }
}
