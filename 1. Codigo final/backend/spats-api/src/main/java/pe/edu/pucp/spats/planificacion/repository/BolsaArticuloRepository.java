package pe.edu.pucp.spats.planificacion.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import pe.edu.pucp.spats.planificacion.entity.BolsaArticulo;

public interface BolsaArticuloRepository extends JpaRepository<BolsaArticulo, Integer> {

    boolean existsByPlanificacionIdAndKgSaldoFinalGreaterThan(Integer idPlanificacion, java.math.BigDecimal saldo);

    @Query("""
            select b from BolsaArticulo b join fetch b.articulo a
            where b.planificacion.id = :idPlanificacion order by a.codigo, b.fecha
            """)
    List<BolsaArticulo> deLaPlanificacion(Integer idPlanificacion);

    @Query("""
            select b from BolsaArticulo b join fetch b.articulo a
            where b.planificacion.id = :idPlanificacion and b.fecha = :fecha order by a.codigo
            """)
    List<BolsaArticulo> deLaPlanificacionEn(Integer idPlanificacion, LocalDate fecha);

    @org.springframework.data.jpa.repository.Modifying
    @Query("delete from BolsaArticulo b where b.planificacion.id = :idPlanificacion")
    void eliminarDe(Integer idPlanificacion);
}
