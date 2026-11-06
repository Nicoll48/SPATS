package pe.edu.pucp.spats.seguridad.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import pe.edu.pucp.spats.comun.error.ReglaNegocioException;

/** Identifica al usuario autenticado a partir del token (el {@code sub} es su {@code id_usuario}). */
@Component
public class UsuarioActual {

    public Integer id() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion != null && autenticacion.getPrincipal() instanceof Jwt jwt)
            return Integer.valueOf(jwt.getSubject());
        throw new ReglaNegocioException("SIN_SESION", "No hay un usuario autenticado.");
    }
}
