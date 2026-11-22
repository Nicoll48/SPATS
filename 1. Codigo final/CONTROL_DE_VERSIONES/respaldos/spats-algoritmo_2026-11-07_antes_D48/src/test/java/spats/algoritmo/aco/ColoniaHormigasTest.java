package spats.algoritmo.aco;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static spats.algoritmo.DatosPrueba.dia;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import spats.algoritmo.DatosPrueba;
import spats.algoritmo.entrada.Tonalidad;
import spats.algoritmo.nucleo.ConstructorInstancia;
import spats.algoritmo.nucleo.FuncionObjetivo;
import spats.algoritmo.nucleo.InstanciaSemana;
import spats.algoritmo.nucleo.Solucion;
import spats.algoritmo.nucleo.Validador;

class ColoniaHormigasTest {

    static final FuncionObjetivo FO = new FuncionObjetivo(1, 1, 0.25);

    /** Dos máquinas de la misma barca y ocho partidas disponibles el lunes. */
    static InstanciaSemana dosMaquinas() {
        return ConstructorInstancia.construir(new DatosPrueba().maquina("M200-1", 200, 16).maquina("M200-2", 200, 16)
                .pedido("P1", dia(20)).partidasIguales("X", 8, Tonalidad.CLARO).stock("A", 800).construir());
    }

    @Test
    @DisplayName("PU-22 Parámetros inválidos de la colonia se rechazan")
    void parametrosInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> new ColoniaHormigas(new ParametrosACO().setHormigas(0)));
        assertThrows(IllegalArgumentException.class, () -> new ColoniaHormigas(new ParametrosACO().setRho(1.0)));
        assertThrows(IllegalArgumentException.class, () -> new ColoniaHormigas(new ParametrosACO().setFeromonaInicial(0)));
        assertThrows(IllegalArgumentException.class, () -> new ColoniaHormigas(new ParametrosACO().setEvaluaciones(0)));
        assertThrows(IllegalArgumentException.class, () -> new ColoniaHormigas(new ParametrosACO().setTiempoMaximoSegundos(0)));
    }

    @Test
    @DisplayName("PU-23 Con la misma semilla se obtiene la misma solución")
    void reproducible() {
        InstanciaSemana I = dosMaquinas();
        ParametrosACO p = new ParametrosACO().setEvaluaciones(200).setSemilla(7);
        ResultadoBusqueda a = new ColoniaHormigas(p).buscar(I, FO), b = new ColoniaHormigas(p).buscar(I, FO);
        assertEquals(a.evaluacion().F, b.evaluacion().F, 0);
        assertArrayEquals(a.solucion().maquina, b.solucion().maquina);
        assertArrayEquals(a.solucion().orden, b.solucion().orden);
    }

    @Test
    @DisplayName("PU-24 La búsqueda respeta el número de evaluaciones")
    void presupuesto() {
        ResultadoBusqueda r = new ColoniaHormigas(new ParametrosACO().setEvaluaciones(45)).buscar(dosMaquinas(), FO);
        assertEquals(45, r.evaluaciones());
        assertEquals(3, r.iteraciones());        // 20 + 20 + 5 hormigas
    }

    @Test
    @DisplayName("PU-25 Evaporación y depósito de feromona")
    void feromona() {
        double[][] tau = { { 0.5, 0.5 }, { 0.5, 0.5 } };
        Solucion s = new Solucion();
        s.maquina = new int[] { 1, -1 };          // la partida 0 en la máquina 1; la partida 1 no se conformó
        ColoniaHormigas.actualizarFeromona(tau, s, 9.0, 0.9);
        assertEquals(0.45 + 0.1 / 10, tau[0][1], 1e-12);
        assertEquals(0.45, tau[0][0], 1e-12);
        assertEquals(0.45, tau[1][0], 1e-12);
        assertEquals(0.45, tau[1][1], 1e-12);
    }

    @Test
    @DisplayName("PU-26 La búsqueda se detiene al alcanzar el tiempo máximo")
    void tiempoMaximo() {
        ResultadoBusqueda r = new ColoniaHormigas(new ParametrosACO().setEvaluaciones(1_000_000).setTiempoMaximoSegundos(0.05))
                .buscar(dosMaquinas(), FO);
        assertTrue(r.detenidaPorTiempo());
        assertTrue(r.evaluaciones() >= 1 && r.evaluaciones() < 1_000_000);
        assertTrue(Validador.validar(dosMaquinas(), r.solucion(), r.evaluacion()).isEmpty());
    }

    @Test
    @DisplayName("PU-27 La colonia reparte la carga entre las máquinas de una misma barca")
    void equilibrio() {
        InstanciaSemana I = dosMaquinas();
        ResultadoBusqueda r = new ColoniaHormigas(new ParametrosACO().setEvaluaciones(400)).buscar(I, FO);
        int[] n = new int[I.M];
        for (int m : r.solucion().maquina) if (m >= 0) n[m]++;
        assertArrayEquals(new int[] { 4, 4 }, n, "carga por máquina: " + Arrays.toString(n));
        assertEquals(288, r.evaluacion().desbalanceSemanal);    // 2 × (4 − 16)²
    }
}
