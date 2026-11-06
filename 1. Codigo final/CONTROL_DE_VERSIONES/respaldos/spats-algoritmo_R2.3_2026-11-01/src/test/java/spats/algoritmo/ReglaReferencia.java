package spats.algoritmo;

import spats.algoritmo.nucleo.EstrategiaDecision;
import spats.algoritmo.nucleo.InstanciaSemana;

/**
 * Regla de referencia usada solo en las pruebas: la regla voraz del R2.2 (la tela va a las partidas
 * en orden de registro y cada partida va a la máquina compatible con menor carga relativa). Permite
 * comprobar que el modelo implementado reproduce los valores de la experimentación.
 */
public final class ReglaReferencia implements EstrategiaDecision {

    @Override
    public int elegirPartida(InstanciaSemana I, int[] empatadas, int n, int[] carga) {
        int p = empatadas[0];
        for (int i = 1; i < n; i++)
            if (I.partidas[empatadas[i]].ordenRegistro() < I.partidas[p].ordenRegistro()) p = empatadas[i];
        return p;
    }

    @Override
    public int elegirMaquina(InstanciaSemana I, int p, int[] carga) {
        int[] cm = I.compat[p];
        int mejor = cm[0];
        double v = (double) carga[cm[0]] / I.objetivo[cm[0]];
        for (int j = 1; j < cm.length; j++) {
            double x = (double) carga[cm[j]] / I.objetivo[cm[j]];
            if (x < v) { v = x; mejor = cm[j]; }
        }
        return mejor;
    }
}
