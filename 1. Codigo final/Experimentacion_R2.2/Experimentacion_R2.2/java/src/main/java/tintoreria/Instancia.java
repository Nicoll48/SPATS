package tintoreria;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Datos de una semana de tintorería (lunes a domingo), exportados desde R
 * (08_exportar_java.R). Los índices de partidas, máquinas, bolsas y días se
 * guardan en base 0; los días de conformación se manejan en base 1 (1 = lunes)
 * para que las claves de orden coincidan con la implementación en R.
 */
public final class Instancia {
    public String nombre;
    public int P, M, B, C, ND;
    /* P = numero de partidas candidatas de una semana (son todas en general)
    *  M = numero de maquinas de tintoreria
    *  ND = Numero de dias del horizonte de planificacion (7)
    *  B = Bolda de tela, cada artculo tendra una bolsa poara saber cuando esta sobrando de cada tela */

    // máquinas
    public String[] maquina;
    public int[] O, L, U;              // objetivo semanal, mínimo y máximo diario
    public boolean[][] habil;          // [M][ND]
    public int[][] sigHabil;           // [M][ND+1]: primer día hábil (base 1) desde el día a; 0 = ninguno
    public int[][] diasHabiles;        // [M][]: días hábiles (base 1)

    // partidas candidatas
    public String[] partida; /* identificadores de todas las partidas candidatas */
    public int[] fecha, tono, registro;
    /* fecha = fecha de entrega de cada partida representada numericamente
    * tono = clasificacion numerica del tono o color de cada partida
    * registros = orden de registro de cada partida */
    public int[][] compat; /* maquinas compatibles con cada partida */      // máquinas de la barca de cada partida
    public int fechaMin; /* Valor de referencia para la codificación de las fechas de la instancia. */

    // requerimientos: kg que cada partida necesita de cada bolsa (artículo)
    public int R; /* Numero total de requerimientos de tela de todas las partidas de la instancia */
    public int[] reqPartida, reqBolsa; /*
    reqpartida = indica a que partida pertenece el requerimiento q
    reqbolsa = indica de que bolsa se obtiene la tela de ese requerimiento */
    public double[] reqKg /* indica cuantos kilogramos necesita el requerimiento */;
    public int[][] reqsDePartida /* contiene todos los requerimientos que tiene cada partida */;

    // bolsas de tela (artículo)
    public String[] bolsa; /* Identifica a que conjunto de tela corresponde cada posicion :  "TelaA" */
    public double[] stock0; /* Contiene los kilos disponibles ese dia por cada pedido-articulo, esto es como la bolsa de pesos que hay */
    public double[][] llegadas;     /* indica cuántos kilos se incorporan a cada bolsa en cada día.
    I.llegadas[0][1] = 300; // Martes: Tela A
    I.llegadas[0][2] = 200; // Miércoles: Tela A
    I.llegadas[1][2] = 50;  // Miércoles: Tela B
    */   // [B][ND]

    private static List<String[]> leer(Path f) throws IOException {
        List<String> lineas = Files.readAllLines(f, StandardCharsets.UTF_8);
        List<String[]> filas = new ArrayList<>();
        for (int i = 1; i < lineas.size(); i++) {
            String l = lineas.get(i);
            if (!l.isBlank()) filas.add(l.split(",", -1));
        }
        return filas;
    }

    public static Instancia cargar(Path dir) throws IOException {
        Instancia I = new Instancia();
        I.nombre = dir.getFileName().toString();

        List<String[]> mq = leer(dir.resolve("maquinas.csv"));
        I.M = mq.size(); /*número de maquinas */
        I.ND = mq.get(0).length - 5;
        I.maquina = new String[I.M]; I.O = new int[I.M]; I.L = new int[I.M]; I.U = new int[I.M];
        I.habil = new boolean[I.M][I.ND];
        for (int m = 0; m < I.M; m++) {
            String[] r = mq.get(m);
            I.maquina[m] = r[0];
            I.O[m] = Integer.parseInt(r[2]); I.L[m] = Integer.parseInt(r[3]); I.U[m] = Integer.parseInt(r[4]);
            for (int d = 0; d < I.ND; d++) I.habil[m][d] = r[5 + d].equals("1");
        }
        I.sigHabil = new int[I.M][I.ND + 1];
        I.diasHabiles = new int[I.M][];
        for (int m = 0; m < I.M; m++) {
            List<Integer> h = new ArrayList<>();
            for (int d = 1; d <= I.ND; d++) if (I.habil[m][d - 1]) h.add(d);
            I.diasHabiles[m] = h.stream().mapToInt(Integer::intValue).toArray();
            for (int a = 1; a <= I.ND; a++) {
                int s = 0;
                for (int d : I.diasHabiles[m]) if (d >= a) { s = d; break; }
                I.sigHabil[m][a] = s;
            }
        }

        List<String[]> pa = leer(dir.resolve("partidas.csv"));
        I.P = pa.size();
        I.partida = new String[I.P]; I.fecha = new int[I.P]; I.tono = new int[I.P];
        I.registro = new int[I.P];
        I.compat = new int[I.P][];
        I.fechaMin = Integer.MAX_VALUE;
        for (int p = 0; p < I.P; p++) {
            String[] r = pa.get(p);
            I.partida[p] = r[0];
            I.fecha[p] = Integer.parseInt(r[1]);
            I.tono[p] = Integer.parseInt(r[2]);
            I.registro[p] = Integer.parseInt(r[3]);
            String[] cs = r[4].split(";");
            I.compat[p] = new int[cs.length];
            for (int j = 0; j < cs.length; j++) I.compat[p][j] = Integer.parseInt(cs[j]) - 1;
            I.fechaMin = Math.min(I.fechaMin, I.fecha[p]);
        }
        if (I.P == 0) I.fechaMin = 0;

        List<String[]> bo = leer(dir.resolve("bolsas.csv"));
        I.B = bo.size();
        I.bolsa = new String[I.B]; I.stock0 = new double[I.B]; I.llegadas = new double[I.B][I.ND];
        for (int b = 0; b < I.B; b++) {
            String[] r = bo.get(b);
            I.bolsa[b] = r[0];
            I.stock0[b] = Double.parseDouble(r[1]);
            for (int d = 0; d < I.ND; d++) I.llegadas[b][d] = Double.parseDouble(r[2 + d]);
        }

        List<String[]> rq = leer(dir.resolve("requerimientos.csv"));
        I.R = rq.size();
        I.reqPartida = new int[I.R]; I.reqBolsa = new int[I.R]; I.reqKg = new double[I.R];
        List<List<Integer>> porPartida = new ArrayList<>();
        for (int p = 0; p < I.P; p++) porPartida.add(new ArrayList<>());
        for (int k = 0; k < I.R; k++) {
            String[] r = rq.get(k);
            I.reqPartida[k] = Integer.parseInt(r[0]) - 1;
            I.reqBolsa[k] = Integer.parseInt(r[1]) - 1;
            I.reqKg[k] = Double.parseDouble(r[2]);
            porPartida.get(I.reqPartida[k]).add(k);
        }
        I.reqsDePartida = new int[I.P][];
        for (int p = 0; p < I.P; p++) I.reqsDePartida[p] = porPartida.get(p).stream().mapToInt(Integer::intValue).toArray();
        return I;
    }

    public int objetivoTotal() { int s = 0; for (int o : O) s += o; return s; }
}
