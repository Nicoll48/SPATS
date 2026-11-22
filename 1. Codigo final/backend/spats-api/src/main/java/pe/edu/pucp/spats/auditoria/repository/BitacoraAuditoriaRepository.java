package pe.edu.pucp.spats.auditoria.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

import pe.edu.pucp.spats.auditoria.entity.BitacoraAuditoria;

public interface BitacoraAuditoriaRepository extends JpaRepository<BitacoraAuditoria, Integer> {

    @EntityGraph(attributePaths = "usuario")
    Page<BitacoraAuditoria> findAllByOrderByFechaHoraDescIdDesc(Pageable pagina);

    @EntityGraph(attributePaths = "usuario")
    Page<BitacoraAuditoria> findByEntidadAfectadaOrderByFechaHoraDescIdDesc(String entidad, Pageable pagina);

    /** Valores anteriores de un campo de una entidad cuando pasó a un valor dado, del más reciente al más antiguo. */
    @Query("""
            select b.valorAnterior from BitacoraAuditoria b
            where b.entidadAfectada = :entidad and b.idEntidad = :id and b.campoModificado = :campo and b.valorNuevo = :nuevo
            order by b.fechaHora desc, b.id desc
            """)
    List<String> valoresAnteriores(@Param("entidad") String entidad, @Param("id") Integer id, @Param("campo") String campo,
                                   @Param("nuevo") String nuevo);

    /** Códigos de las máquinas cuyas tonalidades aceptadas cambiaron después de un momento (D-51). */
    @Query("""
            select m.codigo from MaquinaTintoreria m where m.id in (
                select b.idEntidad from BitacoraAuditoria b
                where b.entidadAfectada = 'maquina_tintoreria' and b.campoModificado = 'tonalidades_aceptadas'
                  and b.fechaHora > :desde)
            order by m.codigo
            """)
    List<String> maquinasConTonalidadesCambiadasDesde(@Param("desde") LocalDateTime desde);
}
