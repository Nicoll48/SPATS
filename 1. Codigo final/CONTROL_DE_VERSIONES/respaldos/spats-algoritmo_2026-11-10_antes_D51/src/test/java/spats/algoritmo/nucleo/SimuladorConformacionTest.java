package spats.algoritmo.nucleo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static spats.algoritmo.DatosPrueba.dia;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import spats.algoritmo.DatosPrueba;
import spats.algoritmo.ReglaReferencia;
import spats.algoritmo.entrada.Tonalidad;

class SimuladorConformacionTest {

    /** Estrategia de prueba que elige la última partida empatada y cuenta sus consultas. */
    static final class ElegirUltima implements EstrategiaDecision {
        int consultasPartida = 0, consultasMaquina = 0;
        @Override public int elegirPartida(InstanciaSemana I, int[] e, int n, int[] c) { consultasPartida++; return e[n - 1]; }
        @Override public int elegirMaquina(InstanciaSemana I, int p, int[] c) { consultasMaquina++; return I.compat[p][I.compat[p].length - 1]; }
    }

    static int idx(InstanciaSemana I, String codigo) {
        for (int p = 0; p < I.P; p++) if (I.partidas[p].codigo().equals(codigo)) return p;
        throw new AssertionError("No existe " + codigo);
    }

    @Test
    @DisplayName("PU-06 Una partida solo se conforma completa y el sobrante de tela queda en memoria")
    void conformacionCompletaYMemoria() {
        // 600 kg el lunes y tres partidas de 500 kg; el miércoles llegan 400 kg más
        InstanciaSemana I = ConstructorInstancia.construir(new DatosPrueba().maquina("M600-1", 600, 16).pedido("P1", dia(20))
                .partida("X1", "P1", 500, Tonalidad.CLARO, "600 C").componente("X1", "A", 500)
                .partida("X2", "P1", 500, Tonalidad.CLARO, "600 C").componente("X2", "A", 500)
                .partida("X3", "P1", 500, Tonalidad.CLARO, "600 C").componente("X3", "A", 500)
                .tejido("A", "P1", LocalDate.of(2026, 9, 12), 600)
                .tejido("A", "P1", LocalDate.of(2026, 9, 15), 400).construir());
        Solucion s = SimuladorConformacion.simular(I, new ReglaReferencia());
        assertEquals(1, s.diaConformacion[idx(I, "X1")]);
        assertEquals(3, s.diaConformacion[idx(I, "X2")]);
        assertEquals(-1, s.diaConformacion[idx(I, "X3")]);
        assertEquals(0.0, s.stockFinal[0], 1e-9);

        // con solo los 600 kg del lunes, quedan 100 kg guardados para la semana siguiente
        InstanciaSemana I2 = ConstructorInstancia.construir(new DatosPrueba().maquina("M600-1", 600, 16).pedido("P1", dia(20))
                .partida("X1", "P1", 500, Tonalidad.CLARO, "600 C").componente("X1", "A", 500)
                .partida("X2", "P1", 500, Tonalidad.CLARO, "600 C").componente("X2", "A", 500)
                .tejido("A", "P1", LocalDate.of(2026, 9, 12), 600).construir());
        Solucion s2 = SimuladorConformacion.simular(I2, new ReglaReferencia());
        assertEquals(100.0, s2.stockFinal[0], 1e-9);
        assertEquals(-1, s2.maquina[idx(I2, "X2")]);
    }

    @Test
    @DisplayName("PU-07 Una partida con complemento espera a que llegue la tela de todos sus artículos")
    void complemento() {
        InstanciaSemana I = ConstructorInstancia.construir(new DatosPrueba().maquina("M400-1", 400, 16).pedido("P1", dia(20))
                .partida("Y", "P1", 350, Tonalidad.MEDIO, "400 C").componente("Y", "A", 300).componente("Y", "B", 50)
                .tejido("A", "P1", LocalDate.of(2026, 9, 12), 300)     // disponible lunes
                .tejido("B", "P1", LocalDate.of(2026, 9, 16), 50)      // disponible jueves
                .construir());
        Solucion s = SimuladorConformacion.simular(I, new ReglaReferencia());
        assertEquals(4, s.diaConformacion[0]);
    }

    @Test
    @DisplayName("PU-08 Prioridad por la fecha de entrega del pedido")
    void prioridadFechaEntrega() {
        InstanciaSemana I = ConstructorInstancia.construir(new DatosPrueba().maquina("M600-1", 600, 16)
                .pedido("TARDE", dia(40)).pedido("PRONTO", dia(20))
                .partida("X", "TARDE", 500, Tonalidad.CLARO, "600 C").componente("X", "A", 500)
                .partida("Y", "PRONTO", 500, Tonalidad.CLARO, "600 C").componente("Y", "A", 500)
                .stock("A", 500).construir());
        ElegirUltima e = new ElegirUltima();
        Solucion s = SimuladorConformacion.simular(I, e);
        assertEquals(1, s.diaConformacion[idx(I, "Y")]);
        assertEquals(-1, s.diaConformacion[idx(I, "X")]);
        assertEquals(0, e.consultasPartida);   // no hubo empate: decidió la fecha de entrega
    }

    @Test
    @DisplayName("PU-09 Con la misma fecha de entrega, la elección la toma el algoritmo")
    void empateLoDecideLaEstrategia() {
        DatosPrueba datos = new DatosPrueba().maquina("M600-1", 600, 16).pedido("P1", dia(20)).pedido("P2", dia(20))
                .partida("X", "P1", 500, Tonalidad.CLARO, "600 C").componente("X", "A", 500)
                .partida("Y", "P2", 500, Tonalidad.CLARO, "600 C").componente("Y", "A", 500)
                .stock("A", 500);
        InstanciaSemana I = ConstructorInstancia.construir(datos.construir());
        Solucion primera = SimuladorConformacion.simular(I, new ReglaReferencia());
        ElegirUltima e = new ElegirUltima();
        Solucion ultima = SimuladorConformacion.simular(I, e);
        assertEquals(1, primera.diaConformacion[idx(I, "X")]);
        assertEquals(1, ultima.diaConformacion[idx(I, "Y")]);
        assertEquals(1, e.consultasPartida);
    }

    @Test
    @DisplayName("PU-10 La máquina se elige entre las de la barca de la partida")
    void maquinaDeLaBarca() {
        InstanciaSemana I = ConstructorInstancia.construir(new DatosPrueba()
                .maquina("M400-1", 400, 16).maquina("M400-2", 400, 16).maquina("M200-1", 200, 16).pedido("P1", dia(20))
                .partida("G", "P1", 350, Tonalidad.CLARO, "400 C").componente("G", "A", 350)
                .partida("C", "P1", 150, Tonalidad.CLARO, "200 C").componente("C", "A", 150)
                .stock("A", 500).construir());
        ElegirUltima e = new ElegirUltima();
        Solucion s = SimuladorConformacion.simular(I, e);
        assertEquals(1, s.maquina[idx(I, "G")]);    // M400-2: la última de su barca
        assertEquals(2, s.maquina[idx(I, "C")]);    // M200-1: única máquina de su barca
        assertEquals(1, e.consultasMaquina);        // solo se consulta cuando hay más de una máquina
        assertTrue(s.orden[idx(I, "G")] > 0 && s.orden[idx(I, "C")] > 0);
    }
}
