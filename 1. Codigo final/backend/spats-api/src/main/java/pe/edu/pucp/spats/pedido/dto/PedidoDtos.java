package pe.edu.pucp.spats.pedido.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import pe.edu.pucp.spats.pedido.entity.EstadoPedido;

/** Contrato JSON del módulo de pedidos y artículos. */
public final class PedidoDtos {

    private PedidoDtos() { }

    public record DetallePedidoResponse(Integer id, Integer idArticulo, String codigoArticulo, String nombreArticulo,
                                        BigDecimal pesoTotalRequerido) { }

    public record PedidoResponse(Integer id, String numeroOp, String cliente, LocalDate fechaEntrega,
                                 EstadoPedido estado, List<DetallePedidoResponse> detalles) { }

    public record ArticuloResponse(Integer id, String codigo, String nombre) { }

    public record ModificarPedidoRequest(
            @NotBlank(message = "Indica el cliente.") @Size(max = 150) String cliente,
            @NotNull(message = "Indica la fecha de entrega.") LocalDate fechaEntrega) { }

    public record ModificarArticuloRequest(
            @NotBlank(message = "Indica el nombre del artículo.") @Size(max = 150) String nombre) { }

    public record ModificarDetallePedidoRequest(
            @NotNull @Positive(message = "El peso debe ser mayor que cero.") @Digits(integer = 8, fraction = 2)
            BigDecimal pesoTotalRequerido) { }
}
