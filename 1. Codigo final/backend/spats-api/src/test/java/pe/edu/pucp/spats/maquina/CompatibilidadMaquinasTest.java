package pe.edu.pucp.spats.maquina;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.edu.pucp.spats.comun.dominio.Barca;
import pe.edu.pucp.spats.comun.dominio.ClasificacionColor;
import pe.edu.pucp.spats.comun.error.ReglaNegocioException;
import pe.edu.pucp.spats.maquina.entity.EstadoMaquina;
import pe.edu.pucp.spats.maquina.entity.MaquinaTintoreria;
import pe.edu.pucp.spats.maquina.service.CompatibilidadMaquinas;
import pe.edu.pucp.spats.partida.entity.TonalidadColor;

/** D-51: compatibilidad por capacidad (número de la barca) y tonalidades aceptadas, con el parque oficial. */
class CompatibilidadMaquinasTest {

    static final Set<TonalidadColor> TODAS = EnumSet.allOf(TonalidadColor.class);

    static MaquinaTintoreria maquina(String codigo, int capacidad, ClasificacionColor letra, Set<TonalidadColor> aceptadas) {
        return new MaquinaTintoreria(codigo, BigDecimal.valueOf(capacidad), letra, 16, capacidad + " " + letra.name(),
                EstadoMaquina.OPERATIVA, aceptadas);
    }

    /** Parque oficial (punto 7 de D-51). */
    static final List<MaquinaTintoreria> PARQUE = List.of(
            maquina("M600-1", 600, ClasificacionColor.C, TODAS), maquina("M600-2", 600, ClasificacionColor.C, TODAS),
            maquina("M400-1", 400, ClasificacionColor.C, TODAS), maquina("M400-2", 400, ClasificacionColor.C, TODAS),
            maquina("M450-1", 450, ClasificacionColor.B, EnumSet.of(TonalidadColor.BLANCO, TonalidadColor.CLARO, TonalidadColor.MEDIO)),
            maquina("M300-1", 300, ClasificacionColor.B, EnumSet.of(TonalidadColor.BLANCO)),
            maquina("M300-2", 300, ClasificacionColor.B, EnumSet.of(TonalidadColor.BLANCO, TonalidadColor.CLARO)),
            maquina("M200-1", 200, ClasificacionColor.C, TODAS), maquina("M200-2", 200, ClasificacionColor.C, TODAS),
            maquina("M200-3", 200, ClasificacionColor.C, TODAS));

    static List<String> compatibles(String barca, TonalidadColor t) {
        return CompatibilidadMaquinas.compatibles(PARQUE, barca, t).stream().map(MaquinaTintoreria::getCodigo).toList();
    }

    @Test
    @DisplayName("Partida 300 blanca: compatible con las dos máquinas de 300")
    void blanca300() {
        assertThat(compatibles("300 B", TonalidadColor.BLANCO)).containsExactly("M300-1", "M300-2");
    }

    @Test
    @DisplayName("Partida 300 clara: compatible solo con la de 300 que acepta Claro")
    void clara300() {
        assertThat(compatibles("300 B", TonalidadColor.CLARO)).containsExactly("M300-2");
    }

    @Test
    @DisplayName("Partida 300 media: ninguna compatible (al generar queda Bloqueada) y el aviso lo explica")
    void media300() {
        assertThat(CompatibilidadMaquinas.hayCompatible(PARQUE, "300 B", TonalidadColor.MEDIO)).isFalse();
        assertThat(CompatibilidadMaquinas.avisoSinCompatible("P-9", TonalidadColor.MEDIO, "300 B"))
                .contains("P-9", "Medio", "300 B", "ninguna máquina de 300 kg", "Bloqueada");
        assertThat(CompatibilidadMaquinas.MOTIVO_SIN_COMPATIBLE)
                .isEqualTo(spats.algoritmo.nucleo.PartidaExcluida.SIN_MAQUINA_COMPATIBLE);
    }

    @Test
    @DisplayName("Partida blanca de 540 kg con barca 600: compatible con las dos de 600")
    void blanca600() {
        assertThat(compatibles("600 B", TonalidadColor.BLANCO)).containsExactly("M600-1", "M600-2");
    }

    @Test
    @DisplayName("La letra no influye: \"600 C\", \"600 B\" y \"600\" tienen las mismas máquinas compatibles")
    void letraNoInfluye() {
        for (TonalidadColor t : TonalidadColor.values()) {
            assertThat(compatibles("600 B", t)).isEqualTo(compatibles("600 C", t)).isEqualTo(compatibles("600", t));
            assertThat(compatibles("300 C", t)).isEqualTo(compatibles("300 B", t));
        }
    }

    @Test
    @DisplayName("La barca de la partida debe empezar con un número")
    void barcaEmpiezaConNumero() {
        assertThat(Barca.validadaPorCapacidad(" 400  c ")).isEqualTo("400 C");
        assertThat(Barca.validadaPorCapacidad("450")).isEqualTo("450");
        assertThat(Barca.validadaPorCapacidad("")).isNull();
        assertThatThrownBy(() -> Barca.validadaPorCapacidad("B400")).isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("debe empezar con la capacidad");
    }

    @Test
    @DisplayName("Texto de las tonalidades aceptadas")
    void texto() {
        assertThat(CompatibilidadMaquinas.textoTonalidades(TODAS)).isEqualTo("Todas");
        assertThat(CompatibilidadMaquinas.textoTonalidades(EnumSet.of(TonalidadColor.CLARO, TonalidadColor.BLANCO))).isEqualTo("Blanco y Claro");
        assertThat(CompatibilidadMaquinas.codigos(EnumSet.of(TonalidadColor.MEDIO, TonalidadColor.BLANCO))).isEqualTo("BLANCO,MEDIO");
    }
}
