package spats.algoritmo.nucleo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static spats.algoritmo.DatosPrueba.dia;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import spats.algoritmo.DatosPrueba;
import spats.algoritmo.entrada.DatosSemana;
import spats.algoritmo.entrada.EstadoPartida;
import spats.algoritmo.entrada.ParametrosPlanificacion;
import spats.algoritmo.entrada.RegistroProyeccion;
import spats.algoritmo.entrada.Tonalidad;

class ConstructorInstanciaTest {

    @Test
    @DisplayName("PU-01 Fecha de disponibilidad de la tela según el día de tejido")
    void fechaDisponible() {
        assertEquals(LocalDate.of(2026, 9, 15), RegistroProyeccion.fechaDisponible(LocalDate.of(2026, 9, 14))); // lunes → martes
        assertEquals(LocalDate.of(2026, 9, 17), RegistroProyeccion.fechaDisponible(LocalDate.of(2026, 9, 16))); // miércoles → jueves
        assertEquals(LocalDate.of(2026, 9, 19), RegistroProyeccion.fechaDisponible(LocalDate.of(2026, 9, 18))); // viernes → sábado
        assertEquals(LocalDate.of(2026, 9, 14), RegistroProyeccion.fechaDisponible(LocalDate.of(2026, 9, 12))); // sábado → lunes
        assertEquals(LocalDate.of(2026, 9, 14), RegistroProyeccion.fechaDisponible(LocalDate.of(2026, 9, 13))); // domingo → lunes
    }

    @Test
    @DisplayName("PU-02 La tela se acumula por artículo, con stock inicial y llegadas diarias")
    void telaPorArticulo() {
        DatosSemana d = new DatosPrueba().maquina("M200-1", 200, 16).pedido("P1", dia(20)).pedido("P2", dia(30))
                .partida("X", "P1", 100, Tonalidad.CLARO, "200 C").componente("X", "A", 100)
                .stock("A", 100)
                .tejido("A", "P1", LocalDate.of(2026, 9, 12), 200)   // sábado → lunes (día 1)
                .tejido("A", "P2", LocalDate.of(2026, 9, 15), 50)    // martes → miércoles (día 3), otro pedido
                .construir();
        InstanciaSemana I = ConstructorInstancia.construir(d);
        assertEquals(1, I.B);
        assertEquals("A", I.bolsas[0]);
        assertEquals(100.0, I.stockInicial[0], 1e-9);
        assertEquals(200.0, I.llegadas[0][0], 1e-9);
        assertEquals(50.0, I.llegadas[0][2], 1e-9);
    }

    @Test
    @DisplayName("PU-03 Objetivo semanal, mínimo y máximo diario con un día indisponible")
    void objetivoConIndisponibilidad() {
        DatosSemana d = new DatosPrueba().maquina("M300-1", 300, 16).maquina("M300-2", 300, 16)
                .indisponible("M300-1", dia(3)).construir();
        InstanciaSemana I = ConstructorInstancia.construir(d);
        assertEquals(14, I.objetivo[0]);
        assertEquals(2, I.minimoDiario[0]);
        assertEquals(3, I.maximoDiario[0]);
        assertFalse(I.habil[0][2]);
        assertEquals(6, I.diasHabiles[0].length);
        assertEquals(16, I.objetivo[1]);
        assertEquals(2, I.minimoDiario[1]);
        assertEquals(3, I.maximoDiario[1]);
    }

    @Test
    @DisplayName("PU-04 Partidas candidatas, bloqueadas y no pendientes")
    void candidatasYBloqueadas() {
        DatosSemana d = new DatosPrueba().maquina("M200-1", 200, 16).pedido("P1", dia(20))
                .partida("OK", "P1", 200, Tonalidad.CLARO, "200 C").componente("OK", "A", 200)
                .partida("SINTELA", "P1", 200, Tonalidad.CLARO, "200 C").componente("SINTELA", "B", 500)
                .partida("SINMAQ", "P1", 600, Tonalidad.CLARO, "600 C").componente("SINMAQ", "A", 100)
                .partida("PLAN", "P1", 200, Tonalidad.CLARO, "200 C", EstadoPartida.PLANIFICADA).componente("PLAN", "A", 100)
                .stock("A", 300).stock("B", 300)
                .construir();
        InstanciaSemana I = ConstructorInstancia.construir(d);
        assertEquals(1, I.P);
        assertEquals("OK", I.partidas[0].codigo());
        assertEquals(2, I.excluidas.size());
        assertTrue(I.excluidas.stream().anyMatch(b -> b.partida().codigo().equals("SINTELA") && b.motivo().equals(PartidaExcluida.SIN_TELA)));
        assertTrue(I.excluidas.stream().anyMatch(b -> b.partida().codigo().equals("SINMAQ") && b.motivo().equals(PartidaExcluida.SIN_MAQUINA)));
    }

    @Test
    @DisplayName("PU-05 Datos inválidos se rechazan con un mensaje")
    void datosInvalidos() {
        DatosSemana base = new DatosPrueba().maquina("M200-1", 200, 16).construir();
        DatosSemana martes = new DatosSemana(new ParametrosPlanificacion(LocalDate.of(2026, 9, 15)), base.maquinas(),
                base.indisponibilidades(), base.pedidos(), base.partidas(), base.componentes(), base.proyeccion(), base.stockInicial());
        assertThrows(IllegalArgumentException.class, () -> ConstructorInstancia.construir(martes));
        DatosSemana repetida = new DatosPrueba().maquina("M200-1", 200, 16).pedido("P1", dia(20))
                .partida("X", "P1", 100, Tonalidad.CLARO, "200 C").partida("X", "P1", 100, Tonalidad.CLARO, "200 C").construir();
        assertThrows(IllegalArgumentException.class, () -> ConstructorInstancia.construir(repetida));
        DatosSemana sinMaquinas = new DatosPrueba().construir();
        assertThrows(IllegalArgumentException.class, () -> ConstructorInstancia.construir(sinMaquinas));
    }
}
