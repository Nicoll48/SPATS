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
 * valida la mejor solución y la devuelve en términos de partidas, máquinas y días.
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
