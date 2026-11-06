package pe.edu.pucp.spats.seguridad.config;

import java.time.Duration;
import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración de seguridad.
 *
 * @param jwtSecreto         clave HMAC para firmar los tokens (al menos 32 caracteres; en producción, por la
 *                           variable de entorno {@code SPATS_JWT_SECRETO})
 * @param duracionInactividad vida del token: si el usuario no tiene actividad durante este periodo, la
 *                           sesión se cierra (RF04). El frontend renueva el token mientras hay actividad.
 * @param origenesPermitidos orígenes del frontend autorizados por CORS
 */
@Validated
@ConfigurationProperties(prefix = "spats.seguridad")
public record PropiedadesSeguridad(
        @NotNull @Size(min = 32) String jwtSecreto,
        @NotNull Duration duracionInactividad,
        @NotEmpty List<String> origenesPermitidos) { }
