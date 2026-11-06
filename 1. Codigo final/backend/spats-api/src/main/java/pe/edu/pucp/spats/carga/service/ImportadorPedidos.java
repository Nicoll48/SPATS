package pe.edu.pucp.spats.carga.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import pe.edu.pucp.spats.carga.hoja.Plantilla;
import pe.edu.pucp.spats.carga.hoja.Plantilla.Columnas;
import pe.edu.pucp.spats.carga.hoja.ValidacionLibro;
import pe.edu.pucp.spats.comun.hoja.LectorLibro;
import pe.edu.pucp.spats.pedido.entity.Articulo;
import pe.edu.pucp.spats.pedido.entity.DetallePedido;
import pe.edu.pucp.spats.pedido.entity.Pedido;
import pe.edu.pucp.spats.pedido.repository.ArticuloRepository;
import pe.edu.pucp.spats.pedido.repository.DetallePedidoRepository;
import pe.edu.pucp.spats.pedido.repository.PedidoRepository;
import pe.edu.pucp.spats.pedido.service.PedidoService;

/**
 * Importa pedidos, clientes, artículos y el detalle pedido-artículo (plantilla PEDIDOS). La carga es
 * acumulable (RF11): lo que ya existe se conserva sin reemplazarlo y se informa; las correcciones se hacen
 * con la modificación manual (RF13).
 */
@Component
public class ImportadorPedidos {

    private static final String HOJA = "Pedidos";

    private final PedidoRepository pedidos;
    private final ArticuloRepository articulos;
    private final DetallePedidoRepository detalles;
    private final PedidoService pedidoService;

    public ImportadorPedidos(PedidoRepository pedidos, ArticuloRepository articulos, DetallePedidoRepository detalles,
                             PedidoService pedidoService) {
        this.pedidos = pedidos;
        this.articulos = articulos;
        this.detalles = detalles;
        this.pedidoService = pedidoService;
    }

    private record Fila(int numero, String numeroOp, String cliente, LocalDate fechaEntrega, String codigoArticulo,
                        String nombreArticulo, BigDecimal peso) { }

    public ResultadoImportacion importar(ValidacionLibro v, Integer idCarga) {
        v.exigirEstructura();
        List<Fila> filas = leerFilas(v);
        validarConsistencia(v, filas);
        v.rechazarSiHayErrores();

        Map<String, Pedido> existentes = pedidos.findByNumeroOpIn(filas.stream().map(Fila::numeroOp).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Pedido::getNumeroOp, Function.identity()));
        Map<String, Articulo> articulosExistentes = articulos.findByCodigoIn(filas.stream().map(Fila::codigoArticulo).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Articulo::getCodigo, Function.identity()));
        Set<String> paresExistentes = detalles.listarCompletos().stream()
                .map(d -> d.getPedido().getNumeroOp() + "|" + d.getArticulo().getCodigo()).collect(Collectors.toSet());

        ResultadoImportacion.Constructor resultado = new ResultadoImportacion.Constructor();
        Map<String, Pedido> pedidosArchivo = new HashMap<>();
        Set<String> avisadosPedido = new HashSet<>();
        for (Fila f : filas) {
            Pedido pedido = pedidosArchivo.computeIfAbsent(f.numeroOp(), n -> {
                Pedido p = existentes.get(n);
                if (p != null) return p;
                resultado.nuevo("pedidos");
                Pedido nuevo = new Pedido(pedidoService.obtenerOCrearCliente(f.cliente()), n, f.fechaEntrega());
                nuevo.asociarCarga(idCarga);
                return pedidos.save(nuevo);
            });
            if (existentes.containsKey(f.numeroOp()) && avisadosPedido.add(f.numeroOp())
                    && (!pedido.getCliente().getNombre().equalsIgnoreCase(f.cliente()) || !pedido.getFechaEntrega().equals(f.fechaEntrega())))
                v.advertencia(HOJA, f.numero(), Columnas.NUMERO_OP, "El pedido " + f.numeroOp()
                        + " ya estaba registrado con otro cliente o fecha de entrega; se conservó lo registrado.");

            Articulo articulo = articulosExistentes.computeIfAbsent(f.codigoArticulo(), c -> {
                resultado.nuevo("articulos");
                return articulos.save(new Articulo(c, f.nombreArticulo()));
            });
            if (!articulo.getNombre().equals(f.nombreArticulo()))
                v.advertencia(HOJA, f.numero(), Columnas.NOMBRE_ARTICULO, "El artículo " + f.codigoArticulo()
                        + " ya estaba registrado como «" + articulo.getNombre() + "»; se conservó ese nombre.");

            if (paresExistentes.add(f.numeroOp() + "|" + f.codigoArticulo())) {
                DetallePedido detalle = new DetallePedido(pedido, articulo, f.peso());
                detalle.asociarCarga(idCarga);
                detalles.save(detalle);
                resultado.nuevo("detalles de pedido");
            } else {
                resultado.existente();
                v.advertencia(HOJA, f.numero(), Columnas.CODIGO_ARTICULO, "El artículo " + f.codigoArticulo()
                        + " ya estaba registrado en el pedido " + f.numeroOp() + "; no se modificó.");
            }
        }
        return resultado.construir(v.advertencias());
    }

    private List<Fila> leerFilas(ValidacionLibro v) {
        List<Fila> filas = new ArrayList<>();
        for (LectorLibro.Fila f : v.filas(HOJA)) {
            Fila fila = new Fila(f.numero(), v.texto(HOJA, f, Columnas.NUMERO_OP, 50), v.texto(HOJA, f, Columnas.CLIENTE, 150),
                    v.fecha(HOJA, f, Columnas.FECHA_ENTREGA), v.codigoArticulo(HOJA, f, Columnas.CODIGO_ARTICULO),
                    v.texto(HOJA, f, Columnas.NOMBRE_ARTICULO, 150), v.positivo(HOJA, f, Columnas.PESO_TOTAL_REQUERIDO, 8, 2));
            if (fila.numeroOp() != null && fila.cliente() != null && fila.fechaEntrega() != null && fila.codigoArticulo() != null
                    && fila.nombreArticulo() != null && fila.peso() != null) filas.add(fila);
        }
        if (v.filas(HOJA).isEmpty()) v.error(HOJA, 0, "", "La hoja no tiene registros.");
        return filas;
    }

    /** Dentro del archivo, un pedido tiene un solo cliente y fecha de entrega, y no repite artículos. */
    private static void validarConsistencia(ValidacionLibro v, List<Fila> filas) {
        Map<String, Fila> primeraPorPedido = new LinkedHashMap<>();
        Set<String> pares = new HashSet<>();
        for (Fila f : filas) {
            Fila primera = primeraPorPedido.putIfAbsent(f.numeroOp(), f);
            if (primera != null && (!primera.cliente().equalsIgnoreCase(f.cliente())
                    || !Objects.equals(primera.fechaEntrega(), f.fechaEntrega())))
                v.error(HOJA, f.numero(), Columnas.NUMERO_OP, "El pedido " + f.numeroOp()
                        + " tiene otro cliente o fecha de entrega que en la fila " + primera.numero() + ".");
            if (!pares.add(f.numeroOp() + "|" + f.codigoArticulo()))
                v.error(HOJA, f.numero(), Columnas.CODIGO_ARTICULO, "El artículo " + f.codigoArticulo()
                        + " se repite en el pedido " + f.numeroOp() + ".");
        }
    }

    public Plantilla plantilla() { return Plantilla.PEDIDOS; }
}
