package spats.algoritmo.nucleo;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static spats.algoritmo.DatosPrueba.dia;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import spats.algoritmo.DatosPrueba;
import spats.algoritmo.ReglaReferencia;
import spats.algoritmo.entrada.Tonalidad;

class ValidadorTest {

    static final FuncionObjetivo FO = new FuncionObjetivo(1, 1, 0.25);

    /** Solución construida a mano: conforma las partidas indicadas el lunes, en ese orden, en la máquina m. */
    static Solucion manual(InstanciaSemana I, int m, int... partidas) {
        Solucion s = new Solucion();
        s.diaConformacion = new int[I.P]; s.maquina = new int[I.P]; s.orden = new int[I.P];
        Arrays.fill(s.diaConformacion, -1); Arrays.fill(s.maquina, -1); Arrays.fill(s.orden, -1);
        int k = 0;
        for (int p : partidas) { s.diaConformacion[p] = 1; s.maquina[p] = m; s.orden[p] = ++k; }
        s.stockFinal = new double[I.B];
        return s;
    }

    static boolean contiene(List<String> errores, String inicio) {
        return errores.stream().anyMatch(e -> e.startsWith(inicio));
    }

    @Test
    @DisplayName("PU-17 Una solución construida por el simulador es factible")
    void solucionFactible() {
        InstanciaSemana I = ConstructorInstancia.construir(new DatosPrueba().maquina("M200-1", 200, 16).maquina("M200-2", 200, 16)
                .pedido("P1", dia(20)).partidasIguales("X", 20, Tonalidad.MEDIO).stock("A", 2000).construir());
        Solucion s = SimuladorConformacion.simular(I, new ReglaReferencia());
        assertTrue(Validador.validar(I, s, FO.evaluar(I, s)).isEmpty());
    }

    @Test
    @DisplayName("PU-18 Detecta una partida asignada a una máquina fuera de su barca")
    void maquinaIncompatible() {
        InstanciaSemana I = ConstructorInstancia.construir(new DatosPrueba().maquina("M200-1", 200, 16).maquina("M400-1", 400, 16)
                .pedido("P1", dia(20)).partidasIguales("X", 1, Tonalidad.CLARO).stock("A", 100).construir());
        Solucion s = manual(I, 1, 0);   // partida de barca 200 C en la máquina de 400 kg
        assertTrue(contiene(Validador.validar(I, s, FO.evaluar(I, s)), "Asignación"));
    }

    @Test
    @DisplayName("PU-19 Detecta que se adelantó una partida con fecha de entrega posterior")
    void prioridad() {
        InstanciaSemana I = ConstructorInstancia.construir(new DatosPrueba().maquina("M600-1", 600, 16)
                .pedido("TARDE", dia(40)).pedido("PRONTO", dia(20))
                .partida("X", "TARDE", 500, Tonalidad.CLARO, "600 C").componente("X", "A", 500)
                .partida("Y", "PRONTO", 500, Tonalidad.CLARO, "600 C").componente("Y", "A", 500)
                .stock("A", 500).construir());
        Solucion s = manual(I, 0, 0);   // conforma X en lugar de Y
        assertTrue(contiene(Validador.validar(I, s, FO.evaluar(I, s)), "Prioridad"));
    }

    @Test
    @DisplayName("PU-20 Detecta una partida conformada sin tela suficiente")
    void sinTela() {
        InstanciaSemana I = ConstructorInstancia.construir(new DatosPrueba().maquina("M600-1", 600, 16).pedido("P1", dia(20))
                .partida("X", "P1", 500, Tonalidad.CLARO, "600 C").componente("X", "A", 500)
                .partida("Y", "P1", 500, Tonalidad.CLARO, "600 C").componente("Y", "A", 500)
                .stock("A", 500).construir());
        Solucion s = manual(I, 0, 0, 1);   // conforma ambas con 500 kg
        assertTrue(contiene(Validador.validar(I, s, FO.evaluar(I, s)), "Disponibilidad de tela"));
    }

    @Test
    @DisplayName("PU-21 Detecta una partida completable que quedó sin conformar")
    void inclusion() {
        InstanciaSemana I = ConstructorInstancia.construir(new DatosPrueba().maquina("M600-1", 600, 16).pedido("P1", dia(20))
                .partida("X", "P1", 500, Tonalidad.CLARO, "600 C").componente("X", "A", 500)
                .stock("A", 500).construir());
        Solucion s = manual(I, 0);       // no conforma ninguna
        assertTrue(contiene(Validador.validar(I, s, FO.evaluar(I, s)), "Inclusión"));
    }
}
