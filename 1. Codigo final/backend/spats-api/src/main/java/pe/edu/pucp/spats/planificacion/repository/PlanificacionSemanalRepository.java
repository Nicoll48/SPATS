package pe.edu.pucp.spats.planificacion.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import pe.edu.pucp.spats.planificacion.entity.PlanificacionSemanal;

public interface PlanificacionSemanalRepository extends JpaRepository<PlanificacionSemanal, Integer> {

    @EntityGraph(attributePaths = {"semana", "proyeccion"})
    Optional<PlanificacionSemanal> findBySemanaIdAndEsVigenteTrue(Integer idSemana);

    @EntityGraph(attributePaths = {"semana", "proyeccion"})
    Optional<PlanificacionSemanal> findWithSemanaById(Integer id);

    Optional<PlanificacionSemanal> findBySemanaFechaInicioAndEsVigenteTrue(LocalDate lunes);

    /** Re-planificaciones propuestas de una semana (a lo más una pendiente; las de días anteriores vencieron). */
    @EntityGraph(attributePaths = {"semana", "proyeccion"})
    @Query("""
            select p from PlanificacionSemanal p
            where p.semana.id = :idSemana
              and p.estado = pe.edu.pucp.spats.planificacion.entity.Enumeraciones.EstadoPlanificacion.PROPUESTA
            order by p.numeroVersion
            """)
    List<PlanificacionSemanal> propuestasDe(Integer idSemana);

    @EntityGraph(attributePaths = {"semana", "proyeccion"})
    List<PlanificacionSemanal> findBySemanaIdOrderByNumeroVersionDesc(Integer idSemana);

    @EntityGraph(attributePaths = {"semana", "proyeccion"})
    List<PlanificacionSemanal> findByProyeccionIdOrderByNumeroVersionAsc(Integer idProyeccion);

    /** Planificaciones de la semana en un estado (por ejemplo, las CANDIDATA de D-52), de la más nueva a la más antigua. */
    @EntityGraph(attributePaths = {"semana", "proyeccion"})
    List<PlanificacionSemanal> findBySemanaIdAndEstadoOrderByNumeroVersionDesc(Integer idSemana,
            pe.edu.pucp.spats.planificacion.entity.Enumeraciones.EstadoPlanificacion estado);

    /** Candidatas generadas con una versión de la proyección (a lo más una vigente por versión, D-52). */
    @EntityGraph(attributePaths = {"semana", "proyeccion"})
    @Query("""
            select p from PlanificacionSemanal p
            where p.proyeccion.id = :idProyeccion
              and p.estado = pe.edu.pucp.spats.planificacion.entity.Enumeraciones.EstadoPlanificacion.CANDIDATA
            order by p.numeroVersion desc
            """)
    List<PlanificacionSemanal> candidatasDe(Integer idProyeccion);

    /** Candidatas de la semana que empieza el lunes indicado (D-19 las marca como desactualizadas). */
    @Query("""
            select p from PlanificacionSemanal p
            where p.semana.fechaInicio = :lunes
              and p.estado = pe.edu.pucp.spats.planificacion.entity.Enumeraciones.EstadoPlanificacion.CANDIDATA
            """)
    List<PlanificacionSemanal> candidatasDeLaSemana(LocalDate lunes);

    /** Candidatas de semanas abiertas en las que la partida quedó Asignada o Sobrante (D-24, D-52). */
    @Query("""
            select p from PlanificacionSemanal p join fetch p.semana
            where p.estado = pe.edu.pucp.spats.planificacion.entity.Enumeraciones.EstadoPlanificacion.CANDIDATA
              and exists (select 1 from PlanificacionPartida pp
                          where pp.planificacion = p and pp.partida.id = :idPartida
                            and pp.clasificacionResultado in (
                                pe.edu.pucp.spats.planificacion.entity.Enumeraciones.ClasificacionResultado.ASIGNADA,
                                pe.edu.pucp.spats.planificacion.entity.Enumeraciones.ClasificacionResultado.SOBRANTE))
              and not exists (select 1 from PlanificacionSemanal v
                              where v.semana = p.semana and v.esVigente = true
                                and v.estado = pe.edu.pucp.spats.planificacion.entity.Enumeraciones.EstadoPlanificacion.CERRADA)
            """)
    List<PlanificacionSemanal> candidatasConPartidaUbicada(Integer idPartida);

    @Query("select coalesce(max(p.numeroVersion), 0) from PlanificacionSemanal p where p.semana.id = :idSemana")
    int ultimaVersion(Integer idSemana);

    /** Semanas posteriores con planificación vigente: tomaron como stock inicial el saldo de las anteriores. */
    @Query("""
            select p.semana.fechaInicio from PlanificacionSemanal p
            where p.esVigente = true and p.semana.fechaInicio > :lunes
            order by p.semana.fechaInicio
            """)
    List<LocalDate> semanasPosterioresConVigente(LocalDate lunes);

    /** Vigentes abiertas en las que la partida quedó Asignada o Sobrante (D-24). */
    @Query("""
            select p from PlanificacionSemanal p join fetch p.semana
            where p.esVigente = true
              and p.estado = pe.edu.pucp.spats.planificacion.entity.Enumeraciones.EstadoPlanificacion.ABIERTA
              and exists (select 1 from PlanificacionPartida pp
                          where pp.planificacion = p and pp.partida.id = :idPartida
                            and pp.clasificacionResultado in (
                                pe.edu.pucp.spats.planificacion.entity.Enumeraciones.ClasificacionResultado.ASIGNADA,
                                pe.edu.pucp.spats.planificacion.entity.Enumeraciones.ClasificacionResultado.SOBRANTE))
            order by p.semana.fechaInicio
            """)
    List<PlanificacionSemanal> vigentesAbiertasConPartidaUbicada(Integer idPartida);
}
