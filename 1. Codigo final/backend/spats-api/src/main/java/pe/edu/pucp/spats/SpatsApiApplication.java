package pe.edu.pucp.spats;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * API de SPATS (Sistema de Planificación de Abastecimiento de Tintorería).
 * Coordina la asignación de partidas a máquinas de tintorería con el algoritmo de colonia de hormigas
 * del módulo {@code spats-algoritmo}, que se usa sin Spring como dependencia.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SpatsApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpatsApiApplication.class, args);
    }
}
