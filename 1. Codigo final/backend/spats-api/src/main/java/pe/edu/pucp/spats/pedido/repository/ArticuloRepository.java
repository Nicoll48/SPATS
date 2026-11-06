package pe.edu.pucp.spats.pedido.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import pe.edu.pucp.spats.pedido.entity.Articulo;

public interface ArticuloRepository extends JpaRepository<Articulo, Integer> {

    Optional<Articulo> findByCodigo(String codigo);

    List<Articulo> findByCodigoIn(Collection<String> codigos);

    List<Articulo> findAllByOrderByCodigoAsc();

    /** Quita los artículos que ya ningún pedido ni bolsa usa. Devuelve cuántos. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            delete from articulo a
            where not exists (select 1 from detalle_pedido d where d.id_articulo = a.id_articulo)
              and not exists (select 1 from bolsa_articulo b where b.id_articulo = a.id_articulo)
            """, nativeQuery = true)
    int eliminarSinUso();
}
