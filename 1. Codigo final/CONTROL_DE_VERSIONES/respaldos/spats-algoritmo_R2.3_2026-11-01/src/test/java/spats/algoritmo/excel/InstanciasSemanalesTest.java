package spats.algoritmo.excel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import spats.algoritmo.PlanificadorSemanal;
import spats.algoritmo.ReglaReferencia;
import spats.algoritmo.aco.ColoniaHormigas;
import spats.algoritmo.aco.ParametrosACO;
import spats.algoritmo.aco.ResultadoBusqueda;
import spats.algoritmo.entrada.DatosSemana;
import spats.algoritmo.nucleo.ConstructorInstancia;
import spats.algoritmo.nucleo.Evaluacion;
import spats.algoritmo.nucleo.FuncionObjetivo;
import spats.algoritmo.nucleo.InstanciaSemana;
import spats.algoritmo.nucleo.SimuladorConformacion;
import spats.algoritmo.nucleo.Solucion;
import spats.algoritmo.salida.ResultadoPlanificacion;

/** Pruebas de integración con las doce instancias semanales construidas en el R2.2. */
@Tag("integracion")
class InstanciasSemanalesTest {

    static final Path DATOS = Path.of("datos");
    static final String[] INSTANCIAS = { "S37-B", "S37-I", "S37-R", "S37-IR", "S38-B", "S38-I", "S38-R", "S38-IR",
            "S39-B", "S39-I", "S39-R", "S39-IR" };

    record Referencia(String instancia, String regla, long semilla, double F, int es, int ed, int lavados, int conformadas) { }

    static List<Referencia> referencias() throws Exception {
        List<Referencia> r = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                InstanciasSemanalesTest.class.getResourceAsStream("/referencia_r22.csv"), StandardCharsets.UTF_8))) {
            br.readLine();
            for (String l; (l = br.readLine()) != null; ) {
                if (l.isBlank()) continue;
                String[] c = l.split(",");
                r.add(new Referencia(c[0], c[1], Long.parseLong(c[2]), Double.parseDouble(c[3]), Integer.parseInt(c[4]),
                        Integer.parseInt(c[5]), Integer.parseInt(c[6]), Integer.parseInt(c[7])));
            }
        }
        return r;
    }

    @Test
    @DisplayName("PI-03 El modelo de evaluación reproduce los valores del R2.2 en las doce instancias")
    void reproduceModeloR22() throws Exception {
        int n = 0;
        for (Referencia ref : referencias()) {
            if (!ref.regla().equals("referencia")) continue;
            DatosSemana d = LectorDatosExcel.leer(DATOS.resolve(ref.instancia() + ".xlsx")).datos();
            InstanciaSemana I = ConstructorInstancia.construir(d);
            Solucion s = SimuladorConformacion.simular(I, new ReglaReferencia());
            Evaluacion e = new FuncionObjetivo(d.parametros()).evaluar(I, s);
            assertEquals(ref.F(), e.F, 1e-9, ref.instancia());
            assertEquals(ref.es(), e.desbalanceSemanal, ref.instancia());
            assertEquals(ref.ed(), e.desbalanceDiario, ref.instancia());
            assertEquals(ref.lavados(), e.lavados, ref.instancia());
            assertEquals(ref.conformadas(), e.conformadas, ref.instancia());
            n++;
        }
        assertEquals(12, n);
    }

    @Test
    @DisplayName("PI-04 La colonia de hormigas reproduce las corridas del R2.2 con la misma semilla")
    void reproduceCorridasR22() throws Exception {
        int n = 0;
        for (Referencia ref : referencias()) {
            if (!ref.regla().equals("aco")) continue;
            LectorDatosExcel.Configuracion c = LectorDatosExcel.leer(DATOS.resolve(ref.instancia() + ".xlsx"));
            InstanciaSemana I = ConstructorInstancia.construir(c.datos());
            ResultadoBusqueda r = new ColoniaHormigas(c.parametrosACO().setSemilla(ref.semilla()))
                    .buscar(I, new FuncionObjetivo(c.datos().parametros()));
            assertEquals(ref.F(), r.evaluacion().F, 1e-9, ref.instancia() + " semilla " + ref.semilla());
            assertEquals(ref.conformadas(), r.evaluacion().conformadas, ref.instancia());
            n++;
        }
        assertEquals(24, n);
    }

    @Test
    @DisplayName("PI-05 Soluciones factibles, dentro del tiempo y mejores que la regla de referencia en las doce instancias")
    void factibilidadYTiempo() throws Exception {
        PlanificadorSemanal planificador = new PlanificadorSemanal();
        for (String nombre : INSTANCIAS) {
            LectorDatosExcel.Configuracion c = LectorDatosExcel.leer(DATOS.resolve(nombre + ".xlsx"));
            ResultadoPlanificacion r = planificador.planificar(c.datos(), c.parametrosACO());
            assertTrue(r.indicadores().factible(), nombre + ": " + r.indicadores().incumplimientos());
            assertTrue(r.indicadores().tiempoMs() < 60_000, nombre + ": tiempo");
            assertFalse(r.indicadores().detenidaPorTiempo(), nombre);
            InstanciaSemana I = ConstructorInstancia.construir(c.datos());
            double referencia = new FuncionObjetivo(c.datos().parametros())
                    .evaluar(I, SimuladorConformacion.simular(I, new ReglaReferencia())).F;
            assertTrue(r.indicadores().funcionObjetivo() <= referencia, nombre + ": F mayor que la regla de referencia");
        }
    }

    @Test
    @DisplayName("PI-06 Encadenamiento de semanas con la memoria que deja la planificación anterior")
    void encadenamiento() throws Exception {
        PlanificadorSemanal planificador = new PlanificadorSemanal();
        Set<String> yaPlanificadas = new HashSet<>();
        ResultadoPlanificacion anterior = null;
        DatosSemana datosAnteriores = null;
        for (String nombre : new String[] { "S37-B", "S38-B", "S39-B" }) {
            LectorDatosExcel.Configuracion c = LectorDatosExcel.leer(DATOS.resolve(nombre + ".xlsx"));
            DatosSemana d = c.datos();
            if (anterior != null)    // la semana parte del estado que dejó la planificación de la anterior
                d = d.conMemoria(anterior.estadoSiguiente().actualizar(datosAnteriores.partidas()),
                        anterior.estadoSiguiente().stockFinal());
            ResultadoPlanificacion r = planificador.planificar(d, c.parametrosACO());
            assertTrue(r.indicadores().factible(), nombre + ": " + r.indicadores().incumplimientos());
            for (var a : r.asignaciones()) assertTrue(yaPlanificadas.add(a.partida()), "partida planificada dos veces: " + a.partida());
            anterior = r;
            datosAnteriores = d;
        }
    }
}
