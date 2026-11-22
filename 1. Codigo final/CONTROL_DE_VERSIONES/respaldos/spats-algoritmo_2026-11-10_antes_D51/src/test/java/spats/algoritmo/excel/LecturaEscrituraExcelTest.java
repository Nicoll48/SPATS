package spats.algoritmo.excel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import spats.algoritmo.PlanificadorSemanal;
import spats.algoritmo.entrada.DatosSemana;
import spats.algoritmo.entrada.EstadoPartida;
import spats.algoritmo.salida.ResultadoPlanificacion;

@Tag("integracion")
class LecturaEscrituraExcelTest {

    static final Path DATOS = Path.of("datos");

    @Test
    @DisplayName("PI-01 Lectura de los datos de una semana desde Excel")
    void lectura() throws Exception {
        LectorDatosExcel.Configuracion c = LectorDatosExcel.leer(DATOS.resolve("S38-B.xlsx"));
        DatosSemana d = c.datos();
        assertEquals(LocalDate.of(2026, 9, 14), d.parametros().getFechaInicioSemana());
        assertEquals(9, d.maquinas().size());
        assertEquals(537, d.partidas().size());
        assertEquals(490, d.partidas().stream().filter(p -> p.estado() == EstadoPartida.PENDIENTE).count());
        assertEquals(88, d.pedidos().size());
        assertEquals(930, d.componentes().size());
        assertEquals(151, d.proyeccion().size());
        assertEquals(31, d.stockInicial().size());
        assertEquals(0, d.indisponibilidades().size());
        assertEquals(20, c.parametrosACO().getHormigas());
        assertEquals(0.9, c.parametrosACO().getRho(), 0);
        assertEquals(6000, c.parametrosACO().getEvaluaciones());

        DatosSemana i = LectorDatosExcel.leer(DATOS.resolve("S38-I.xlsx")).datos();
        assertEquals(1, i.indisponibilidades().size());
        assertEquals("M300-1", i.indisponibilidades().get(0).maquina());
        assertEquals(LocalDate.of(2026, 9, 16), i.indisponibilidades().get(0).fecha());
    }

    @Test
    @DisplayName("PI-02 Escritura del resultado en Excel y lectura posterior")
    void escritura() throws Exception {
        LectorDatosExcel.Configuracion c = LectorDatosExcel.leer(DATOS.resolve("S39-B.xlsx"));
        ResultadoPlanificacion r = new PlanificadorSemanal().planificar(c.datos(), c.parametrosACO().setEvaluaciones(200));
        Path tmp = Files.createTempDirectory("spats").resolve("S39-B_resultado.xlsx");
        EscritorResultadoExcel.guardar(r, tmp);
        try (LectorXlsx x = new LectorXlsx(tmp)) {
            assertEquals(List.of("Calendario", "Asignaciones", "Resumen por maquina", "Bloqueadas", "Indicadores", "StockFinal"), x.hojas());
            assertEquals(r.indicadores().conformadas(), x.leerHoja("Asignaciones").size());
            assertEquals(9, x.leerHoja("Calendario").size());
            assertEquals(r.bloqueadas().size(), x.leerHoja("Bloqueadas").size());
            Map<String, String> f = x.leerHoja("Indicadores").get(0);
            assertEquals(r.indicadores().funcionObjetivo(), Double.parseDouble(f.get("valor")), 1e-9);
            assertTrue(x.leerHoja("Indicadores").stream().anyMatch(m -> m.get("indicador").equals("Factible") && m.get("valor").equals("Sí")));
        }
    }
}
