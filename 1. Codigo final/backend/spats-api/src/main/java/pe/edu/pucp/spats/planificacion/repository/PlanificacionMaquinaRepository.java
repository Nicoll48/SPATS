package pe.edu.pucp.spats.planificacion.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import pe.edu.pucp.spats.planificacion.entity.PlanificacionMaquina;

public interface PlanificacionMaquinaRepository extends JpaRepository<PlanificacionMaquina, Integer> {

    @Query("""
            select pm from PlanificacionMaquina pm join fetch pm.maquina m
            where pm.planificacion.id = :idPlanificacion order by m.codigo
            """)
    List<PlanificacionMaquina> deLaPlanificacion(Integer idPlanificacion);

    @org.springframework.data.jpa.repository.Modifying
    @Query("delete from PlanificacionMaquina pm where pm.planificacion.id = :idPlanificacion")
    void eliminarDe(Integer idPlanificacion);
}
