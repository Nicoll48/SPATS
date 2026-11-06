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
 */
public final class ConstructorCalendario {

    private ConstructorCalendario() { }

    public static Calendario construir(InstanciaSemana I, Solucion s) {
        Calendario cal = new Calendario();
        cal.dia = new int[I.P];
        Arrays.fill(cal.dia, -1);
        cal.sobrante = new boolean[I.P];
        cal.partidasPorDia = new int[I.M][I.ND];
        List<Integer> conf = new ArrayList<>();
        for (int p = 0; p < I.P; p++) if (s.conformada(p)) conf.add(p);
        conf.sort(Comparator.<Integer>comparingInt(p -> s.maquina[p])
                .thenComparingLong(p -> I.fechaEntrega[p])
                .thenComparingInt(p -> s.diaConformacion[p])
                .thenComparingInt(p -> s.orden[p]));
        int i = 0;
        while (i < conf.size()) {
            int m = s.maquina[conf.get(i)];
            int j = i;
            while (j < conf.size() && s.maquina[conf.get(j)] == m) j++;
            int n = j - i;
            int[] idx = new int[n], disp = new int[n];
            boolean[] libre = new boolean[n];
            for (int t = 0; t < n; t++) {
                idx[t] = conf.get(i + t);
                disp[t] = I.sigHabil[m][s.diaConformacion[idx[t]]];   // primer día hábil disponible
                libre[t] = disp[t] > 0;
            }
            int restante = I.objetivo[m], L = I.minimoDiario[m], U = I.maximoDiario[m];
            int[] hab = I.diasHabiles[m];
            for (int jj = 0; jj < hab.length && restante > 0; jj++) {
                int d = hab[jj];
                int cuota = (restante - U >= L * (hab.length - jj - 1)) ? U : L;
                cuota = Math.min(cuota, restante);
                int tomadas = 0;
                for (int t = 0; t < n && tomadas < cuota; t++)
                    if (libre[t] && disp[t] <= d) { libre[t] = false; cal.dia[idx[t]] = d; tomadas++; }
                cal.partidasPorDia[m][d - 1] = tomadas;
                restante -= tomadas;
            }
            for (int t = 0; t < n; t++)
                if (cal.dia[idx[t]] < 0) {
                    cal.sobrante[idx[t]] = true;
                    cal.dia[idx[t]] = disp[t] > 0 ? disp[t] : s.diaConformacion[idx[t]];
                }
            i = j;
        }
        return cal;
    }
}
