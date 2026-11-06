package spats.algoritmo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static spats.algoritmo.DatosPrueba.dia;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import spats.algoritmo.aco.ParametrosACO;
import spats.algoritmo.entrada.DatosSemana;
import spats.algoritmo.entrada.EstadoPartida;
import spats.algoritmo.entrada.Partida;
import spats.algoritmo.entrada.Tonalidad;
import spats.algoritmo.nucleo.PartidaExcluida;
import spats.algoritmo.salida.AsignacionPartida;
import spats.algoritmo.salida.PartidaBloqueada;
import spats.algoritmo.salida.ResultadoPlanificacion;

class PlanificadorSemanalTest {

    static DatosSemana semana() {
        return new DatosPrueba()
                .maquina("M200-1", 200, 16).maquina("M200-2", 200, 16).maquina("M400-1", 400, 16)
                .pedido("P1", dia(17)).pedido("P2", dia(29))
                .partida("X1", "P1", 180, Tonalidad.CLARO, "200 C").componente("X1", "A", 180)
                .partida("X2", "P1", 180, Tonalidad.OSCURO, "200 C").componente("X2", "A", 180)
                .partida("X3", "P1", 180, Tonalidad.MEDIO, "200 C").componente("X3", "A", 180)
                .partida("X4", "P1", 180, Tonalidad.BLANCO, "200 C").componente("X4", "A", 180)
                .partida("Y1", "P2", 350, Tonalidad.MEDIO, "400 C").componente("Y1", "B", 300).componente("Y1", "C", 50)
                .partida("Z1", "P2", 550, Tonalidad.CLARO, "600 C").componente("Z1", "A", 100)
                .partida("W1", "P1", 180, Tonalidad.CLARO, "200 C").componente("W1", "D", 180)
                .partida("V1", "P1", 180, Tonalidad.CLARO, "200 C", EstadoPartida.PLANIFICADA).componente("V1", "A", 180)
                .stock("A", 400)                                              // memoria de la semana anterior
                .tejido("A", "P2", LocalDate.of(2026, 9, 12), 300)            // otro pedido, mismo artículo
                .tejido("B", "P2", LocalDate.of(2026, 9, 15), 300)            // disponible miércoles
                .tejido("C", "P2", LocalDate.of(2026, 9, 16), 50)             // disponible jueves
                .tejido("D", "P1", LocalDate.of(2026, 9, 15), 100)
                .construir();
    }

    @Test
    @DisplayName("PU-28 Planificación completa de una semana pequeña")
    void planificacion() {
        DatosSemana datos = semana();
        ResultadoPlanificacion r = new PlanificadorSemanal().planificar(datos, new ParametrosACO().setEvaluaciones(300));
        assertTrue(r.indicadores().factible(), r.indicadores().incumplimientos().toString());
        assertEquals(4, r.indicadores().conformadas());                // 3 de las X (700 kg de A) y Y1
        assertEquals(4, r.asignaciones().size());
        for (AsignacionPartida a : r.asignaciones()) {
            String barca = datos.partidas().stream().filter(p -> p.codigo().equals(a.partida())).findFirst().orElseThrow().barca();
            assertTrue(a.maquina().startsWith("M" + barca.split(" ")[0]), "máquina de su barca");
        }
        AsignacionPartida y = r.asignaciones().stream().filter(a -> a.partida().equals("Y1")).findFirst().orElseThrow();
        assertEquals("M400-1", y.maquina());
        assertEquals(dia(4), y.fechaDisponible());                     // el complemento llega el jueves

        List<PartidaBloqueada> nc = r.bloqueadas();
        assertEquals(3, nc.size());
        assertTrue(nc.stream().anyMatch(p -> p.partida().equals("Z1") && p.motivo().equals(PartidaExcluida.SIN_MAQUINA)));
        assertTrue(nc.stream().anyMatch(p -> p.partida().equals("W1") && p.motivo().equals(PartidaExcluida.SIN_TELA)));
        assertTrue(nc.stream().anyMatch(p -> p.partida().startsWith("X") && p.motivo().equals(PartidaBloqueada.TELA_PRIORIZADA)));
        assertTrue(nc.stream().noneMatch(p -> p.partida().equals("V1")));

        // memoria para la semana siguiente
        assertEquals(2, r.estadoSiguiente().stockFinal().size());
        assertEquals(160.0, r.estadoSiguiente().stockFinal().stream().filter(s -> s.claveArticulo().equals("A")).findFirst().orElseThrow().kg(), 1e-9);
        assertEquals(100.0, r.estadoSiguiente().stockFinal().stream().filter(s -> s.claveArticulo().equals("D")).findFirst().orElseThrow().kg(), 1e-9);
        List<Partida> siguiente = r.estadoSiguiente().actualizar(datos.partidas());
        assertEquals(5, siguiente.stream().filter(p -> p.estado() == EstadoPartida.PLANIFICADA).count());   // 4 nuevas + V1
    }
}
