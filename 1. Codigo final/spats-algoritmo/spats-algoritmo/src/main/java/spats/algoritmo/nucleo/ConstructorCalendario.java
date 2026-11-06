package spats.algoritmo.nucleo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Regla común que distribuye las partidas de cada máquina entre los días de la semana.
 *
 * <p>Las partidas de cada máquina se ordenan por fecha de entrega, día en que quedan disponibles y
 * orden de llegada. Cada día hábil recibe partidas completas: el máximo diario mientras se requiera
 * para alcanzar el objetivo y el mínimo en los días restantes (con objetivo 16 en siete días:
 * 3, 3, 2, 2, 2, 2, 2). Si en un día hay más partidas disponibles que espacio, el resto pasa al día
 * hábil siguiente. Las partidas que exceden el objetivo, o que llegan cuando los días restantes ya
 * alcanzaron su máximo, se ubican en su primer día disponible como Sobrantes.</p>
 *
 * <p>Re-planificación: las fijas quedan en su día y con su clasificación; sus Asignadas ocupan lugar en el
 * día y descuentan del objetivo restante. Los días anteriores a {@code I.diaInicio} no reciben partidas
 * nuevas. Desde ese día se aplica la misma regla de cuotas, y ninguna partida se ubica antes de
 * {@code I.diaInicio} ni de su día {@code I.desde}.</p>
 */
public final class ConstructorCalendario {

    private ConstructorCalendario() { }

    public static Calendario construir(InstanciaSemana I, Solucion s) {
        Calendario cal = new Calendario();
        cal.dia = new int[I.P];
        Arrays.fill(cal.dia, -1);
        cal.sobrante = new boolean[I.P];
        cal.partidasPorDia = new int[I.M][I.ND];
        int[][] fijasPorDia = new int[I.M][I.ND];
        List<Integer> conf = new ArrayList<>();
        for (int p = 0; p < I.P; p++) {
            if (I.fija[p]) {
                cal.dia[p] = I.fijaDia[p];
                cal.sobrante[p] = I.fijaSobrante[p];
                if (!I.fijaSobrante[p]) fijasPorDia[I.fijaMaquina[p]][I.fijaDia[p] - 1]++;
            } else if (s.conformada(p)) conf.add(p);
        }
        conf.sort(Comparator.<Integer>comparingInt(p -> s.maquina[p])
                .thenComparingLong(p -> I.fechaEntrega[p])
                .thenComparingInt(p -> s.diaConformacion[p])
                .thenComparingInt(p -> s.orden[p]));
        boolean[] repartida = new boolean[I.M];
        int i = 0;
        while (i < conf.size()) {
            int m = s.maquina[conf.get(i)];
            int j = i;
            while (j < conf.size() && s.maquina[conf.get(j)] == m) j++;
            repartir(I, s, cal, m, conf.subList(i, j), fijasPorDia[m]);
            repartida[m] = true;
            i = j;
        }
        for (int m = 0; m < I.M; m++)
            if (!repartida[m] && I.nFijas > 0) repartir(I, s, cal, m, List.of(), fijasPorDia[m]);
        return cal;
    }

    /** Reparte entre los días de la máquina {@code m} sus partidas no fijas, ya ordenadas. */
    private static void repartir(InstanciaSemana I, Solucion s, Calendario cal, int m, List<Integer> partidas,
                                 int[] fijas) {
        int n = partidas.size();
        int[] idx = new int[n], disp = new int[n];
        boolean[] libre = new boolean[n];
        for (int t = 0; t < n; t++) {
            idx[t] = partidas.get(t);
            disp[t] = I.sigHabil[m][primerDia(I, s, idx[t])];   // primer día hábil disponible
            libre[t] = disp[t] > 0;
        }
        int restante = I.objetivo[m], L = I.minimoDiario[m], U = I.maximoDiario[m];
        int[] hab = I.diasHabiles[m];
        for (int d = 1; d <= I.ND; d++)                          // fijas Asignadas en un día ya no hábil
            if (!I.habil[m][d - 1] && fijas[d - 1] > 0) { cal.partidasPorDia[m][d - 1] = fijas[d - 1]; restante -= fijas[d - 1]; }
        for (int jj = 0; jj < hab.length; jj++) {
            int d = hab[jj];
            int fijasHoy = fijas[d - 1];
            if (d < I.diaInicio || restante <= 0) {
                cal.partidasPorDia[m][d - 1] = fijasHoy;
                restante -= fijasHoy;
                continue;
            }
            int cuota = (restante - U >= L * (hab.length - jj - 1)) ? U : L;
            cuota = Math.min(cuota, restante);
            int tomadas = fijasHoy;
            for (int t = 0; t < n && tomadas < cuota; t++)
                if (libre[t] && disp[t] <= d) { libre[t] = false; cal.dia[idx[t]] = d; tomadas++; }
            cal.partidasPorDia[m][d - 1] = tomadas;
            restante -= tomadas;
        }
        for (int t = 0; t < n; t++)
            if (cal.dia[idx[t]] < 0) {
                cal.sobrante[idx[t]] = true;
                cal.dia[idx[t]] = disp[t] > 0 ? disp[t] : primerDia(I, s, idx[t]);
            }
    }

    /** Primer día en que la partida puede ubicarse: su conformación, el día de inicio y su día {@code desde}. */
    static int primerDia(InstanciaSemana I, Solucion s, int p) {
        return Math.max(s.diaConformacion[p], Math.max(I.diaInicio, I.desde[p]));
    }
}
