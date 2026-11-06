package spats.algoritmo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
import spats.algoritmo.entrada.Tonalidad;

/** Constructor de semanas pequeñas para las pruebas unitarias. La semana inicia el lunes 14/09/2026. */
public final class DatosPrueba {

    public static final LocalDate LUNES = LocalDate.of(2026, 9, 14);

    private final List<Maquina> maquinas = new ArrayList<>();
    private final List<Indisponibilidad> indisp = new ArrayList<>();
    private final List<Pedido> pedidos = new ArrayList<>();
    private final List<Partida> partidas = new ArrayList<>();
    private final List<Componente> componentes = new ArrayList<>();
    private final List<RegistroProyeccion> proyeccion = new ArrayList<>();
    private final List<StockArticulo> stock = new ArrayList<>();
    private int orden = 0;

    /** Día d de la semana (1 = lunes 14/09). */
    public static LocalDate dia(int d) { return LUNES.plusDays(d - 1); }

    public DatosPrueba maquina(String codigo, int capacidad, int objetivo) {
        maquinas.add(new Maquina(codigo, capacidad, "C", capacidad + " C", objetivo));
        return this;
    }

    public DatosPrueba pedido(String codigo, LocalDate entrega) {
        pedidos.add(new Pedido(codigo, "Cliente " + codigo, entrega.minusDays(40), entrega));
        return this;
    }

    public DatosPrueba partida(String codigo, String pedido, double peso, Tonalidad t, String barca) {
        return partida(codigo, pedido, peso, t, barca, EstadoPartida.PENDIENTE);
    }

    public DatosPrueba partida(String codigo, String pedido, double peso, Tonalidad t, String barca, EstadoPartida e) {
        partidas.add(new Partida(codigo, pedido, "C" + codigo, "Color " + codigo, peso, t, barca, ++orden, e));
        return this;
    }

    public DatosPrueba componente(String partida, String articulo, double kg) {
        componentes.add(new Componente(partida, articulo, "Artículo " + articulo, articulo,
                componentes.stream().anyMatch(c -> c.partida().equals(partida)) ? "Complemento" : "Principal", kg));
        return this;
    }

    /** Tela tejida en una fecha (queda disponible según RD-08). */
    public DatosPrueba tejido(String articulo, String pedido, LocalDate fecha, double kg) {
        proyeccion.add(new RegistroProyeccion("T1", pedido, "Artículo " + articulo, articulo, fecha, kg));
        return this;
    }

    public DatosPrueba stock(String articulo, double kg) {
        stock.add(new StockArticulo(articulo, "Artículo " + articulo, kg));
        return this;
    }

    public DatosPrueba indisponible(String maquina, LocalDate fecha) {
        indisp.add(new Indisponibilidad(maquina, fecha, "Mantenimiento"));
        return this;
    }

    /** n partidas de 100 kg del artículo A, barca 200 C, pedido P1, con la tonalidad indicada. */
    public DatosPrueba partidasIguales(String prefijo, int n, Tonalidad t) {
        for (int i = 1; i <= n; i++) {
            partida(prefijo + i, "P1", 100, t, "200 C");
            componente(prefijo + i, "A", 100);
        }
        return this;
    }

    public DatosSemana construir() {
        return new DatosSemana(new ParametrosPlanificacion(LUNES), maquinas, indisp, pedidos, partidas, componentes,
                proyeccion, stock);
    }
}
