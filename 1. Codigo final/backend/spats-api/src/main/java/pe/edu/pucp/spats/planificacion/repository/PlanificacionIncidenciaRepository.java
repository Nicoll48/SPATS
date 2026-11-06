package pe.edu.pucp.spats.planificacion.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import pe.edu.pucp.spats.incidencia.entity.Incidencia;
import pe.edu.pucp.spats.planificacion.entity.PlanificacionIncidencia;

public interface PlanificacionIncidenciaRepository extends JpaRepository<PlanificacionIncidencia, Integer> {

    /** Incidencias consideradas en una versión, con su entidad afectada. */
    @Query("""
            select i from PlanificacionIncidencia pi join pi.incidencia i
            left join fetch i.pedido left join fetch i.partida left join fetch i.maquina
            where pi.planificacion.id = :idPlanificacion order by i.id
            """)
    List<Incidencia> incidenciasDe(Integer idPlanificacion);

    @Modifying
    @Query("delete from PlanificacionIncidencia pi where pi.planificacion.id = :idPlanificacion")
    void eliminarDe(Integer idPlanificacion);
}
