package tintoreria;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** Regla voraz (línea base), algoritmo genético y colonia de hormigas. */
public final class Algoritmos {

    public static final class Resultado { /* EL objetivo es guardar lo que devuelve cualquiera de los algoritmos */
        public double F; /* Valor objetivo (el numero) de la planificacion encontrada */
        public Modelo.Solucion sol; /* Guarda el objeto que representa la planificacion generada */
        public Modelo.Evaluacion ev /* Guarda el resultado de evaluar la planificacion */;
        public double[] historial /* Guarda como va mejorando el mejor valor F encontrado durante la busqueda */;
        public int evaluaciones /* Guarda cuantas observaciones fueron evaluadas durante la busqueda */;
        public long tiempoMs /* Guarda cuando demoro la ejecucion */;
    }

    public static int PASO_CONVERGENCIA = 100; /* Para que el algoirtmo regisre su mejor valor F cada 100 evaluaciones
    Si evaluaciones es = 6000 entonces 6000/100 = 60, 60 puntos de convergencia */

    /** Registro del mejor valor encontrado y de la convergencia. */
    static final class Registro {
        int evals; /* Cuenta la cantidad de evaluaciones realizadas, inicialmente cero */
        double mejor = Double.POSITIVE_INFINITY; /* Inicializa la variable mejor con infinito positivo, porque el problema de es minimizacion */
        Object mejorSol; /* Guarda la mejor solucion encontrada */
        final double[] hist /* Declara el arreglo que almacenará el historial de convergencia. */;

        Registro(int presupuesto) {
            hist = new double[presupuesto / PASO_CONVERGENCIA]; /* UN arreglo del tamaño de la division */
            Arrays.fill(hist, Double.NaN) /* llena las posiciones con NaN*/;
        }

        void anotar(double F, Object sol) { /* Cada que el algoritmo evalua una solucion candidata: F (valor objetivo obtenido, sol (Solcuion de lo que salio) */
            evals++;
            if (F < mejor) { /* SI es mejor que la anterior (de la funcion objetivo) entonces se cambia por la mejor encontrada*/
                mejor = F;
                mejorSol = sol;
            }
            if (evals % PASO_CONVERGENCIA == 0 && evals / PASO_CONVERGENCIA <= hist.length) /* Si estamos en una evaluacion multiplo de 100 y
            verifica que la posición correspondiente no supere la cantidad de registros disponibles. */
                hist[evals / PASO_CONVERGENCIA - 1] = mejor; /* Guarda el mejor F encontrado cada 100 evaluaciones sin que superere el limite*/
        }
    }

    // =====================================================================
    // REGLA VORAZ: la tela va a las partidas en el orden en que fueron
    // registradas y cada partida va a la máquina compatible con menor carga.
    // =====================================================================
    public static Resultado reglaVoraz(Instancia I) {
        long t0 = System.nanoTime(); /* solo es para guardar el tiempo en que comienza la ejecucion */
        Modelo.Regla r = new Modelo.Regla(); /* Crea un objeto Regla, establece el criterio con el cual se toman las decisiones */

        r.prioridad = I.registro; /* Indica que las prioridades se toman del orden de registro de las partidas. */
        r.tipoMaquina = Modelo.Regla.MENOR_CARGA; /* al elegir una máquina compatible, se utilizará la estrategia MENOR_CARGA */
        Resultado res = new Resultado(); /* Crea un objeto vacío para guardar el resultado. */

        res.sol = Modelo.simular(I, r); /* Construye una planificación utilizando la instancia I y la regla voraz r. */
        res.ev = Modelo.evaluar(I, res.sol); /* Evalúa la planificación generada, mide su calidad */
        res.F = res.ev.F; /* Extrae el valor final de la función objetivo. */
        res.evaluaciones = 1; /* Indica que se evaluó una sola planificación. */
        res.tiempoMs = (System.nanoTime() - t0) / 1_000_000;
        return res; /* De vuelve un resultado con la planificacion generada y sus metricas */
    }

    // =====================================================================
    // ALGORITMO GENÉTICO (Campo et al., 2022)
    // Gen = (partida, número entre 0 y 1). El orden de los genes define qué
    // partida recibe primero la tela cuando hay empate; el número del gen elige
    // la máquina de su barca con igual probabilidad. Población aleatoria,
    // elitismo, selección aleatoria de padres, cruce de un punto con reparación
    // y mutación SWAP.
    // =====================================================================
    /** Valores de la calibración 2^3 (R/07_calibracion.R). */
    public static final class ParamAG {
        public int poblacion = 50; /*Trabaja con una población de 50 individuos, cada uno con una propuesta de planificación. */
        public double elitismo = 0.10, pCruce = 0.90, pMutacion = 0.30;
        /* elitismo =Conserva el 10 % de los mejores individuos al formar la siguiente generación.
        * pCruce = Cada nuevo descendiente tiene un 90 % de probabilidad de generarse combinando dos padres.
        * pmutacion = Cada nuevo descendiente tiene un 30 % de probabilidad de intercambiar dos posiciones de su orden de partidas.*/
    }

    static final class Individuo {
        int[] perm; /* orden de preferencia de las partidas, el orden de las partidas se utiliza para desempatar partidas
        conformables que tienen la misma fecha de entrega */
        double[] r; /* numeros entre 0 y 1 que sirven para elegir maquinas comptatibles, mayor a 0.5 elige una y el otro caso elige otra*/
        double F; /* calidad de la solucion a partir de ese individuo */
    }

    static Modelo.Solucion decodificar(Instancia I, int[] perm, double[] r) {
        /*toma las decisiones propuestas por un individuio del algoritmo genetico y las convierte en intrucciones que simular puede utilizar
        para construir una planificaicon */

        int[] pos = new int[I.P]; /* Arreglo de prioridades */
        for (int i = 0; i < I.P; i++)
            pos[perm[i]] = i + 1; /* con esto como que le asigna una prioridad, el menor numero tiene mayor preferencia */

        int[] maq = new int[I.P];
        for (int p = 0; p < I.P; p++) {
            int[] cm = I.compat[p]; /* cm = maquinas compatatibles para esa partida */

            maq[p] = cm[Math.max(1, (int) Math.ceil(r[pos[p] - 1] * cm.length)) - 1];
            /* de todas las maquinas compatabiles elijo una de manera aleatoria gracias al
            * arreglo de r*/
        }
        /*Selecciona una de las máquinas compatibles con la partida p, utilizando un número decimal del cromosoma genético,
         y guarda la máquina seleccionada en maq[p].*/


        Modelo.Regla reg = new Modelo.Regla();
        reg.prioridad = pos;
        reg.tipoMaquina = Modelo.Regla.FIJA; /* es fija por GA y porque el cromosoma ya propuso una maquina para cada partida durante la funcion */
        reg.maquinaFija = maq;
        return Modelo.simular(I, reg);
    }

    public static Resultado algoritmoGenetico(Instancia I, long semilla, int presupuesto, ParamAG par) {
        /* Esta función busca una planificación con el menor valor posible de la función objetivo F.*/
        /* Genera mutltiples individuos donde cada individuio propone
        * Un orden de preferencia de partidas (perm)
        * Una eleccion de maquina compatible por partida, codificada mediante
        * numeros decimales (r) */

        /* Recibe
        * semilla = numero utilizado para incializar el generador aleatorio y permitir reproducir
        * una ejecucion
        * presupuesto = cantidad maaxima de evalauciones de soluciones que realizara el algoritmo
        * par configuracion del algoritmo genetico, problacion, elitismo, cruce y mutacion */

        long t0 = System.nanoTime(); /* guarda el instante incial de ejecucion */
        Random rnd = new Random(semilla); /* crea el generador de numeros aleatorios */
        Registro R = new Registro(presupuesto) /* guarda el registros de las evalauciones */;
        int n = I.P, NP = par.poblacion;

        /* n = numero de partidas candidatas existentes en la instancia
        * NP = numero de individuos que tendra la poblacion genetica. */

        List<Individuo> pob = new ArrayList<>(); /* lista para almacenar los individuos */

        for (int i = 0; i < NP && R.evals < presupuesto; i++) { /* construye la poblacion incial */
            Individuo x = new Individuo();

            x.perm = permutacion(n, rnd); /* crea un orden aleatorio con todas las partidas de la instancia */
            x.r = new double[n]; /* crea un arreglo de numeros decimales con una posicion por partida*/

            for (int j = 0; j < n; j++)
                x.r[j] = rnd.nextDouble(); /* lo rellena con numeros aleatorios entre 0 y 1 */

            x.F = Modelo.evaluar(I, decodificar(I, x.perm, x.r)).F; /* Genera el resultado */
            R.anotar(x.F, x); /* incrementa R.evals , actualiza el mejor F, guarda el individui si es el mejor encontrado */
            pob.add(x); /* añade ese individuo a la poblacion */
        }

        /* ELITISMO */
        int nElite = Math.max(1, (int) Math.round(par.elitismo * NP)); /* calcula cuantos individuos se convervaran en cada nueva generacion */
        /* par.elistismo = 0.10 -> NP = 50 -> 5*/

        /* EVOLUCION GENETICA */
        while (R.evals < presupuesto) {
            pob.sort((a, b) -> Double.compare(a.F, b.F));
            /* Esta línea ordena la lista pob de menor a mayor F. */

            /* CREAR LA NUEVA POBLACION CON LOS MEJORES INDIVIDUOS */
            List<Individuo> nueva = new ArrayList<>(pob.subList(0, nElite)); /* coloca los primeros nElite */

            /* GENERAR LOS DESCENDIENTES */
            while (nueva.size() < NP && R.evals < presupuesto) { /* hasta completar la nueva poblacion o alcanzar el presupuesto */
                int p1 = rnd.nextInt(NP), p2 = rnd.nextInt(NP - 1); /* se selecciona aleatoriamente dos posiciones de la poblacion */
                /* para los padres */
                if (p2 >= p1) /* ajusta el indice para garantizar que p2 sea distinto de p1*/
                    p2++;   // selección aleatoria

                Individuo a = pob.get(p1), b = pob.get(p2), h = new Individuo();

                if (rnd.nextDouble() < par.pCruce && n > 1) { /* el cruce se realiza cuando el numero aleatorio generado es menor que 0.9
                y existen al menos dos partidas (cada descendiente tiene un 90 % de probabilidad de realizar un cruce) */
                    int c = 1 + rnd.nextInt(n - 1); // cruce de un punto, es el punto que divide el cromosoma
                    h.perm = new int[n];
                    h.r = new double[n];
                    boolean[] usado = new boolean[n]; /* Crea un arreglo para recordar cuáles partidas ya fueron copiadas al hijo. */

                    for (int i = 0; i < c; i++) {
                        h.perm[i] = a.perm[i]; /* copiar la primera parteo del padre A*/
                        usado[a.perm[i]] = true;
                        h.r[i] = a.r[i];
                    }
                    int k = c;
                    for (int i = 0; i < n; i++)
                        if (!usado[b.perm[i]])
                            h.perm[k++] = b.perm[i];   // reparación

                    for (int i = c; i < n; i++)
                        h.r[i] = b.r[i]; /* para r cada hijo */
                }
                else {
                    /* El hijo recibe una copia de los arreglos del primer padre. */
                    h.perm = a.perm.clone(); h.r = a.r.clone();
                }

                /* MUTACION SWAP*/
                /* Esta parte se ejecuta después del cruce, por lo que un hijo puede experimentar ambos procesos. */
                if (rnd.nextDouble() < par.pMutacion && n > 1) {    // mutación SWAP
                    int i = rnd.nextInt(n), j = rnd.nextInt(n - 1);
                    if (j >= i) j++; // seleccionar dos indices distintos
                    int t = h.perm[i]; h.perm[i] = h.perm[j]; h.perm[j] = t; // intercambiar las partidas
                    /* No cambia r pero como esta asociado a posiciones del cromosoma, al cambiar perm también puede
                    cambiar qué número r utiliza cada partida y, por tanto, su máquina propuesta.*/
                }
                h.F = Modelo.evaluar(I, decodificar(I, h.perm, h.r)).F; /* evaluar un hijo*/
                R.anotar(h.F, h);
                nueva.add(h);
            }
            pob = nueva; /*Cuando termina el ciclo de descendientes, la nueva población reemplaza a la anterior.*/
        }
        Individuo best = (Individuo) R.mejorSol;
        Resultado res = new Resultado();
        res.sol = decodificar(I, best.perm, best.r);
        res.ev = Modelo.evaluar(I, res.sol);
        res.F = res.ev.F;
        res.historial = R.hist;
        res.evaluaciones = R.evals;
        res.tiempoMs = (System.nanoTime() - t0) / 1_000_000;
        /* obtener el mejor y construirlo*/
        return res;
    }

    static int[] permutacion(int n, Random rnd) {
        int[] p = new int[n];

        for (int i = 0; i < n; i++)
            p[i] = i; /* recorre todas las posiciones y les asgina su propia indice */
        for (int i = n - 1; i > 0; i--) {
            /* mezclar aleatoriamente el arreglo */
            int j = rnd.nextInt(i + 1);
            int t = p[i];
            p[i] = p[j];
            p[j] = t;
        }
        return p;
    }

    // =====================================================================
    // COLONIA DE HORMIGAS (Fidanova et al., 2025)
    // Nodo = par partida-máquina con feromona inicial tau0. Cada hormiga
    // construye la semana día a día: entre partidas empatadas elige un nodo
    // partida-máquina con P ~ tau^a (1/T)^b, T = carga relativa de la máquina
    // al agregarle la partida; al conformarse la partida elige su máquina con
    // la misma probabilidad. Al final de cada iteración tau <- rho*tau y la
    // mejor hormiga de la iteración deposita (1-rho)/(1+F) en sus nodos.
    // =====================================================================
    /** Valores de la calibración 2^3 (R/07_calibracion.R); rho = fracción de feromona que se conserva. */
    public static final class ParamACO { public int hormigas = 20; public double rho = 0.9, tau0 = 0.5, a = 1, b = 2; }

    public static Resultado coloniaHormigas(Instancia I, long semilla, int presupuesto, ParamACO par) {
        /* ACO se inspira en como las hormigas encuentran caminos utilizando
        * feromonas
        * Cada hormiga construye una propuesta de planificacion semanal, tomando decisiones sobre:
        * - que partidas seleccionar cuando varias conformables tienen la misma fecha de entrega
        * - a que maquina compatible asignarla */

        /* Feromonas (tau): representa que asociaciones partida-maquina han recibido refuerzo
        * de buenas soluciones anteriores
        *
        * Carga relativa (T): favorece las maquinas que tienen menor carga respecto a su objetivo */

        /* ParamACO
        * hormigas = 20 -> numero maximo de soluciones que se construyen por iteracion
        * rho = 0.9 -> proporcion de feromonas que se conservan despue de cada iteracion
        * tau0 = 0.5 -> cantidad incial de feromona de cada combinacion partida - maquina
        * a = 1 -> importancia de la feromona al calcular probabilidades
        * b = 2 -> importancia de la carga relativa al calcular probabilidades  */


        long t0 = System.nanoTime();
        Random rnd = new Random(semilla);
        Registro R = new Registro(presupuesto);

        double[][] tau = new double[I.P][I.M]; /* crear la matriz de feromonas
        porque hay una unica feromona por cada partida-maquina
        */

        for (double[] fila : tau)
            Arrays.fill(fila, par.tau0); /* lo llena con el inicial de tau0, si bien estan todas las posibilidades de
            partida con todas las maquinas, despues solo se evalua a las maquinas compataibles por partida */

        /*Crea la clase regla, con el tipo de maquina eligiendo la regla de ACO, el tau como tau
        * y asi sucesivamente */
        Modelo.Regla reg = new Modelo.Regla();
        reg.tipoMaquina = Modelo.Regla.HORMIGA;
        reg.tau = tau;
        reg.a = par.a;
        reg.b = par.b;
        reg.rnd = rnd;

        /* No hay prioridad porque el simulador utiliza las feromons y la carga para seleccionar una */

        while (R.evals < presupuesto) { /* mientras no se hayan realizado todas las evaluaciones permitidas */
            /* Dentro de cada iteracion trabajan hasta 20 hormigas */
            Modelo.Solucion mejorIt = null; /* mejor solucion durante la iteracion actual de las hormigas */
            double fIt = Double.POSITIVE_INFINITY; /* el mejor valor F encontrado durante esa iteracion */
            /* es el maximo porque se busca minimizar el F */
            for (int h = 0; h < par.hormigas && R.evals < presupuesto; h++) {
                /* Por cada hormiga que se esta ejecutando */
                Modelo.Solucion s = Modelo.simular(I, reg); // cada hormiga construye una planificacion completa,
                // no solo una sola asignacion
                /* las hormigas producen soluciones distintas porque utilizan
                * decisiones aleatorias */
                double F = Modelo.evaluar(I, s).F;
                R.anotar(F, s);
                if (F < fIt) {
                    fIt = F;
                    mejorIt = s;
                }
            }
            /* EVAPORAR LAS FEROMONAS */
            for (double[] fila : tau)
                for (int m = 0; m < I.M; m++)
                    fila[m] *= par.rho; /* cada feromona conserva el 90 % de su valor anterior. */
            /* AUMENTAR LAS FEROMONAS DE LA MEJOR PLANIFICAICON */
            for (int p = 0; p < I.P; p++) /* Recorro cada partida */
                if (mejorIt.maq[p] >= 0) /* de la mejor solucion, a que maquina fue asignada la partida P
                 es >= 0 porque -1 indica que la partida no recibio una maquina en esa simulacion */
                    tau[p][mejorIt.maq[p]] += (1 - par.rho) / (1 + fIt); // como se minimiza F, una planificaicon menor
                    // con menor F produce un deposito mayor de feromona
                    /* de esa partida y esa maquina, calcula cuanto se incrementara la feromona */
        }

        Resultado res = new Resultado();
        res.sol = (Modelo.Solucion) R.mejorSol;
        res.ev = Modelo.evaluar(I, res.sol);
        res.F = res.ev.F;
        res.historial = R.hist;
        res.evaluaciones = R.evals;
        res.tiempoMs = (System.nanoTime() - t0) / 1_000_000;
        return res;
    }
}
