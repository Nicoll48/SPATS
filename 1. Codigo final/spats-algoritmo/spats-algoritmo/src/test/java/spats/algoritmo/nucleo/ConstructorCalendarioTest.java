package spats.algoritmo.nucleo;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static spats.algoritmo.DatosPrueba.dia;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import spats.algoritmo.DatosPrueba;
import spats.algoritmo.ReglaReferencia;
import spats.algoritmo.entrada.Tonalidad;

class ConstructorCalendarioTest {

    /** Una máquina de 200 kg con objetivo 16 y n partidas de 100 kg disponibles desde el lunes. */
    static DatosPrueba unaMaquina(int n) {
        return new DatosPrueba().maquina("M200-1", 200, 16).pedido("P1", dia(20))
                .partidasIguales("X", n, Tonalidad.CLARO).stock("A", 100.0 * n);
    }

    static Evaluacion evaluar(InstanciaSemana I) {
        Solucion s = SimuladorConformacion.simular(I, new ReglaReferencia());
        Evaluacion e = new FuncionObjetivo(1, 1, 0.25).evaluar(I, s);
        assertTrue(Validador.validar(I, s, e).isEmpty(), "la solución debe ser factible");
        return e;
    }

    @Test
    @DisplayName("PU-11 Distribución diaria 3, 3, 2, 2, 2, 2, 2 para un objetivo de 16 partidas")
    void distribucionDiaria() {
        Evaluacion e = evaluar(ConstructorInstancia.construir(unaMaquina(16).construir()));
        assertArrayEquals(new int[] { 3, 3, 2, 2, 2, 2, 2 }, e.calendario.partidasPorDia[0]);
        assertEquals(0, e.sobrantes);
    }

    @Test
    @DisplayName("PU-12 Las partidas que exceden el objetivo son Sobrantes en su primer día disponible")
    void sobrantesPorExceso() {
        InstanciaSemana I = ConstructorInstancia.construir(unaMaquina(18).construir());
        Evaluacion e = evaluar(I);
        assertEquals(2, e.sobrantes);
        assertEquals(16, e.asignadas);
        for (int p = 0; p < I.P; p++)
            if (e.calendario.sobrante[p]) {
                assertTrue(I.partidas[p].ordenRegistro() > 16, "Sobrantes: las últimas en llegar");
                assertEquals(1, e.calendario.dia[p]);
            }
    }

    @Test
    @DisplayName("PU-13 Un día indisponible no recibe partidas y el objetivo baja a 14")
    void diaIndisponible() {
        InstanciaSemana I = ConstructorInstancia.construir(unaMaquina(14).indisponible("M200-1", dia(3)).construir());
        Evaluacion e = evaluar(I);
        assertArrayEquals(new int[] { 3, 3, 0, 2, 2, 2, 2 }, e.calendario.partidasPorDia[0]);
        assertEquals(0, e.sobrantes);
        assertEquals(0, e.desbalanceDiario);
    }

    @Test
    @DisplayName("PU-14 Partidas que llegan cuando los días restantes ya están llenos son Sobrantes")
    void llegadaTardia() {
        InstanciaSemana I = ConstructorInstancia.construir(new DatosPrueba().maquina("M200-1", 200, 16).pedido("P1", dia(20))
                .partidasIguales("X", 16, Tonalidad.CLARO)
                .tejido("A", "P1", LocalDate.of(2026, 9, 17), 1600)   // jueves → disponible viernes
                .construir());
        Evaluacion e = evaluar(I);
        assertArrayEquals(new int[] { 0, 0, 0, 0, 3, 3, 3 }, e.calendario.partidasPorDia[0]);
        assertEquals(7, e.sobrantes);
        for (int p = 0; p < I.P; p++) if (e.calendario.sobrante[p]) assertEquals(5, e.calendario.dia[p]);
        assertEquals(0, e.desbalanceSemanal);    // las Sobrantes cuentan para el objetivo semanal
        assertEquals(16, e.desbalanceDiario);    // lunes a jueves sin partidas: 4 × 2²
    }
}
