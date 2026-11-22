package pe.edu.pucp.spats.planificacion.recomendacion;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pe.edu.pucp.spats.maquina.service.CompatibilidadMaquinas;
import pe.edu.pucp.spats.partida.entity.TonalidadColor;

/**
 * Recomendaciones de tejido adicional de una versión de planificación (RF30, HU-13, D-56). Dicen qué tejer DE MÁS en la
 * misma semana para llenar el espacio libre del calendario y acercar cada máquina a su objetivo. Es cálculo puro sobre
 * el resultado ya guardado: no usa la base ni el algoritmo.
 *
 * <ol>
 *   <li>Espacio libre por día hábil = máximo diario − partidas del día (el «2/3» del calendario). Déficit = max(0,
 *       objetivo − Asignadas). Por máquina se recomiendan a lo más min(déficit, espacio libre de la semana).</li>
 *   <li>Candidatas: partidas pendientes no ubicadas en la versión, compatibles con la máquina (RN-06).</li>
 *   <li>Orden: entrega más próxima; si empatan, menos kg por tejer.</li>
 *   <li>Reparto: primero la máquina con mayor déficit. En ella, el día sigue la misma regla de llenado que el
 *       calendario (D-48, RN-13): con la meta = partidas del calendario + las recomendadas, cada día debería llevar
 *       ⌊meta·j/H + desfase⌋ − lo anterior (tope: máximo diario), con el mismo desfase que rota entre las máquinas y las
 *       semanas. Se elige el día al que más le falta para su cuota; si empatan, el más temprano. Así la vista previa no
 *       carga los primeros días (3, 3, 2, 2…) y se parece al reparto que hará el algoritmo. Cada partida, una sola vez.</li>
 *   <li>Kg por componente = max(0, kg requeridos − tela que la versión dejó sin usar y ya estaba disponible ese día),
 *       descontada una sola vez en el orden del reparto. La partida que ya tiene toda su tela no se recomienda.</li>
 *   <li>Día de tejido: el más tardío, entre el inicio y el fin de la ventana de tejido y antes del día que se llena,
 *       cuya tela llega a tiempo (lunes a viernes → al día siguiente; sábado → lunes). No se teje en domingo.</li>
 * </ol>
 */
public final class CalculoRecomendaciones {

    private CalculoRecomendaciones() { }

    public enum TipoComponente { PRINCIPAL, COMPLEMENTO }

    /** Día hábil de una máquina con su espacio libre y las partidas regulares que ya tiene. */
    public record Dia(LocalDate fecha, int libre, int regulares) {
        public Dia(LocalDate fecha, int libre) { this(fecha, libre, 0); }
    }

    /** @param posicion posición de la máquina en el parque (la m del desfase de la D-48) */
    public record Maquina(Integer id, String codigo, BigDecimal capacidad, Set<TonalidadColor> aceptadas, int objetivo,
                          int asignadas, List<Dia> dias, int posicion) {
        public Maquina(Integer id, String codigo, BigDecimal capacidad, Set<TonalidadColor> aceptadas, int objetivo,
                       int asignadas, List<Dia> dias) {
            this(id, codigo, capacidad, aceptadas, objetivo, asignadas, dias, 0);
        }
        public int deficit() { return Math.max(0, objetivo - asignadas); }
        public int libre() { return dias.stream().mapToInt(Dia::libre).sum(); }
        public int maximo() { return Math.min(deficit(), libre()); }
    }

    public record Componente(Integer idDetallePedido, String numeroOp, Integer idArticulo, String codigoArticulo,
                             TipoComponente tipo, double kgRequeridos) { }

    public record Candidata(Integer idPartida, String numero, String barca, TonalidadColor tonalidad, LocalDate fechaEntrega,
                            List<Componente> componentes) { }

    /**
     * @param sinUsar        tela que la versión dejó sin usar y ya estaba disponible cada día: id de artículo → día → kg
     * @param inicioTejido   primer día de tejido posible (el sábado anterior a la semana)
     * @param finTejido      último día de tejido posible (el viernes de la semana)
     * @param numeroMaquinas M del desfase de la D-48
     * @param semanaIso      semana ISO del lunes, para rotar el desfase como el algoritmo
     */
    public record Entrada(List<Maquina> maquinas, List<Candidata> candidatas, Map<Integer, Map<LocalDate, Double>> sinUsar,
                          LocalDate inicioTejido, LocalDate finTejido, int numeroMaquinas, int semanaIso) {
        public Entrada(List<Maquina> maquinas, List<Candidata> candidatas, Map<Integer, Map<LocalDate, Double>> sinUsar,
                       LocalDate inicioTejido, LocalDate finTejido) {
            this(maquinas, candidatas, sinUsar, inicioTejido, finTejido, 1, 0);
        }
    }

    /** Una fila por componente que hay que tejer. */
    public record Fila(Integer idMaquina, String maquina, LocalDate dia, Integer idPartida, String partida,
                       Integer idDetallePedido, String numeroOp, Integer idArticulo, String articulo, TipoComponente tipo,
                       double kg, LocalDate diaTejido, LocalDate fechaEntrega, int orden) { }

    private static final double EPS = 1e-6;

    public static List<Fila> calcular(Entrada e) {
        List<Fila> filas = new ArrayList<>();
        Map<Integer, Double> usado = new HashMap<>();
        LocalDate ultimoDia = e.maquinas().stream().flatMap(m -> m.dias().stream()).map(Dia::fecha)
                .max(LocalDate::compareTo).orElse(e.finTejido());

        List<Candidata> orden = new ArrayList<>(e.candidatas());
        orden.sort(Comparator.comparing(Candidata::fechaEntrega, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingDouble(c -> kgPorTejer(c, e.sinUsar(), ultimoDia))
                .thenComparing(Candidata::numero));

        List<Maquina> maquinas = e.maquinas().stream().filter(m -> m.maximo() > 0)
                .sorted(Comparator.comparingInt(Maquina::deficit).reversed().thenComparing(Maquina::codigo)).toList();
        Set<String> tomadas = new HashSet<>();
        int n = 0;
        for (Maquina m : maquinas) {
            Map<LocalDate, Integer> libre = new LinkedHashMap<>();
            m.dias().forEach(d -> { if (d.libre() > 0 && diaTejido(d.fecha(), e.inicioTejido(), e.finTejido()) != null) libre.put(d.fecha(), d.libre()); });
            Map<LocalDate, Integer> cuota = cuotaPorDia(m, e.numeroMaquinas(), e.semanaIso());
            Map<LocalDate, Integer> lleva = new HashMap<>();
            m.dias().forEach(d -> lleva.put(d.fecha(), d.regulares()));
            int cubiertas = 0;
            while (cubiertas < m.maximo()) {
                LocalDate dia = libre.entrySet().stream().filter(x -> x.getValue() > 0).map(Map.Entry::getKey)
                        .max(Comparator.<LocalDate>comparingInt(f -> cuota.getOrDefault(f, 0) - lleva.get(f))
                                .thenComparing(Comparator.reverseOrder()))
                        .orElse(null);
                if (dia == null) break;
                LocalDate tejido = diaTejido(dia, e.inicioTejido(), e.finTejido());
                Candidata elegida = null;
                Map<Componente, Double> porTejer = null;
                for (Candidata c : orden) {
                    if (tomadas.contains(c.numero())
                            || !CompatibilidadMaquinas.compatible(m.capacidad(), m.aceptadas(), c.barca(), c.tonalidad())) continue;
                    Map<Componente, Double> falta = new LinkedHashMap<>();
                    for (Componente k : c.componentes()) {
                        double disponible = Math.max(0, sinUsar(e.sinUsar(), k.idArticulo(), dia) - usado.getOrDefault(k.idArticulo(), 0.0));
                        double kg = Math.max(0, k.kgRequeridos() - disponible);
                        if (kg > EPS) falta.put(k, kg);
                    }
                    if (falta.isEmpty()) {   // ya tiene toda su tela: no se recomienda, pero esa tela queda para ella
                        tomadas.add(c.numero());
                        consumir(c, dia, e.sinUsar(), usado);
                        continue;
                    }
                    elegida = c;
                    porTejer = falta;
                    break;
                }
                if (elegida == null) break;   // no hay más candidatas compatibles
                tomadas.add(elegida.numero());
                consumir(elegida, dia, e.sinUsar(), usado);
                for (Map.Entry<Componente, Double> f : porTejer.entrySet()) {
                    Componente k = f.getKey();
                    filas.add(new Fila(m.id(), m.codigo(), dia, elegida.idPartida(), elegida.numero(), k.idDetallePedido(),
                            k.numeroOp(), k.idArticulo(), k.codigoArticulo(), k.tipo(), redondear(f.getValue()), tejido,
                            elegida.fechaEntrega(), ++n));
                }
                libre.merge(dia, -1, Integer::sum);
                lleva.merge(dia, 1, Integer::sum);
                cubiertas++;
            }
        }
        return filas;
    }

    /**
     * Partidas que debería llevar cada día hábil de la máquina con sus recomendaciones, con la regla de llenado del
     * calendario (D-48, {@code nucleo.ConstructorCalendario}): meta = regulares + las que se recomiendan; al cerrar el
     * día j de H, ⌊meta·j/H + desfase⌋ (el último día, la meta); cuota = eso − lo anterior, con tope en el máximo
     * diario; desfase = ((posición + semana ISO) mod M + 0.5) / M.
     */
    static Map<LocalDate, Integer> cuotaPorDia(Maquina m, int numeroMaquinas, int semanaIso) {
        List<Dia> dias = m.dias();
        int H = dias.size();
        int maximoDiario = dias.stream().mapToInt(d -> d.libre() + d.regulares()).max().orElse(0);
        int meta = dias.stream().mapToInt(Dia::regulares).sum() + m.maximo();
        int M = Math.max(1, numeroMaquinas);
        double desfase = (((m.posicion() + semanaIso) % M) + 0.5) / M;
        Map<LocalDate, Integer> cuota = new HashMap<>();
        int ubicadas = 0;
        for (int j = 1; j <= H; j++) {
            int hastaHoy = j == H ? meta : (int) Math.floor((double) meta * j / H + desfase);
            int c = Math.max(0, Math.min(maximoDiario, hastaHoy - ubicadas));
            cuota.put(dias.get(j - 1).fecha(), c);
            ubicadas += c;
        }
        return cuota;
    }

    /**
     * Día de tejido más tardío cuya tela llega a tiempo para el día que se llena: entre el inicio de la ventana y el día
     * anterior (sin pasar el fin de la ventana). Lunes a viernes → disponible al día siguiente; sábado → el lunes. No se
     * teje en domingo. Null si ningún día llega a tiempo.
     */
    public static LocalDate diaTejido(LocalDate dia, LocalDate inicio, LocalDate fin) {
        LocalDate w = dia.minusDays(1).isAfter(fin) ? fin : dia.minusDays(1);
        for (; !w.isBefore(inicio); w = w.minusDays(1)) {
            if (w.getDayOfWeek() == DayOfWeek.SUNDAY) continue;
            if (!disponible(w).isAfter(dia)) return w;
        }
        return null;
    }

    static LocalDate disponible(LocalDate tejido) {
        return tejido.getDayOfWeek() == DayOfWeek.SATURDAY ? tejido.plusDays(2) : tejido.plusDays(1);
    }

    /** Descuenta, una sola vez, la tela sin usar que la partida toma de cada bolsa. */
    private static void consumir(Candidata c, LocalDate dia, Map<Integer, Map<LocalDate, Double>> sinUsar, Map<Integer, Double> usado) {
        for (Componente k : c.componentes()) {
            double disponible = Math.max(0, sinUsar(sinUsar, k.idArticulo(), dia) - usado.getOrDefault(k.idArticulo(), 0.0));
            usado.merge(k.idArticulo(), Math.min(disponible, k.kgRequeridos()), Double::sum);
        }
    }

    static double sinUsar(Map<Integer, Map<LocalDate, Double>> sinUsar, Integer articulo, LocalDate dia) {
        return sinUsar.getOrDefault(articulo, Map.of()).getOrDefault(dia, 0.0);
    }

    /** Kg por tejer de una partida con la tela sin usar al final de la semana (para desempatar el orden). */
    static double kgPorTejer(Candidata c, Map<Integer, Map<LocalDate, Double>> sinUsar, LocalDate dia) {
        return c.componentes().stream().mapToDouble(k -> Math.max(0, k.kgRequeridos() - sinUsar(sinUsar, k.idArticulo(), dia))).sum();
    }

    private static double redondear(double v) { return Math.round(v * 1000) / 1000.0; }
}
