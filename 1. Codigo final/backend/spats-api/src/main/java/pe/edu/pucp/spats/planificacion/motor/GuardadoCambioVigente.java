package pe.edu.pucp.spats.planificacion.motor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.pucp.spats.auditoria.entity.TipoOperacion;
import pe.edu.pucp.spats.auditoria.service.BitacoraService;
import pe.edu.pucp.spats.comun.error.ConflictoException;
import pe.edu.pucp.spats.comun.error.RecursoNoEncontradoException;
import pe.edu.pucp.spats.planificacion.entity.Enumeraciones.EstadoPlanificacion;
import pe.edu.pucp.spats.planificacion.entity.Enumeraciones.TipoGeneracion;
import pe.edu.pucp.spats.planificacion.entity.PlanificacionSemanal;
import pe.edu.pucp.spats.planificacion.repository.PlanificacionSemanalRepository;
import pe.edu.pucp.spats.tejeduria.service.ProyeccionService;

/**
 * «Usar como vigente» sin re-planificar (D-52): la semana todavía no empezó, o no tiene vigente. Todo va en una sola
 * transacción: la candidata pasa a vigente y abierta, y la vigente anterior vuelve a ser candidata de su versión
 * (se puede volver a ella). Si la anterior era una re-planificación, queda REEMPLAZADA.
 */
@Component
public class GuardadoCambioVigente {

    private final PlanificacionSemanalRepository planificaciones;
    private final PropuestasPendientes propuestas;
    private final ReglasVigencia reglasVigencia;
    private final ProyeccionService proyecciones;
    private final BitacoraService bitacora;

    public GuardadoCambioVigente(PlanificacionSemanalRepository planificaciones, PropuestasPendientes propuestas,
                                 ReglasVigencia reglasVigencia, ProyeccionService proyecciones, BitacoraService bitacora) {
        this.planificaciones = planificaciones;
        this.propuestas = propuestas;
        this.reglasVigencia = reglasVigencia;
        this.proyecciones = proyecciones;
        this.bitacora = bitacora;
    }

    @Transactional
    public PlanificacionSemanal usarDirecto(Integer idCandidata) {
        PlanificacionSemanal c = candidata(idCandidata);
        Integer idSemana = c.getSemana().getId();
        propuestas.eliminarVencidas(idSemana);
        propuestas.exigirSinPendiente(idSemana, "usar otra versión como vigente");
        PlanificacionSemanal anterior = planificaciones.findBySemanaIdAndEsVigenteTrue(idSemana).orElse(null);
        if (anterior != null) {
            if (!anterior.abierta())
                throw new ConflictoException("SEMANA_CERRADA", "La planificación de esta semana ya se cerró; sus versiones quedan en solo lectura.");
            if (anterior.getTipoGeneracion() == TipoGeneracion.INICIAL) anterior.volverACandidata();
            else anterior.quitarVigencia(EstadoPlanificacion.REEMPLAZADA);
            bitacora.registrarCambio("planificacion_semanal", anterior.getId(), "estado", EstadoPlanificacion.ABIERTA, anterior.getEstado());
            planificaciones.flush();   // libera uq_vigente_por_semana antes de que la candidata pase a vigente
        }
        c.usarComoVigente();
        bitacora.registrarCambio("planificacion_semanal", c.getId(), "estado", EstadoPlanificacion.CANDIDATA, EstadoPlanificacion.ABIERTA);
        proyecciones.hacerVigente(c.getProyeccion());
        reglasVigencia.marcarSemanaSiguiente(c.getSemana().getFechaInicio());
        bitacora.registrar(TipoOperacion.CAMBIAR_VIGENTE, "planificacion_semanal", c.getId(), "Semana "
                + c.getSemana().getFechaInicio() + ": la versión " + c.getNumeroVersion() + " (proyección V"
                + c.getProyeccion().getVersion() + ") pasa a ser la vigente"
                + (anterior == null ? "" : " en lugar de la versión " + anterior.getNumeroVersion() + " (proyección V"
                        + anterior.getProyeccion().getVersion() + ")"));
        return c;
    }

    PlanificacionSemanal candidata(Integer id) {
        PlanificacionSemanal c = planificaciones.findWithSemanaById(id).orElseThrow(() ->
                new RecursoNoEncontradoException("PLANIFICACION_NO_ENCONTRADA", "No existe la versión de planificación indicada."));
        if (!c.candidata())
            throw new ConflictoException("NO_ES_CANDIDATA", "La versión " + c.getNumeroVersion()
                    + " no es una planificación de otra versión de la proyección que se pueda usar como vigente.");
        return c;
    }
}
