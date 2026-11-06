package pe.edu.pucp.spats.carga.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.pucp.spats.carga.entity.CargaDatos;

public interface CargaDatosRepository extends JpaRepository<CargaDatos, Integer> {

    @EntityGraph(attributePaths = "usuario")
    List<CargaDatos> findTop100ByOrderByFechaHoraDescIdDesc();
}
