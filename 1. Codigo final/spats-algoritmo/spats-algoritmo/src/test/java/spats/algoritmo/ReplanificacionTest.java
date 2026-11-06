package spats.algoritmo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import spats.algoritmo.aco.ParametrosACO;
import spats.algoritmo.entrada.AsignacionFija;
import spats.algoritmo.entrada.Componente;
import spats.algoritmo.entrada.ConformacionConservada;
import spats.algoritmo.entrada.DatosSemana;
import spats.algoritmo.entrada.DisponibilidadPartida;
import spats.algoritmo.entrada.Indisponibilidad;
import spats.algoritmo.entrada.Pedido;
import spats.algoritmo.entrada.RegistroProyeccion;
import spats.algoritmo.entrada.Replanificacion;
import spats.algoritmo.entrada.StockArticulo;
import spats.algoritmo.excel.LectorDatosExcel;
import spats.algoritmo.nucleo.PartidaExcluida;
import spats.algoritmo.salida.AsignacionPartida;
import spats.algoritmo.salida.EstadoAsignacion;
import spats.algoritmo.salida.Indicadores;
import spats.algoritmo.salida.ResultadoPlanificacion;
import spats.algoritmo.salida.ResumenMaquina;

/**
 * Re-planificación desde el día D (HU-18, RN-25) sobre la semana 38 del R2.2: lo anterior a D no cambia,
 * la tela no se consume dos veces, cada incidencia se respeta y, sin datos fijos, el resultado es el mismo
 * que el de la planificación de la semana completa.
 */
class ReplanificacionTest {

    static final Path DATOS = Path.of("datos");
    static final ParametrosACO ACO = new ParametrosACO();

    static Stream<String> instancias() throws Exception {
        try (Stream<Path> s = Files.list(DATOS)) {
            return s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".xlsx")).map(n -> n.replace(".xlsx", ""))
                    .sorted().toList().stream();
        }
    }

    // ------------------------------------------------------------------ regresión
    @ParameterizedTest(name = "{0}")
    @MethodSource("instancias")
    @DisplayName("PU-29 Con D = lunes y sin datos fijos, el resultado es el mismo que la planificación completa")
    void sinDatosFijosDaElMismoResultado(String instancia) throws Exception {
        var c = LectorDatosExcel.leer(DATOS.resolve(instancia + ".xlsx"));
        ResultadoPlanificacion normal = new PlanificadorSemanal().planificar(c.datos(), c.parametrosACO());
        DatosSemana re = c.datos().conReplanificacion(
                new Replanificacion(c.datos().parametros().getFechaInicioSemana(), List.of(), List.of(), List.of()));
        ResultadoPlanificacion r = new PlanificadorSemanal().planificar(re, c.parametrosACO());
        assertEquals(normal.indicadores().funcionObjetivo(), r.indicadores().funcionObjetivo());
        assertEquals(normal.asignaciones(), r.asignaciones());
        assertEquals(normal.bloqueadas(), r.bloqueadas());
        assertEquals(normal.estadoSiguiente(), r.estadoSiguiente());
        // y evaluar la solución encontrada da su misma F
        Indicadores ev = new PlanificadorSemanal().evaluar(c.datos(), normal.asignaciones());
        assertTrue(ev.factible(), ev.incumplimientos().toString());
        assertEquals(normal.indicadores().funcionObjetivo(), ev.funcionObjetivo());
    }

    // ------------------------------------------------------- lo anterior a D
    @Test
    @DisplayName("PU-30 Lo ubicado antes de D queda idéntico y nada nuevo se conforma antes de D")
    void conservaLoAnteriorAD() throws Exception {
        Escenario e = Escenario.s38(4);
        ResultadoPlanificacion r = e.replanificar(e.base());
        assertTrue(r.indicadores().factible(), r.indicadores().incumplimientos().toString());
        Map<String, AsignacionPartida> nuevas = porPartida(r.asignaciones());
        for (AsignacionPartida a : e.vigente.asignaciones())
            if (a.fecha().isBefore(e.D)) assertEquals(guardado(a), guardado(nuevas.get(a.partida())), "fija " + a.partida());
        assertEquals(secuencias(e.vigente.asignaciones(), e.D), secuencias(r.asignaciones(), e.D));   // mismo orden_secuencia
        for (AsignacionPartida a : r.asignaciones()) {
            AsignacionPartida antes = porPartida(e.vigente.asignaciones()).get(a.partida());
            boolean fija = antes != null && antes.fecha().isBefore(e.D);
            boolean conservada = antes != null && !fija && antes.fechaDisponible().isBefore(e.D);
            if (fija) continue;
            assertFalse(a.fecha().isBefore(e.D), "ubicada desde D: " + a.partida());
            if (conservada) assertEquals(antes.fechaDisponible(), a.fechaDisponible(), "conserva su conformación: " + a.partida());
            else assertFalse(a.fechaDisponible().isBefore(e.D), "conformada desde D: " + a.partida());
        }
        e.verificarTela(r);
    }

    @Test
    @DisplayName("PU-31 Partidas del día D ya en máquina: quedan fijas y su tela sale del saldo de D")
    void partidasDelDiaDYaEnMaquina() throws Exception {
        Escenario e = Escenario.s38(4);
        List<String> enMaquina = e.vigente.asignaciones().stream().filter(a -> a.fecha().equals(e.D)).limit(2)
                .map(AsignacionPartida::partida).toList();
        assertFalse(enMaquina.isEmpty());
        e.marcadas.addAll(enMaquina);
        ResultadoPlanificacion r = e.replanificar(e.base());
        assertTrue(r.indicadores().factible(), r.indicadores().incumplimientos().toString());
        Map<String, AsignacionPartida> antes = porPartida(e.vigente.asignaciones()), nuevas = porPartida(r.asignaciones());
        for (String p : enMaquina) {
            assertEquals(antes.get(p).maquina(), nuevas.get(p).maquina());
            assertEquals(antes.get(p).fecha(), nuevas.get(p).fecha());
            assertEquals(antes.get(p).estado(), nuevas.get(p).estado());
        }
        e.verificarTela(r);
    }

    // ----------------------------------------------------------- incidencias
    @Test
    @DisplayName("PU-32 Incidencia de máquina: sin partidas en el periodo afectado y objetivo reducido")
    void incidenciaDeMaquina() throws Exception {
        Escenario e = Escenario.s38(4);
        String maquina = e.maquinaConPartidasDesdeD();
        DatosSemana d = e.conIndisponible(e.base(), maquina, e.D, e.D.plusDays(1));      // jueves y viernes
        ResultadoPlanificacion r = e.replanificar(d);
        assertTrue(r.indicadores().factible(), r.indicadores().incumplimientos().toString());
        for (AsignacionPartida a : r.asignaciones())
            if (a.maquina().equals(maquina) && !a.fecha().isBefore(e.D))
                assertTrue(a.fecha().isAfter(e.D.plusDays(1)), "no se ubica en el periodo afectado: " + a);
        ResumenMaquina antes = resumen(e.vigente, maquina), ahora = resumen(r, maquina);
        assertEquals(antes.objetivo() - 4, ahora.objetivo());                            // 2 por cada día perdido
        e.verificarTela(r);
        // caída hasta el domingo: la vigente usaba la máquina desde D y ya no es factible (RF55)
        DatosSemana hastaDomingo = e.conIndisponible(e.base(), maquina, e.D, e.lunes.plusDays(6));
        Indicadores vig = new PlanificadorSemanal().evaluar(hastaDomingo.conReplanificacion(e.replanificacion(hastaDomingo)),
                e.vigente.asignaciones());
        assertFalse(vig.factible());
        r = e.replanificar(hastaDomingo);
        assertTrue(r.indicadores().factible(), r.indicadores().incumplimientos().toString());
        assertTrue(r.asignaciones().stream().noneMatch(a -> a.maquina().equals(maquina) && !a.fecha().isBefore(e.D)));
        e.verificarTela(r);
    }

    @Test
    @DisplayName("PU-33 Problema de complemento: la partida no se ubica ni se conforma antes de la fecha prevista")
    void problemaDeComplemento() throws Exception {
        Escenario e = Escenario.s38(3);
        LocalDate fecha = e.D.plusDays(2);
        String conservada = e.conservadas().get(0);
        String porConformar = e.vigente.asignaciones().stream().filter(a -> !a.fechaDisponible().isBefore(e.D))
                .map(AsignacionPartida::partida).findFirst().orElseThrow();
        e.ubicableDesde.put(conservada, fecha);
        e.disponibilidades.put(porConformar, fecha);
        ResultadoPlanificacion r = e.replanificar(e.base());
        assertTrue(r.indicadores().factible(), r.indicadores().incumplimientos().toString());
        Map<String, AsignacionPartida> n = porPartida(r.asignaciones());
        assertFalse(n.get(conservada).fecha().isBefore(fecha));
        if (n.containsKey(porConformar)) assertFalse(n.get(porConformar).fechaDisponible().isBefore(fecha));
        e.verificarTela(r);
    }

    @Test
    @DisplayName("PU-34 Complemento después del domingo: la partida queda Bloqueada y su tela vuelve a la bolsa el día D")
    void complementoFueraDeLaSemana() throws Exception {
        Escenario e = Escenario.s38(4);
        String conservada = e.conservadas().get(0);
        e.ubicableDesde.put(conservada, e.lunes.plusDays(8));
        ResultadoPlanificacion r = e.replanificar(e.base());
        assertTrue(r.indicadores().factible(), r.indicadores().incumplimientos().toString());
        assertFalse(porPartida(r.asignaciones()).containsKey(conservada));
        assertTrue(r.bloqueadas().stream().anyMatch(b -> b.partida().equals(conservada)
                && b.motivo().equals(PartidaExcluida.TELA_DEVUELTA)));
        e.devueltas.add(conservada);
        e.verificarTela(r);
    }

    @Test
    @DisplayName("PU-35 Barca con máquinas caídas: la partida se ubica cuando vuelven; si no vuelven en la semana, su tela regresa")
    void barcaSinMaquinaDesdeD() throws Exception {
        Escenario e = Escenario.s38(4);
        String conservada = e.conservadas().get(0);
        String barca = e.barca(conservada);
        DatosSemana d = e.base();
        for (String m : e.maquinasDeBarca(barca)) d = e.conIndisponible(d, m, e.D, e.D.plusDays(1));   // vuelven el sábado
        ResultadoPlanificacion r = e.replanificar(d);
        assertTrue(r.indicadores().factible(), r.indicadores().incumplimientos().toString());
        assertTrue(porPartida(r.asignaciones()).get(conservada).fecha().isAfter(e.D.plusDays(1)));

        d = e.base();
        for (String m : e.maquinasDeBarca(barca)) d = e.conIndisponible(d, m, e.D, e.lunes.plusDays(6));  // no vuelven
        r = e.replanificar(d);
        assertTrue(r.indicadores().factible(), r.indicadores().incumplimientos().toString());
        assertTrue(r.bloqueadas().stream().anyMatch(b -> b.partida().equals(conservada)
                && b.motivo().equals(PartidaExcluida.TELA_DEVUELTA)));
        for (String p : e.conservadas()) if (e.barca(p).equals(barca)) e.devueltas.add(p);
        e.verificarTela(r);
    }

    @Test
    @DisplayName("PU-36 Cambio de prioridad: la nueva fecha de entrega ordena la conformación desde D")
    void cambioDePrioridad() throws Exception {
        Escenario e = Escenario.s38(3);
        String pedido = e.vigente.asignaciones().stream().filter(a -> !a.fechaDisponible().isBefore(e.D))
                .map(AsignacionPartida::pedido).reduce((x, y) -> y).orElseThrow();
        LocalDate nueva = e.lunes.minusDays(30);
        ResultadoPlanificacion r = e.replanificar(e.conEntrega(e.base(), pedido, nueva));
        assertTrue(r.indicadores().factible(), r.indicadores().incumplimientos().toString());   // el validador revisa la prioridad
        assertTrue(r.asignaciones().stream().filter(a -> a.pedido().equals(pedido)).allMatch(a -> a.fechaEntrega().equals(nueva)));
        e.verificarTela(r);
    }

    @Test
    @DisplayName("PU-37 Las tres incidencias juntas")
    void variasIncidencias() throws Exception {
        Escenario e = Escenario.s38(4);
        String maquina = e.maquinaConPartidasDesdeD();
        List<String> todas = e.conservadas();
        String conservada = todas.get(todas.size() - 1);
        e.ubicableDesde.put(conservada, e.D.plusDays(1));
        String pedido = e.vigente.asignaciones().get(0).pedido();
        DatosSemana d = e.conEntrega(e.conIndisponible(e.base(), maquina, e.D, e.D), pedido, e.lunes.minusDays(30));
        ResultadoPlanificacion r = e.replanificar(d);
        assertTrue(r.indicadores().factible(), r.indicadores().incumplimientos().toString());
        assertTrue(r.asignaciones().stream().noneMatch(a -> a.maquina().equals(maquina) && a.fecha().equals(e.D)
                && !a.fecha().isBefore(e.D) && !e.esFija(a.partida())));
        e.verificarTela(r);
    }

    // ------------------------------------------------------------- apoyo
    static Map<String, AsignacionPartida> porPartida(List<AsignacionPartida> l) {
        Map<String, AsignacionPartida> m = new HashMap<>();
        l.forEach(a -> m.put(a.partida(), a));
        return m;
    }

    /** Lo que se guarda de una asignación (el orden global de conformación no se guarda: se guarda orden_secuencia). */
    static List<Object> guardado(AsignacionPartida a) {
        return List.of(a.partida(), a.maquina(), a.fecha(), a.estado(), a.fechaDisponible(), a.requiereLavado());
    }

    /** Secuencia de cada máquina y día anterior a D: Asignadas y luego Sobrantes, por orden de conformación. */
    static Map<String, List<String>> secuencias(List<AsignacionPartida> l, LocalDate D) {
        Map<String, List<String>> m = new java.util.TreeMap<>();
        l.stream().filter(a -> a.fecha().isBefore(D))
                .sorted(java.util.Comparator.comparing(AsignacionPartida::estado).thenComparingInt(AsignacionPartida::ordenConformacion))
                .forEach(a -> m.computeIfAbsent(a.maquina() + " " + a.fecha(), k -> new ArrayList<>()).add(a.partida()));
        return m;
    }

    static ResumenMaquina resumen(ResultadoPlanificacion r, String maquina) {
        return r.maquinas().stream().filter(m -> m.maquina().equals(maquina)).findFirst().orElseThrow();
    }

    /** Semana del R2.2 planificada completa (la «vigente») y re-planificada desde el día {@code dia}. */
    static final class Escenario {
        final DatosSemana datos;
        final ResultadoPlanificacion vigente;
        final LocalDate lunes, D;
        final Set<String> marcadas = new HashSet<>(), devueltas = new HashSet<>();
        final Map<String, LocalDate> ubicableDesde = new HashMap<>(), disponibilidades = new HashMap<>();

        Escenario(DatosSemana datos, int dia) {
            this.datos = datos;
            this.vigente = new PlanificadorSemanal().planificar(datos, ACO);
            this.lunes = datos.parametros().getFechaInicioSemana();
            this.D = lunes.plusDays(dia - 1);
        }

        static Escenario s38(int dia) throws Exception {
            return new Escenario(LectorDatosExcel.leer(DATOS.resolve("S38-B.xlsx")).datos(), dia);
        }

        boolean esFija(String partida) {
            AsignacionPartida a = porPartida(vigente.asignaciones()).get(partida);
            return a != null && (a.fecha().isBefore(D) || (a.fecha().equals(D) && marcadas.contains(partida)));
        }

        List<String> conservadas() {
            return vigente.asignaciones().stream().filter(a -> !esFija(a.partida()) && a.fechaDisponible().isBefore(D))
                    .map(AsignacionPartida::partida).toList();
        }

        String barca(String partida) {
            return datos.partidas().stream().filter(p -> p.codigo().equals(partida)).findFirst().orElseThrow().barca();
        }

        List<String> maquinasDeBarca(String barca) {
            return datos.maquinas().stream().filter(m -> m.barca().trim().equals(barca.trim())).map(m -> m.codigo()).toList();
        }

        String maquinaConPartidasDesdeD() {
            return vigente.asignaciones().stream().filter(a -> !a.fecha().isBefore(D)).map(AsignacionPartida::maquina)
                    .findFirst().orElseThrow();
        }

        /** Datos con el stock al iniciar el día D: el de la vigente después de lo conformado antes de D. */
        DatosSemana base() {
            Map<String, Double> saldo = new HashMap<>();
            datos.stockInicial().forEach(s -> saldo.merge(s.claveArticulo().trim(), s.kg(), Double::sum));
            for (RegistroProyeccion r : datos.proyeccion())
                if (!r.fechaDisponible().isBefore(lunes) && r.fechaDisponible().isBefore(D))
                    saldo.merge(r.claveArticulo().trim(), r.kg(), Double::sum);
            for (AsignacionPartida a : vigente.asignaciones())
                if (a.fechaDisponible().isBefore(D)) consumo(a.partida()).forEach((k, kg) -> saldo.merge(k, -kg, Double::sum));
            List<StockArticulo> stock = new ArrayList<>();
            saldo.forEach((k, kg) -> { if (kg > 1e-9) stock.add(new StockArticulo(k, k, kg)); });
            return new DatosSemana(datos.parametros(), datos.maquinas(), datos.indisponibilidades(), datos.pedidos(),
                    datos.partidas(), datos.componentes(), datos.proyeccion(), stock);
        }

        Map<String, Double> consumo(String partida) {
            Map<String, Double> m = new HashMap<>();
            for (Componente c : datos.componentes())
                if (c.partida().equals(partida) && c.kgRequeridos() > 1e-9) m.merge(c.claveArticulo().trim(), c.kgRequeridos(), Double::sum);
            return m;
        }

        Replanificacion replanificacion(DatosSemana d) {
            List<AsignacionFija> fijas = new ArrayList<>();
            List<ConformacionConservada> cons = new ArrayList<>();
            for (AsignacionPartida a : vigente.asignaciones()) {
                if (esFija(a.partida()))
                    fijas.add(new AsignacionFija(a.partida(), a.maquina(), a.fecha(), a.estado() == EstadoAsignacion.SOBRANTE,
                            a.fechaDisponible(), a.ordenConformacion()));
                else if (a.fechaDisponible().isBefore(D))
                    cons.add(new ConformacionConservada(a.partida(), a.fechaDisponible(), ubicableDesde.getOrDefault(a.partida(), D)));
            }
            List<DisponibilidadPartida> disp = new ArrayList<>();
            disponibilidades.forEach((p, f) -> disp.add(new DisponibilidadPartida(p, f)));
            return new Replanificacion(D, fijas, cons, disp);
        }

        ResultadoPlanificacion replanificar(DatosSemana d) {
            return new PlanificadorSemanal().planificar(d.conReplanificacion(replanificacion(d)), ACO);
        }

        DatosSemana conIndisponible(DatosSemana d, String maquina, LocalDate desde, LocalDate hasta) {
            List<Indisponibilidad> l = new ArrayList<>(d.indisponibilidades());
            for (LocalDate f = desde; !f.isAfter(hasta); f = f.plusDays(1)) l.add(new Indisponibilidad(maquina, f, "Incidencia"));
            return new DatosSemana(d.parametros(), d.maquinas(), l, d.pedidos(), d.partidas(), d.componentes(), d.proyeccion(),
                    d.stockInicial());
        }

        DatosSemana conEntrega(DatosSemana d, String pedido, LocalDate fecha) {
            List<Pedido> l = d.pedidos().stream().map(p -> p.codigo().equals(pedido)
                    ? new Pedido(p.codigo(), p.cliente(), p.fechaPedido(), fecha) : p).toList();
            return new DatosSemana(d.parametros(), d.maquinas(), d.indisponibilidades(), l, d.partidas(), d.componentes(),
                    d.proyeccion(), d.stockInicial());
        }

        /**
         * Ninguna tela se consume dos veces: saldo de D − tela de las fijas conformadas desde D + tela devuelta
         * + proyección desde D − consumo de lo conformado desde D (sin las conservadas) = stock final.
         */
        void verificarTela(ResultadoPlanificacion r) {
            Map<String, Double> saldo = new HashMap<>();
            base().stockInicial().forEach(s -> saldo.merge(s.claveArticulo(), s.kg(), Double::sum));
            devueltas.forEach(p -> consumo(p).forEach((k, kg) -> saldo.merge(k, kg, Double::sum)));
            for (RegistroProyeccion x : datos.proyeccion())
                if (!x.fechaDisponible().isBefore(D) && x.fechaDisponible().isBefore(lunes.plusDays(7)))
                    saldo.merge(x.claveArticulo().trim(), x.kg(), Double::sum);
            Set<String> conservadas = new HashSet<>(conservadas());
            for (AsignacionPartida a : r.asignaciones()) {
                if (a.fechaDisponible().isBefore(D)) continue;                       // fija o conservada: tela ya descontada
                assertFalse(conservadas.contains(a.partida()), "una conservada no consume tela nueva");
                consumo(a.partida()).forEach((k, kg) -> saldo.merge(k, -kg, Double::sum));
            }
            Map<String, Double> fin = new HashMap<>();
            r.estadoSiguiente().stockFinal().forEach(s -> fin.put(s.claveArticulo(), s.kg()));
            for (String k : saldo.keySet()) {
                assertTrue(saldo.get(k) > -1e-6, "saldo negativo " + k);
                assertEquals(saldo.get(k), fin.getOrDefault(k, 0.0), 1e-3, "stock final de " + k);
            }
        }
    }
}
