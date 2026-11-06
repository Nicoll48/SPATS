package pe.edu.pucp.spats.carga.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.pucp.spats.auditoria.entity.TipoOperacion;
import pe.edu.pucp.spats.auditoria.service.BitacoraService;
import pe.edu.pucp.spats.carga.dto.CargaDtos.CargaEliminadaResponse;
import pe.edu.pucp.spats.carga.entity.CargaDatos;
import pe.edu.pucp.spats.carga.entity.TipoDato;
import pe.edu.pucp.spats.carga.repository.CargaDatosRepository;
import pe.edu.pucp.spats.comun.error.ConflictoException;
import pe.edu.pucp.spats.comun.error.RecursoNoEncontradoException;
import pe.edu.pucp.spats.partida.entity.EstadoPartida;
import pe.edu.pucp.spats.partida.entity.Partida;
import pe.edu.pucp.spats.partida.entity.PartidaComponente;
import pe.edu.pucp.spats.partida.repository.ColorRepository;
import pe.edu.pucp.spats.partida.repository.PartidaComponenteRepository;
import pe.edu.pucp.spats.partida.repository.PartidaRepository;
import pe.edu.pucp.spats.pedido.entity.DetallePedido;
import pe.edu.pucp.spats.pedido.entity.Pedido;
import pe.edu.pucp.spats.pedido.repository.ArticuloRepository;
import pe.edu.pucp.spats.pedido.repository.ClienteRepository;
import pe.edu.pucp.spats.pedido.repository.DetallePedidoRepository;
import pe.edu.pucp.spats.pedido.repository.PedidoRepository;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.VersionProyeccionEliminadaResponse;
import pe.edu.pucp.spats.planificacion.service.EliminacionVersionProyeccionService;
import pe.edu.pucp.spats.tejeduria.entity.ProyeccionTejeduria;
import pe.edu.pucp.spats.tejeduria.repository.ProyeccionRepository;
import pe.edu.pucp.spats.tejeduria.service.ProyeccionService;

/**
 * Elimina lo que agregó un archivo (D-40): solo los registros creados por esa carga, en una sola transacción.
 * Lo que ya existía antes de la carga no cambia. Si otros datos usan esos registros, no se elimina nada y se
 * informa qué eliminar primero. La carga deja de aparecer en el historial y la operación queda en la trazabilidad.
 * La proyección es distinta: su versión y su carga quedan como eliminadas y se puede volver a ellas (D-46).
 */
@Service
public class EliminacionCargaService {

    private final CargaDatosRepository cargas;
    private final PedidoRepository pedidos;
    private final DetallePedidoRepository detalles;
    private final ArticuloRepository articulos;
    private final ClienteRepository clientes;
    private final PartidaRepository partidas;
    private final PartidaComponenteRepository componentes;
    private final ColorRepository colores;
    private final ProyeccionRepository proyecciones;
    private final ProyeccionService proyeccionService;
    private final EliminacionVersionProyeccionService versiones;
    private final BitacoraService bitacora;

    public EliminacionCargaService(CargaDatosRepository cargas, PedidoRepository pedidos, DetallePedidoRepository detalles,
                                   ArticuloRepository articulos, ClienteRepository clientes, PartidaRepository partidas,
                                   PartidaComponenteRepository componentes, ColorRepository colores,
                                   ProyeccionRepository proyecciones, ProyeccionService proyeccionService,
                                   EliminacionVersionProyeccionService versiones, BitacoraService bitacora) {
        this.cargas = cargas;
        this.pedidos = pedidos;
        this.detalles = detalles;
        this.articulos = articulos;
        this.clientes = clientes;
        this.partidas = partidas;
        this.componentes = componentes;
        this.colores = colores;
        this.proyecciones = proyecciones;
        this.proyeccionService = proyeccionService;
        this.versiones = versiones;
        this.bitacora = bitacora;
    }

    @Transactional
    public CargaEliminadaResponse eliminar(Integer id) {
        CargaDatos carga = cargas.findById(id).orElseThrow(() -> new RecursoNoEncontradoException(
                "CARGA_NO_ENCONTRADA", "No existe la carga indicada."));
        if (carga.getTipoDato() == TipoDato.PROYECCION_TEJEDURIA) return eliminarProyeccion(carga);
        Map<String, Integer> eliminados = carga.getTipoDato() == TipoDato.PEDIDOS ? eliminarPedidos(id) : eliminarPartidas(id);
        bitacora.registrar(TipoOperacion.ELIMINAR, "carga_datos", id,
                carga.getTipoDato() + " " + carga.getNombreArchivo() + " " + eliminados);
        cargas.delete(carga);
        return new CargaEliminadaResponse(carga.getTipoDato(), carga.getNombreArchivo(), eliminados);
    }

    private Map<String, Integer> eliminarPedidos(Integer id) {
        List<DetallePedido> suyos = detalles.findByIdCarga(id);
        List<Pedido> pedidosCarga = pedidos.findByIdCarga(id);
        Map<String, Integer> eliminados = new LinkedHashMap<>();
        try {
            purgarEliminadas(suyos);
            detalles.deleteAll(suyos);
            detalles.flush();
            // Un pedido que también recibió artículos de otra carga se conserva con ellos.
            List<Pedido> sinDetalles = pedidosCarga.stream().filter(p -> !detalles.existsByPedidoId(p.getId())).toList();
            pedidos.deleteAll(sinDetalles);
            pedidos.flush();
            eliminados.put("pedidos", sinDetalles.size());
            eliminados.put("detalles de pedido", suyos.size());
        } catch (DataIntegrityViolationException e) {
            throw new ConflictoException("CARGA_EN_USO", "Las partidas, la proyección o las incidencias registradas usan los "
                    + "pedidos de este archivo. Elimina primero esas cargas.");
        }
        eliminados.put("artículos", articulos.eliminarSinUso());
        clientes.eliminarSinPedidos();
        return eliminados;
    }

    private Map<String, Integer> eliminarPartidas(Integer id) {
        List<Partida> suyas = partidas.findByIdCarga(id);
        if (suyas.stream().anyMatch(p -> p.getEstadoPartida() != EstadoPartida.PENDIENTE))
            throw new ConflictoException("CARGA_EN_USO", "Este archivo tiene partidas completadas o arrastradas: "
                    + "ya pasaron por una planificación cerrada y no se pueden eliminar.");
        List<PartidaComponente> suyos = componentes.findByPartidaIdIn(suyas.stream().map(Partida::getId).toList());
        Map<String, Integer> eliminados = new LinkedHashMap<>();
        try {
            componentes.deleteAll(suyos);
            partidas.deleteAll(suyas);
            partidas.flush();
            eliminados.put("partidas", suyas.size());
            eliminados.put("componentes", suyos.size());
        } catch (DataIntegrityViolationException e) {
            throw new ConflictoException("CARGA_EN_USO", "Algunas partidas de este archivo aparecen en una planificación "
                    + "o incidencia, así que no se puede eliminar.");
        }
        eliminados.put("colores", colores.eliminarSinUso());
        return eliminados;
    }

    /** La carga sigue en el historial, marcada como eliminada, para poder volver a su versión (D-46). */
    private CargaEliminadaResponse eliminarProyeccion(CargaDatos carga) {
        Map<String, Integer> eliminados = new LinkedHashMap<>();
        for (ProyeccionTejeduria version : proyecciones.findByIdCarga(carga.getId())) {
            VersionProyeccionEliminadaResponse r = versiones.eliminar(version.getId(), true);
            eliminados.put("versión de proyección", 1);
            eliminados.put("registros de proyección", r.registros());
            eliminados.put("planificaciones", r.planificaciones());
        }
        return new CargaEliminadaResponse(carga.getTipoDato(), carga.getNombreArchivo(), eliminados);
    }

    /** Sin el pedido ya no se podría volver a una versión eliminada que lo usa: se borra con su carga. */
    private void purgarEliminadas(List<DetallePedido> suyos) {
        cargas.deleteAllById(proyeccionService.purgarEliminadasQueUsan(suyos.stream().map(DetallePedido::getId).toList()));
    }
}
