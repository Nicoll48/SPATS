package pe.edu.pucp.spats.pedido.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.pucp.spats.auditoria.entity.TipoOperacion;
import pe.edu.pucp.spats.auditoria.service.BitacoraService;
import pe.edu.pucp.spats.carga.repository.CargaDatosRepository;
import pe.edu.pucp.spats.comun.error.ConflictoException;
import pe.edu.pucp.spats.comun.error.RecursoNoEncontradoException;
import pe.edu.pucp.spats.pedido.dto.PedidoDtos.ArticuloResponse;
import pe.edu.pucp.spats.pedido.dto.PedidoDtos.DetallePedidoResponse;
import pe.edu.pucp.spats.pedido.dto.PedidoDtos.ModificarArticuloRequest;
import pe.edu.pucp.spats.pedido.dto.PedidoDtos.ModificarDetallePedidoRequest;
import pe.edu.pucp.spats.pedido.dto.PedidoDtos.ModificarPedidoRequest;
import pe.edu.pucp.spats.pedido.dto.PedidoDtos.PedidoResponse;
import pe.edu.pucp.spats.pedido.entity.Articulo;
import pe.edu.pucp.spats.pedido.entity.Cliente;
import pe.edu.pucp.spats.pedido.entity.DetallePedido;
import pe.edu.pucp.spats.pedido.entity.Pedido;
import pe.edu.pucp.spats.pedido.mapper.PedidoMapper;
import pe.edu.pucp.spats.pedido.repository.ArticuloRepository;
import pe.edu.pucp.spats.pedido.repository.ClienteRepository;
import pe.edu.pucp.spats.pedido.repository.DetallePedidoRepository;
import pe.edu.pucp.spats.pedido.repository.PedidoRepository;
import pe.edu.pucp.spats.tejeduria.service.ProyeccionService;

/** Consulta y modificación manual de pedidos, artículos y detalle de pedido (RF13, HU-06). */
@Service
@Transactional(readOnly = true)
public class PedidoService {

    private final PedidoRepository pedidos;
    private final ClienteRepository clientes;
    private final ArticuloRepository articulos;
    private final DetallePedidoRepository detalles;
    private final PedidoMapper mapper;
    private final ProyeccionService proyecciones;
    private final CargaDatosRepository cargas;
    private final BitacoraService bitacora;

    public PedidoService(PedidoRepository pedidos, ClienteRepository clientes, ArticuloRepository articulos,
                         DetallePedidoRepository detalles, PedidoMapper mapper, ProyeccionService proyecciones,
                         CargaDatosRepository cargas, BitacoraService bitacora) {
        this.pedidos = pedidos;
        this.clientes = clientes;
        this.articulos = articulos;
        this.detalles = detalles;
        this.mapper = mapper;
        this.proyecciones = proyecciones;
        this.cargas = cargas;
        this.bitacora = bitacora;
    }

    public List<PedidoResponse> listar() {
        Map<Integer, List<DetallePedido>> porPedido = detalles.listarCompletos().stream()
                .collect(Collectors.groupingBy(d -> d.getPedido().getId()));
        return pedidos.listarConCliente().stream()
                .map(p -> mapper.aResponse(p, porPedido.getOrDefault(p.getId(), List.of())))
                .toList();
    }

    public List<ArticuloResponse> listarArticulos() {
        return articulos.findAllByOrderByCodigoAsc().stream().map(mapper::aResponse).toList();
    }

    @Transactional
    public PedidoResponse modificarPedido(Integer id, ModificarPedidoRequest solicitud) {
        Pedido pedido = buscarPedido(id);
        String clienteNuevo = solicitud.cliente().trim();
        if (!clienteNuevo.equals(pedido.getCliente().getNombre())) {
            bitacora.registrarCambio("pedido", id, "cliente", pedido.getCliente().getNombre(), clienteNuevo);
            pedido.cambiarCliente(obtenerOCrearCliente(clienteNuevo));
        }
        bitacora.registrarCambio("pedido", id, "fecha_entrega", pedido.getFechaEntrega(), solicitud.fechaEntrega());
        pedido.cambiarFechaEntrega(solicitud.fechaEntrega());
        List<DetallePedido> suyos = detalles.listarCompletos().stream()
                .filter(d -> d.getPedido().getId().equals(id)).toList();
        return mapper.aResponse(pedido, suyos);
    }

    @Transactional
    public ArticuloResponse modificarArticulo(Integer id, ModificarArticuloRequest solicitud) {
        Articulo articulo = articulos.findById(id).orElseThrow(() -> new RecursoNoEncontradoException(
                "ARTICULO_NO_ENCONTRADO", "No existe el artículo indicado."));
        bitacora.registrarCambio("articulo", id, "nombre", articulo.getNombre(), solicitud.nombre().trim());
        articulo.cambiarNombre(solicitud.nombre().trim());
        return mapper.aResponse(articulo);
    }

    @Transactional
    public DetallePedidoResponse modificarDetalle(Integer id, ModificarDetallePedidoRequest solicitud) {
        DetallePedido detalle = detalles.findById(id).orElseThrow(() -> new RecursoNoEncontradoException(
                "DETALLE_NO_ENCONTRADO", "No existe el artículo del pedido indicado."));
        bitacora.registrarCambio("detalle_pedido", id, "peso_total_requerido", detalle.getPesoTotalRequerido(),
                solicitud.pesoTotalRequerido());
        detalle.cambiarPesoTotalRequerido(solicitud.pesoTotalRequerido());
        return mapper.aResponse(detalle);
    }

    /** Elimina un pedido con sus artículos del pedido. Si otros datos lo usan, se conserva (D-42). */
    @Transactional
    public void eliminarPedido(Integer id) {
        Pedido pedido = buscarPedido(id);
        try {
            List<DetallePedido> suyos = detalles.findByPedidoId(id);
            // Sin el pedido ya no se podría volver a una versión eliminada de la proyección que lo usa (D-46).
            cargas.deleteAllById(proyecciones.purgarEliminadasQueUsan(suyos.stream().map(DetallePedido::getId).toList()));
            detalles.deleteAll(suyos);
            pedidos.delete(pedido);
            pedidos.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictoException("PEDIDO_EN_USO", "El pedido " + pedido.getNumeroOp()
                    + " tiene partidas, proyección o incidencias registradas. Elimínalas primero.");
        }
        clientes.eliminarSinPedidos();
        bitacora.registrar(TipoOperacion.ELIMINAR, "pedido", id, pedido.getNumeroOp());
    }

    /** Elimina un artículo que ningún pedido usa (D-42). */
    @Transactional
    public void eliminarArticulo(Integer id) {
        Articulo articulo = articulos.findById(id).orElseThrow(() -> new RecursoNoEncontradoException(
                "ARTICULO_NO_ENCONTRADO", "No existe el artículo indicado."));
        try {
            articulos.delete(articulo);
            articulos.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictoException("ARTICULO_EN_USO", "El artículo " + articulo.getCodigo()
                    + " está en pedidos o planificaciones. Elimina primero esos pedidos.");
        }
        bitacora.registrar(TipoOperacion.ELIMINAR, "articulo", id, articulo.getCodigo());
    }

    /**
     * Cambio de prioridad (D-13, D-13b): actualiza la fecha de entrega del pedido y deja el valor anterior en la
     * bitácora. Devuelve la fecha anterior. Lo usará la confirmación de la re-planificación (D-03); al registrar
     * la incidencia el pedido no cambia.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public LocalDate cambiarFechaEntregaPorPrioridad(Pedido pedido, LocalDate nueva) {
        LocalDate anterior = pedido.getFechaEntrega();
        bitacora.registrarCambio("pedido", pedido.getId(), "fecha_entrega", anterior, nueva);
        pedido.cambiarFechaEntrega(nueva);
        return anterior;
    }

    /** Completa los pedidos cuyas partidas quedaron todas COMPLETADA (sección 6.1). Devuelve cuántos se completaron. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int completarPedidosTerminados(Collection<Integer> idsPedido) {
        if (idsPedido.isEmpty()) return 0;
        List<Pedido> terminados = pedidos.pendientesConTodasSusPartidasCompletadas(idsPedido);
        for (Pedido p : terminados) {
            bitacora.registrarCambio("pedido", p.getId(), "estado", p.getEstado(), "COMPLETADO");
            p.completar();
            bitacora.registrar(TipoOperacion.COMPLETAR, "pedido", p.getId(), null);
        }
        return terminados.size();
    }

    public Pedido buscarPedido(Integer id) {
        return pedidos.findById(id).orElseThrow(() -> new RecursoNoEncontradoException(
                "PEDIDO_NO_ENCONTRADO", "No existe el pedido indicado."));
    }

    public Pedido buscarPedidoPorNumero(String numeroOp) {
        return pedidos.findByNumeroOp(numeroOp.trim()).orElseThrow(() -> new RecursoNoEncontradoException(
                "PEDIDO_NO_ENCONTRADO", "No existe el pedido " + numeroOp + "."));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Cliente obtenerOCrearCliente(String nombre) {
        return clientes.findFirstByNombreIgnoreCase(nombre.trim()).orElseGet(() -> clientes.save(new Cliente(nombre.trim())));
    }
}
