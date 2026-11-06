package pe.edu.pucp.spats.auditoria.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.pucp.spats.auditoria.dto.EntradaBitacoraResponse;
import pe.edu.pucp.spats.auditoria.entity.BitacoraAuditoria;
import pe.edu.pucp.spats.auditoria.entity.TipoOperacion;
import pe.edu.pucp.spats.auditoria.repository.BitacoraAuditoriaRepository;
import pe.edu.pucp.spats.comun.dto.PaginaResponse;
import pe.edu.pucp.spats.seguridad.repository.UsuarioRepository;
import pe.edu.pucp.spats.seguridad.service.UsuarioActual;

/**
 * Trazabilidad de operaciones (RF60). Cada registro va en la misma transacción que la operación que
 * lo origina: si la operación falla, tampoco queda el registro.
 */
@Service
@Transactional(readOnly = true)
public class BitacoraService {

    private final BitacoraAuditoriaRepository repositorio;
    private final UsuarioRepository usuarios;
    private final UsuarioActual usuarioActual;
    private final Clock reloj;

    public BitacoraService(BitacoraAuditoriaRepository repositorio, UsuarioRepository usuarios,
                           UsuarioActual usuarioActual, Clock reloj) {
        this.repositorio = repositorio;
        this.usuarios = usuarios;
        this.usuarioActual = usuarioActual;
        this.reloj = reloj;
    }

    /** Registra una operación sin campo específico (importar, generar, cerrar…). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(TipoOperacion tipo, String entidad, Integer idEntidad, String detalle) {
        registrarComo(usuarioActual.id(), tipo, entidad, idEntidad, null, null, detalle);
    }

    /** Registra el cambio de un campo; no registra nada si el valor no cambió. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarCambio(String entidad, Integer idEntidad, String campo, Object anterior, Object nuevo) {
        String a = texto(anterior), n = texto(nuevo);
        if (Objects.equals(a, n)) return;
        registrarComo(usuarioActual.id(), TipoOperacion.MODIFICAR, entidad, idEntidad, campo, a, n);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarComo(Integer idUsuario, TipoOperacion tipo, String entidad, Integer idEntidad,
                              String campo, String anterior, String nuevo) {
        repositorio.save(new BitacoraAuditoria(usuarios.getReferenceById(idUsuario), LocalDateTime.now(reloj),
                entidad, idEntidad, campo, anterior, nuevo, tipo));
    }

    public PaginaResponse<EntradaBitacoraResponse> consultar(String entidad, int pagina, int tamanio) {
        PageRequest solicitud = PageRequest.of(Math.max(0, pagina), Math.clamp(tamanio, 1, 200));
        var resultado = entidad == null || entidad.isBlank()
                ? repositorio.findAllByOrderByFechaHoraDescIdDesc(solicitud)
                : repositorio.findByEntidadAfectadaOrderByFechaHoraDescIdDesc(entidad.trim(), solicitud);
        return PaginaResponse.de(resultado, EntradaBitacoraResponse::de);
    }

    private static String texto(Object valor) {
        return valor == null ? null : valor instanceof java.math.BigDecimal bd ? bd.stripTrailingZeros().toPlainString()
                : valor.toString();
    }
}
