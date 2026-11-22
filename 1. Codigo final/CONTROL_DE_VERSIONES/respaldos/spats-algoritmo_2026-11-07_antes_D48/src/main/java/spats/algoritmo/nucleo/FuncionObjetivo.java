package spats.algoritmo.nucleo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import spats.algoritmo.entrada.ParametrosPlanificacion;

/**
 * Función objetivo que se minimiza: F = α·E_S + β·E_D + γ·Λ.
 * <ul>
 *   <li>E_S: para cada máquina, cuadrado de la diferencia entre sus partidas (incluidas las Sobrantes)
 *       y su objetivo semanal.</li>
 *   <li>E_D: para cada máquina y día hábil, cuadrado de lo que falta para el mínimo diario o de lo
 *       que excede el máximo diario (solo partidas asignadas; las Sobrantes no cuentan).</li>
 *   <li>Λ: cambios de una tonalidad a otra más clara entre partidas consecutivas de una máquina.</li>
 * </ul>
 */
public final class FuncionObjetivo {

    private final double alfa, beta, gamma;

    public FuncionObjetivo(double alfa, double beta, double gamma) {
        this.alfa = alfa; this.beta = beta; this.gamma = gamma;
    }

    public FuncionObjetivo(ParametrosPlanificacion par) {
        this(par.getPesoDesbalanceSemanal(), par.getPesoDesbalanceDiario(), par.getPesoLavados());
    }

    public Evaluacion evaluar(InstanciaSemana I, Solucion s) {
        Evaluacion e = new Evaluacion();
        Calendario cal = ConstructorCalendario.construir(I, s);
        e.calendario = cal;
        e.partidasPorMaquina = new int[I.M];
        e.requiereLavado = new boolean[I.P];
        List<Integer> conf = new ArrayList<>();
        for (int p = 0; p < I.P; p++) if (s.conformada(p)) { conf.add(p); e.partidasPorMaquina[s.maquina[p]]++; }
        for (int m = 0; m < I.M; m++) {
            int dif = e.partidasPorMaquina[m] - I.objetivo[m];
            e.desbalanceSemanal += dif * dif;
        }
        for (int m = 0; m < I.M; m++)
            for (int d = 0; d < I.ND; d++)
                if (I.habil[m][d]) {
                    int nd = cal.partidasPorDia[m][d];
                    int faltan = Math.max(0, I.minimoDiario[m] - nd), exceso = Math.max(0, nd - I.maximoDiario[m]);
                    e.desbalanceDiario += faltan * faltan + exceso * exceso;
                }
        // secuencia de cada máquina: por día, asignadas antes que Sobrantes y orden de llegada
        conf.sort(Comparator.<Integer>comparingInt(p -> s.maquina[p])
                .thenComparingInt(p -> cal.dia[p])
                .thenComparing(p -> cal.sobrante[p])
                .thenComparingInt(p -> s.orden[p]));
        for (int t = 1; t < conf.size(); t++) {
            int a = conf.get(t - 1), b = conf.get(t);
            if (s.maquina[a] == s.maquina[b] && I.tono[b] < I.tono[a]) { e.lavados++; e.requiereLavado[b] = true; }
        }
        e.conformadas = conf.size();
        for (int p : conf) if (cal.sobrante[p]) e.sobrantes++;
        e.asignadas = e.conformadas - e.sobrantes;
        e.F = alfa * e.desbalanceSemanal + beta * e.desbalanceDiario + gamma * e.lavados;
        return e;
    }
}
