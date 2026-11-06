package spats.algoritmo.entrada;

import java.util.List;

/**
 * Información de una semana que el sistema entrega al algoritmo.
 * Las partidas incluyen las del mes con su estado; solo se evalúan las pendientes.
 */
public record DatosSemana(ParametrosPlanificacion parametros,
                          List<Maquina> maquinas,
                          List<Indisponibilidad> indisponibilidades,
                          List<Pedido> pedidos,
                          List<Partida> partidas,
                          List<Componente> componentes,
                          List<RegistroProyeccion> proyeccion,
                          List<StockArticulo> stockInicial) {

    public DatosSemana {
        maquinas = List.copyOf(maquinas);
        indisponibilidades = List.copyOf(indisponibilidades);
        pedidos = List.copyOf(pedidos);
        partidas = List.copyOf(partidas);
        componentes = List.copyOf(componentes);
        proyeccion = List.copyOf(proyeccion);
        stockInicial = List.copyOf(stockInicial);
    }

    /** Copia de los datos con otras partidas y otro stock inicial (memoria de la semana anterior). */
    public DatosSemana conMemoria(List<Partida> nuevasPartidas, List<StockArticulo> nuevoStock) {
        return new DatosSemana(parametros, maquinas, indisponibilidades, pedidos, nuevasPartidas, componentes,
                proyeccion, nuevoStock);
    }
}
