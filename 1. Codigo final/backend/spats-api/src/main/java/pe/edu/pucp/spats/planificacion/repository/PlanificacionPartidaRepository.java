package pe.edu.pucp.spats.planificacion.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import pe.edu.pucp.spats.planificacion.entity.PlanificacionPartida;

public interface PlanificacionPartidaRepository extends JpaRepository<PlanificacionPartida, Integer> {

    @Query("""
            select pp from PlanificacionPartida pp
            join fetch pp.partida p join fetch p.pedido join fetch p.color left join fetch pp.maquina m
            where pp.planificacion.id = :idPlanificacion
            order by m.codigo nulls last, pp.diaAbastecimiento, pp.ordenSecuencia, p.numeroPartida
            """)
    List<PlanificacionPartida> deLaPlanificacion(Integer idPlanificacion);

    /** Resultados de cierre ARRASTRADA de las partidas que siguen arrastradas, del cierre más reciente al más antiguo. */
    @Query("""
            select pp from PlanificacionPartida pp
            join fetch pp.planificacion pl join fetch pl.semana
            join fetch pp.partida p join fetch p.pedido join fetch p.color left join fetch pp.maquina
            where pp.estadoConfirmacionCierre = pe.edu.pucp.spats.planificacion.entity.Enumeraciones.EstadoConfirmacionCierre.ARRASTRADA
              and p.estadoPartida = pe.edu.pucp.spats.partida.entity.EstadoPartida.ARRASTRADA
            order by pl.fechaCierre desc, p.numeroPartida
            """)
    List<PlanificacionPartida> arrastradasPendientes();

    @org.springframework.data.jpa.repository.Modifying
    @Query("delete from PlanificacionPartida pp where pp.planificacion.id = :idPlanificacion")
    void eliminarDe(Integer idPlanificacion);

    interface ConteoClasificacion {
        Integer getIdPlanificacion();
        pe.edu.pucp.spats.planificacion.entity.Enumeraciones.ClasificacionResultado getClasificacion();
        Long getCantidad();
    }

    @Query("""
            select pp.planificacion.id as idPlanificacion, pp.clasificacionResultado as clasificacion, count(pp) as cantidad
            from PlanificacionPartida pp where pp.planificacion.id in :ids
            group by pp.planificacion.id, pp.clasificacionResultado
            """)
    List<ConteoClasificacion> contarPorClasificacion(List<Integer> ids);
}
