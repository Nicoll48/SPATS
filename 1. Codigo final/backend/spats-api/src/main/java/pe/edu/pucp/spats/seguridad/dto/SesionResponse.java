package pe.edu.pucp.spats.seguridad.dto;

import java.time.Instant;

public record SesionResponse(String token, Instant expiraEn, UsuarioResponse usuario) { }
