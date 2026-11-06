package pe.edu.pucp.spats.pedido.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import pe.edu.pucp.spats.pedido.dto.PedidoDtos.ArticuloResponse;
import pe.edu.pucp.spats.pedido.dto.PedidoDtos.DetallePedidoResponse;
import pe.edu.pucp.spats.pedido.dto.PedidoDtos.PedidoResponse;
import pe.edu.pucp.spats.pedido.entity.Articulo;
import pe.edu.pucp.spats.pedido.entity.DetallePedido;
import pe.edu.pucp.spats.pedido.entity.Pedido;

@Component
public class PedidoMapper {

    public PedidoResponse aResponse(Pedido p, List<DetallePedido> detalles) {
        return new PedidoResponse(p.getId(), p.getNumeroOp(), p.getCliente().getNombre(), p.getFechaEntrega(),
                p.getEstado(), detalles.stream().map(this::aResponse).toList());
    }

    public DetallePedidoResponse aResponse(DetallePedido d) {
        return new DetallePedidoResponse(d.getId(), d.getArticulo().getId(), d.getArticulo().getCodigo(),
                d.getArticulo().getNombre(), d.getPesoTotalRequerido());
    }

    public ArticuloResponse aResponse(Articulo a) {
        return new ArticuloResponse(a.getId(), a.getCodigo(), a.getNombre());
    }
}
