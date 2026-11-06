package pe.edu.pucp.spats.maquina.mapper;

import org.springframework.stereotype.Component;

import pe.edu.pucp.spats.maquina.dto.MaquinaDtos.IndisponibilidadResponse;
import pe.edu.pucp.spats.maquina.dto.MaquinaDtos.MaquinaResponse;
import pe.edu.pucp.spats.maquina.entity.EstadoMaquina;
import pe.edu.pucp.spats.maquina.entity.IndisponibilidadMaquina;
import pe.edu.pucp.spats.maquina.entity.MaquinaTintoreria;

@Component
public class MaquinaMapper {

    public MaquinaResponse aResponse(MaquinaTintoreria m, EstadoMaquina estadoActual) {
        return new MaquinaResponse(m.getId(), m.getCodigo(), m.getCapacidad(), m.getClasificacionColor(),
                m.getObjetivoSemanal(), m.getBarca(), m.getEstado(), estadoActual);
    }

    public IndisponibilidadResponse aResponse(IndisponibilidadMaquina i) {
        return new IndisponibilidadResponse(i.getId(), i.getMaquina().getId(), i.getMaquina().getCodigo(),
                i.getFechaHoraInicio(), i.getFechaHoraFin(), i.getTipo(), i.getMotivo(),
                i.getTipo().estadoResultante());
    }
}
