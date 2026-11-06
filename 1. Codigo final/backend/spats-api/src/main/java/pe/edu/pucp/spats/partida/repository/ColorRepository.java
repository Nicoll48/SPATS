package pe.edu.pucp.spats.partida.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import pe.edu.pucp.spats.partida.entity.Color;

public interface ColorRepository extends JpaRepository<Color, Integer> {

    Optional<Color> findByCodigoColor(String codigoColor);

    List<Color> findByCodigoColorIn(Collection<String> codigos);

    List<Color> findAllByOrderByCodigoColorAsc();

    /** Quita los colores que ya ninguna partida usa. Devuelve cuántos. */
    @Modifying(flushAutomatically = true)
    @Query(value = "delete from color c where not exists (select 1 from partida p where p.id_color = c.id_color)", nativeQuery = true)
    int eliminarSinUso();
}
