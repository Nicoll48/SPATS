package pe.edu.pucp.spats.maquina.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.pucp.spats.maquina.entity.MaquinaTintoreria;

public interface MaquinaRepository extends JpaRepository<MaquinaTintoreria, Integer> {

    Optional<MaquinaTintoreria> findByCodigo(String codigo);

    List<MaquinaTintoreria> findAllByOrderByCodigoAsc();

    boolean existsByCodigoIgnoreCase(String codigo);
}
