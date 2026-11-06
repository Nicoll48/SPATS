package pe.edu.pucp.spats.seguridad.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.pucp.spats.seguridad.entity.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

    @EntityGraph(attributePaths = "rol")
    Optional<Usuario> findByUsuarioLoginIgnoreCase(String usuarioLogin);

    @EntityGraph(attributePaths = "rol")
    Optional<Usuario> findWithRolById(Integer id);
}
