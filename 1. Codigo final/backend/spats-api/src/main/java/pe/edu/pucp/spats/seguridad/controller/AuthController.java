package pe.edu.pucp.spats.seguridad.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.pucp.spats.seguridad.dto.CambiarModoRequest;
import pe.edu.pucp.spats.seguridad.dto.LoginRequest;
import pe.edu.pucp.spats.seguridad.dto.SesionResponse;
import pe.edu.pucp.spats.seguridad.dto.UsuarioResponse;
import pe.edu.pucp.spats.seguridad.service.AutenticacionService;

/**
 * Autenticación (HU-01) y modo de interacción (HU-03). Como la sesión no tiene estado en el servidor,
 * cerrarla (RF03) consiste en que el frontend descarte el token.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AutenticacionService servicio;

    public AuthController(AutenticacionService servicio) { this.servicio = servicio; }

    @PostMapping("/login")
    public SesionResponse iniciarSesion(@Valid @RequestBody LoginRequest solicitud) {
        return servicio.iniciarSesion(solicitud);
    }

    @PostMapping("/renovacion")
    public SesionResponse renovar() {
        return servicio.renovar();
    }

    @GetMapping("/yo")
    public UsuarioResponse yo() {
        return servicio.yo();
    }

    @PutMapping("/yo/modo")
    public UsuarioResponse cambiarModo(@Valid @RequestBody CambiarModoRequest solicitud) {
        return servicio.cambiarModo(solicitud.modoInteraccion());
    }
}
