package pe.edu.pucp.spats.partida.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import pe.edu.pucp.spats.partida.entity.PartidaComponente;

public interface PartidaComponenteRepository extends JpaRepository<PartidaComponente, Integer> {

    @Query("""
            select c from PartidaComponente c
            join fetch c.partida p join fetch c.detallePedido d join fetch d.articulo join fetch d.pedido
            where p.id in :idsPartida
            order by p.id, c.tipoComponente desc, c.id
            """)
    List<PartidaComponente> deLasPartidas(Collection<Integer> idsPartida);

    @Query("""
            select c from PartidaComponente c
            join fetch c.partida p join fetch c.detallePedido d join fetch d.articulo join fetch d.pedido
            order by p.id, c.tipoComponente desc, c.id
            """)
    List<PartidaComponente> listarCompletos();

    List<PartidaComponente> findByPartidaIdIn(Collection<Integer> idsPartida);
}
