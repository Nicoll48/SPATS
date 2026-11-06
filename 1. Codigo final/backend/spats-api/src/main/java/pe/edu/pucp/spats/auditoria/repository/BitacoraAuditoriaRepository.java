package pe.edu.pucp.spats.auditoria.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.pucp.spats.auditoria.entity.BitacoraAuditoria;

public interface BitacoraAuditoriaRepository extends JpaRepository<BitacoraAuditoria, Integer> {

    @EntityGraph(attributePaths = "usuario")
    Page<BitacoraAuditoria> findAllByOrderByFechaHoraDescIdDesc(Pageable pagina);

    @EntityGraph(attributePaths = "usuario")
    Page<BitacoraAuditoria> findByEntidadAfectadaOrderByFechaHoraDescIdDesc(String entidad, Pageable pagina);
}
