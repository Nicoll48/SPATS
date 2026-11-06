package pe.edu.pucp.spats.planificacion.motor;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import pe.edu.pucp.spats.comun.error.FalloPlanificacionException;
import pe.edu.pucp.spats.comun.error.ReglaNegocioException;
import spats.algoritmo.PlanificadorSemanal;
import spats.algoritmo.aco.ParametrosACO;
import spats.algoritmo.entrada.DatosSemana;
import spats.algoritmo.salida.AsignacionPartida;
import spats.algoritmo.salida.Indicadores;
import spats.algoritmo.salida.ResultadoPlanificacion;

/**
 * Ejecuta el algoritmo de colonia de hormigas del módulo {@code spats-algoritmo} sin modificarlo. Los
 * parámetros son los calibrados en el R2.2 ({@code new ParametrosACO()}: 20 hormigas, ρ = 0.90, τ0 = 0.50,
 * a = 1, b = 2, 6000 evaluaciones, 60 s, semilla 1) y quedan en el código (sección 8.4 del contexto).
 * Corre fuera de toda transacción: no tiene ninguna conexión abierta mientras busca.
 */
@Component
public class EjecutorAlgoritmo {

    private static final Logger LOG = LoggerFactory.getLogger(EjecutorAlgoritmo.class);

    public ResultadoPlanificacion ejecutar(DatosSemana datos) {
        ParametrosACO parametros = new ParametrosACO();
        try {
            ResultadoPlanificacion r = new PlanificadorSemanal().planificar(datos, parametros);
            LOG.info("Planificación {}: F = {}, {} evaluaciones, {} ms, factible = {}",
                    datos.parametros().getFechaInicioSemana(), r.indicadores().funcionObjetivo(),
                    r.indicadores().evaluaciones(), r.indicadores().tiempoMs(), r.indicadores().factible());
            return r;
        } catch (IllegalArgumentException e) {
            throw new ReglaNegocioException("DATOS_RECHAZADOS_POR_ALGORITMO",
                    "El algoritmo rechazó los datos de la semana: " + e.getMessage());
        } catch (RuntimeException e) {
            throw new FalloPlanificacionException(
                    "El algoritmo falló durante la ejecución; la planificación vigente se conserva sin cambios.", e);
        }
    }

    /** Evalúa una alternativa dada (la vigente) sobre la instancia de {@code datos} (RF55). */
    public Indicadores evaluar(DatosSemana datos, List<AsignacionPartida> alternativa) {
        try {
            return new PlanificadorSemanal().evaluar(datos, alternativa);
        } catch (IllegalArgumentException e) {
            throw new ReglaNegocioException("DATOS_RECHAZADOS_POR_ALGORITMO",
                    "El algoritmo rechazó los datos de la re-planificación: " + e.getMessage());
        }
    }
}
