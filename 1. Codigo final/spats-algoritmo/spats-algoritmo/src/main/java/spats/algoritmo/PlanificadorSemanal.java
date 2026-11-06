package spats.algoritmo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import spats.algoritmo.aco.ColoniaHormigas;
import spats.algoritmo.aco.ParametrosACO;
import spats.algoritmo.aco.ResultadoBusqueda;
import spats.algoritmo.entrada.DatosSemana;
import spats.algoritmo.entrada.Pedido;
import spats.algoritmo.entrada.StockArticulo;
import spats.algoritmo.nucleo.Calendario;
import spats.algoritmo.nucleo.ConstructorInstancia;
import spats.algoritmo.nucleo.Evaluacion;
import spats.algoritmo.nucleo.FuncionObjetivo;
import spats.algoritmo.nucleo.InstanciaSemana;
import spats.algoritmo.nucleo.PartidaExcluida;
import spats.algoritmo.nucleo.Solucion;
import spats.algoritmo.nucleo.Validador;
import spats.algoritmo.salida.AsignacionPartida;
import spats.algoritmo.salida.EstadoAsignacion;
import spats.algoritmo.salida.EstadoSiguiente;
import spats.algoritmo.salida.Indicadores;
import spats.algoritmo.salida.PartidaBloqueada;
import spats.algoritmo.salida.ResultadoPlanificacion;
import spats.algoritmo.salida.ResumenMaquina;

/**
 * Punto de entrada del componente: recibe los datos de una semana, ejecuta la colonia de hormigas,
 * valida la mejor solución y la devuelve en términos de partidas, máquinas y días. En una
 * re-planificación, el resultado cubre la semana completa: las fijas junto con lo decidido desde D.
 */
public final class PlanificadorSemanal {

    public ResultadoPlanificacion planificar(DatosSemana datos, ParametrosACO parametrosACO) {
        parametrosACO.validar();
        InstanciaSemana I = ConstructorInstancia.construir(datos);
        FuncionObjetivo fo = new FuncionObjetivo(datos.parametros());
        ResultadoBusqueda rb = new ColoniaHormigas(parametrosACO).buscar(I, fo);
        List<String> incumplimientos = Validador.validar(I, rb.solucion(), rb.evaluacion());
        return construirResultado(I, datos, rb, incumplimientos);
    }

    /**
     * Evalúa una alternativa dada (por ejemplo, la planificación vigente) sobre la instancia de {@code datos},
     * con la misma función objetivo y el mismo validador que la búsqueda (comparación de RF55). De cada
     * asignación se toma la máquina, el día de conformación ({@code fechaDisponible}) y el orden de
     * conformación; el día y la clasificación salen del calendario, como en cualquier solución. Las fijas se
     * toman de la instancia. Si la alternativa no es factible en este escenario, sus incumplimientos quedan
     * en {@link Indicadores#incumplimientos()}.
     */
    public Indicadores evaluar(DatosSemana datos, List<AsignacionPartida> alternativa) {
        InstanciaSemana I = ConstructorInstancia.construir(datos);
        FuncionObjetivo fo = new FuncionObjetivo(datos.parametros());
        Map<String, Integer> indicePartida = new HashMap<>(), indiceMaquina = new HashMap<>();
        for (int p = 0; p < I.P; p++) indicePartida.put(I.partidas[p].codigo(), p);
        for (int m = 0; m < I.M; m++) indiceMaquina.put(I.maquinas[m].codigo(), m);

        Solucion s = new Solucion();
        s.diaConformacion = new int[I.P]; s.maquina = new int[I.P]; s.orden = new int[I.P];
        java.util.Arrays.fill(s.diaConformacion, -1); java.util.Arrays.fill(s.maquina, -1); java.util.Arrays.fill(s.orden, -1);
        s.stockFinal = I.stockInicial.clone();
        for (int p = 0; p < I.P; p++)
            if (I.fija[p]) {
                s.maquina[p] = I.fijaMaquina[p];
                s.diaConformacion[p] = I.diaConformacionPrevia[p];
                s.orden[p] = I.fijaOrden[p];
            }
        List<String> incumplimientos = new ArrayList<>();
        List<AsignacionPartida> ordenadas = new ArrayList<>(alternativa);
        ordenadas.sort(Comparator.comparingInt(AsignacionPartida::ordenConformacion));
        List<Integer> decididas = new ArrayList<>();
        for (AsignacionPartida a : ordenadas) {
            Integer p = indicePartida.get(a.partida());
            if (p == null) {
                incumplimientos.add("Alternativa: la partida " + a.partida() + " no puede planificarse en este escenario");
                continue;
            }
            if (I.fija[p]) continue;
            Integer m = indiceMaquina.get(a.maquina());
            long dia = java.time.temporal.ChronoUnit.DAYS.between(I.dias[0], a.fechaDisponible()) + 1;
            if (m == null || dia < 1 || dia > I.ND) {
                incumplimientos.add("Alternativa: la partida " + a.partida() + " tiene una máquina o un día fuera de la semana");
                continue;
            }
            s.maquina[p] = m;
            s.diaConformacion[p] = I.conservada[p] ? I.diaConformacionPrevia[p] : (int) dia;
            if (!I.conservada[p]) decididas.add(p);
        }
        int k = I.nFijas;                                   // mismo orden que la simulación: conservadas y luego el resto
        for (int p = 0; p < I.P; p++) if (I.conservada[p] && s.maquina[p] >= 0) s.orden[p] = ++k;
        for (int p : decididas) s.orden[p] = ++k;

        Evaluacion e = fo.evaluar(I, s);
        incumplimientos.addAll(Validador.validar(I, s, e));
        int bloqueadas = I.excluidas.size();
        for (int p = 0; p < I.P; p++) if (!s.conformada(p)) bloqueadas++;
        return new Indicadores(e.F, e.desbalanceSemanal, e.desbalanceDiario, e.lavados, I.objetivoTotal(),
                e.conformadas, e.asignadas, e.sobrantes, bloqueadas, 0, 0, 0, false, List.copyOf(incumplimientos));
    }

    static ResultadoPlanificacion construirResultado(InstanciaSemana I, DatosSemana datos, ResultadoBusqueda rb,
                                                     List<String> incumplimientos) {
        Solucion s = rb.solucion();
        Evaluacion e = rb.evaluacion();
        Calendario cal = e.calendario;
        Map<String, LocalDate> entrega = new HashMap<>();
        for (Pedido p : datos.pedidos()) if (p.fechaEntrega() != null) entrega.put(p.codigo(), p.fechaEntrega());

        List<AsignacionPartida> asig = new ArrayList<>();
        List<PartidaBloqueada> noConf = new ArrayList<>();
        Set<String> planificadas = new LinkedHashSet<>();
        for (int p = 0; p < I.P; p++) {
            var x = I.partidas[p];
            LocalDate fe = LocalDate.ofEpochDay(I.fechaEntrega[p]);
            if (s.conformada(p)) {
                asig.add(new AsignacionPartida(x.codigo(), x.pedido(), fe, x.barca(), I.maquinas[s.maquina[p]].codigo(),
                        I.dias[cal.dia[p] - 1], cal.sobrante[p] ? EstadoAsignacion.SOBRANTE : EstadoAsignacion.ASIGNADA,
                        I.dias[s.diaConformacion[p] - 1], s.orden[p], x.tonalidad(), e.requiereLavado[p]));
                planificadas.add(x.codigo());
            } else {
                noConf.add(new PartidaBloqueada(x.codigo(), x.pedido(), fe, x.barca(), PartidaBloqueada.TELA_PRIORIZADA));
            }
        }
        for (PartidaExcluida b : I.excluidas)
            noConf.add(new PartidaBloqueada(b.partida().codigo(), b.partida().pedido(),
                    entrega.get(b.partida().pedido()), b.partida().barca(), b.motivo()));
        asig.sort(Comparator.comparing(AsignacionPartida::maquina).thenComparing(AsignacionPartida::fecha)
                .thenComparing(AsignacionPartida::estado).thenComparingInt(AsignacionPartida::ordenConformacion));

        List<ResumenMaquina> maquinas = new ArrayList<>();
        for (int m = 0; m < I.M; m++) {
            int[] porDia = new int[I.ND];
            for (int d = 0; d < I.ND; d++) porDia[d] = I.habil[m][d] ? cal.partidasPorDia[m][d] : -1;
            int sob = 0;
            for (int p = 0; p < I.P; p++) if (s.maquina[p] == m && cal.sobrante[p]) sob++;
            maquinas.add(new ResumenMaquina(I.maquinas[m].codigo(), I.maquinas[m].barca(), I.objetivo[m],
                    I.minimoDiario[m], I.maximoDiario[m], e.partidasPorMaquina[m] - sob, sob, porDia));
        }

        Map<String, String> nombreArticulo = new HashMap<>();
        datos.componentes().forEach(c -> nombreArticulo.putIfAbsent(c.claveArticulo().trim(), c.articulo()));
        datos.proyeccion().forEach(r -> nombreArticulo.putIfAbsent(r.claveArticulo().trim(), r.articulo()));
        datos.stockInicial().forEach(r -> nombreArticulo.putIfAbsent(r.claveArticulo().trim(), r.articulo()));
        List<StockArticulo> stockFinal = new ArrayList<>();
        for (int b = 0; b < I.B; b++)
            if (s.stockFinal[b] > 1e-9)
                stockFinal.add(new StockArticulo(I.bolsas[b], nombreArticulo.get(I.bolsas[b]),
                        Math.round(s.stockFinal[b] * 1000) / 1000.0));

        Indicadores ind = new Indicadores(e.F, e.desbalanceSemanal, e.desbalanceDiario, e.lavados, I.objetivoTotal(),
                e.conformadas, e.asignadas, e.sobrantes, noConf.size(), rb.evaluaciones(), rb.iteraciones(),
                rb.tiempoMs(), rb.detenidaPorTiempo(), List.copyOf(incumplimientos));
        return new ResultadoPlanificacion(I.dias.clone(), List.copyOf(asig), List.copyOf(maquinas), List.copyOf(noConf),
                ind, new EstadoSiguiente(List.copyOf(stockFinal), Set.copyOf(planificadas)), List.copyOf(I.advertencias));
    }
}
