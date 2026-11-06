package pe.edu.pucp.spats.planificacion.motor;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import pe.edu.pucp.spats.planificacion.entity.Enumeraciones.ClasificacionResultado;
import pe.edu.pucp.spats.planificacion.motor.ResultadoTraducido.FilaPartida;
import spats.algoritmo.PlanificadorSemanal;
import spats.algoritmo.excel.LectorDatosExcel;
import spats.algoritmo.salida.ResultadoPlanificacion;

/**
 * Regresión con las doce instancias del R2.2 (sección 8.5 del contexto): el motor usado por el backend
 * da el mismo F que el código del R2.3 con la misma semilla, sin incumplimientos del validador, y su
 * traducción cumple las restricciones del script de la base.
 */
class RegresionInstanciasR22Test {

    static final Path MODULO = Path.of("..", "..", "spats-algoritmo", "spats-algoritmo");

    static Stream<Arguments> instancias() throws Exception {
        List<String> lineas = Files.readAllLines(MODULO.resolve("docs/resumen_ejecuciones.csv"), StandardCharsets.UTF_8);
        return lineas.stream().skip(1).filter(l -> !l.isBlank()).map(l -> l.split(","))
                .map(c -> Arguments.of(c[0], Double.parseDouble(c[1]), Integer.parseInt(c[6])));
    }

    @ParameterizedTest(name = "{0}: F = {1}")
    @MethodSource("instancias")
    void reproduceElResultadoDelR23YCumpleLasRestriccionesDeLaBase(String instancia, double fR23, int conformadasR23) throws Exception {
        var configuracion = LectorDatosExcel.leer(MODULO.resolve("datos").resolve(instancia + ".xlsx"));
        ResultadoPlanificacion r = new PlanificadorSemanal().planificar(configuracion.datos(), configuracion.parametrosACO());

        assertThat(r.indicadores().incumplimientos()).isEmpty();
        assertThat(r.indicadores().funcionObjetivo()).isEqualTo(fR23);
        assertThat(r.indicadores().conformadas()).isEqualTo(conformadasR23);

        ResultadoTraducido t = TraductorResultado.traducir(configuracion.datos(), r);
        verificarRestriccionesDeLaBase(t);
    }

    /** Las mismas reglas que el script BD.sql exige con CHECK y UNIQUE. */
    static void verificarRestriccionesDeLaBase(ResultadoTraducido t) {
        Map<String, Integer> vecesPorPartida = new HashMap<>();
        for (FilaPartida f : t.partidas()) {
            vecesPorPartida.merge(f.numeroPartida(), 1, Integer::sum);
            assertThat(f.codigoMaquina()).isNotNull();
            assertThat(f.diaAbastecimiento()).isAfterOrEqualTo(f.fechaDisponible());   // ck_planpart_dia_desde_disponible
            assertThat(f.clasificacion()).isIn(ClasificacionResultado.ASIGNADA, ClasificacionResultado.SOBRANTE);
        }
        t.bloqueadas().forEach(b -> vecesPorPartida.merge(b, 1, Integer::sum));
        assertThat(vecesPorPartida.values()).allMatch(v -> v == 1);                   // uq_partida_por_planificacion

        Map<String, Map<LocalDate, List<Integer>>> secuencias = new HashMap<>();
        for (FilaPartida f : t.partidas())
            secuencias.computeIfAbsent(f.codigoMaquina(), k -> new HashMap<>())
                      .computeIfAbsent(f.diaAbastecimiento(), k -> new java.util.ArrayList<>()).add(f.ordenSecuencia());
        secuencias.values().forEach(porDia -> porDia.values().forEach(orden ->
                assertThat(orden).containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, orden.size()).boxed().toList())));

        t.maquinas().forEach(m -> {
            assertThat(m.diferencia()).isEqualTo(m.asignadas() + m.sobrantes() - m.objetivoAplicado()); // ck_planmaq_diferencia
            assertThat(m.maximoDiario()).isGreaterThanOrEqualTo(m.minimoDiario());
        });
        t.bolsas().forEach(b -> {
            assertThat(b.saldoFinal()).isEqualByComparingTo(b.saldoInicial().add(b.ingresados()).add(b.devueltos()).subtract(b.consumidos())); // ck_bolsa_balance
            assertThat(b.saldoFinal().compareTo(BigDecimal.ZERO)).isGreaterThanOrEqualTo(0);
            assertThat(b.saldoInicial().scale()).isLessThanOrEqualTo(3);
        });
    }
}
