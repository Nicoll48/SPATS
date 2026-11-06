package pe.edu.pucp.spats.comun.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj del negocio (zona America/Lima). Las fechas y horas de registro se toman de aquí.
 * {@code spats.reloj.inicio} (fecha y hora local, solo para pruebas) adelanta o atrasa el reloj para que empiece
 * en ese instante y siga avanzando; vacío en uso normal.
 */
@Configuration
public class RelojConfig {

    @Bean
    Clock reloj(@Value("${spats.zona-horaria:America/Lima}") String zona, @Value("${spats.reloj.inicio:}") String inicio) {
        Clock sistema = Clock.system(ZoneId.of(zona));
        if (inicio.isBlank()) return sistema;
        java.time.Instant objetivo = java.time.LocalDateTime.parse(inicio).atZone(sistema.getZone()).toInstant();
        return Clock.offset(sistema, java.time.Duration.between(sistema.instant(), objetivo));
    }
}
