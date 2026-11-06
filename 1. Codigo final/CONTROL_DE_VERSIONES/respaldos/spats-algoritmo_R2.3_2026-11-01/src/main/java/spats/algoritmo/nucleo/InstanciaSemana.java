package spats.algoritmo.nucleo;

import java.time.LocalDate;
import java.util.List;

import spats.algoritmo.entrada.Maquina;
import spats.algoritmo.entrada.Partida;

/**
 * Representación indexada de una semana, construida por {@link ConstructorInstancia}.
 * Las partidas, máquinas y bolsas de tela se identifican por su posición (base 0);
 * los días se numeran de 1 (lunes) a ND (domingo).
 */
public final class InstanciaSemana {
    // --- calendario --------------------------------------------------------
    public int ND;                      // días del calendario
    public LocalDate[] dias;            // fecha de cada día (índice 0 = lunes)

    // --- máquinas ----------------------------------------------------------
    public int M;
    public Maquina[] maquinas;
    public int[] objetivo;              // objetivo semanal efectivo
    public int[] minimoDiario, maximoDiario;
    public boolean[][] habil;           // [M][ND]
    public int[][] diasHabiles;         // [M][]: días hábiles (base 1)
    public int[][] sigHabil;            // [M][ND+1]: primer día hábil desde el día a (base 1); 0 = ninguno

    // --- partidas candidatas ----------------------------------------------
    public int P;
    public Partida[] partidas;
    public long[] fechaEntrega;         // día de época de la fecha de entrega del pedido
    public int[] tono;                  // rango de tonalidad (1 = Blanco ... 4 = Oscuro)
    public int[][] compat;              // máquinas de la barca de cada partida

    // --- bolsas de tela (una por artículo) ----------------------------------
    public int B;
    public String[] bolsas;
    public double[] stockInicial;       // kg en almacén al iniciar la semana
    public double[][] llegadas;         // [B][ND] kg que quedan disponibles cada día

    // --- requerimientos: kg que cada partida necesita de cada bolsa -----------
    public int R;
    public int[] reqPartida, reqBolsa;
    public double[] reqKg;
    public int[][] reqsDePartida;

    // --- partidas que no pueden conformarse esta semana -----------------------
    public List<PartidaExcluida> excluidas;
    public List<String> advertencias;

    public int objetivoTotal() {
        int s = 0;
        for (int o : objetivo) s += o;
        return s;
    }
}
