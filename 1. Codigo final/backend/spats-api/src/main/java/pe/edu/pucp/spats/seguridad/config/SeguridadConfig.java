package pe.edu.pucp.spats.seguridad.config;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * Seguridad de la API (Spring Security): autenticación sin estado con token Bearer (JWT HMAC-SHA256)
 * y dos perfiles. Cualquier usuario autenticado puede leer ({@code GET}); toda operación que crea,
 * modifica o altera la planificación exige el perfil Planificador (RF05, RF06).
 */
@Configuration
@EnableMethodSecurity
public class SeguridadConfig {

    static final String CLAIM_PERFIL = "perfil";

    @Bean
    SecurityFilterChain cadenaSeguridad(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())   // token Bearer sin cookies: CSRF no aplica
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                    .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                    .requestMatchers("/actuator/health").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/**").authenticated()
                    .requestMatchers("/api/auth/**").authenticated()
                    .requestMatchers("/api/**").hasRole("PLANIFICADOR")
                    .anyRequest().denyAll())
            .oauth2ResourceServer(o -> o
                    .jwt(j -> j.jwtAuthenticationConverter(conversorAutenticacion()))
                    .authenticationEntryPoint(sinAutenticar())
                    .accessDeniedHandler(sinPermiso()))
            .exceptionHandling(e -> e.authenticationEntryPoint(sinAutenticar()).accessDeniedHandler(sinPermiso()));
        return http.build();
    }

    @Bean
    SecretKey claveJwt(PropiedadesSeguridad propiedades) {
        return new SecretKeySpec(propiedades.jwtSecreto().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey claveJwt) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(claveJwt));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey claveJwt) {
        return NimbusJwtDecoder.withSecretKey(claveJwt).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    PasswordEncoder codificadorContrasenas() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(PropiedadesSeguridad propiedades) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(propiedades.origenesPermitidos());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setExposedHeaders(List.of("Content-Disposition"));
        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/api/**", cors);
        return fuente;
    }

    private static JwtAuthenticationConverter conversorAutenticacion() {
        JwtGrantedAuthoritiesConverter perfiles = new JwtGrantedAuthoritiesConverter();
        perfiles.setAuthoritiesClaimName(CLAIM_PERFIL);
        perfiles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(perfiles);
        return conversor;
    }

    private static AuthenticationEntryPoint sinAutenticar() {
        return (request, response, ex) -> escribirProblema(response, 401, "Sesión no válida",
                "Tu sesión no es válida o expiró. Inicia sesión nuevamente.", "SESION_INVALIDA");
    }

    private static AccessDeniedHandler sinPermiso() {
        return (request, response, ex) -> escribirProblema(response, 403, "Sin permiso",
                "Tu perfil es Consulta: puedes ver la información, pero no ejecutar operaciones que modifiquen datos ni la planificación.",
                "SIN_PERMISO");
    }

    private static void escribirProblema(jakarta.servlet.http.HttpServletResponse response, int estado,
                                         String titulo, String detalle, String codigo) throws java.io.IOException {
        response.setStatus(estado);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"title\":\"" + titulo + "\",\"status\":" + estado + ",\"detail\":\"" + detalle
                + "\",\"codigo\":\"" + codigo + "\"}");
    }
}
