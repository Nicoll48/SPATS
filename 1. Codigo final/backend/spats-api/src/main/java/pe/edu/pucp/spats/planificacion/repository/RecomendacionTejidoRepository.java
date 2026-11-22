package pe.edu.pucp.spats.planificacion.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import pe.edu.pucp.spats.planificacion.entity.RecomendacionTejido;

public interface RecomendacionTejidoRepository extends JpaRepository<RecomendacionTejido, Integer> {

    @Query("""
            select r from RecomendacionTejido r join fetch r.maquina join fetch r.partida pa join fetch pa.color
            join fetch r.detallePedido d join fetch d.pedido join fetch d.articulo
            where r.planificacion.id = :idPlanificacion order by r.orden, r.id
            """)
    List<RecomendacionTejido> deLaPlanificacion(Integer idPlanificacion);

    @Modifying
    @Query("delete from RecomendacionTejido r where r.planificacion.id = :idPlanificacion")
    void eliminarDe(Integer idPlanificacion);

    /** Al cerrar la semana solo se conservan las de la vigente (D-56). */
    @Modifying
    @Query("""
            delete from RecomendacionTejido r where r.planificacion.id in (
                select p.id from PlanificacionSemanal p where p.semana.id = :idSemana and p.id <> :idVigente)
            """)
    void eliminarDeLaSemanaExcepto(Integer idSemana, Integer idVigente);
}
