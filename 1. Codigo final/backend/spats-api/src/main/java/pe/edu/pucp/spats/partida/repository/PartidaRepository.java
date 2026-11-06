package pe.edu.pucp.spats.partida.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import pe.edu.pucp.spats.partida.entity.EstadoPartida;
import pe.edu.pucp.spats.partida.entity.Partida;

public interface PartidaRepository extends JpaRepository<Partida, Integer> {

    @EntityGraph(attributePaths = {"pedido", "color"})
    Optional<Partida> findByNumeroPartida(String numeroPartida);

    @EntityGraph(attributePaths = {"pedido", "color"})
    List<Partida> findByNumeroPartidaIn(Collection<String> numeros);

    @EntityGraph(attributePaths = {"pedido", "pedido.cliente", "color"})
    @Query("select p from Partida p order by p.fechaRegistro, p.id")
    List<Partida> listarCompletas();

    @EntityGraph(attributePaths = {"pedido", "color"})
    List<Partida> findByEstadoPartidaOrderByNumeroPartida(EstadoPartida estado);

    List<Partida> findByIdCarga(Integer idCarga);

    /** Barcas usadas por partidas pendientes que no corresponden a ninguna máquina registrada. */
    @Query("""
            select distinct p.barca from Partida p
            where p.estadoPartida = pe.edu.pucp.spats.partida.entity.EstadoPartida.PENDIENTE
              and p.barca is not null and p.barca not in :barcasMaquinas
            """)
    List<String> barcasPendientesSin(Collection<String> barcasMaquinas);
}
