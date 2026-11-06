package pe.edu.pucp.spats.planificacion.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import pe.edu.pucp.spats.comun.error.ConflictoException;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.GeneracionResponse;
import pe.edu.pucp.spats.planificacion.entity.PlanificacionSemanal;
import pe.edu.pucp.spats.planificacion.mapper.PlanificacionMapper;
import pe.edu.pucp.spats.planificacion.motor.EjecutorAlgoritmo;
import pe.edu.pucp.spats.planificacion.motor.EntradaPlanificacion;
import pe.edu.pucp.spats.planificacion.motor.GuardadoPlanificacion;
import pe.edu.pucp.spats.planificacion.motor.LectorDatosPlanificacion;
import pe.edu.pucp.spats.planificacion.motor.ResultadoTraducido;
import pe.edu.pucp.spats.planificacion.motor.TraductorResultado;
import spats.algoritmo.salida.ResultadoPlanificacion;

/**
 * Componente "Planificación semanal" del C4: coordina la generación sin implementar la búsqueda (HU-11).
 *
 * <ol>
 *   <li>Leer: transacción de solo lectura que arma la entrada del algoritmo.</li>
 *   <li>Optimizar: el algoritmo corre sin transacción ni conexión abierta.</li>
 *   <li>Traducir y guardar: una sola transacción, que antes verifica que los datos no cambiaron.</li>
 * </ol>
 * <p>La solicitud espera el resultado (el algoritmo tiene un tope de 60 s, RNF03). No se pueden ejecutar
 * dos generaciones de la misma semana a la vez.</p>
 */
@Service
public class GeneracionPlanificacionService {

    private final LectorDatosPlanificacion lector;
    private final EjecutorAlgoritmo ejecutor;
    private final GuardadoPlanificacion guardado;
    private final PlanificacionMapper mapper;
    private final Set<LocalDate> enCurso = ConcurrentHashMap.newKeySet();

    public GeneracionPlanificacionService(LectorDatosPlanificacion lector, EjecutorAlgoritmo ejecutor,
                                          GuardadoPlanificacion guardado, PlanificacionMapper mapper) {
        this.lector = lector;
        this.ejecutor = ejecutor;
        this.guardado = guardado;
        this.mapper = mapper;
    }

    @PreAuthorize("hasRole('PLANIFICADOR')")
    public GeneracionResponse generar(LocalDate lunes) {
        if (!enCurso.add(lunes))
            throw new ConflictoException("PLANIFICACION_EN_CURSO", "Ya se está generando la planificación de esta semana.");
        try {
            EntradaPlanificacion entrada = lector.leer(lunes);
            ResultadoPlanificacion resultado = ejecutor.ejecutar(entrada.datos());
            ResultadoTraducido traducido = TraductorResultado.traducir(entrada.datos(), resultado);
            PlanificacionSemanal plan = guardado.guardar(entrada, traducido);
            List<String> advertencias = new ArrayList<>(entrada.advertencias());
            advertencias.addAll(traducido.advertencias());
            return new GeneracionResponse(plan.getId(), plan.getNumeroVersion(), mapper.indicadores(plan),
                    traducido.indicadores().detenidaPorTiempo(), List.copyOf(advertencias));
        } finally {
            enCurso.remove(lunes);
        }
    }
}
