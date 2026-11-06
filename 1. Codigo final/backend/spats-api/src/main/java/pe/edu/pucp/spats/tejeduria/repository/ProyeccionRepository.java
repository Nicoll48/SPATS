package pe.edu.pucp.spats.tejeduria.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import pe.edu.pucp.spats.tejeduria.entity.EstadoProyeccion;
import pe.edu.pucp.spats.tejeduria.entity.ProyeccionTejeduria;

public interface ProyeccionRepository extends JpaRepository<ProyeccionTejeduria, Integer> {

    /** La de mayor número, aunque esté eliminada: define el número de la versión siguiente. */
    Optional<ProyeccionTejeduria> findFirstBySemanaIdOrderByVersionDesc(Integer idSemana);

    /** La versión no eliminada con mayor número es la que se usa para planificar (D-06, D-46). */
    Optional<ProyeccionTejeduria> findFirstBySemanaIdAndEstadoNotOrderByVersionDesc(Integer idSemana, EstadoProyeccion excluido);

    List<ProyeccionTejeduria> findBySemanaIdOrderByVersionDesc(Integer idSemana);

    List<ProyeccionTejeduria> findByIdCarga(Integer idCarga);

    @Query("select p.idCarga from ProyeccionTejeduria p where p.idCarga is not null and p.estado = pe.edu.pucp.spats.tejeduria.entity.EstadoProyeccion.ELIMINADA")
    Set<Integer> cargasEliminadas();
}
