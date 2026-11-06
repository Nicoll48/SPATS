package pe.edu.pucp.spats.seguridad.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import pe.edu.pucp.spats.seguridad.config.PropiedadesSeguridad;
import pe.edu.pucp.spats.seguridad.entity.Usuario;

/** Emite el token de sesión. Expira tras el periodo de inactividad configurado (RF04). */
@Component
public class EmisorToken {

    private final JwtEncoder codificador;
    private final PropiedadesSeguridad propiedades;
    private final Clock reloj;

    public EmisorToken(JwtEncoder codificador, PropiedadesSeguridad propiedades, Clock reloj) {
        this.codificador = codificador;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    public TokenEmitido emitir(Usuario usuario) {
        Instant ahora = reloj.instant();
        Instant expira = ahora.plus(propiedades.duracionInactividad());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("spats")
                .subject(String.valueOf(usuario.getId()))
                .issuedAt(ahora)
                .expiresAt(expira)
                .claim("perfil", usuario.getRol().perfil().name())
                .claim("usuario", usuario.getUsuarioLogin())
                .build();
        String token = codificador.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new TokenEmitido(token, expira);
    }

    public record TokenEmitido(String valor, Instant expiraEn) { }
}
