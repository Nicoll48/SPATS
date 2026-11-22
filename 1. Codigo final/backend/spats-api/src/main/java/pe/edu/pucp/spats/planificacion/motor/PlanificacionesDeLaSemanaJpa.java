package pe.edu.pucp.spats.planificacion.motor;

import org.springframework.stereotype.Component;

import pe.edu.pucp.spats.comun.error.ConflictoException;
import pe.edu.pucp.spats.planificacion.entity.Enumeraciones.EstadoPlanificacion;
import pe.edu.pucp.spats.planificacion.repository.PlanificacionSemanalRepository;
import pe.edu.pucp.spats.tejeduria.service.PlanificacionesDeLaSemana;

/** Lo que la proyección consulta de la planificación de su semana (D-52). */
@Component
public class PlanificacionesDeLaSemanaJpa implements PlanificacionesDeLaSemana {

    private final PlanificacionSemanalRepository planificaciones;

    public PlanificacionesDeLaSemanaJpa(PlanificacionSemanalRepository planificaciones) {
        this.planificaciones = planificaciones;
    }

    @Override
    public boolean tieneVigente(Integer idSemana) {
        return planificaciones.findBySemanaIdAndEsVigenteTrue(idSemana).isPresent();
    }

    @Override
    public void exigirSemanaAbierta(Integer idSemana) {
        planificaciones.findBySemanaIdAndEsVigenteTrue(idSemana).filter(p -> p.getEstado() == EstadoPlanificacion.CERRADA)
                .ifPresent(p -> {
                    throw new ConflictoException("SEMANA_CERRADA", "La planificación de esta semana ya se cerró; "
                            + "sus versiones de la proyección quedan en solo lectura.");
                });
    }
}
