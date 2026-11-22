package spats.algoritmo.nucleo;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import spats.algoritmo.entrada.AsignacionFija;
import spats.algoritmo.entrada.Componente;
import spats.algoritmo.entrada.ConformacionConservada;
import spats.algoritmo.entrada.DatosSemana;
import spats.algoritmo.entrada.DisponibilidadPartida;
import spats.algoritmo.entrada.EstadoPartida;
import spats.algoritmo.entrada.Indisponibilidad;
import spats.algoritmo.entrada.Maquina;
import spats.algoritmo.entrada.ParametrosPlanificacion;
import spats.algoritmo.entrada.Partida;
import spats.algoritmo.entrada.Pedido;
import spats.algoritmo.entrada.RegistroProyeccion;
import spats.algoritmo.entrada.Replanificacion;
import spats.algoritmo.entrada.StockArticulo;

/**
 * Construye la instancia de una semana de tintorería a partir de los datos del sistema.
 *
 * <ul>
 *   <li>Máquinas: días hábiles, objetivo efectivo (se descuenta por día indisponible) y
 *       mínimo y máximo diario en partidas completas.</li>
 *   <li>Tela: se acumula por artículo ("bolsa"), sin importar el pedido para el que se tejió.
 *       Cada bolsa parte con el stock en almacén y recibe cada día la tela que tejeduría deja
 *       disponible.</li>
 *   <li>Partidas: se consideran las pendientes. Son candidatas las que tienen una máquina de su
 *       barca y cuyos requerimientos caben en la tela total de la semana; el resto queda bloqueada.</li>
 *   <li>Re-planificación desde el día D ({@link Replanificacion}): las fijas y las conservadas entran a la
 *       instancia sin requerimientos de tela. El stock inicial es el saldo al iniciar el día D, más la tela
 *       de las conservadas que ya no tienen día hábil (vuelve a la bolsa). La tela de las fijas conformadas
 *       el día D sale de la bolsa ese día, después de sumar lo que llega. Desde D, cada partida solo va a
 *       máquinas de su barca con algún día hábil desde que puede ubicarse.</li>
 * </ul>
 */
public final class ConstructorInstancia {

    static final double EPS = 1e-9;

    private ConstructorInstancia() { }

    /** Clave con la que se acumula la tela: la tela de un artículo abastece partidas de cualquier pedido. */
    static String claveBolsa(String claveArticulo) { return claveArticulo.trim(); }

    public static InstanciaSemana construir(DatosSemana datos) {
        ParametrosPlanificacion par = datos.parametros();
        par.validar();
        validarDatos(datos);
        InstanciaSemana I = new InstanciaSemana();
        I.advertencias = new ArrayList<>();
        I.ND = par.getDiasSemana();
        LocalDate lunes = par.getFechaInicioSemana();
        I.dias = new LocalDate[I.ND];
        for (int d = 0; d < I.ND; d++) I.dias[d] = lunes.plusDays(d);
        Replanificacion rp = datos.replanificacion();
        if (rp != null) I.diaInicio = diaDeLaSemana(I, rp.diaInicio(), "El día de inicio de la re-planificación");

        construirMaquinas(I, datos, par);
        Map<String, Map<String, Double>> req = requerimientos(datos);
        Reparto rep = rp == null ? null : repartir(I, datos, rp, req);
        Map<String, Double> stock = new TreeMap<>();
        Map<String, double[]> llegadas = new TreeMap<>();
        construirTela(I, datos, lunes, stock, llegadas, rep);
        construirPartidas(I, datos, req, rep);
        return I;
    }

    // ---------------------------------------------------------------- máquinas
    private static void construirMaquinas(InstanciaSemana I, DatosSemana datos, ParametrosPlanificacion par) {
        List<Maquina> mq = datos.maquinas();
        I.M = mq.size();
        I.maquinas = mq.toArray(new Maquina[0]);
        Map<String, Integer> idx = new HashMap<>();
        for (int m = 0; m < I.M; m++) idx.put(I.maquinas[m].codigo(), m);
        I.habil = new boolean[I.M][I.ND];
        for (boolean[] fila : I.habil) java.util.Arrays.fill(fila, true);
        for (Indisponibilidad x : datos.indisponibilidades()) {
            Integer m = idx.get(x.maquina());
            if (m == null) { I.advertencias.add("Indisponibilidad de una máquina no registrada: " + x.maquina()); continue; }
            long d = ChronoUnit.DAYS.between(I.dias[0], x.fecha());
            if (d >= 0 && d < I.ND) I.habil[m][(int) d] = false;
        }
        I.objetivo = new int[I.M]; I.minimoDiario = new int[I.M]; I.maximoDiario = new int[I.M];
        I.diasHabiles = new int[I.M][];
        I.sigHabil = new int[I.M][I.ND + 1];
        for (int m = 0; m < I.M; m++) {
            List<Integer> h = new ArrayList<>();
            for (int d = 1; d <= I.ND; d++) if (I.habil[m][d - 1]) h.add(d);
            I.diasHabiles[m] = h.stream().mapToInt(Integer::intValue).toArray();
            int perdidos = I.ND - h.size();
            int o = I.maquinas[m].objetivoSemanal() - par.getReduccionObjetivoPorDiaIndisponible() * perdidos;
            I.objetivo[m] = h.isEmpty() ? 0 : Math.max(0, o);
            I.minimoDiario[m] = h.isEmpty() ? 0 : Math.floorDiv(I.objetivo[m], h.size());
            I.maximoDiario[m] = h.isEmpty() ? 0 : (I.objetivo[m] + h.size() - 1) / h.size();
            for (int a = 1; a <= I.ND; a++) {
                int s = 0;
                for (int d : I.diasHabiles[m]) if (d >= a) { s = d; break; }
                I.sigHabil[m][a] = s;
            }
        }
    }

    // -------------------------------------------------------------------- tela
    private static void construirTela(InstanciaSemana I, DatosSemana datos, LocalDate lunes,
                                      Map<String, Double> stock, Map<String, double[]> llegadas, Reparto rep) {
        for (StockArticulo s : datos.stockInicial())
            if (s.kg() > EPS) stock.merge(claveBolsa(s.claveArticulo()), s.kg(), Double::sum);
        if (rep != null) rep.devolucion.forEach((clave, kg) -> stock.merge(clave, kg, Double::sum));
        int anteriores = 0, posteriores = 0;
        for (RegistroProyeccion r : datos.proyeccion()) {
            long d = ChronoUnit.DAYS.between(lunes, r.fechaDisponible());
            if (d < I.diaInicio - 1) { anteriores++; continue; }
            if (d >= I.ND) { posteriores++; continue; }
            llegadas.computeIfAbsent(claveBolsa(r.claveArticulo()), k -> new double[I.ND])[(int) d] += r.kg();
        }
        if (anteriores > 0)
            I.advertencias.add(I.diaInicio == 1
                    ? anteriores + " registros de proyección quedan disponibles antes de la semana; "
                            + "se consideran parte del stock inicial y no se suman otra vez"
                    : anteriores + " registros de proyección quedaron disponibles antes del día de inicio; "
                            + "ya están en el saldo de ese día y no se suman otra vez");
        if (posteriores > 0)
            I.advertencias.add(posteriores + " registros de proyección quedan disponibles después de la semana");
        TreeSet<String> claves = new TreeSet<>(llegadas.keySet());
        claves.addAll(stock.keySet());
        if (rep != null) claves.addAll(rep.consumoInicio.keySet());
        I.B = claves.size();
        I.bolsas = claves.toArray(new String[0]);
        I.stockInicial = new double[I.B];
        I.llegadas = new double[I.B][];
        I.consumoFijasInicio = new double[I.B];
        for (int b = 0; b < I.B; b++) {
            I.stockInicial[b] = stock.getOrDefault(I.bolsas[b], 0.0);
            I.llegadas[b] = llegadas.getOrDefault(I.bolsas[b], new double[I.ND]);
            if (rep == null) continue;
            I.consumoFijasInicio[b] = rep.consumoInicio.getOrDefault(I.bolsas[b], 0.0);
            if (I.consumoFijasInicio[b] > 0) I.hayConsumoFijasInicio = true;
            if (I.stockInicial[b] + I.llegadas[b][I.diaInicio - 1] - I.consumoFijasInicio[b] < -1e-6)
                throw new IllegalArgumentException("La tela de las partidas fijas conformadas el día de inicio supera "
                        + "la tela disponible ese día en la bolsa " + I.bolsas[b]);
        }
    }

    // ---------------------------------------------------------------- partidas
    private static void construirPartidas(InstanciaSemana I, DatosSemana datos, Map<String, Map<String, Double>> req,
                                          Reparto rep) {
        Map<String, Integer> bolsaIdx = new HashMap<>();
        for (int b = 0; b < I.B; b++) bolsaIdx.put(I.bolsas[b], b);
        double[] total = new double[I.B];
        for (int b = 0; b < I.B; b++) {
            total[b] = I.stockInicial[b];
            for (double x : I.llegadas[b]) total[b] += x;
            if (rep != null) total[b] -= I.consumoFijasInicio[b];
        }
        Map<String, LocalDate> entrega = new HashMap<>();
        LocalDate maxEntrega = null;
        for (Pedido p : datos.pedidos()) {
            if (p.fechaEntrega() == null) continue;
            entrega.put(p.codigo(), p.fechaEntrega());
            if (maxEntrega == null || p.fechaEntrega().isAfter(maxEntrega)) maxEntrega = p.fechaEntrega();
        }
        if (maxEntrega == null) maxEntrega = I.dias[I.ND - 1];

        Set<String> barcas = new HashSet<>();
        for (Maquina m : I.maquinas) barcas.add(m.barca().trim());

        List<Partida> pendientes = new ArrayList<>();
        for (Partida p : datos.partidas()) if (p.estado() == EstadoPartida.PENDIENTE) pendientes.add(p);
        pendientes.sort(Comparator.comparingInt(Partida::ordenRegistro));

        List<Partida> candidatas = new ArrayList<>();
        List<Partida> conservadas = new ArrayList<>(), fijas = new ArrayList<>();
        I.excluidas = new ArrayList<>();
        int sinComponentes = 0;
        for (Partida p : pendientes) {
            if (rep != null && rep.fijas.containsKey(p.codigo())) { fijas.add(p); continue; }
            if (rep != null && rep.conservadas.containsKey(p.codigo())) {
                if (rep.devueltas.contains(p.codigo())) I.excluidas.add(new PartidaExcluida(p, PartidaExcluida.TELA_DEVUELTA));
                else conservadas.add(p);
                continue;
            }
            if (!barcas.contains(p.barca().trim())) { I.excluidas.add(new PartidaExcluida(p, PartidaExcluida.SIN_MAQUINA)); continue; }
            if (rep != null && !tieneDiaHabil(I, p.barca(), rep.desdeDe(p.codigo(), I.diaInicio))) {
                I.excluidas.add(new PartidaExcluida(p, PartidaExcluida.SIN_DIA_DISPONIBLE));
                continue;
            }
            Map<String, Double> rq = req.getOrDefault(p.codigo(), Map.of());
            if (rq.isEmpty()) sinComponentes++;
            boolean alcanza = true;
            for (Map.Entry<String, Double> e : rq.entrySet()) {
                Integer b = bolsaIdx.get(e.getKey());
                if (b == null || e.getValue() > total[b] + EPS) { alcanza = false; break; }
            }
            if (alcanza) candidatas.add(p);
            else I.excluidas.add(new PartidaExcluida(p, PartidaExcluida.SIN_TELA));
        }
        if (sinComponentes > 0) I.advertencias.add(sinComponentes + " partidas pendientes no tienen componentes registrados");

        int nDecidibles = candidatas.size();
        I.nConservadas = conservadas.size();
        I.nFijas = fijas.size();
        List<Partida> todas = new ArrayList<>(candidatas);   // las que se deciden van primero, en su orden de registro
        todas.addAll(conservadas);
        todas.addAll(fijas);
        I.P = todas.size();
        I.partidas = todas.toArray(new Partida[0]);
        I.fechaEntrega = new long[I.P];
        I.tono = new int[I.P];
        I.compat = new int[I.P][];
        I.fija = new boolean[I.P]; I.conservada = new boolean[I.P];
        I.fijaMaquina = new int[I.P]; I.fijaDia = new int[I.P]; I.fijaSobrante = new boolean[I.P]; I.fijaOrden = new int[I.P];
        I.diaConformacionPrevia = new int[I.P];
        I.desde = new int[I.P];
        java.util.Arrays.fill(I.desde, 1);
        java.util.Arrays.fill(I.fijaMaquina, -1);
        for (int p = 0; p < I.P; p++) {
            Partida x = I.partidas[p];
            I.fechaEntrega[p] = entrega.getOrDefault(x.pedido(), maxEntrega).toEpochDay();
            I.tono[p] = x.tonalidad().rango();
            if (p >= nDecidibles + I.nConservadas) {                                   // fija
                AsignacionFija f = rep.fijas.get(x.codigo());
                I.fija[p] = true;
                I.fijaMaquina[p] = rep.indiceMaquina.get(f.maquina());
                I.fijaDia[p] = diaDeLaSemana(I, f.fecha(), "El día de la partida fija " + x.codigo());
                I.fijaSobrante[p] = f.sobrante();
                I.diaConformacionPrevia[p] = diaDeLaSemana(I, f.fechaDisponible(), "La conformación de la partida fija " + x.codigo());
                I.desde[p] = I.fijaDia[p];
                I.compat[p] = new int[] { I.fijaMaquina[p] };
                continue;
            }
            if (rep != null) I.desde[p] = rep.desdeDe(x.codigo(), I.diaInicio);
            if (p >= nDecidibles) {                                                    // conservada
                I.conservada[p] = true;
                I.diaConformacionPrevia[p] = diaDeLaSemana(I, rep.conservadas.get(x.codigo()).fechaConformacion(),
                        "La conformación de la partida conservada " + x.codigo());
            }
            List<Integer> cm = new ArrayList<>();
            for (int m = 0; m < I.M; m++)
                if (I.maquinas[m].barca().trim().equals(x.barca().trim())
                        && (rep == null || (I.desde[p] <= I.ND && I.sigHabil[m][I.desde[p]] > 0))) cm.add(m);
            I.compat[p] = cm.stream().mapToInt(Integer::intValue).toArray();
            if (p < nDecidibles) I.R += req.getOrDefault(x.codigo(), Map.of()).size();
        }
        ordenarFijas(I, rep);
        I.reqPartida = new int[I.R]; I.reqBolsa = new int[I.R]; I.reqKg = new double[I.R];
        List<List<Integer>> porPartida = new ArrayList<>();
        for (int p = 0; p < I.P; p++) porPartida.add(new ArrayList<>());
        int k = 0;
        for (int p = 0; p < nDecidibles; p++)
            for (Map.Entry<String, Double> e : req.getOrDefault(I.partidas[p].codigo(), Map.of()).entrySet()) {
                I.reqPartida[k] = p; I.reqBolsa[k] = bolsaIdx.get(e.getKey()); I.reqKg[k] = e.getValue();
                porPartida.get(p).add(k); k++;
            }
        I.reqsDePartida = new int[I.P][];
        for (int p = 0; p < I.P; p++) I.reqsDePartida[p] = porPartida.get(p).stream().mapToInt(Integer::intValue).toArray();
    }

    /** Requerimiento de cada partida por bolsa (dos componentes del mismo artículo se suman). */
    private static Map<String, Map<String, Double>> requerimientos(DatosSemana datos) {
        Map<String, Map<String, Double>> req = new HashMap<>();
        for (Componente c : datos.componentes()) {
            if (c.kgRequeridos() <= EPS) continue;
            req.computeIfAbsent(c.partida(), k -> new LinkedHashMap<>())
               .merge(claveBolsa(c.claveArticulo()), c.kgRequeridos(), Double::sum);
        }
        return req;
    }

    // -------------------------------------------------------- re-planificación
    /** Cómo se reparten las partidas de una re-planificación y cómo cambia la tela del día D. */
    private static final class Reparto {
        final Map<String, AsignacionFija> fijas = new HashMap<>();
        final Map<String, ConformacionConservada> conservadas = new HashMap<>();
        final Set<String> devueltas = new HashSet<>();
        final Map<String, Integer> desde = new HashMap<>();          // primer día (base 1; puede pasar del domingo)
        final Map<String, Double> devolucion = new TreeMap<>();      // kg que vuelven a la bolsa al iniciar el día D
        final Map<String, Double> consumoInicio = new TreeMap<>();   // kg de las fijas conformadas el día D
        final Map<String, Integer> ordenSecuencia = new HashMap<>();
        final Map<String, Integer> indiceMaquina = new HashMap<>();

        int desdeDe(String partida, int diaInicio) { return Math.max(diaInicio, desde.getOrDefault(partida, diaInicio)); }
    }

    private static Reparto repartir(InstanciaSemana I, DatosSemana datos, Replanificacion rp,
                                    Map<String, Map<String, Double>> req) {
        Reparto r = new Reparto();
        for (int m = 0; m < I.M; m++) r.indiceMaquina.put(I.maquinas[m].codigo(), m);
        Map<String, Partida> pendientes = new HashMap<>();
        for (Partida p : datos.partidas()) if (p.estado() == EstadoPartida.PENDIENTE) pendientes.put(p.codigo(), p);
        Set<String> vistas = new HashSet<>();
        for (AsignacionFija f : rp.fijas()) {
            exigirPendienteUnaVez(f.partida(), pendientes, vistas);
            if (!r.indiceMaquina.containsKey(f.maquina()))
                throw new IllegalArgumentException("La partida fija " + f.partida() + " está en una máquina no registrada: " + f.maquina());
            int dia = diaDeLaSemana(I, f.fecha(), "El día de la partida fija " + f.partida());
            int conf = diaDeLaSemana(I, f.fechaDisponible(), "La conformación de la partida fija " + f.partida());
            if (dia > I.diaInicio || conf > dia)
                throw new IllegalArgumentException("La partida fija " + f.partida() + " debe estar ubicada hasta el día de inicio "
                        + "y después de su conformación");
            r.fijas.put(f.partida(), f);
            r.ordenSecuencia.put(f.partida(), f.ordenSecuencia());
            if (conf >= I.diaInicio)                         // ya en máquina el día D: su tela sale de la bolsa ese día
                req.getOrDefault(f.partida(), Map.of()).forEach((clave, kg) -> r.consumoInicio.merge(clave, kg, Double::sum));
        }
        for (DisponibilidadPartida d : rp.disponibilidades())
            r.desde.merge(d.partida(), (int) ChronoUnit.DAYS.between(I.dias[0], d.disponibleDesde()) + 1, Math::max);
        for (ConformacionConservada c : rp.conservadas()) {
            Partida p = exigirPendienteUnaVez(c.partida(), pendientes, vistas);
            int conf = diaDeLaSemana(I, c.fechaConformacion(), "La conformación de la partida conservada " + c.partida());
            if (conf >= I.diaInicio)
                throw new IllegalArgumentException("La partida conservada " + c.partida() + " debe haberse conformado antes del día de inicio");
            r.conservadas.put(c.partida(), c);
            int desde = Math.max(I.diaInicio, (int) ChronoUnit.DAYS.between(I.dias[0], c.ubicableDesde()) + 1);
            r.desde.merge(c.partida(), desde, Math::max);
            if (!tieneDiaHabil(I, p.barca(), r.desdeDe(c.partida(), I.diaInicio))) {   // su tela vuelve a la bolsa el día D
                r.devueltas.add(c.partida());
                req.getOrDefault(c.partida(), Map.of()).forEach((clave, kg) -> r.devolucion.merge(clave, kg, Double::sum));
            }
        }
        return r;
    }

    private static Partida exigirPendienteUnaVez(String codigo, Map<String, Partida> pendientes, Set<String> vistas) {
        Partida p = pendientes.get(codigo);
        if (p == null) throw new IllegalArgumentException("La partida " + codigo + " de la re-planificación no está pendiente");
        if (!vistas.add(codigo)) throw new IllegalArgumentException("La partida " + codigo + " está repetida en la re-planificación");
        return p;
    }

    /** true si alguna máquina de la barca tiene un día hábil desde el día {@code desde} (base 1). */
    private static boolean tieneDiaHabil(InstanciaSemana I, String barca, int desde) {
        if (desde > I.ND) return false;
        for (int m = 0; m < I.M; m++)
            if (I.maquinas[m].barca().trim().equals(barca.trim()) && I.sigHabil[m][Math.max(1, desde)] > 0) return true;
        return false;
    }

    /** Orden de las fijas (1..nFijas) por día, clasificación (Asignadas primero) y orden_secuencia. */
    private static void ordenarFijas(InstanciaSemana I, Reparto rep) {
        if (I.nFijas == 0) return;
        List<Integer> f = new ArrayList<>();
        for (int p = 0; p < I.P; p++) if (I.fija[p]) f.add(p);
        f.sort(Comparator.<Integer>comparingInt(p -> I.fijaDia[p]).thenComparing(p -> I.fijaSobrante[p])
                .thenComparingInt(p -> rep.ordenSecuencia.get(I.partidas[p].codigo()))
                .thenComparingInt(p -> I.fijaMaquina[p]));
        for (int i = 0; i < f.size(); i++) I.fijaOrden[f.get(i)] = i + 1;
    }

    /** Día de la semana (base 1) de una fecha; debe estar dentro de la semana. */
    private static int diaDeLaSemana(InstanciaSemana I, LocalDate fecha, String que) {
        long d = fecha == null ? -1 : ChronoUnit.DAYS.between(I.dias[0], fecha);
        if (d < 0 || d >= I.ND) throw new IllegalArgumentException(que + " está fuera de la semana: " + fecha);
        return (int) d + 1;
    }

    // -------------------------------------------------------------- validación
    private static void validarDatos(DatosSemana d) {
        if (d.maquinas().isEmpty()) throw new IllegalArgumentException("No hay máquinas registradas");
        Set<String> vistos = new HashSet<>();
        for (Maquina m : d.maquinas()) {
            if (!vistos.add(m.codigo())) throw new IllegalArgumentException("Máquina repetida: " + m.codigo());
            if (m.objetivoSemanal() < 0) throw new IllegalArgumentException("Objetivo semanal negativo en " + m.codigo());
            if (m.barca() == null || m.barca().isBlank()) throw new IllegalArgumentException("Máquina sin barca: " + m.codigo());
        }
        vistos.clear();
        for (Partida p : d.partidas()) {
            if (!vistos.add(p.codigo())) throw new IllegalArgumentException("Partida repetida: " + p.codigo());
            if (p.barca() == null || p.barca().isBlank()) throw new IllegalArgumentException("Partida sin barca: " + p.codigo());
            if (p.tonalidad() == null) throw new IllegalArgumentException("Partida sin tonalidad: " + p.codigo());
        }
        for (Componente c : d.componentes())
            if (c.kgRequeridos() < 0) throw new IllegalArgumentException("Componente con kg negativos en la partida " + c.partida());
        for (RegistroProyeccion r : d.proyeccion()) {
            if (r.fechaTejido() == null) throw new IllegalArgumentException("Registro de proyección sin fecha de tejido");
            if (r.kg() < 0) throw new IllegalArgumentException("Registro de proyección con kg negativos");
        }
        for (StockArticulo s : d.stockInicial())
            if (s.kg() < 0) throw new IllegalArgumentException("Stock negativo del artículo " + s.claveArticulo());
    }
}
