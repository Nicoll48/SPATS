package spats.algoritmo.entrada;

import java.util.List;

/**
 * Información de una semana que el sistema entrega al algoritmo.
 * Las partidas incluyen las del mes con su estado; solo se evalúan las pendientes.
 * {@code replanificacion} es null en una planificación de la semana completa.
 */
public record DatosSemana(ParametrosPlanificacion parametros,
                          List<Maquina> maquinas,
                          List<Indisponibilidad> indisponibilidades,
                          List<Pedido> pedidos,
                          List<Partida> partidas,
                          List<Componente> componentes,
                          List<RegistroProyeccion> proyeccion,
                          List<StockArticulo> stockInicial,
                          Replanificacion replanificacion) {

    public DatosSemana {
        maquinas = List.copyOf(maquinas);
        indisponibilidades = List.copyOf(indisponibilidades);
        pedidos = List.copyOf(pedidos);
        partidas = List.copyOf(partidas);
        componentes = List.copyOf(componentes);
        proyeccion = List.copyOf(proyeccion);
        stockInicial = List.copyOf(stockInicial);
    }

    /** Planificación de la semana completa, desde el lunes. */
    public DatosSemana(ParametrosPlanificacion parametros, List<Maquina> maquinas, List<Indisponibilidad> indisponibilidades,
                       List<Pedido> pedidos, List<Partida> partidas, List<Componente> componentes,
                       List<RegistroProyeccion> proyeccion, List<StockArticulo> stockInicial) {
        this(parametros, maquinas, indisponibilidades, pedidos, partidas, componentes, proyeccion, stockInicial, null);
    }

    /** Copia de los datos para re-planificar desde un día. */
    public DatosSemana conReplanificacion(Replanificacion r) {
        return new DatosSemana(parametros, maquinas, indisponibilidades, pedidos, partidas, componentes, proyeccion,
                stockInicial, r);
    }

    /** Copia de los datos con otras partidas y otro stock inicial (memoria de la semana anterior). */
    public DatosSemana conMemoria(List<Partida> nuevasPartidas, List<StockArticulo> nuevoStock) {
        return new DatosSemana(parametros, maquinas, indisponibilidades, pedidos, nuevasPartidas, componentes,
                proyeccion, nuevoStock, replanificacion);
    }
}
