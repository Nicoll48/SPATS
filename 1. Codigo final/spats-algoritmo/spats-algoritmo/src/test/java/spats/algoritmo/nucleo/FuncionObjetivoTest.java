package spats.algoritmo.nucleo;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static spats.algoritmo.DatosPrueba.dia;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import spats.algoritmo.DatosPrueba;
import spats.algoritmo.ReglaReferencia;
import spats.algoritmo.entrada.Tonalidad;

class FuncionObjetivoTest {

    @Test
    @DisplayName("PU-15 Cálculo del desbalance semanal y diario")
    void desbalance() {
        // 10 partidas para una máquina con objetivo 16: 3, 3, 2, 2, 0, 0, 0
        InstanciaSemana I = ConstructorInstancia.construir(new DatosPrueba().maquina("M200-1", 200, 16).pedido("P1", dia(20))
                .partidasIguales("X", 10, Tonalidad.CLARO).stock("A", 1000).construir());
        Solucion s = SimuladorConformacion.simular(I, new ReglaReferencia());
        Evaluacion e = new FuncionObjetivo(1, 1, 0.25).evaluar(I, s);
        assertArrayEquals(new int[] { 3, 3, 2, 2, 0, 0, 0 }, e.calendario.partidasPorDia[0]);
        assertEquals(36, e.desbalanceSemanal);    // (10 − 16)²
        assertEquals(12, e.desbalanceDiario);     // tres días con 2 partidas faltantes: 3 × 2²
        assertEquals(0, e.lavados);
        assertEquals(48.0, e.F, 1e-9);
        assertEquals(84.0, new FuncionObjetivo(2, 1, 0.25).evaluar(I, s).F, 1e-9);   // α = 2: 2 · 36 + 12
    }

    @Test
    @DisplayName("PU-16 Lavados al pasar de una tonalidad a otra más clara")
    void lavados() {
        InstanciaSemana I = ConstructorInstancia.construir(new DatosPrueba().maquina("M200-1", 200, 16).pedido("P1", dia(20))
                .partida("OSC", "P1", 100, Tonalidad.OSCURO, "200 C").componente("OSC", "A", 100)
                .partida("CLA", "P1", 100, Tonalidad.CLARO, "200 C").componente("CLA", "A", 100)
                .partida("MED", "P1", 100, Tonalidad.MEDIO, "200 C").componente("MED", "A", 100)
                .stock("A", 300).construir());
        Solucion s = SimuladorConformacion.simular(I, new ReglaReferencia());
        Evaluacion e = new FuncionObjetivo(1, 1, 0.25).evaluar(I, s);
        assertEquals(1, e.lavados);                           // Oscuro → Claro
        assertFalse(e.requiereLavado[0]);
        assertTrue(e.requiereLavado[1]);
        assertFalse(e.requiereLavado[2]);                     // Claro → Medio no requiere lavado
        assertArrayEquals(new int[] { 3, 0, 0, 0, 0, 0, 0 }, e.calendario.partidasPorDia[0]);
        assertEquals(169 + 24 + 0.25, e.F, 1e-9);             // E_S = (3 − 16)², E_D = 6 días × 2², Λ = 1
    }
}
