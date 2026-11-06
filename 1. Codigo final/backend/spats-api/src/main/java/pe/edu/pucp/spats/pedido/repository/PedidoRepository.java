package pe.edu.pucp.spats.pedido.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import pe.edu.pucp.spats.pedido.entity.Pedido;

public interface PedidoRepository extends JpaRepository<Pedido, Integer> {

    @EntityGraph(attributePaths = "cliente")
    Optional<Pedido> findByNumeroOp(String numeroOp);

    @EntityGraph(attributePaths = "cliente")
    List<Pedido> findByNumeroOpIn(Collection<String> numeros);

    @EntityGraph(attributePaths = "cliente")
    @Query("select p from Pedido p order by p.fechaEntrega, p.numeroOp")
    List<Pedido> listarConCliente();

    @EntityGraph(attributePaths = "cliente")
    List<Pedido> findByIdCarga(Integer idCarga);

    /** Pedidos pendientes cuyas partidas están todas COMPLETADA (se completan automáticamente, sección 6.1). */
    @Query("""
            select p from Pedido p
            where p.id in :ids and p.estado = pe.edu.pucp.spats.pedido.entity.EstadoPedido.PENDIENTE
              and exists (select 1 from Partida x where x.pedido = p)
              and not exists (select 1 from Partida x where x.pedido = p
                              and x.estadoPartida <> pe.edu.pucp.spats.partida.entity.EstadoPartida.COMPLETADA)
            """)
    List<Pedido> pendientesConTodasSusPartidasCompletadas(Collection<Integer> ids);
}
