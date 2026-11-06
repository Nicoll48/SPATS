package spats.algoritmo.aco;

import java.util.Arrays;
import java.util.Random;

import spats.algoritmo.nucleo.EstrategiaDecision;
import spats.algoritmo.nucleo.Evaluacion;
import spats.algoritmo.nucleo.FuncionObjetivo;
import spats.algoritmo.nucleo.InstanciaSemana;
import spats.algoritmo.nucleo.SimuladorConformacion;
import spats.algoritmo.nucleo.Solucion;

/**
 * Optimización por colonia de hormigas para la conformación y asignación de partidas,
 * siguiendo a Fidanova et al. (2025).
 *
 * <ul>
 *   <li>Cada nodo es un par partida–máquina con feromona inicial τ0.</li>
 *   <li>Cada hormiga construye una semana completa con {@link SimuladorConformacion}. Cuando la tela
 *       permite completar a varias partidas empatadas por fecha de entrega, elige la partida con
 *       probabilidad proporcional a Σ<sub>m</sub> τ[p][m]<sup>a</sup>·η[m]<sup>b</sup> sobre las máquinas
 *       de su barca; luego elige la máquina con probabilidad proporcional a τ[p][m]<sup>a</sup>·η[m]<sup>b</sup>.</li>
 *   <li>La información heurística es la inversa de la carga relativa de la máquina:
 *       η[m] = objetivo[m] / (carga[m] + 1), lo que favorece las máquinas menos cargadas.</li>
 *   <li>Al terminar cada iteración la feromona se evapora (τ ← ρ·τ) y la mejor hormiga de la
 *       iteración deposita (1 − ρ) / (1 + F) en los nodos que utilizó.</li>
 *   <li>La búsqueda termina al agotar las evaluaciones o el tiempo máximo.</li>
 * </ul>
 */
public final class ColoniaHormigas {

    private final ParametrosACO par;

    public ColoniaHormigas(ParametrosACO parametros) {
        parametros.validar();
        this.par = parametros;
    }

    public ResultadoBusqueda buscar(InstanciaSemana I, FuncionObjetivo fo) {
        long t0 = System.nanoTime();
        long limiteNs = (long) (par.getTiempoMaximoSegundos() * 1e9);
        Random rnd = new Random(par.getSemilla());
        double[][] tau = new double[I.P][I.M];
        for (double[] fila : tau) Arrays.fill(fila, par.getFeromonaInicial());
        Hormiga hormiga = new Hormiga(tau, par.getPesoFeromona(), par.getPesoHeuristica(), rnd);

        Solucion mejor = null;
        double fMejor = Double.POSITIVE_INFINITY;
        int evaluaciones = 0, iteraciones = 0;
        boolean porTiempo = false;
        while (evaluaciones < par.getEvaluaciones() && !porTiempo) {
            Solucion mejorIt = null;
            double fIt = Double.POSITIVE_INFINITY;
            for (int h = 0; h < par.getHormigas() && evaluaciones < par.getEvaluaciones(); h++) {
                if (evaluaciones > 0 && System.nanoTime() - t0 > limiteNs) { porTiempo = true; break; }
                Solucion s = SimuladorConformacion.simular(I, hormiga);
                double F = fo.evaluar(I, s).F;
                evaluaciones++;
                if (F < fMejor) { fMejor = F; mejor = s; }
                if (F < fIt) { fIt = F; mejorIt = s; }
            }
            if (mejorIt == null) break;
            actualizarFeromona(tau, mejorIt, fIt, par.getRho());
            iteraciones++;
        }
        Evaluacion ev = fo.evaluar(I, mejor);
        return new ResultadoBusqueda(mejor, ev, evaluaciones, iteraciones, (System.nanoTime() - t0) / 1_000_000, porTiempo);
    }

    /** Evaporación en todos los nodos y depósito de la mejor hormiga de la iteración. */
    static void actualizarFeromona(double[][] tau, Solucion mejorIteracion, double fIteracion, double rho) {
        for (double[] fila : tau) for (int m = 0; m < fila.length; m++) fila[m] *= rho;
        double deposito = (1 - rho) / (1 + fIteracion);
        for (int p = 0; p < tau.length; p++)
            if (mejorIteracion.maquina[p] >= 0) tau[p][mejorIteracion.maquina[p]] += deposito;
    }

    /** Reglas de decisión de una hormiga. */
    static final class Hormiga implements EstrategiaDecision {
        private final double[][] tau;
        private final double a, b;
        private final Random rnd;
        private double[] w = new double[16];

        Hormiga(double[][] tau, double a, double b, Random rnd) { this.tau = tau; this.a = a; this.b = b; this.rnd = rnd; }

        private double atractivo(InstanciaSemana I, int p, int m, int[] carga) {
            double cargaRelativa = (carga[m] + 1.0) / I.objetivo[m];
            return Math.pow(tau[p][m], a) * Math.pow(1.0 / cargaRelativa, b);
        }

        @Override
        public int elegirPartida(InstanciaSemana I, int[] empatadas, int n, int[] carga) {
            if (w.length < n) w = new double[Math.max(n, 2 * w.length)];
            for (int i = 0; i < n; i++) {
                double suma = 0;
                for (int m : I.compat[empatadas[i]]) suma += atractivo(I, empatadas[i], m, carga);
                w[i] = suma;
            }
            return empatadas[ruleta(w, n, rnd)];
        }

        @Override
        public int elegirMaquina(InstanciaSemana I, int p, int[] carga) {
            int[] cm = I.compat[p];
            if (w.length < cm.length) w = new double[Math.max(cm.length, 2 * w.length)];
            for (int j = 0; j < cm.length; j++) w[j] = atractivo(I, p, cm[j], carga);
            return cm[ruleta(w, cm.length, rnd)];
        }
    }

    /** Selección por ruleta: posición elegida con probabilidad proporcional a su peso. */
    static int ruleta(double[] w, int n, Random rnd) {
        double total = 0;
        for (int i = 0; i < n; i++) total += w[i];
        double u = rnd.nextDouble() * total, acumulado = 0;
        for (int i = 0; i < n; i++) { acumulado += w[i]; if (acumulado >= u) return i; }
        return n - 1;
    }
}
