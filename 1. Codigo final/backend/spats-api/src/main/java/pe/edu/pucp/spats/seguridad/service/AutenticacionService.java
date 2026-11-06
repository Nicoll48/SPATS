package pe.edu.pucp.spats.seguridad.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.pucp.spats.auditoria.entity.TipoOperacion;
import pe.edu.pucp.spats.auditoria.service.BitacoraService;
import pe.edu.pucp.spats.comun.error.CredencialesInvalidasException;
import pe.edu.pucp.spats.comun.error.ReglaNegocioException;
import pe.edu.pucp.spats.seguridad.dto.LoginRequest;
import pe.edu.pucp.spats.seguridad.dto.SesionResponse;
import pe.edu.pucp.spats.seguridad.dto.UsuarioResponse;
import pe.edu.pucp.spats.seguridad.entity.ModoInteraccion;
import pe.edu.pucp.spats.seguridad.entity.PerfilUsuario;
import pe.edu.pucp.spats.seguridad.entity.Usuario;
import pe.edu.pucp.spats.seguridad.repository.UsuarioRepository;

/** Inicio de sesión (RF01, RF02), renovación por actividad (RF04) y modo de interacción (RF07). */
@Service
@Transactional(readOnly = true)
public class AutenticacionService {

    static final String CREDENCIALES_INCORRECTAS = "Usuario o contraseña incorrectos. Verifica los datos e inténtalo de nuevo.";

    private final UsuarioRepository usuarios;
    private final PasswordEncoder codificador;
    private final EmisorToken emisor;
    private final UsuarioActual usuarioActual;
    private final BitacoraService bitacora;

    public AutenticacionService(UsuarioRepository usuarios, PasswordEncoder codificador, EmisorToken emisor,
                                UsuarioActual usuarioActual, BitacoraService bitacora) {
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.emisor = emisor;
        this.usuarioActual = usuarioActual;
        this.bitacora = bitacora;
    }

    @Transactional
    public SesionResponse iniciarSesion(LoginRequest solicitud) {
        Usuario usuario = usuarios.findByUsuarioLoginIgnoreCase(solicitud.usuario().trim())
                .filter(u -> codificador.matches(solicitud.contrasena(), u.getContrasena()))
                .orElseThrow(() -> new CredencialesInvalidasException(CREDENCIALES_INCORRECTAS));
        if (!usuario.activo())
            throw new CredencialesInvalidasException("La cuenta está inactiva. Consulta con el jefe de planeamiento.");
        bitacora.registrarComo(usuario.getId(), TipoOperacion.INICIAR_SESION, "usuario", usuario.getId(),
                null, null, null);
        return sesion(usuario);
    }

    /** Emite un token nuevo mientras el usuario tenga actividad; sin actividad, el token vence (RF04). */
    public SesionResponse renovar() {
        return sesion(usuarioAutenticado());
    }

    public UsuarioResponse yo() {
        return UsuarioResponse.de(usuarioAutenticado());
    }

    @Transactional
    public UsuarioResponse cambiarModo(ModoInteraccion modo) {
        Usuario usuario = usuarioAutenticado();
        if (usuario.getRol().perfil() != PerfilUsuario.PLANIFICADOR)
            throw new ReglaNegocioException("SOLO_PLANIFICADOR", "Solo el Planificador alterna entre los modos Guiado y Experto.");
        bitacora.registrarCambio("usuario", usuario.getId(), "modo_interaccion", usuario.getModoInteraccion(), modo);
        usuario.cambiarModo(modo);
        return UsuarioResponse.de(usuario);
    }

    private Usuario usuarioAutenticado() {
        return usuarios.findWithRolById(usuarioActual.id())
                .filter(Usuario::activo)
                .orElseThrow(() -> new CredencialesInvalidasException("La cuenta ya no está activa."));
    }

    private SesionResponse sesion(Usuario usuario) {
        EmisorToken.TokenEmitido token = emisor.emitir(usuario);
        return new SesionResponse(token.valor(), token.expiraEn(), UsuarioResponse.de(usuario));
    }
}
