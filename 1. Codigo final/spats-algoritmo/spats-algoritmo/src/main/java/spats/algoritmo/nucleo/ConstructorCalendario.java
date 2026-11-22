package spats.algoritmo.nucleo;

import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Regla común que distribuye las partidas de cada máquina entre los días de la semana.
 *
 * <p>Las partidas de cada máquina se ordenan por fecha de entrega, día en que quedan disponibles y
 * orden de llegada. Reparto proporcional y escalonado (D-48): la meta de la máquina es
 * {@code min(objetivo, partidas conformadas)} y, al cerrar el día hábil j de H, la máquina debería llevar
 * {@code floor(meta · j / H + desfase)} partidas (en el último día, la meta completa). La cuota del día es
 * esa cifra menos lo ya ubicado, sin superar el máximo diario. El desfase
 * {@code ((m + semana ISO) mod M + 0.5) / M} reparte entre las máquinas los días que reciben una partida de
 * más y los rota cada semana, para que la planta tenga una carga diaria pareja (con objetivo 16 en siete
 * días y desfase 0.5: 2, 3, 2, 2, 2, 3, 2). Si un día no hay suficientes partidas disponibles, el faltante
 * se recupera en los días siguientes con la misma fórmula. Las partidas que exceden la meta, o que llegan
 * cuando los días restantes ya alcanzaron su máximo, se ubican en su primer día disponible como Sobrantes.</p>
 *
 * <p>Re-planificación: las fijas quedan en su día y con su clasificación. Las Asignadas fijas de días
 * anteriores a {@code I.diaInicio} (o de días ya no hábiles) descuentan de la meta; las del día
 * {@code I.diaInicio} ocupan lugar en ese día. Desde ese día se aplica la misma fórmula, con H y j contados
 * desde {@code I.diaInicio}, y ninguna partida se ubica antes de {@code I.diaInicio} ni de su día
 * {@code I.desde}.</p>
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
        int U = I.maximoDiario[m];
        int fijasFuera = 0, fijasDentro = 0, H = 0;
        for (int d = 1; d <= I.ND; d++) {
            boolean dentro = I.habil[m][d - 1] && d >= I.diaInicio;
            if (dentro) { H++; fijasDentro += fijas[d - 1]; }
            else if (fijas[d - 1] > 0) { cal.partidasPorDia[m][d - 1] = fijas[d - 1]; fijasFuera += fijas[d - 1]; }
        }
        // D-48: meta por ubicar desde el día de inicio y desfase que rota cada semana entre las máquinas
        int semana = I.dias[0].get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int meta = Math.max(0, Math.min(I.objetivo[m] - fijasFuera, n + fijasDentro)), ubicadas = 0, j = 0;
        double desfase = (((m + semana) % I.M) + 0.5) / I.M;
        for (int d : I.diasHabiles[m]) {
            if (d < I.diaInicio) continue;
            j++;
            int metaHastaHoy = j == H ? meta : (int) Math.floor((double) meta * j / H + desfase);
            int cuota = Math.min(U, metaHastaHoy - ubicadas);
            int tomadas = fijas[d - 1];
            for (int t = 0; t < n && tomadas < cuota; t++)
                if (libre[t] && disp[t] <= d) { libre[t] = false; cal.dia[idx[t]] = d; tomadas++; }
            cal.partidasPorDia[m][d - 1] = tomadas;
            ubicadas += tomadas;
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
