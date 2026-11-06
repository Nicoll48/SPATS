package tintoreria;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Uso:
 *   java -cp clases tintoreria.Experimento experimento <carpeta_csv> <carpeta_salida> [corridas] [evaluaciones]
 *        [ag=poblacion,elitismo,pCruce,pMutacion] [aco=hormigas,rho,tau0,a,b]
 *   java -cp clases tintoreria.Experimento planificar <carpeta_instancia> [semilla]
 */
public final class Experimento {

    public static void main(String[] args) throws IOException {
        Locale.setDefault(Locale.US);
        if (args.length >= 3 && args[0].equals("experimento")) experimento(args);
        else if (args.length >= 2 && args[0].equals("planificar")) planificar(args);
        else System.out.println("Uso: experimento <carpeta_csv> <salida> [corridas] [evaluaciones] [ag=...] [aco=...] | planificar <carpeta_instancia> [semilla]");
    }

    static void experimento(String[] args) throws IOException {
        Path dir = Path.of(args[1]), out = Path.of(args[2]);
        int corridas = args.length > 3 ? Integer.parseInt(args[3]) : 20;
        int presupuesto = args.length > 4 ? Integer.parseInt(args[4]) : 6000;
        Algoritmos.ParamAG pag = new Algoritmos.ParamAG();
        Algoritmos.ParamACO paco = new Algoritmos.ParamACO();
        for (int i = 5; i < args.length; i++) {
            String[] v = args[i].substring(args[i].indexOf('=') + 1).split(",");
            if (args[i].startsWith("ag=")) {
                pag.poblacion = Integer.parseInt(v[0]); pag.elitismo = Double.parseDouble(v[1]);
                pag.pCruce = Double.parseDouble(v[2]); pag.pMutacion = Double.parseDouble(v[3]);
            } else if (args[i].startsWith("aco=")) {
                paco.hormigas = Integer.parseInt(v[0]); paco.rho = Double.parseDouble(v[1]);
                paco.tau0 = Double.parseDouble(v[2]); paco.a = Double.parseDouble(v[3]); paco.b = Double.parseDouble(v[4]);
            }
        }
        Files.createDirectories(out);
        List<String> nombres = Files.readAllLines(dir.resolve("lista.txt"), StandardCharsets.UTF_8);
        try (PrintWriter pv = new PrintWriter(Files.newBufferedWriter(out.resolve("voraz_java.csv")));
             PrintWriter pc = new PrintWriter(Files.newBufferedWriter(out.resolve("corridas_java.csv")))) {
            pv.println("instancia,F,E_S,E_D,lavados,conformadas,asignadas,sobrantes,factible");
            pc.println("instancia,algoritmo,corrida,semilla,F,E_S,E_D,lavados,conformadas,asignadas,sobrantes,tiempo_ms,factible");
            int ii = 0;
            for (String n : nombres) {
                if (n.isBlank()) continue;
                ii++;
                Instancia I = Instancia.cargar(dir.resolve(n));
                Algoritmos.Resultado g = Algoritmos.reglaVoraz(I);
                boolean fv = Modelo.validar(I, g.sol, g.ev).isEmpty();
                pv.printf("%s,%.2f,%d,%d,%d,%d,%d,%d,%b%n", n, g.F, g.ev.ES, g.ev.ED, g.ev.lavados,
                        g.ev.conformadas, g.ev.asignadas, g.ev.sobrantes, fv);
                double sAG = 0, sACO = 0; long tAG = 0, tACO = 0;
                for (int c = 1; c <= corridas; c++) {
                    long sem = 1000L * ii + c;
                    Algoritmos.Resultado a = Algoritmos.algoritmoGenetico(I, sem, presupuesto, pag);
                    Algoritmos.Resultado h = Algoritmos.coloniaHormigas(I, sem, presupuesto, paco);
                    escribir(pc, n, "AG", c, sem, a, Modelo.validar(I, a.sol, a.ev).isEmpty());
                    escribir(pc, n, "ACO", c, sem, h, Modelo.validar(I, h.sol, h.ev).isEmpty());
                    sAG += a.F; sACO += h.F; tAG += a.tiempoMs; tACO += h.tiempoMs;
                }
                System.out.printf("%-7s voraz %8.2f | AG prom %8.2f (%5d ms) | ACO prom %8.2f (%5d ms)%n",
                        n, g.F, sAG / corridas, tAG / corridas, sACO / corridas, tACO / corridas);
            }
        }
    }

    static void escribir(PrintWriter pc, String n, String alg, int c, long sem, Algoritmos.Resultado r, boolean f) {
        pc.printf("%s,%s,%d,%d,%.2f,%d,%d,%d,%d,%d,%d,%d,%b%n", n, alg, c, sem, r.F, r.ev.ES, r.ev.ED,
                r.ev.lavados, r.ev.conformadas, r.ev.asignadas, r.ev.sobrantes, r.tiempoMs, f);
    }

    static void planificar(String[] args) throws IOException {
        /*args[0] = "planificar" y args[1] = "../R_experimentacion/instancias/csv/S38-B"*/
        Instancia I = Instancia.cargar(Path.of(args[1]));
        long sem = args.length > 2 ? Long.parseLong(args[2]) : 1L;
        Algoritmos.Resultado h = Algoritmos.coloniaHormigas(I, sem, 6000, new Algoritmos.ParamACO());
        String[] dias = {"Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"};
        System.out.printf("Instancia %s  F = %.2f  (E_S = %d, E_D = %d, lavados = %d)  tiempo = %d ms%n",
                I.nombre, h.F, h.ev.ES, h.ev.ED, h.ev.lavados, h.tiempoMs);
        System.out.printf("%-8s", "Máquina");
        for (String d : dias) System.out.printf("%7s", d);
        System.out.printf("%8s%n", "Total");
        for (int m = 0; m < I.M; m++) {
            System.out.printf("%-8s", I.maquina[m]);
            for (int d = 0; d < I.ND; d++) {
                if (!I.habil[m][d]) { System.out.printf("%7s", "X"); continue; }
                int reg = 0, sob = 0;
                for (int p = 0; p < I.P; p++) if (h.sol.maq[p] == m && h.ev.cal.dia[p] == d + 1) { if (h.ev.cal.sobrante[p]) sob++; else reg++; }
                System.out.printf("%7s", sob > 0 ? reg + "+" + sob + "S" : String.valueOf(reg));
            }
            System.out.printf("%8s%n", h.ev.N[m] + "/" + I.O[m]);
        }
        System.out.println("Validación: " + (Modelo.validar(I, h.sol, h.ev).isEmpty() ? "factible" : Modelo.validar(I, h.sol, h.ev)));
    }
}
