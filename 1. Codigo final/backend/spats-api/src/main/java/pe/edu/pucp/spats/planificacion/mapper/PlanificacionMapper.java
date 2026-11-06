package pe.edu.pucp.spats.planificacion.mapper;

import java.util.Map;

import org.springframework.stereotype.Component;

import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.IndicadoresResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.PartidaCalendarioResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.VersionResponse;
import pe.edu.pucp.spats.planificacion.entity.Enumeraciones.ClasificacionResultado;
import pe.edu.pucp.spats.planificacion.entity.PlanificacionPartida;
import pe.edu.pucp.spats.planificacion.entity.PlanificacionSemanal;

@Component
public class PlanificacionMapper {

    public IndicadoresResponse indicadores(PlanificacionSemanal p) {
        return new IndicadoresResponse(p.getValorFuncionObjetivo(), p.getDesbalanceSemanal(), p.getDesbalanceDiario(),
                p.getNumeroLavados(), p.getEvaluaciones(), p.getTiempoEjecucionMs());
    }

    public VersionResponse version(PlanificacionSemanal p, Map<ClasificacionResultado, Long> conteos) {
        return new VersionResponse(p.getId(), p.getSemana().getFechaInicio(), p.getNumeroVersion(), p.getTipoGeneracion(),
                p.getFechaGeneracion(), p.isEsVigente(), p.getEstado(), p.getFechaCierre(), p.getProyeccion().getVersion(),
                indicadores(p), cantidad(conteos, ClasificacionResultado.ASIGNADA),
                cantidad(conteos, ClasificacionResultado.SOBRANTE), cantidad(conteos, ClasificacionResultado.BLOQUEADA));
    }

    public PartidaCalendarioResponse partida(PlanificacionPartida pp) {
        var p = pp.getPartida();
        return new PartidaCalendarioResponse(p.getNumeroPartida(), p.getPedido().getNumeroOp(),
                p.getPedido().getFechaEntrega(), p.getColor().getCodigoColor(), p.getColor().getNombre(),
                p.getColor().getClasificacionColor(), p.getColor().getTonalidad(), p.getPesoTotal(),
                pp.getClasificacionResultado(), pp.isRequiereLavadoPrevio(),
                pp.getOrdenSecuencia() == null ? 0 : pp.getOrdenSecuencia(), pp.getFechaDisponible(),
                pp.getEstadoConfirmacionCierre());
    }

    private static int cantidad(Map<ClasificacionResultado, Long> conteos, ClasificacionResultado c) {
        return conteos.getOrDefault(c, 0L).intValue();
    }
}
