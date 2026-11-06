package pe.edu.pucp.spats.comun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.edu.pucp.spats.comun.dominio.Barca;
import pe.edu.pucp.spats.comun.dominio.ClasificacionColor;
import pe.edu.pucp.spats.comun.error.ReglaNegocioException;
import pe.edu.pucp.spats.comun.fecha.SemanaPlanificacion;

class DominioComunTest {

    static final LocalDate LUNES = LocalDate.of(2026, 9, 14);

    @Test
    @DisplayName("La semana va de lunes a domingo y no acepta otro día de inicio")
    void semanaDeLunesADomingo() {
        SemanaPlanificacion s = new SemanaPlanificacion(LUNES);
        assertThat(s.domingo()).isEqualTo(LocalDate.of(2026, 9, 20));
        assertThat(s.dias()).hasSize(7);
        assertThatThrownBy(() -> new SemanaPlanificacion(LUNES.plusDays(1))).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("La ventana de la proyección va del sábado anterior al viernes")
    void ventanaDeLaProyeccion() {
        SemanaPlanificacion s = new SemanaPlanificacion(LUNES);
        assertThat(s.enVentanaProyeccion(LocalDate.of(2026, 9, 12))).isTrue();
        assertThat(s.enVentanaProyeccion(LocalDate.of(2026, 9, 18))).isTrue();
        assertThat(s.enVentanaProyeccion(LocalDate.of(2026, 9, 11))).isFalse();
        assertThat(s.enVentanaProyeccion(LocalDate.of(2026, 9, 19))).isFalse();
    }

    @Test
    @DisplayName("Un día tocado en parte por una indisponibilidad cuenta como indisponible (D-08)")
    void indisponibilidadParcial() {
        SemanaPlanificacion s = new SemanaPlanificacion(LUNES);
        assertThat(s.diasTocados(LUNES.atTime(18, 0), LUNES.plusDays(1).atTime(6, 0)))
                .containsExactly(LUNES, LUNES.plusDays(1));
        assertThat(s.diasTocados(LUNES.atStartOfDay(), LUNES.plusDays(1).atStartOfDay())).containsExactly(LUNES);
        assertThat(s.diasTocados(LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2026, 9, 2, 0, 0))).isEmpty();
    }

    @Test
    @DisplayName("La barca se normaliza y su letra debe coincidir con la clasificación de color (D-07)")
    void barca() {
        assertThat(Barca.normalizar(" 400 c ")).isEqualTo("400 C");
        assertThat(Barca.validada("600 B", ClasificacionColor.B, "el color")).isEqualTo("600 B");
        assertThat(Barca.validada("", ClasificacionColor.C, "el color")).isNull();
        assertThatThrownBy(() -> Barca.validada("600 B", ClasificacionColor.C, "el color")).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> Barca.validada("600", ClasificacionColor.C, "el color")).isInstanceOf(ReglaNegocioException.class);
    }
}
