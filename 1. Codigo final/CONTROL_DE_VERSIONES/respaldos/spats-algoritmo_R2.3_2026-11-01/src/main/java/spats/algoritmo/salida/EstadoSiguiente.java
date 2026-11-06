package spats.algoritmo.salida;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import spats.algoritmo.entrada.EstadoPartida;
import spats.algoritmo.entrada.Partida;
import spats.algoritmo.entrada.StockArticulo;

/**
 * Memoria que deja la semana para la siguiente: tela no utilizada de cada artículo y partidas
 * conformadas, que pasan a estado Planificada.
 */
public record EstadoSiguiente(List<StockArticulo> stockFinal, Set<String> partidasPlanificadas) {

    /** Devuelve las partidas con el estado actualizado para la semana siguiente. */
    public List<Partida> actualizar(List<Partida> partidas) {
        List<Partida> r = new ArrayList<>(partidas.size());
        for (Partida p : partidas)
            r.add(partidasPlanificadas.contains(p.codigo()) ? p.conEstado(EstadoPartida.PLANIFICADA) : p);
        return r;
    }
}
