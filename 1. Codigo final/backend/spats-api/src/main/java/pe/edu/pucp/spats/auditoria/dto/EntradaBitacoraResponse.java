package pe.edu.pucp.spats.auditoria.dto;

import java.time.LocalDateTime;

import pe.edu.pucp.spats.auditoria.entity.BitacoraAuditoria;
import pe.edu.pucp.spats.auditoria.entity.TipoOperacion;

public record EntradaBitacoraResponse(Integer id, LocalDateTime fechaHora, String usuario, String entidad,
                                      Integer idEntidad, String campo, String valorAnterior, String valorNuevo,
                                      TipoOperacion tipoOperacion) {

    public static EntradaBitacoraResponse de(BitacoraAuditoria b) {
        return new EntradaBitacoraResponse(b.getId(), b.getFechaHora(), b.getUsuario().getNombreCompleto(),
                b.getEntidadAfectada(), b.getIdEntidad(), b.getCampoModificado(), b.getValorAnterior(),
                b.getValorNuevo(), b.getTipoOperacion());
    }
}
