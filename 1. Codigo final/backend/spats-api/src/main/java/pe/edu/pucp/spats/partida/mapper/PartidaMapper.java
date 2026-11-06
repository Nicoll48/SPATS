package pe.edu.pucp.spats.partida.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import pe.edu.pucp.spats.partida.dto.PartidaDtos.ColorResponse;
import pe.edu.pucp.spats.partida.dto.PartidaDtos.ComponenteResponse;
import pe.edu.pucp.spats.partida.dto.PartidaDtos.PartidaResponse;
import pe.edu.pucp.spats.partida.entity.Color;
import pe.edu.pucp.spats.partida.entity.Partida;
import pe.edu.pucp.spats.partida.entity.PartidaComponente;

@Component
public class PartidaMapper {

    public PartidaResponse aResponse(Partida p, List<PartidaComponente> componentes) {
        return new PartidaResponse(p.getId(), p.getNumeroPartida(), p.getPedido().getId(), p.getPedido().getNumeroOp(),
                p.getPedido().getCliente().getNombre(), p.getPedido().getFechaEntrega(), p.getColor().getCodigoColor(),
                p.getColor().getNombre(), p.getColor().getClasificacionColor(), p.getColor().getTonalidad(), p.getBarca(),
                p.getPesoTotal(), p.getFechaRegistro(), p.getEstadoPartida(),
                componentes.stream().map(this::aResponse).toList());
    }

    public ComponenteResponse aResponse(PartidaComponente c) {
        return new ComponenteResponse(c.getId(), c.getDetallePedido().getId(),
                c.getDetallePedido().getArticulo().getCodigo(), c.getDetallePedido().getArticulo().getNombre(),
                c.getTipoComponente(), c.isTubularAbierto(), c.getPesoRequerido());
    }

    public ColorResponse aResponse(Color c) {
        return new ColorResponse(c.getId(), c.getCodigoColor(), c.getNombre(), c.getClasificacionColor(), c.getTonalidad());
    }
}
