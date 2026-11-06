package spats.algoritmo.nucleo;

import java.util.Arrays;

/**
 * Simula la semana día a día y construye una solución con las decisiones de una estrategia.
 *
 * <p>Cada día se suma a cada bolsa la tela que queda disponible. Mientras la tela acumulada permita
 * completar alguna partida pendiente:</p>
 * <ol>
 *   <li>se toman las partidas cuya tela alcanza para completarlas por entero (una partida nunca
 *       se llena a medias);</li>
 *   <li>entre ellas se conservan las de fecha de entrega más próxima (RD-07);</li>
 *   <li>si queda más de una, la estrategia elige cuál recibe la tela;</li>
 *   <li>la partida consume toda su tela y la estrategia elige la máquina de su barca.</li>
 * </ol>
 * <p>Los kg que no alcanzan para completar otra partida quedan guardados en la bolsa.</p>
 * <p>Re-planificación: la simulación empieza el día {@code I.diaInicio}. Las fijas ya tienen máquina y día y
 * cuentan en la carga desde el principio; la tela de las fijas conformadas el día de inicio sale ese día. Antes de repartir la tela del día de inicio, la estrategia elige la
 * máquina de cada conservada. Una partida no se conforma antes de su día {@code I.desde}.</p>
 */
public final class SimuladorConformacion {

    static final double EPS = 1e-9;

    private SimuladorConformacion() { }

    public static Solucion simular(InstanciaSemana I, EstrategiaDecision estrategia) {
        double[] stock = I.stockInicial.clone();
        boolean[] pendiente = new boolean[I.P];
        Arrays.fill(pendiente, true);
        Solucion s = new Solucion();
        s.diaConformacion = new int[I.P]; s.maquina = new int[I.P]; s.orden = new int[I.P];
        Arrays.fill(s.diaConformacion, -1); Arrays.fill(s.maquina, -1); Arrays.fill(s.orden, -1);
        int[] carga = new int[I.M];
        int[] cub = new int[I.P];
        int k = I.nFijas;
        for (int p = 0; p < I.P; p++)
            if (I.fija[p]) {
                pendiente[p] = false;
                s.maquina[p] = I.fijaMaquina[p];
                s.diaConformacion[p] = I.diaConformacionPrevia[p];
                s.orden[p] = I.fijaOrden[p];
                carga[s.maquina[p]]++;
            }
        for (int p = 0; p < I.P; p++)
            if (I.conservada[p]) {
                pendiente[p] = false;
                s.diaConformacion[p] = I.diaConformacionPrevia[p];
                s.orden[p] = ++k;
                int m = I.compat[p].length == 1 ? I.compat[p][0] : estrategia.elegirMaquina(I, p, carga);
                s.maquina[p] = m;
                carga[m]++;
            }
        for (int d = I.diaInicio; d <= I.ND; d++) {
            for (int b = 0; b < I.B; b++) stock[b] += I.llegadas[b][d - 1];
            if (d == I.diaInicio && I.hayConsumoFijasInicio)
                for (int b = 0; b < I.B; b++) stock[b] -= I.consumoFijasInicio[b];
            while (true) {
                int n = completables(I, stock, pendiente, cub, d);
                if (n == 0) break;                                   // la tela queda guardada
                long fmin = Long.MAX_VALUE;                          // fecha de entrega más próxima
                for (int i = 0; i < n; i++) fmin = Math.min(fmin, I.fechaEntrega[cub[i]]);
                int n2 = 0;
                for (int i = 0; i < n; i++) if (I.fechaEntrega[cub[i]] == fmin) cub[n2++] = cub[i];
                int p = n2 == 1 ? cub[0] : estrategia.elegirPartida(I, cub, n2, carga);
                for (int q : I.reqsDePartida[p]) stock[I.reqBolsa[q]] -= I.reqKg[q];
                pendiente[p] = false;
                s.diaConformacion[p] = d;
                s.orden[p] = ++k;
                int m = I.compat[p].length == 1 ? I.compat[p][0] : estrategia.elegirMaquina(I, p, carga);
                s.maquina[p] = m;
                carga[m]++;
            }
        }
        s.stockFinal = stock;
        return s;
    }

    /**
     * Guarda en {@code cub} las partidas pendientes que pueden conformarse el día {@code d} y cuya tela alcanza
     * para completarlas; devuelve cuántas son.
     */
    static int completables(InstanciaSemana I, double[] stock, boolean[] pendiente, int[] cub, int d) {
        boolean[] falta = new boolean[I.P];
        for (int k = 0; k < I.R; k++)
            if (I.reqKg[k] > stock[I.reqBolsa[k]] + EPS) falta[I.reqPartida[k]] = true;
        int n = 0;
        for (int p = 0; p < I.P; p++) if (pendiente[p] && !falta[p] && I.desde[p] <= d) cub[n++] = p;
        return n;
    }
}
