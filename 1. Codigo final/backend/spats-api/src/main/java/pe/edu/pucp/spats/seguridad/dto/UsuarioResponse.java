package pe.edu.pucp.spats.seguridad.dto;

import pe.edu.pucp.spats.seguridad.entity.ModoInteraccion;
import pe.edu.pucp.spats.seguridad.entity.PerfilUsuario;
import pe.edu.pucp.spats.seguridad.entity.Usuario;

public record UsuarioResponse(Integer id, String usuario, String nombre, PerfilUsuario perfil,
                              ModoInteraccion modoInteraccion) {

    public static UsuarioResponse de(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getUsuarioLogin(), u.getNombreCompleto(), u.getRol().perfil(),
                u.getModoInteraccion());
    }
}
