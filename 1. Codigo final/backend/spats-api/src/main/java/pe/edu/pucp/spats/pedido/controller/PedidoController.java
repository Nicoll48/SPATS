package pe.edu.pucp.spats.pedido.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.pucp.spats.pedido.dto.PedidoDtos.ArticuloResponse;
import pe.edu.pucp.spats.pedido.dto.PedidoDtos.DetallePedidoResponse;
import pe.edu.pucp.spats.pedido.dto.PedidoDtos.ModificarArticuloRequest;
import pe.edu.pucp.spats.pedido.dto.PedidoDtos.ModificarDetallePedidoRequest;
import pe.edu.pucp.spats.pedido.dto.PedidoDtos.ModificarPedidoRequest;
import pe.edu.pucp.spats.pedido.dto.PedidoDtos.PedidoResponse;
import pe.edu.pucp.spats.pedido.service.PedidoService;

@RestController
@RequestMapping("/api")
public class PedidoController {

    private final PedidoService servicio;

    public PedidoController(PedidoService servicio) { this.servicio = servicio; }

    @GetMapping("/pedidos")
    public List<PedidoResponse> listar() { return servicio.listar(); }

    @PutMapping("/pedidos/{id}")
    public PedidoResponse modificar(@PathVariable Integer id, @Valid @RequestBody ModificarPedidoRequest solicitud) {
        return servicio.modificarPedido(id, solicitud);
    }

    @DeleteMapping("/pedidos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Integer id) { servicio.eliminarPedido(id); }

    @PutMapping("/detalles-pedido/{id}")
    public DetallePedidoResponse modificarDetalle(@PathVariable Integer id,
                                                  @Valid @RequestBody ModificarDetallePedidoRequest solicitud) {
        return servicio.modificarDetalle(id, solicitud);
    }

    @GetMapping("/articulos")
    public List<ArticuloResponse> listarArticulos() { return servicio.listarArticulos(); }

    @DeleteMapping("/articulos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarArticulo(@PathVariable Integer id) { servicio.eliminarArticulo(id); }

    @PutMapping("/articulos/{id}")
    public ArticuloResponse modificarArticulo(@PathVariable Integer id,
                                              @Valid @RequestBody ModificarArticuloRequest solicitud) {
        return servicio.modificarArticulo(id, solicitud);
    }
}
