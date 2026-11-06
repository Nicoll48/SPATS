package pe.edu.pucp.spats.carga.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.pucp.spats.carga.entity.TipoDato;
import pe.edu.pucp.spats.carga.hoja.Plantilla;
import pe.edu.pucp.spats.comun.hoja.EscritorLibro;
import pe.edu.pucp.spats.partida.entity.PartidaComponente;
import pe.edu.pucp.spats.partida.repository.PartidaComponenteRepository;
import pe.edu.pucp.spats.partida.repository.PartidaRepository;
import pe.edu.pucp.spats.pedido.repository.DetallePedidoRepository;
import pe.edu.pucp.spats.tejeduria.service.ProyeccionService;
import pe.edu.pucp.spats.tejeduria.service.SemanaService;

/**
 * Plantillas vacías con filas de ejemplo (RF09) y descarga de los datos registrados en el mismo formato de
 * la plantilla, para poder corregirlos y volver a cargarlos (RF12, HU-05).
 */
@Service
@Transactional(readOnly = true)
public class PlantillaService {

    private final DetallePedidoRepository detalles;
    private final PartidaRepository partidas;
    private final PartidaComponenteRepository componentes;
    private final SemanaService semanas;
    private final ProyeccionService proyecciones;

    public PlantillaService(DetallePedidoRepository detalles, PartidaRepository partidas,
                            PartidaComponenteRepository componentes, SemanaService semanas, ProyeccionService proyecciones) {
        this.detalles = detalles;
        this.partidas = partidas;
        this.componentes = componentes;
        this.semanas = semanas;
        this.proyecciones = proyecciones;
    }

    public record Archivo(String nombre, byte[] contenido) { }

    public Archivo plantilla(TipoDato tipo) {
        Plantilla p = Plantilla.de(tipo);
        return new Archivo(p.archivo(), escribir(p, p.hojas().stream()
                .collect(Collectors.toMap(Plantilla.Hoja::nombre, Plantilla.Hoja::ejemplo))));
    }

    public Archivo exportar(TipoDato tipo, LocalDate lunes) {
        Plantilla p = Plantilla.de(tipo);
        return switch (tipo) {
            case PEDIDOS -> new Archivo("pedidos_registrados.xlsx", escribir(p, Map.of("Pedidos",
                    detalles.listarCompletos().stream().map(d -> fila(d.getPedido().getNumeroOp(), d.getPedido().getCliente().getNombre(),
                            d.getPedido().getFechaEntrega(), d.getArticulo().getCodigo(), d.getArticulo().getNombre(),
                            d.getPesoTotalRequerido())).toList())));
            case PARTIDAS -> {
                List<List<Object>> filasPartida = partidas.listarCompletas().stream().map(x -> fila(x.getNumeroPartida(),
                        x.getPedido().getNumeroOp(), x.getColor().getCodigoColor(), x.getColor().getNombre(),
                        x.getColor().getClasificacionColor().name(), x.getColor().getTonalidad().name(), x.getBarca(),
                        x.getPesoTotal())).toList();
                List<List<Object>> filasComponente = new ArrayList<>();
                for (PartidaComponente c : componentes.listarCompletos())
                    filasComponente.add(fila(c.getPartida().getNumeroPartida(), c.getDetallePedido().getArticulo().getCodigo(),
                            c.getTipoComponente().name(), c.isTubularAbierto(), c.getPesoRequerido()));
                yield new Archivo("partidas_registradas.xlsx",
                        escribir(p, Map.of("Partidas", filasPartida, "Componentes", filasComponente)));
            }
            case PROYECCION_TEJEDURIA -> {
                List<List<Object>> filas = semanas.buscar(lunes).flatMap(s -> proyecciones.vigente(s.getId()))
                        .map(v -> proyecciones.detallesEntidad(v.getId()).stream().map(d -> fila(
                                d.getDetallePedido().getPedido().getNumeroOp(), d.getDetallePedido().getArticulo().getCodigo(),
                                d.getFechaProyectada(), d.getKgProyectados())).toList())
                        .orElse(List.of());
                yield new Archivo("proyeccion_" + lunes + ".xlsx", escribir(p, Map.of("Proyeccion", filas)));
            }
        };
    }

    private static byte[] escribir(Plantilla plantilla, Map<String, List<List<Object>>> filasPorHoja) {
        try (EscritorLibro libro = new EscritorLibro()) {
            for (Plantilla.Hoja hoja : plantilla.hojas())
                libro.hoja(hoja.nombre(), hoja.columnas(), filasPorHoja.getOrDefault(hoja.nombre(), List.of()));
            return libro.aBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Object> fila(Object... valores) {
        return Arrays.asList(valores);
    }
}
