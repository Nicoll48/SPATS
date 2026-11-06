package pe.edu.pucp.spats.incidencia.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.pucp.spats.incidencia.entity.Incidencia;

public interface IncidenciaRepository extends JpaRepository<Incidencia, Integer> {

    boolean existsByPlanificacionOrigenIn(java.util.Collection<pe.edu.pucp.spats.planificacion.entity.PlanificacionSemanal> planificaciones);

    @Query("""
            select i from Incidencia i join fetch i.usuario join fetch i.planificacionOrigen po
            left join fetch i.pedido left join fetch i.partida left join fetch i.maquina
            where po.semana.id = :idSemana
            order by i.fechaHoraOcurrencia desc, i.id desc
            """)
    List<Incidencia> deLaSemana(Integer idSemana);
}
