package pe.edu.pucp.spats.planificacion.motor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

import pe.edu.pucp.spats.comun.error.FalloPlanificacionException;
import pe.edu.pucp.spats.planificacion.entity.Enumeraciones.ClasificacionResultado;
import pe.edu.pucp.spats.planificacion.motor.ResultadoTraducido.FilaBolsa;
import pe.edu.pucp.spats.planificacion.motor.ResultadoTraducido.FilaMaquina;
import pe.edu.pucp.spats.planificacion.motor.ResultadoTraducido.FilaPartida;
import spats.algoritmo.entrada.Componente;
import spats.algoritmo.entrada.DatosSemana;
import spats.algoritmo.entrada.Partida;
import spats.algoritmo.entrada.RegistroProyeccion;
import spats.algoritmo.entrada.StockArticulo;
import spats.algoritmo.salida.AsignacionPartida;
import spats.algoritmo.salida.EstadoAsignacion;
import spats.algoritmo.salida.PartidaBloqueada;
import spats.algoritmo.salida.ResultadoPlanificacion;
import spats.algoritmo.salida.ResumenMaquina;
import spats.algoritmo.nucleo.PartidaExcluida;

/**
 * Traduce la salida del algoritmo a las filas de la base (sección 9.3 del contexto). No decide nada que
 * le corresponda al algoritmo: solo copia su resultado y reconstruye el saldo diario de las bolsas a
 * partir de los mismos datos que recibió el algoritmo (decisión D-02). Es Java puro, sin Spring ni JPA.
 *
 * <p>Reglas que aplica:</p>
 * <ul>
 *   <li>Una solución con incumplimientos del validador no es factible: es un fallo (RN-19, sección 9.3).</li>
 *   <li>{@code orden_secuencia}: posición dentro de la máquina y el día, primero las Asignadas y luego las
 *       Sobrantes, cada grupo por orden de conformación (RN-15); es el mismo orden con que el algoritmo
 *       cuenta los lavados.</li>
 *   <li>BLOQUEADA (sección 6.2): las no conformadas por la prioridad de otras ({@code TELA_PRIORIZADA}) y
 *       las {@code SIN_TELA} con algún artículo que tuvo kilos en la semana. Las {@code SIN_TELA} sin
 *       ningún kilo no se registran (siguen PENDIENTE). Las {@code SIN_MAQUINA} no se registran y se
 *       informan como advertencia (D-12). En una re-planificación también quedan BLOQUEADA las que ya no
 *       tienen día hábil en la semana ({@code SIN_DIA_DISPONIBLE}, {@code TELA_DEVUELTA}).</li>
 *   <li>Bolsa: saldo inicial = stock inicial (lunes) o saldo final del día anterior; ingresos = proyección
 *       que queda disponible ese día según {@link RegistroProyeccion#fechaDisponible(LocalDate)} (la regla
 *       del algoritmo, no una copia); consumo = componentes de las partidas conformadas ese día. El saldo
 *       del domingo debe coincidir con {@code EstadoSiguiente.stockFinal}; si no, es un fallo.</li>
 * </ul>
 */
public final class TraductorResultado {

    static final int ESCALA_KG = 3;
    static final BigDecimal TOLERANCIA_KG = new BigDecimal("0.001");

    private TraductorResultado() { }

    public static ResultadoTraducido traducir(DatosSemana datos, ResultadoPlanificacion r) {
        if (!r.indicadores().factible())
            throw new FalloPlanificacionException(
                    "El algoritmo devolvió una asignación que no cumple todas las reglas; no se guardó ninguna versión.",
                    r.indicadores().incumplimientos());

        Map<String, Map<String, BigDecimal>> requerimientos = requerimientosPorPartida(datos.componentes());
        Map<String, BigDecimal> stock = stockInicial(datos.stockInicial());
        LocalDate[] dias = r.dias();
        Map<String, BigDecimal[]> ingresos = ingresosPorDia(datos.proyeccion(), dias);
        List<String> advertencias = new ArrayList<>(r.advertencias());

        List<FilaPartida> partidas = filasPartida(r.asignaciones());
        List<String> bloqueadas = bloqueadas(r.bloqueadas(), datos.partidas(), requerimientos, stock, ingresos, advertencias);
        List<FilaMaquina> maquinas = filasMaquina(r.maquinas(), r.asignaciones());
        List<FilaBolsa> bolsas = filasBolsa(dias, stock, ingresos, consumoPorDia(r.asignaciones(), requerimientos, dias), r);
        return new ResultadoTraducido(partidas, bloqueadas, maquinas, bolsas, r.indicadores(), List.copyOf(advertencias));
    }

    /**
     * Traducción de una re-planificación desde el día {@code D} (HU-18). Las partidas y las máquinas cubren la
     * semana completa (el algoritmo devuelve las fijas junto con lo nuevo). La bolsa se copia de la vigente
     * hasta D-1; desde D se reconstruye: saldo inicial de D en la vigente, proyección que llega desde D, tela
     * devuelta el día D ({@code TELA_DEVUELTA}) y consumo de lo conformado desde D. Las partidas conservadas y
     * las fijas conformadas antes de D no consumen, porque su tela ya salió de la bolsa. El domingo debe
     * coincidir con {@code EstadoSiguiente.stockFinal}.
     */
    public static ResultadoTraducido traducirReplanificacion(DatosSemana datos, ResultadoPlanificacion r, LocalDate D,
                                                             List<FilaBolsa> bolsaVigente) {
        if (!r.indicadores().factible())
            throw new FalloPlanificacionException(
                    "El algoritmo devolvió una re-planificación que no cumple todas las reglas; no se guardó nada.",
                    r.indicadores().incumplimientos());
        LocalDate[] dias = r.dias();
        int iD = indiceDia(dias, D);
        if (iD < 0) throw new FalloPlanificacionException("El día de inicio está fuera de la semana.", List.of(D.toString()));
        Map<String, Map<String, BigDecimal>> requerimientos = requerimientosPorPartida(datos.componentes());
        Map<String, Map<LocalDate, FilaBolsa>> vigente = new TreeMap<>();
        for (FilaBolsa b : bolsaVigente) vigente.computeIfAbsent(clave(b.codigoArticulo()), k -> new HashMap<>()).put(b.fecha(), b);

        // la semana completa para decidir si un artículo «tuvo kilos» (sección 6.2): lo copiado antes de D y lo que llega desde D
        Map<String, BigDecimal[]> ingresosDesdeD = ingresosPorDia(datos.proyeccion(), dias);
        ingresosDesdeD.values().forEach(a -> { for (int d = 0; d < iD; d++) a[d] = BigDecimal.ZERO.setScale(ESCALA_KG); });
        Map<String, BigDecimal> stockLunes = new HashMap<>();
        Map<String, BigDecimal[]> ingresosSemana = new HashMap<>();
        vigente.forEach((clave, porDia) -> {
            FilaBolsa lunes = porDia.get(dias[0]);
            if (lunes != null) stockLunes.put(clave, lunes.saldoInicial());
            BigDecimal[] a = ingresosSemana.computeIfAbsent(clave, k -> ceros(dias.length));
            for (int d = 0; d < iD; d++) if (porDia.containsKey(dias[d])) a[d] = porDia.get(dias[d]).ingresados();
        });
        ingresosDesdeD.forEach((clave, a) -> {
            BigDecimal[] s = ingresosSemana.computeIfAbsent(clave, k -> ceros(dias.length));
            for (int d = iD; d < dias.length; d++) s[d] = a[d];
        });
        List<String> advertencias = new ArrayList<>(r.advertencias());
        List<String> bloqueadas = bloqueadas(r.bloqueadas(), datos.partidas(), requerimientos, stockLunes, ingresosSemana, advertencias);

        Map<String, BigDecimal> devueltos = new HashMap<>();
        for (PartidaBloqueada b : r.bloqueadas())
            if (PartidaExcluida.TELA_DEVUELTA.equals(b.motivo()))
                requerimientos.getOrDefault(b.partida(), Map.of()).forEach((clave, kg) -> devueltos.merge(clave, kg, BigDecimal::add));
        List<AsignacionPartida> desdeD = r.asignaciones().stream().filter(a -> !a.fechaDisponible().isBefore(D)).toList();
        Map<String, BigDecimal[]> consumo = consumoPorDia(desdeD, requerimientos, dias);

        TreeSet<String> claves = new TreeSet<>(vigente.keySet());
        claves.addAll(ingresosDesdeD.keySet());
        claves.addAll(consumo.keySet());
        claves.addAll(devueltos.keySet());
        List<FilaBolsa> filas = new ArrayList<>();
        Map<String, BigDecimal> saldoDomingo = new HashMap<>();
        for (String clave : claves) {
            Map<LocalDate, FilaBolsa> previas = vigente.getOrDefault(clave, Map.of());
            BigDecimal cero = BigDecimal.ZERO.setScale(ESCALA_KG);
            for (int d = 0; d < iD; d++) {
                FilaBolsa v = previas.get(dias[d]);
                filas.add(v != null ? v : new FilaBolsa(clave, dias[d], cero, cero, cero, cero, cero));
            }
            BigDecimal saldo = previas.containsKey(dias[iD]) ? previas.get(dias[iD]).saldoInicial() : cero;
            BigDecimal[] ing = ingresosDesdeD.getOrDefault(clave, ceros(dias.length));
            BigDecimal[] con = consumo.getOrDefault(clave, ceros(dias.length));
            for (int d = iD; d < dias.length; d++) {
                BigDecimal dev = d == iD ? devueltos.getOrDefault(clave, cero) : cero;
                BigDecimal fin = saldo.add(ing[d]).add(dev).subtract(con[d]);
                if (fin.signum() < 0)
                    throw new FalloPlanificacionException("El consumo de tela superó lo disponible en la bolsa.",
                            List.of(clave + " " + dias[d] + ": " + fin.toPlainString() + " kg"));
                filas.add(new FilaBolsa(clave, dias[d], saldo, ing[d], dev, con[d], fin));
                saldo = fin;
            }
            saldoDomingo.put(clave, saldo);
        }
        verificarSaldoFinal(saldoDomingo, r);
        return new ResultadoTraducido(filasPartida(r.asignaciones()), bloqueadas, filasMaquina(r.maquinas(), r.asignaciones()),
                filas, r.indicadores(), List.copyOf(advertencias));
    }

    // ------------------------------------------------------------- partidas
    static List<FilaPartida> filasPartida(List<AsignacionPartida> asignaciones) {
        List<AsignacionPartida> ordenadas = new ArrayList<>(asignaciones);
        ordenadas.sort(Comparator.comparing(AsignacionPartida::maquina).thenComparing(AsignacionPartida::fecha)
                .thenComparing(AsignacionPartida::estado).thenComparingInt(AsignacionPartida::ordenConformacion));
        List<FilaPartida> filas = new ArrayList<>(ordenadas.size());
        String maquina = null;
        LocalDate dia = null;
        int posicion = 0;
        for (AsignacionPartida a : ordenadas) {
            if (!a.maquina().equals(maquina) || !a.fecha().equals(dia)) { maquina = a.maquina(); dia = a.fecha(); posicion = 0; }
            filas.add(new FilaPartida(a.partida(), a.maquina(), a.fecha(),
                    a.estado() == EstadoAsignacion.SOBRANTE ? ClasificacionResultado.SOBRANTE : ClasificacionResultado.ASIGNADA,
                    ++posicion, a.requiereLavado(), a.fechaDisponible()));
        }
        return filas;
    }

    // ----------------------------------------------------------- bloqueadas
    static List<String> bloqueadas(List<PartidaBloqueada> noConformadas, List<Partida> partidas,
                                   Map<String, Map<String, BigDecimal>> requerimientos, Map<String, BigDecimal> stock,
                                   Map<String, BigDecimal[]> ingresos, List<String> advertencias) {
        Map<String, String> barcaPorPartida = new HashMap<>();
        partidas.forEach(p -> barcaPorPartida.put(p.codigo(), p.barca()));
        List<String> bloqueadas = new ArrayList<>();
        for (PartidaBloqueada b : noConformadas) {
            String motivo = b.motivo();
            if (PartidaBloqueada.TELA_PRIORIZADA.equals(motivo) || PartidaExcluida.SIN_DIA_DISPONIBLE.equals(motivo)
                    || PartidaExcluida.TELA_DEVUELTA.equals(motivo)) {
                bloqueadas.add(b.partida());
            } else if (PartidaExcluida.SIN_TELA.equals(motivo)) {
                boolean algunArticuloTuvoKilos = requerimientos.getOrDefault(b.partida(), Map.of()).keySet().stream()
                        .anyMatch(clave -> tuvoKilos(clave, stock, ingresos));
                if (algunArticuloTuvoKilos) bloqueadas.add(b.partida());
            } else if (PartidaExcluida.SIN_MAQUINA.equals(motivo)) {
                advertencias.add("La partida " + b.partida() + " tiene la barca " + barcaPorPartida.get(b.partida())
                        + " sin máquina registrada; no se incluyó en la planificación.");
            } else {
                advertencias.add("La partida " + b.partida() + " no se conformó: " + motivo);
            }
        }
        return bloqueadas;
    }

    static boolean tuvoKilos(String clave, Map<String, BigDecimal> stock, Map<String, BigDecimal[]> ingresos) {
        if (stock.getOrDefault(clave, BigDecimal.ZERO).signum() > 0) return true;
        BigDecimal[] porDia = ingresos.get(clave);
        if (porDia == null) return false;
        for (BigDecimal kg : porDia) if (kg.signum() > 0) return true;
        return false;
    }

    // -------------------------------------------------------------- máquinas
    static List<FilaMaquina> filasMaquina(List<ResumenMaquina> resumen, List<AsignacionPartida> asignaciones) {
        Map<String, Integer> lavados = new HashMap<>();
        for (AsignacionPartida a : asignaciones) if (a.requiereLavado()) lavados.merge(a.maquina(), 1, Integer::sum);
        return resumen.stream().map(m -> new FilaMaquina(m.maquina(), m.objetivo(), m.minimoDiario(), m.maximoDiario(),
                m.asignadas(), m.sobrantes(), m.diferencia(), lavados.getOrDefault(m.maquina(), 0))).toList();
    }

    // ---------------------------------------------------------------- bolsas
    static Map<String, Map<String, BigDecimal>> requerimientosPorPartida(List<Componente> componentes) {
        Map<String, Map<String, BigDecimal>> req = new HashMap<>();
        for (Componente c : componentes) {
            if (c.kgRequeridos() <= 0) continue;
            req.computeIfAbsent(c.partida(), k -> new LinkedHashMap<>())
               .merge(clave(c.claveArticulo()), kg(c.kgRequeridos()), BigDecimal::add);
        }
        return req;
    }

    static Map<String, BigDecimal> stockInicial(List<StockArticulo> stock) {
        Map<String, BigDecimal> s = new HashMap<>();
        for (StockArticulo x : stock) if (x.kg() > 0) s.merge(clave(x.claveArticulo()), kg(x.kg()), BigDecimal::add);
        return s;
    }

    static Map<String, BigDecimal[]> ingresosPorDia(List<RegistroProyeccion> proyeccion, LocalDate[] dias) {
        Map<String, BigDecimal[]> ingresos = new HashMap<>();
        for (RegistroProyeccion r : proyeccion) {
            int d = indiceDia(dias, r.fechaDisponible());
            if (d < 0) continue;   // el algoritmo tampoco la suma (lo informa como advertencia)
            ingresos.computeIfAbsent(clave(r.claveArticulo()), k -> ceros(dias.length))[d] =
                    ingresos.get(clave(r.claveArticulo()))[d].add(kg(r.kg()));
        }
        return ingresos;
    }

    static Map<String, BigDecimal[]> consumoPorDia(List<AsignacionPartida> asignaciones,
                                                   Map<String, Map<String, BigDecimal>> requerimientos, LocalDate[] dias) {
        Map<String, BigDecimal[]> consumo = new HashMap<>();
        for (AsignacionPartida a : asignaciones) {
            int d = indiceDia(dias, a.fechaDisponible());   // día de conformación: cuando la tela salió de la bolsa
            if (d < 0) throw new FalloPlanificacionException("Una partida se conformó fuera de la semana.",
                    List.of(a.partida() + " " + a.fechaDisponible()));
            for (Map.Entry<String, BigDecimal> e : requerimientos.getOrDefault(a.partida(), Map.of()).entrySet())
                consumo.computeIfAbsent(e.getKey(), k -> ceros(dias.length))[d] =
                        consumo.get(e.getKey())[d].add(e.getValue());
        }
        return consumo;
    }

    static List<FilaBolsa> filasBolsa(LocalDate[] dias, Map<String, BigDecimal> stock, Map<String, BigDecimal[]> ingresos,
                                      Map<String, BigDecimal[]> consumo, ResultadoPlanificacion r) {
        TreeSet<String> claves = new TreeSet<>(stock.keySet());
        claves.addAll(ingresos.keySet());
        claves.addAll(consumo.keySet());
        List<FilaBolsa> filas = new ArrayList<>();
        Map<String, BigDecimal> saldoDomingo = new HashMap<>();
        BigDecimal cero = BigDecimal.ZERO.setScale(ESCALA_KG);
        for (String clave : claves) {
            BigDecimal saldo = stock.getOrDefault(clave, BigDecimal.ZERO);
            BigDecimal[] ing = ingresos.getOrDefault(clave, ceros(dias.length));
            BigDecimal[] con = consumo.getOrDefault(clave, ceros(dias.length));
            for (int d = 0; d < dias.length; d++) {
                BigDecimal fin = saldo.add(ing[d]).subtract(con[d]);
                if (fin.signum() < 0)
                    throw new FalloPlanificacionException("El consumo de tela superó lo disponible en la bolsa.",
                            List.of(clave + " " + dias[d] + ": " + fin.toPlainString() + " kg"));
                filas.add(new FilaBolsa(clave, dias[d], saldo, ing[d], cero, con[d], fin));
                saldo = fin;
            }
            saldoDomingo.put(clave, saldo);
        }
        verificarSaldoFinal(saldoDomingo, r);
        return filas;
    }

    /** El saldo reconstruido del domingo debe coincidir con el {@code stockFinal} del algoritmo (D-02). */
    static void verificarSaldoFinal(Map<String, BigDecimal> saldoDomingo, ResultadoPlanificacion r) {
        Map<String, BigDecimal> finalAlgoritmo = new TreeMap<>();
        r.estadoSiguiente().stockFinal().forEach(s -> finalAlgoritmo.merge(clave(s.claveArticulo()), kg(s.kg()), BigDecimal::add));
        List<String> diferencias = new ArrayList<>();
        saldoDomingo.forEach((clave, saldo) -> {
            BigDecimal esperado = finalAlgoritmo.getOrDefault(clave, BigDecimal.ZERO);
            if (saldo.subtract(esperado).abs().compareTo(TOLERANCIA_KG) > 0)
                diferencias.add(clave + ": reconstruido " + saldo.toPlainString() + " kg, algoritmo " + esperado.toPlainString() + " kg");
        });
        for (String clave : finalAlgoritmo.keySet())
            if (!saldoDomingo.containsKey(clave) && finalAlgoritmo.get(clave).signum() > 0)
                diferencias.add(clave + ": el algoritmo deja " + finalAlgoritmo.get(clave).toPlainString() + " kg sin movimientos");
        if (!diferencias.isEmpty())
            throw new FalloPlanificacionException(
                    "El saldo de las bolsas no coincide con el que devolvió el algoritmo; no se guardó ninguna versión.",
                    diferencias);
    }

    // -------------------------------------------------------------- apoyo
    /** Misma normalización de la clave de bolsa que usa el algoritmo. */
    static String clave(String claveArticulo) {
        return Objects.requireNonNull(claveArticulo, "clave de artículo").trim();
    }

    static BigDecimal kg(double valor) {
        return BigDecimal.valueOf(valor).setScale(ESCALA_KG, RoundingMode.HALF_UP);
    }

    private static BigDecimal[] ceros(int n) {
        BigDecimal[] a = new BigDecimal[n];
        java.util.Arrays.fill(a, BigDecimal.ZERO.setScale(ESCALA_KG));
        return a;
    }

    private static int indiceDia(LocalDate[] dias, LocalDate fecha) {
        for (int d = 0; d < dias.length; d++) if (dias[d].equals(fecha)) return d;
        return -1;
    }
}
