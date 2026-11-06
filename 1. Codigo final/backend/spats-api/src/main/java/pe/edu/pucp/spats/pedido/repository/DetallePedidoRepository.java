package pe.edu.pucp.spats.pedido.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import pe.edu.pucp.spats.pedido.entity.DetallePedido;

public interface DetallePedidoRepository extends JpaRepository<DetallePedido, Integer> {

    @Query("""
            select d from DetallePedido d join fetch d.pedido p join fetch p.cliente join fetch d.articulo
            order by p.fechaEntrega, p.numeroOp, d.articulo.codigo
            """)
    List<DetallePedido> listarCompletos();

    List<DetallePedido> findByPedidoId(Integer idPedido);

    List<DetallePedido> findByIdCarga(Integer idCarga);

    boolean existsByPedidoId(Integer idPedido);
}
