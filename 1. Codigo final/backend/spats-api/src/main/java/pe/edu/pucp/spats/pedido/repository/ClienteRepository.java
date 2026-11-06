package pe.edu.pucp.spats.pedido.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import pe.edu.pucp.spats.pedido.entity.Cliente;

public interface ClienteRepository extends JpaRepository<Cliente, Integer> {

    Optional<Cliente> findFirstByNombreIgnoreCase(String nombre);

    /** Quita los clientes que se quedaron sin pedidos. */
    @Modifying(flushAutomatically = true)
    @Query(value = "delete from cliente c where not exists (select 1 from pedido p where p.id_cliente = c.id_cliente)", nativeQuery = true)
    int eliminarSinPedidos();
}
