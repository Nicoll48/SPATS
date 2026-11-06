package pe.edu.pucp.spats.planificacion.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import pe.edu.pucp.spats.partida.entity.Partida;

/** Lectura de las partidas candidatas de una semana (sección 9.2 del contexto). */
public interface CandidatasRepository extends Repository<Partida, Integer> {

    /**
     * Partidas PENDIENTE que no figuran como ASIGNADA ni SOBRANTE en la versión vigente abierta de otra
     * semana: su tela ya se consumió en esa versión. Las ARRASTRADA quedan fuera (RN-18). Van ordenadas por
     * orden de registro ({@code fecha_registro}, {@code id_partida}).
     */
    @Query("""
            select p from Partida p join fetch p.pedido pe join fetch pe.cliente join fetch p.color
            where p.estadoPartida = pe.edu.pucp.spats.partida.entity.EstadoPartida.PENDIENTE
              and p.id not in (
                  select pp.partida.id from PlanificacionPartida pp
                  where pp.clasificacionResultado <> pe.edu.pucp.spats.planificacion.entity.Enumeraciones.ClasificacionResultado.BLOQUEADA
                    and pp.planificacion.esVigente = true
                    and pp.planificacion.estado = pe.edu.pucp.spats.planificacion.entity.Enumeraciones.EstadoPlanificacion.ABIERTA
                    and pp.planificacion.semana.id <> :idSemana)
            order by p.fechaRegistro, p.id
            """)
    List<Partida> candidatas(Integer idSemana);
}
