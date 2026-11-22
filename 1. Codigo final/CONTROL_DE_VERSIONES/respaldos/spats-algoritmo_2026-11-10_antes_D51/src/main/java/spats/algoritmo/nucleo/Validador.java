package spats.algoritmo.nucleo;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Validador independiente: rehace el consumo de tela en el orden de la solución y comprueba las
 * reglas que debe cumplir toda alternativa. Una solución es factible si la lista está vacía.
 *
 * <p>Re-planificación: el consumo se rehace desde {@code I.diaInicio} y solo con las partidas que el
 * algoritmo decide. Las fijas deben quedar exactamente como llegaron; las conservadas, conformadas en su
 * día previo. Las reglas de distribución se comprueban sobre lo decidido desde el día de inicio: lo fijo
 * se toma como dado.</p>
 */
public final class Validador {

    private Validador() { }

    public static List<String> validar(InstanciaSemana I, Solucion s, Evaluacion e) {
        LinkedHashSet<String> err = new LinkedHashSet<>();
        Calendario cal = e.calendario;
        double[] stock = I.stockInicial.clone();
        boolean[] pendiente = new boolean[I.P];
        List<Integer> secuencia = new ArrayList<>();   // decididas y conformadas, en orden de conformación
        List<Integer> ubicadas = new ArrayList<>();    // decididas y conservadas: las que el calendario ubica
        for (int p = 0; p < I.P; p++) {
            boolean conf = s.diaConformacion[p] > 0, asig = s.maquina[p] >= 0;
            if (conf != asig) err.add("Partida " + I.partidas[p].codigo() + ": conformación y asignación inconsistentes");
            if (I.fija[p]) {
                if (s.maquina[p] != I.fijaMaquina[p] || s.diaConformacion[p] != I.diaConformacionPrevia[p]
                        || cal.dia[p] != I.fijaDia[p] || cal.sobrante[p] != I.fijaSobrante[p])
                    err.add("Asignaciones fijas: cambió una partida que ya estaba ubicada");
                continue;
            }
            if (I.conservada[p]) {
                if (!asig || s.diaConformacion[p] != I.diaConformacionPrevia[p])
                    err.add("Conformación conservada: una partida con tela separada perdió su conformación");
                if (asig) ubicadas.add(p);
                continue;
            }
            pendiente[p] = true;
            if (conf) { secuencia.add(p); ubicadas.add(p); }
        }
        secuencia.sort((x, y) -> Integer.compare(s.orden[x], s.orden[y]));
        int[] cub = new int[I.P];
        int j = 0;
        for (int d = I.diaInicio; d <= I.ND; d++) {
            for (int b = 0; b < I.B; b++) stock[b] += I.llegadas[b][d - 1];
            if (d == I.diaInicio && I.hayConsumoFijasInicio)
                for (int b = 0; b < I.B; b++) stock[b] -= I.consumoFijasInicio[b];
            while (j < secuencia.size() && s.diaConformacion[secuencia.get(j)] == d) {
                int p = secuencia.get(j);
                int n = SimuladorConformacion.completables(I, stock, pendiente, cub, d);
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
            if (SimuladorConformacion.completables(I, stock, pendiente, cub, d) > 0)
                err.add("Inclusión: quedó sin conformar una partida que la tela permitía completar");
        }
        if (j < secuencia.size()) err.add("Orden de conformación inconsistente con los días");
        for (int p : ubicadas) {
            int m = s.maquina[p];
            boolean compatible = false;
            for (int c : I.compat[p]) if (c == m) compatible = true;
            if (!compatible) err.add("Asignación: partida asignada a una máquina fuera de su barca o sin día hábil desde que está disponible");
            if (cal.dia[p] < 1 || cal.dia[p] > I.ND) { err.add("Calendario: partida sin día"); continue; }
            if (cal.dia[p] < s.diaConformacion[p]) err.add("Disponibilidad temporal: partida ubicada antes de estar disponible");
            if (cal.dia[p] < Math.max(I.diaInicio, I.desde[p]))
                err.add("Disponibilidad temporal: partida ubicada antes del día de inicio o de su fecha de disponibilidad");
            if (!cal.sobrante[p] && !I.habil[m][cal.dia[p] - 1]) err.add("Disponibilidad temporal: partida en un día indisponible");
        }
        for (int m = 0; m < I.M; m++) {
            int asignadas = 0, fijasAsignadas = 0;
            int[] fijasPorDia = new int[I.ND];
            for (int p = 0; p < I.P; p++)
                if (I.fija[p] && I.fijaMaquina[p] == m && !I.fijaSobrante[p]) { fijasAsignadas++; fijasPorDia[I.fijaDia[p] - 1]++; }
            for (int p : ubicadas) if (s.maquina[p] == m && !cal.sobrante[p]) asignadas++;
            if (asignadas > Math.max(0, I.objetivo[m] - fijasAsignadas))
                err.add("Distribución: partidas asignadas por encima del objetivo semanal");
            for (int d = I.diaInicio - 1; d < I.ND; d++)
                if (cal.partidasPorDia[m][d] > Math.max(I.maximoDiario[m], fijasPorDia[d]))
                    err.add("Distribución: día por encima del máximo diario");
            // Sobrante: solo si la máquina alcanzó su objetivo o ya no hay espacio desde que la partida está disponible
            if (asignadas + fijasAsignadas < I.objetivo[m])
                for (int p : ubicadas)
                    if (s.maquina[p] == m && cal.sobrante[p])
                        for (int d = ConstructorCalendario.primerDia(I, s, p); d <= I.ND; d++)
                            if (I.habil[m][d - 1] && cal.partidasPorDia[m][d - 1] < I.maximoDiario[m])
                                err.add("Sobrantes: partida marcada como Sobrante con espacio disponible en la semana");
        }
        return new ArrayList<>(err);
    }
}
