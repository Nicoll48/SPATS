package spats.algoritmo.nucleo;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static spats.algoritmo.DatosPrueba.dia;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import spats.algoritmo.DatosPrueba;
import spats.algoritmo.entrada.Tonalidad;

/**
 * D-51: una partida va a una máquina de su misma capacidad (número de la barca) que acepte su tonalidad. La letra
 * de la barca no influye. Parque oficial de 10 máquinas.
 */
class CompatibilidadTest {

    private static final Set<Tonalidad> TODAS = EnumSet.allOf(Tonalidad.class);

    /** Parque oficial con una partida y su componente; devuelve los códigos de las máquinas compatibles. */
    private static String[] compatibles(String partida, double peso, Tonalidad t, String barca) {
        InstanciaSemana I = instancia(partida, peso, t, barca);
        if (I.P == 0) return new String[0];
        return Arrays.stream(I.compat[0]).mapToObj(m -> I.maquinas[m].codigo()).sorted().toArray(String[]::new);
    }

    private static InstanciaSemana instancia(String partida, double peso, Tonalidad t, String barca) {
        return ConstructorInstancia.construir(parque().pedido("P1", dia(20))
                .partida(partida, "P1", peso, t, barca).componente(partida, "A", peso)
                .stock("A", 1000).construir());
    }

    private static DatosPrueba parque() {
        return new DatosPrueba()
                .maquina("M600-1", 600, "600 C", 16, TODAS).maquina("M600-2", 600, "600 C", 16, TODAS)
                .maquina("M400-1", 400, "400 C", 16, TODAS).maquina("M400-2", 400, "400 C", 16, TODAS)
                .maquina("M450-1", 450, "450 B", 16, EnumSet.of(Tonalidad.BLANCO, Tonalidad.CLARO, Tonalidad.MEDIO))
                .maquina("M300-1", 300, "300 B", 16, EnumSet.of(Tonalidad.BLANCO))
                .maquina("M300-2", 300, "300 B", 16, EnumSet.of(Tonalidad.BLANCO, Tonalidad.CLARO))
                .maquina("M200-1", 200, "200 C", 16, TODAS).maquina("M200-2", 200, "200 C", 16, TODAS)
                .maquina("M200-3", 200, "200 C", 16, TODAS);
    }

    @Test
    @DisplayName("PU-40 Partida 300 blanca: compatible con las dos máquinas de 300")
    void blanca300() {
        assertArrayEquals(new String[] { "M300-1", "M300-2" }, compatibles("X", 280, Tonalidad.BLANCO, "300 B"));
    }

    @Test
    @DisplayName("PU-41 Partida 300 clara: compatible solo con la de 300 que acepta Claro")
    void clara300() {
        assertArrayEquals(new String[] { "M300-2" }, compatibles("X", 280, Tonalidad.CLARO, "300 B"));
    }

    @Test
    @DisplayName("PU-42 Partida 300 media: Bloqueada con el motivo «Ninguna máquina de su capacidad acepta su tonalidad»")
    void media300() {
        InstanciaSemana I = instancia("X", 280, Tonalidad.MEDIO, "300 B");
        assertEquals(0, I.P);
        assertEquals(1, I.excluidas.size());
        assertEquals("Ninguna máquina de su capacidad acepta su tonalidad", I.excluidas.get(0).motivo());
        assertEquals(PartidaExcluida.SIN_MAQUINA_COMPATIBLE, I.excluidas.get(0).motivo());
    }

    @Test
    @DisplayName("PU-43 Partida blanca de 540 kg con barca 600: compatible con las dos de 600")
    void blanca600() {
        assertArrayEquals(new String[] { "M600-1", "M600-2" }, compatibles("X", 540, Tonalidad.BLANCO, "600 B"));
    }

    @Test
    @DisplayName("PU-44 La letra de la barca no influye: \"600 C\", \"600 B\" y \"600\" tienen las mismas máquinas")
    void letraNoInfluye() {
        for (Tonalidad t : Tonalidad.values()) {
            String[] conC = compatibles("X", 540, t, "600 C");
            assertArrayEquals(conC, compatibles("X", 540, t, "600 B"), t.name());
            assertArrayEquals(conC, compatibles("X", 540, t, "600"), t.name());
        }
        assertArrayEquals(compatibles("X", 280, Tonalidad.BLANCO, "300 B"), compatibles("X", 280, Tonalidad.BLANCO, "300 C"));
    }

    @Test
    @DisplayName("PU-45 Sin columna de tonalidades la máquina acepta las cuatro; barca sin número: ninguna compatible")
    void porDefectoYBarcaSinNumero() {
        InstanciaSemana I = ConstructorInstancia.construir(new DatosPrueba().maquina("M200-1", 200, 16).pedido("P1", dia(20))
                .partida("OSC", "P1", 180, Tonalidad.OSCURO, "200 B").componente("OSC", "A", 180)
                .partida("RARA", "P1", 180, Tonalidad.CLARO, "B200").componente("RARA", "A", 180)
                .stock("A", 1000).construir());
        assertEquals(1, I.P);
        assertEquals("OSC", I.partidas[0].codigo());
        assertTrue(I.excluidas.stream().anyMatch(e -> e.partida().codigo().equals("RARA")
                && e.motivo().equals(PartidaExcluida.SIN_MAQUINA_COMPATIBLE)));
    }
}
