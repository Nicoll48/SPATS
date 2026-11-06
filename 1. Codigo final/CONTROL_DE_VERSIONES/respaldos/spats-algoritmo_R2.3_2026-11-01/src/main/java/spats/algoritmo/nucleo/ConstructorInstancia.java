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

import spats.algoritmo.entrada.Componente;
import spats.algoritmo.entrada.DatosSemana;
import spats.algoritmo.entrada.EstadoPartida;
import spats.algoritmo.entrada.Indisponibilidad;
import spats.algoritmo.entrada.Maquina;
import spats.algoritmo.entrada.ParametrosPlanificacion;
import spats.algoritmo.entrada.Partida;
import spats.algoritmo.entrada.Pedido;
import spats.algoritmo.entrada.RegistroProyeccion;
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

        construirMaquinas(I, datos, par);
        Map<String, Double> stock = new TreeMap<>();
        Map<String, double[]> llegadas = new TreeMap<>();
        construirTela(I, datos, lunes, stock, llegadas);
        construirPartidas(I, datos);
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
                                      Map<String, Double> stock, Map<String, double[]> llegadas) {
        for (StockArticulo s : datos.stockInicial())
            if (s.kg() > EPS) stock.merge(claveBolsa(s.claveArticulo()), s.kg(), Double::sum);
        int anteriores = 0, posteriores = 0;
        for (RegistroProyeccion r : datos.proyeccion()) {
            long d = ChronoUnit.DAYS.between(lunes, r.fechaDisponible());
            if (d < 0) { anteriores++; continue; }
            if (d >= I.ND) { posteriores++; continue; }
            llegadas.computeIfAbsent(claveBolsa(r.claveArticulo()), k -> new double[I.ND])[(int) d] += r.kg();
        }
        if (anteriores > 0)
            I.advertencias.add(anteriores + " registros de proyección quedan disponibles antes de la semana; "
                    + "se consideran parte del stock inicial y no se suman otra vez");
        if (posteriores > 0)
            I.advertencias.add(posteriores + " registros de proyección quedan disponibles después de la semana");
        TreeSet<String> claves = new TreeSet<>(llegadas.keySet());
        claves.addAll(stock.keySet());
        I.B = claves.size();
        I.bolsas = claves.toArray(new String[0]);
        I.stockInicial = new double[I.B];
        I.llegadas = new double[I.B][];
        for (int b = 0; b < I.B; b++) {
            I.stockInicial[b] = stock.getOrDefault(I.bolsas[b], 0.0);
            I.llegadas[b] = llegadas.getOrDefault(I.bolsas[b], new double[I.ND]);
        }
    }

    // ---------------------------------------------------------------- partidas
    private static void construirPartidas(InstanciaSemana I, DatosSemana datos) {
        Map<String, Integer> bolsaIdx = new HashMap<>();
        for (int b = 0; b < I.B; b++) bolsaIdx.put(I.bolsas[b], b);
        double[] total = new double[I.B];
        for (int b = 0; b < I.B; b++) {
            total[b] = I.stockInicial[b];
            for (double x : I.llegadas[b]) total[b] += x;
        }
        Map<String, LocalDate> entrega = new HashMap<>();
        LocalDate maxEntrega = null;
        for (Pedido p : datos.pedidos()) {
            if (p.fechaEntrega() == null) continue;
            entrega.put(p.codigo(), p.fechaEntrega());
            if (maxEntrega == null || p.fechaEntrega().isAfter(maxEntrega)) maxEntrega = p.fechaEntrega();
        }
        if (maxEntrega == null) maxEntrega = I.dias[I.ND - 1];

        // requerimiento de cada partida por bolsa (dos componentes del mismo artículo se suman)
        Map<String, Map<String, Double>> req = new HashMap<>();
        for (Componente c : datos.componentes()) {
            if (c.kgRequeridos() <= EPS) continue;
            req.computeIfAbsent(c.partida(), k -> new LinkedHashMap<>())
               .merge(claveBolsa(c.claveArticulo()), c.kgRequeridos(), Double::sum);
        }
        Set<String> barcas = new HashSet<>();
        for (Maquina m : I.maquinas) barcas.add(m.barca().trim());

        List<Partida> pendientes = new ArrayList<>();
        for (Partida p : datos.partidas()) if (p.estado() == EstadoPartida.PENDIENTE) pendientes.add(p);
        pendientes.sort(Comparator.comparingInt(Partida::ordenRegistro));

        List<Partida> candidatas = new ArrayList<>();
        I.excluidas = new ArrayList<>();
        int sinComponentes = 0;
        for (Partida p : pendientes) {
            if (!barcas.contains(p.barca().trim())) { I.excluidas.add(new PartidaExcluida(p, PartidaExcluida.SIN_MAQUINA)); continue; }
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

        I.P = candidatas.size();
        I.partidas = candidatas.toArray(new Partida[0]);
        I.fechaEntrega = new long[I.P];
        I.tono = new int[I.P];
        I.compat = new int[I.P][];
        for (int p = 0; p < I.P; p++) {
            Partida x = I.partidas[p];
            I.fechaEntrega[p] = entrega.getOrDefault(x.pedido(), maxEntrega).toEpochDay();
            I.tono[p] = x.tonalidad().rango();
            List<Integer> cm = new ArrayList<>();
            for (int m = 0; m < I.M; m++) if (I.maquinas[m].barca().trim().equals(x.barca().trim())) cm.add(m);
            I.compat[p] = cm.stream().mapToInt(Integer::intValue).toArray();
            I.R += req.getOrDefault(x.codigo(), Map.of()).size();
        }
        I.reqPartida = new int[I.R]; I.reqBolsa = new int[I.R]; I.reqKg = new double[I.R];
        List<List<Integer>> porPartida = new ArrayList<>();
        for (int p = 0; p < I.P; p++) porPartida.add(new ArrayList<>());
        int k = 0;
        for (int p = 0; p < I.P; p++)
            for (Map.Entry<String, Double> e : req.getOrDefault(I.partidas[p].codigo(), Map.of()).entrySet()) {
                I.reqPartida[k] = p; I.reqBolsa[k] = bolsaIdx.get(e.getKey()); I.reqKg[k] = e.getValue();
                porPartida.get(p).add(k); k++;
            }
        I.reqsDePartida = new int[I.P][];
        for (int p = 0; p < I.P; p++) I.reqsDePartida[p] = porPartida.get(p).stream().mapToInt(Integer::intValue).toArray();
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
