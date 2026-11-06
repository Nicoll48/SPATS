package tintoreria;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Modelo común con el que los algoritmos construyen y evalúan una semana.
 *
 * Dos decisiones que se evalúan juntas:
 *  1) Conformación: cuando la tela acumulada de un artículo alcanza para
 *     completar a más de una partida (del mismo o de distintos pedidos), primero
 *     manda la fecha de entrega más próxima; entre las empatadas decide el
 *     algoritmo.
 *  2) Máquina: cada partida conformada va a una de las máquinas de su barca.
 *
 * La semana se simula día a día. Una partida nunca se llena a medias: recibe
 * tela solo cuando la tela acumulada cubre todo lo que necesita; los kilogramos
 * que no alcanzan quedan guardados en el artículo para los días siguientes.
 */
public final class Modelo {

    public static double ALFA = 1.0, BETA = 1.0, GAMMA = 0.25; /* */
    static final double EPS = 1e-9;

    /** Cómo elige cada algoritmo. */
    public static final class Regla {
        public static final int FIJA = 0, MENOR_CARGA = 1, HORMIGA = 2; /*
        * Fija = 0, Utilizar una máquina previamente seleccionada, como en el algoritmo genético.
        * Menor_carga = 1, Seleccionar la máquina compatible con menor carga, como en la regla voraz.
        * HORMIGA = 2, Seleccionar una máquina mediante la lógica probabilística de ACO.
        */

        public int[] prioridad;      // menor = recibe primero la tela; null = elige la hormiga
        public int tipoMaquina;
        public int[] maquinaFija;    // máquina de cada partida (algoritmo genético)
        public double[][] tau;       // feromona [P][M]
        public double a = 1, b = 1;
        public Random rnd;
    }

    public static final class Solucion {
        public int[] diaConf, maq, orden;     // diaConf en base 1; -1 = no conformada
        /* el arreglo tiene de linea todas las patidas que fueron asignadas
        * diasconf = dia de conformacion de todas las partidas
        * maq = Índice de la máquina de tintorería asignada a esa partida
        * orden = en que orden se asigno la partida, o sea como fue conformandola: */
        public double[] stockFinal; /* Lo que queda de stock final */
    }

    public static final class Calendario {
        public int[] dia;            // base 1; -1 = sin día
        public boolean[] sobrante;
        public int[][] nDia;         // [M][ND] partidas regulares
    }

    public static final class Evaluacion {
        public double F;
        public int ES, ED, lavados, conformadas, asignadas, sobrantes;
        public int[] N;
        public Calendario cal;
    }

    static int ruleta(double[] w, int n, Random rnd) {
        double tot = 0; for (int i = 0; i < n; i++) tot += w[i];
        double u = rnd.nextDouble() * tot, acc = 0;
        for (int i = 0; i < n; i++) { acc += w[i]; if (acc >= u) return i; }
        return n - 1;
    }

    static int elegirMaquina(Instancia I, Regla r, int p, int[] carga) {
        int[] cm = I.compat[p]; /* Se obtiene las maquinas compatibles para la partida */
        if (cm.length == 1) return cm[0]; /* SI solo es una se coloca */

        if (r.tipoMaquina == Regla.FIJA) return r.maquinaFija[p]; /* esta es la evaluacion del algoritmo genetico, la maquina ya esta definida y se evalara con varias donde corresponda al AG*/
        if (r.tipoMaquina == Regla.MENOR_CARGA) { /* El algoritmo voraz: Su objetivo es seleccionar la máquina compatible con menor carga relativa respecto a su objetivo semanal.*/
            int mejor = cm[0]; /* inicialmente considera que la mejor maqunia es la primera compatible */
            double v = (double) carga[cm[0]] / I.O[cm[0]]; /*  calcula la carga relativa de la maquina primera que se escoge */
            for (int j = 1; j < cm.length; j++) { /* recorre las demas maquinas, inicia en 1 porque la de pos 0 ya fue considerada como la mejor incial */
                double x = (double) carga[cm[j]] / I.O[cm[j]]; /* carga relativa de la maquina*/
                if (x < v) {
                    v = x;
                    mejor = cm[j];
                }
            }
            return mejor;
        }
        /* CASO DE ACO */
        /* Seleccion probabilistica de ACO, ACO calcula un peso para cada máquina compatible y después realiza un sorteo ponderado. */
        double[] w = new double[cm.length]; /* Crea un arreglo para guardar los pesos de las maquinas compatibles */
        for (int j = 0; j < cm.length; j++) { /* por cada maquina compatible */
            double T = (carga[cm[j]] + 1.0) / I.O[cm[j]]; /* la carga proyectada, evalúa cómo quedaría la carga de la máquina si recibe P101. */
            w[j] = Math.pow(r.tau[p][cm[j]], r.a) * Math.pow(1.0 / T, r.b); /* calcula el peso */
        }
        return cm[ruleta(w, cm.length, r.rnd)]; /* ruleta convierte los pesos en probabilidades */
    }

    /** Marca en cub las partidas pendientes que la tela acumulada permite completar. */
    static int cubribles(Instancia I, double[] stock, boolean[] pendiente, int[] cub) {
        /*stock: kg disponibles actualmente de cada artiulo
        * pendiente:: indica que partidas todavia no han sida conformadas
        * cub se guardan las partidas que si podran confirmarse de esta funcion. */
        boolean[] falta = new boolean[I.P]; /* Tamaño de todas las partidas*/
        for (int k = 0; k < I.R; k++) /* voy a recorrer todos los requerimientos (telas que necesitan las partidas) */
            if (I.reqKg[k] > stock[I.reqBolsa[k]] + EPS) /* la cantidad de kg que necesita el req es mayor que el stock disponible de ese articulo*/
                falta[I.reqPartida[k]] = true; /* para esa partida, falta*/
        int n = 0;
        for (int p = 0; p < I.P; p++) /* por cada partida */
            if (pendiente[p] && !falta[p]) /* si esta pendiente y no le falta requerimiento */
                cub[n++] = p;
        return n;
        /* devuelve todas las partidas que se pueden completar con la cantidad de tela que sale de la proyeccion, sin descontar nada,
        * es decir que acepta los casos en que si hay solo 500 kg de tela A y 3 partidas de tela A de 250 kg, las 3 entran como opciones a considerar*/
    }

    public static Solucion simular(Instancia I, Regla r) {
        double[] stock = I.stock0.clone(); /* Aqui crea un arreglo llamado stock que contiene los kilogramos
         disponibles inicialmente en cada bolsa de tela. */

        boolean[] pendiente = new boolean[I.P]; /* Crea un arreglo con una posición por cada partida candidata. */
        Arrays.fill(pendiente, true); /* Coloca todo en true */

        Solucion s = new Solucion();  /* Crea el objeto donde se guardará el resultado. */

        s.diaConf = new int[I.P];
        s.maq = new int[I.P];
        s.orden = new int[I.P];

        Arrays.fill(s.diaConf, -1);
        Arrays.fill(s.maq, -1);
        Arrays.fill(s.orden, -1);

        int[] carga = new int[I.M]; /*Crea un arreglo con una posición por máquina para ver la carga de cada maquina */
        int k = 0; /* Cuenta cuántas partidas se han conformado hasta ese momento. */

        int[] cub = new int[I.P]; /* guardar los índices de las partidas que pueden conformarse con el stock disponible en un momento determinado. */
        double[] w = new double[I.P]; /* Guarda los pesos de selección probabilística cuando se utiliza ACO.
        Esos pesos se utilizan para elegir una partida entre varias alternativas empatadas.
        En la regla voraz y AG, esa selección se hace mediante prioridades, por lo que w no interviene en el desempate.
        */

        for (int d = 1; d <= I.ND; d++) { /* Recorre los dias de la semana */
            for (int b = 0; b < I.B; b++)
                stock[b] += I.llegadas[b][d - 1]; /* Acumular lo que hay en la bolsa de cada articulo que sale por cada dia*/
            while (true) {
                int n = cubribles(I, stock, pendiente, cub);
                /* Este método identifica qué partidas pendientes pueden conformarse completamente con los materiales disponibles
                * I: requerimientos de las partidas.
                * stock: cantidades disponibles de tela.
                * pendiente: cuáles todavía no fueron conformadas.
                * cub: arreglo donde se guardarán los índices de las partidas conformables.
                *  n = cuantas partidas fueron conformables */

                if (n == 0) break;                       // la tela queda guardada y evalua el siguiente dia
                int fmin = Integer.MAX_VALUE;            // (1) fecha de entrega más próxima

                for (int i = 0; i < n; i++) /* Este ciclo recorre las n partidas conformables */
                    fmin = Math.min(fmin, I.fecha[cub[i]]); /* La funcion devuelve el menor valor: aquí se busca la fecha de entrega más próxima entre las partidas
                    que ya pueden conformarse, no entre todas las candidatas */

                int n2 = 0;
                for (int i = 0; i < n; i++)
                    if (I.fecha[cub[i]] == fmin) /* Solamente conserva las partidas cuya fecha de entrega coincide con fmin. */
                        cub[n2++] = cub[i]; /* Sobreescribe el arreglo cub con las posiciones de las partidas con fmin */
                int p;                                   // (2) entre las empatadas decide el algoritmo
                if (n2 == 1) /* Si solamente hay una partida con esa fecha, se selecciona directamente.*/
                    p = cub[0];
                else if (r.prioridad != null) {
                    /* En voraz : r.prioridad = I.registro
                    * en AG = El algoritmo genera una permutación de las partidas como parte de cada cromosoma.*/
                    p = cub[0]; /* toma provisionalmente la primera partida empatada. */
                    for (int i = 1; i < n2; i++) /* revisa las demás partidas empatadas. */
                        if (r.prioridad[cub[i]] < r.prioridad[p]) /* compara sus números de prioridad. */
                            p = cub[i]; /* si encuentra una con menor número, la convierte en la seleccionada. */
                } else { /* Esta es para ACO que tiene su propia forma de evaluar */
                    for (int i = 0; i < n2; i++) { /* Recorro las partidas con fechas iguales */
                        int q = cub[i]; /* Obtiene el indice de partida que esta evaluando en ese momento */
                        double sw = 0;
                        for (int m : I.compat[q]) { /* Para cada máquina compatible con la partida q, calcula qué tan atractiva sería esa asignación. */
                            double T = (carga[m] + 1.0) / I.O[m]; /* Es mas 1 Porque está evaluando cómo quedaría la carga de la máquina si se le agregara la partida que estamos considerando.*/
                            sw += Math.pow(r.tau[q][m], r.a) * Math.pow(1.0 / T, r.b); /* calcular el peso mediante feromonas */
                        }
                        w[i] = sw; /*  utiliza el atractivo conjunto de sus opciones compatibles para calcular el peso de esa partida. De esta manera, una partida con varias máquinas compatibles
                         puede obtener más peso que una con una sola máquina, dependiendo de las feromonas y cargas.*/
                    }
                    p = cub[ruleta(w, n2, r.rnd)]; /* Este método utiliza los pesos almacenados en w para escoger una partida de forma probabilística. */
                }
                /* Despues de seleccionar la partida */
                for (int q : I.reqsDePartida[p]) /* por cada requerimiento de tela que tiene esa partida */
                    stock[I.reqBolsa[q]] -= I.reqKg[q];
                pendiente[p] = false;
                k++;
                s.diaConf[p] = d;
                s.orden[p] = k;
                int m = elegirMaquina(I, r, p, carga); /* Elegir la maquina */
                s.maq[p] = m;
                carga[m]++;
            }
        }
        s.stockFinal = stock;
        return s;
    }

    /** Calendario: cada día 2 o 3 partidas completas hasta el objetivo; el exceso, Sobrante. */
    public static Calendario calendario(Instancia I, Solucion s) {
        Calendario cal = new Calendario();
        cal.dia = new int[I.P]; Arrays.fill(cal.dia, -1);
        cal.sobrante = new boolean[I.P];
        cal.nDia = new int[I.M][I.ND];
        List<Integer> conf = new ArrayList<>();
        for (int p = 0; p < I.P; p++) if (s.maq[p] >= 0) conf.add(p);
        conf.sort((x, y) -> Double.compare(clave1(I, s, x), clave1(I, s, y)));
        int i = 0;
        while (i < conf.size()) {
            int m = s.maq[conf.get(i)];
            int j = i; while (j < conf.size() && s.maq[conf.get(j)] == m) j++;
            int n = j - i;
            int[] idx = new int[n], disp = new int[n]; boolean[] libre = new boolean[n];
            for (int t = 0; t < n; t++) {
                idx[t] = conf.get(i + t);
                disp[t] = I.sigHabil[m][s.diaConf[idx[t]]];
                libre[t] = disp[t] > 0;
            }
            int R = I.O[m], L = I.L[m], U = I.U[m];
            int[] hab = I.diasHabiles[m]; int nk = hab.length;
            for (int jj = 0; jj < nk; jj++) {
                if (R <= 0) break;
                int d = hab[jj];
                int cuota = (R - U >= L * (nk - jj - 1)) ? U : L;
                if (cuota > R) cuota = R;
                int tom = 0;
                for (int t = 0; t < n && tom < cuota; t++) if (libre[t] && disp[t] <= d) {
                    libre[t] = false; cal.dia[idx[t]] = d; tom++;
                }
                cal.nDia[m][d - 1] = tom;
                R -= tom;
            }
            for (int t = 0; t < n; t++) if (cal.dia[idx[t]] < 0) {
                cal.sobrante[idx[t]] = true;
                cal.dia[idx[t]] = disp[t] > 0 ? disp[t] : s.diaConf[idx[t]];
            }
            i = j;
        }
        return cal;
    }

    static double clave1(Instancia I, Solucion s, int p) {
        return (((s.maq[p] + 1) * 2000.0 + (I.fecha[p] - I.fechaMin)) * 10 + s.diaConf[p]) * 10000 + s.orden[p];
    }

    public static Evaluacion evaluar(Instancia I, Solucion s) {
        Evaluacion e = new Evaluacion();
        Calendario cal = calendario(I, s);
        e.cal = cal; e.N = new int[I.M];
        List<Integer> conf = new ArrayList<>();
        for (int p = 0; p < I.P; p++) if (s.maq[p] >= 0) { conf.add(p); e.N[s.maq[p]]++; }
        for (int m = 0; m < I.M; m++) { int dlt = e.N[m] - I.O[m]; e.ES += dlt * dlt; }
        for (int m = 0; m < I.M; m++) for (int d = 0; d < I.ND; d++) if (I.habil[m][d]) {
            int nd = cal.nDia[m][d];
            int def = Math.max(0, I.L[m] - nd), exc = Math.max(0, nd - I.U[m]);
            e.ED += def * def + exc * exc;
        }
        conf.sort((x, y) -> Double.compare(clave2(s, cal, x), clave2(s, cal, y)));
        for (int t = 1; t < conf.size(); t++) {
            int a = conf.get(t - 1), b = conf.get(t);
            if (s.maq[a] == s.maq[b] && I.tono[b] < I.tono[a]) e.lavados++;
        }
        e.conformadas = conf.size();
        for (int p : conf) if (cal.sobrante[p]) e.sobrantes++;
        e.asignadas = e.conformadas - e.sobrantes;
        e.F = ALFA * e.ES + BETA * e.ED + GAMMA * e.lavados;
        return e;
    }

    static double clave2(Solucion s, Calendario cal, int p) {
        return (((s.maq[p] + 1) * 10.0 + cal.dia[p]) * 2 + (cal.sobrante[p] ? 1 : 0)) * 10000 + s.orden[p];
    }

    /** Validador independiente: rehace el consumo de tela y revisa todas las reglas. */
    public static List<String> validar(Instancia I, Solucion s, Evaluacion e) {
        List<String> err = new ArrayList<>();
        double[] stock = I.stock0.clone();
        boolean[] pendiente = new boolean[I.P]; Arrays.fill(pendiente, true);
        List<Integer> sec = new ArrayList<>();
        for (int p = 0; p < I.P; p++) if (s.diaConf[p] > 0) sec.add(p);
        sec.sort((x, y) -> Integer.compare(s.orden[x], s.orden[y]));
        int[] cub = new int[I.P];
        int j = 0;
        for (int d = 1; d <= I.ND; d++) {
            for (int b = 0; b < I.B; b++) stock[b] += I.llegadas[b][d - 1];
            while (j < sec.size() && s.diaConf[sec.get(j)] == d) {
                int p = sec.get(j);
                int n = cubribles(I, stock, pendiente, cub);
                boolean esta = false; int fmin = Integer.MAX_VALUE;
                for (int i = 0; i < n; i++) { if (cub[i] == p) esta = true; fmin = Math.min(fmin, I.fecha[cub[i]]); }
                if (!esta) err.add("partida sin tela suficiente");
                else if (I.fecha[p] > fmin) err.add("prioridad por fecha de entrega incumplida");
                for (int q : I.reqsDePartida[p]) stock[I.reqBolsa[q]] -= I.reqKg[q];
                pendiente[p] = false; j++;
            }
            for (double x : stock) if (x < -1e-6) err.add("consumo mayor que la tela disponible");
            if (cubribles(I, stock, pendiente, cub) > 0) err.add("partida completable no incluida");
        }
        if (j < sec.size()) err.add("orden de conformación inconsistente");
        for (int p = 0; p < I.P; p++) if (s.diaConf[p] > 0) {
            boolean ok = false; for (int m : I.compat[p]) if (m == s.maq[p]) ok = true;
            if (!ok) err.add("máquina incompatible");
            if (e.cal.dia[p] < s.diaConf[p]) err.add("ubicada antes de estar disponible");
            if (!e.cal.sobrante[p] && !I.habil[s.maq[p]][e.cal.dia[p] - 1]) err.add("día indisponible");
        }
        for (int m = 0; m < I.M; m++) {
            int reg = 0, sob = 0;
            for (int p = 0; p < I.P; p++) if (s.maq[p] == m) { if (e.cal.sobrante[p]) sob++; else reg++; }
            if (reg > I.O[m]) err.add("asignadas por encima del objetivo");
            // Sobrante: excede el objetivo o ya no cabe en ningún día hábil desde que está disponible
            if (sob > 0 && reg < I.O[m])
                for (int p = 0; p < I.P; p++) if (s.maq[p] == m && e.cal.sobrante[p])
                    for (int d = s.diaConf[p]; d <= I.ND; d++)
                        if (I.habil[m][d - 1] && e.cal.nDia[m][d - 1] < I.U[m]) err.add("Sobrante con espacio disponible en la semana");
            for (int d = 0; d < I.ND; d++) if (e.cal.nDia[m][d] > I.U[m]) err.add("más que el máximo diario");
        }
        return err.stream().distinct().toList();
    }
}
