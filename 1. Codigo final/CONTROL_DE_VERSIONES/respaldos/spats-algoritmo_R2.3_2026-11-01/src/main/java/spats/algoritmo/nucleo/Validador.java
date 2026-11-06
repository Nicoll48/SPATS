package spats.algoritmo.nucleo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Validador independiente: rehace el consumo de tela en el orden de la solución y comprueba las
 * reglas que debe cumplir toda alternativa. Una solución es factible si la lista está vacía.
 */
public final class Validador {

    private Validador() { }

    public static List<String> validar(InstanciaSemana I, Solucion s, Evaluacion e) {
        LinkedHashSet<String> err = new LinkedHashSet<>();
        Calendario cal = e.calendario;
        double[] stock = I.stockInicial.clone();
        boolean[] pendiente = new boolean[I.P];
        Arrays.fill(pendiente, true);
        List<Integer> secuencia = new ArrayList<>();
        for (int p = 0; p < I.P; p++) {
            boolean conf = s.diaConformacion[p] > 0, asig = s.maquina[p] >= 0;
            if (conf != asig) err.add("Partida " + I.partidas[p].codigo() + ": conformación y asignación inconsistentes");
            if (conf) secuencia.add(p);
        }
        secuencia.sort((x, y) -> Integer.compare(s.orden[x], s.orden[y]));
        int[] cub = new int[I.P];
        int j = 0;
        for (int d = 1; d <= I.ND; d++) {
            for (int b = 0; b < I.B; b++) stock[b] += I.llegadas[b][d - 1];
            while (j < secuencia.size() && s.diaConformacion[secuencia.get(j)] == d) {
                int p = secuencia.get(j);
                int n = SimuladorConformacion.completables(I, stock, pendiente, cub);
                boolean completable = false;
                long fmin = Long.MAX_VALUE;
                for (int i = 0; i < n; i++) { if (cub[i] == p) completable = true; fmin = Math.min(fmin, I.fechaEntrega[cub[i]]); }
                if (!completable) err.add("Disponibilidad de tela: una partida se conformó sin tela suficiente");
                else if (I.fechaEntrega[p] > fmin) err.add("Prioridad de conformación: se adelantó una partida con fecha de entrega posterior");
                for (int q : I.reqsDePartida[p]) stock[I.reqBolsa[q]] -= I.reqKg[q];
                pendiente[p] = false;
                j++;
            }
            for (double x : stock) if (x < -1e-6) err.add("Disponibilidad de tela: el consumo supera la tela disponible");
            if (SimuladorConformacion.completables(I, stock, pendiente, cub) > 0)
                err.add("Inclusión: quedó sin conformar una partida que la tela permitía completar");
        }
        if (j < secuencia.size()) err.add("Orden de conformación inconsistente con los días");
        for (int p : secuencia) {
            int m = s.maquina[p];
            boolean compatible = false;
            for (int c : I.compat[p]) if (c == m) compatible = true;
            if (!compatible) err.add("Asignación: partida asignada a una máquina fuera de su barca");
            if (cal.dia[p] < 1 || cal.dia[p] > I.ND) { err.add("Calendario: partida sin día"); continue; }
            if (cal.dia[p] < s.diaConformacion[p]) err.add("Disponibilidad temporal: partida ubicada antes de estar disponible");
            if (!cal.sobrante[p] && !I.habil[m][cal.dia[p] - 1]) err.add("Disponibilidad temporal: partida en un día indisponible");
        }
        for (int m = 0; m < I.M; m++) {
            int asignadas = 0;
            for (int p : secuencia) if (s.maquina[p] == m && !cal.sobrante[p]) asignadas++;
            if (asignadas > I.objetivo[m]) err.add("Distribución: partidas asignadas por encima del objetivo semanal");
            for (int d = 0; d < I.ND; d++)
                if (cal.partidasPorDia[m][d] > I.maximoDiario[m]) err.add("Distribución: día por encima del máximo diario");
            // Sobrante: solo si la máquina alcanzó su objetivo o ya no hay espacio desde que la partida está disponible
            if (asignadas < I.objetivo[m])
                for (int p : secuencia)
                    if (s.maquina[p] == m && cal.sobrante[p])
                        for (int d = s.diaConformacion[p]; d <= I.ND; d++)
                            if (I.habil[m][d - 1] && cal.partidasPorDia[m][d - 1] < I.maximoDiario[m])
                                err.add("Sobrantes: partida marcada como Sobrante con espacio disponible en la semana");
        }
        return new ArrayList<>(err);
    }
}
