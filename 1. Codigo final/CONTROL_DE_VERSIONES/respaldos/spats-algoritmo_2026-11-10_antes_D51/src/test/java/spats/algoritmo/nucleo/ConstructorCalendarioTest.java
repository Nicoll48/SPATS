package spats.algoritmo.nucleo;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static spats.algoritmo.DatosPrueba.dia;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import spats.algoritmo.DatosPrueba;
import spats.algoritmo.ReglaReferencia;
import spats.algoritmo.entrada.DatosSemana;
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

    /*
     * D-48: reparto proporcional y escalonado. Con una sola máquina (M = 1) y la semana del 14/09/2026, el
     * desfase es ((0 + 38) mod 1 + 0.5) / 1 = 0.5. Para meta 16 en 7 días, lo acumulado al cerrar cada día es
     * floor(16·j/7 + 0.5) = 2, 5, 7, 9, 11, 14 y 16 el último día: 2, 3, 2, 2, 2, 3, 2.
     */
    @Test
    @DisplayName("PU-11 Distribución diaria 2, 3, 2, 2, 2, 3, 2 para un objetivo de 16 partidas")
    void distribucionDiaria() {
        Evaluacion e = evaluar(ConstructorInstancia.construir(unaMaquina(16).construir()));
        assertArrayEquals(new int[] { 2, 3, 2, 2, 2, 3, 2 }, e.calendario.partidasPorDia[0]);
        assertEquals(0, e.sobrantes);
    }

    @Test
    @DisplayName("PU-12 Las partidas que exceden el objetivo son Sobrantes en su primer día disponible")
    void sobrantesPorExceso() {
        InstanciaSemana I = ConstructorInstancia.construir(unaMaquina(18).construir());
        Evaluacion e = evaluar(I);
        assertArrayEquals(new int[] { 2, 3, 2, 2, 2, 3, 2 }, e.calendario.partidasPorDia[0]);   // meta = min(16, 18)
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
        // meta 14 en 6 días hábiles: floor(14·j/6 + 0.5) = 2, 5, 7, 9, 12 y 14 → 2, 3, 2, 2, 3, 2
        InstanciaSemana I = ConstructorInstancia.construir(unaMaquina(14).indisponible("M200-1", dia(3)).construir());
        Evaluacion e = evaluar(I);
        assertArrayEquals(new int[] { 2, 3, 0, 2, 2, 3, 2 }, e.calendario.partidasPorDia[0]);
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

    /** n máquinas de capacidades 200, 201… (cada una con su barca), cada una con 16 partidas disponibles el lunes. */
    static DatosPrueba planta(int maquinas) {
        DatosPrueba d = new DatosPrueba().pedido("P1", dia(20)).stock("A", 100.0 * 16 * maquinas);
        for (int k = 0; k < maquinas; k++) {
            int capacidad = 200 + k;
            d.maquina("M" + capacidad, capacidad, 16);
            for (int i = 1; i <= 16; i++) d.partida("K" + k + "-" + i, "P1", 100, Tonalidad.CLARO, capacidad + " C").componente("K" + k + "-" + i, "A", 100);
        }
        return d;
    }

    static int[] totalPorDia(Evaluacion e) {
        int[] total = new int[e.calendario.partidasPorDia[0].length];
        for (int[] maquina : e.calendario.partidasPorDia)
            for (int d = 0; d < total.length; d++) total[d] += maquina[d];
        return total;
    }

    @Test
    @DisplayName("PU-38 Once máquinas con 16 partidas: cada una recibe 16 y el total diario de la planta es parejo")
    void plantaPareja() {
        InstanciaSemana I = ConstructorInstancia.construir(planta(11).construir());
        Evaluacion e = evaluar(I);
        for (int m = 0; m < I.M; m++) {
            assertEquals(16, Arrays.stream(e.calendario.partidasPorDia[m]).sum(), "máquina " + m);
            assertTrue(Arrays.stream(e.calendario.partidasPorDia[m]).max().getAsInt() <= 3, "máximo diario en la máquina " + m);
        }
        int[] total = totalPorDia(e);
        // 176 partidas en 7 días: 25 o 26 por día (los desfases de las once máquinas son (k + 0.5)/11)
        assertTrue(Arrays.stream(total).max().getAsInt() - Arrays.stream(total).min().getAsInt() <= 1, Arrays.toString(total));
        assertEquals(0, e.sobrantes);
    }

    @Test
    @DisplayName("PU-39 En dos semanas seguidas, una máquina recibe sus 3 partidas en días distintos y la planta sigue pareja")
    void rotacionSemanal() {
        DatosSemana semana1 = planta(11).construir();
        DatosSemana semana2 = planta(11).construir();
        semana2.parametros().setFechaInicioSemana(DatosPrueba.LUNES.plusWeeks(1));
        Evaluacion e1 = evaluar(ConstructorInstancia.construir(semana1));
        Evaluacion e2 = evaluar(ConstructorInstancia.construir(semana2));
        for (Evaluacion e : List.of(e1, e2)) {
            int[] total = totalPorDia(e);
            assertTrue(Arrays.stream(total).max().getAsInt() - Arrays.stream(total).min().getAsInt() <= 1, Arrays.toString(total));
        }
        // máquina 0: semana 38 → desfase 5.5/11 = 0.5 (3 el martes y el sábado); semana 39 → 6.5/11 (3 el martes y el viernes)
        assertArrayEquals(new int[] { 2, 3, 2, 2, 2, 3, 2 }, e1.calendario.partidasPorDia[0]);
        assertArrayEquals(new int[] { 2, 3, 2, 2, 3, 2, 2 }, e2.calendario.partidasPorDia[0]);
        assertNotEquals(diasConTres(e1.calendario.partidasPorDia[0]), diasConTres(e2.calendario.partidasPorDia[0]));
    }

    static List<Integer> diasConTres(int[] dias) {
        return IntStream.range(0, dias.length).filter(d -> dias[d] == 3).boxed().toList();
    }
}
