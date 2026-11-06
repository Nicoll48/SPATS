package spats.algoritmo.excel;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import spats.algoritmo.PlanificadorSemanal;
import spats.algoritmo.salida.Indicadores;
import spats.algoritmo.salida.ResultadoPlanificacion;
import spats.algoritmo.salida.ResumenMaquina;

/**
 * Ejecución de prueba del algoritmo con libros de Excel.
 *
 * <pre>
 *   java -jar spats-algoritmo.jar &lt;archivo.xlsx | carpeta&gt; [carpeta_salida] [--semilla N]
 * </pre>
 * Por cada libro escribe &lt;nombre&gt;_resultado.xlsx en la carpeta de salida (por defecto "resultados")
 * y muestra el calendario en la consola. Con una carpeta, procesa todos sus .xlsx y escribe además
 * resumen_ejecuciones.csv.
 */
public final class Aplicacion {

    /** Nombres sin tildes para que la consola de Windows los muestre correctamente. */
    private static final String[] DIAS_CONSOLA = { "Lun", "Mar", "Mie", "Jue", "Vie", "Sab", "Dom" };

    public static void main(String[] args) throws IOException {
        Locale.setDefault(Locale.US);
        List<String> pos = new ArrayList<>();
        Long semilla = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--semilla") && i + 1 < args.length) semilla = Long.parseLong(args[++i]);
            else pos.add(args[i]);
        }
        if (pos.isEmpty()) {
            System.out.println("Uso: java -jar spats-algoritmo.jar <archivo.xlsx | carpeta> [carpeta_salida] [--semilla N]");
            return;
        }
        Path entrada = Path.of(pos.get(0));
        Path salida = Path.of(pos.size() > 1 ? pos.get(1) : "resultados");
        List<Path> libros = new ArrayList<>();
        if (Files.isDirectory(entrada)) {
            try (Stream<Path> s = Files.list(entrada)) {
                s.filter(p -> p.toString().endsWith(".xlsx") && !p.getFileName().toString().startsWith("~$"))
                 .sorted().forEach(libros::add);
            }
        } else libros.add(entrada);

        Files.createDirectories(salida);
        PlanificadorSemanal planificador = new PlanificadorSemanal();
        try (PrintWriter csv = new PrintWriter(Files.newBufferedWriter(salida.resolve("resumen_ejecuciones.csv"), StandardCharsets.UTF_8))) {
            csv.println("libro,F,desbalance_semanal,desbalance_diario,lavados,objetivo_total,conformadas,asignadas,sobrantes,"
                    + "bloqueadas,evaluaciones,tiempo_ms,factible");
            for (Path libro : libros) {
                LectorDatosExcel.Configuracion cfg = LectorDatosExcel.leer(libro);
                if (semilla != null) cfg.parametrosACO().setSemilla(semilla);
                ResultadoPlanificacion r = planificador.planificar(cfg.datos(), cfg.parametrosACO());
                String nombre = libro.getFileName().toString().replaceFirst("\\.xlsx$", "");
                Path destino = salida.resolve(nombre + "_resultado.xlsx");
                EscritorResultadoExcel.guardar(r, destino);
                imprimir(nombre, r);
                System.out.println("Resultado guardado en " + destino);
                System.out.println();
                Indicadores i = r.indicadores();
                csv.printf("%s,%.2f,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%s%n", nombre, i.funcionObjetivo(), i.desbalanceSemanal(),
                        i.desbalanceDiario(), i.lavados(), i.objetivoTotal(), i.conformadas(), i.asignadas(), i.sobrantes(),
                        i.bloqueadas(), i.evaluaciones(), i.tiempoMs(), i.factible());
            }
        }
    }

    static void imprimir(String nombre, ResultadoPlanificacion r) {
        Indicadores i = r.indicadores();
        System.out.printf("%s  F = %.2f  (desbalance semanal %d, desbalance diario %d, lavados %d)  %d ms%n", nombre,
                i.funcionObjetivo(), i.desbalanceSemanal(), i.desbalanceDiario(), i.lavados(), i.tiempoMs());
        System.out.printf("%-9s", "Maquina");
        for (int d = 0; d < r.dias().length; d++) System.out.printf("%8s", d < DIAS_CONSOLA.length ? DIAS_CONSOLA[d] : "D" + (d + 1));
        System.out.printf("%10s%n", "Total/Obj");
        for (ResumenMaquina m : r.maquinas()) {
            System.out.printf("%-9s", m.maquina());
            for (int d = 0; d < r.dias().length; d++) {
                int v = m.partidasPorDia()[d];
                System.out.printf("%8s", v < 0 ? "X" : String.valueOf(v));
            }
            System.out.printf("%10s%s%n", m.total() + "/" + m.objetivo(), m.sobrantes() > 0 ? "  (" + m.sobrantes() + " Sobrantes)" : "");
        }
        System.out.printf("Conformadas %d de %d pendientes | Sobrantes %d | Bloqueadas %d | Factible: %s%n", i.conformadas(),
                i.conformadas() + i.bloqueadas(), i.sobrantes(), i.bloqueadas(), i.factible() ? "si" : "no " + i.incumplimientos());
        for (String a : r.advertencias()) System.out.println("Advertencia: " + a);
    }
}
