package pe.edu.pucp.spats.incidencia.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.pucp.spats.auditoria.entity.TipoOperacion;
import pe.edu.pucp.spats.auditoria.service.BitacoraService;
import pe.edu.pucp.spats.comun.error.ConflictoException;
import pe.edu.pucp.spats.comun.error.ReglaNegocioException;
import pe.edu.pucp.spats.incidencia.dto.IncidenciaDtos.IncidenciaResponse;
import pe.edu.pucp.spats.incidencia.dto.IncidenciaDtos.RegistrarIncidenciaRequest;
import pe.edu.pucp.spats.incidencia.entity.Incidencia;
import pe.edu.pucp.spats.incidencia.repository.IncidenciaRepository;
import pe.edu.pucp.spats.maquina.service.MaquinaService;
import pe.edu.pucp.spats.partida.service.PartidaService;
import pe.edu.pucp.spats.pedido.entity.Pedido;
import pe.edu.pucp.spats.pedido.service.PedidoService;
import pe.edu.pucp.spats.planificacion.entity.PlanificacionSemanal;
import pe.edu.pucp.spats.planificacion.repository.PlanificacionSemanalRepository;
import pe.edu.pucp.spats.seguridad.repository.UsuarioRepository;
import pe.edu.pucp.spats.seguridad.service.UsuarioActual;
import pe.edu.pucp.spats.tejeduria.entity.Semana;
import pe.edu.pucp.spats.tejeduria.service.SemanaService;

/**
 * Registro de incidencias sobre la planificación vigente de una semana (HU-17, RN-24). Se pueden acumular
 * varias antes de una sola re-planificación (RF52). La re-planificación todavía no está implementada (D-03).
 */
@Service
@Transactional(readOnly = true)
public class IncidenciaService {

    private final IncidenciaRepository incidencias;
    private final SemanaService semanas;
    private final PlanificacionSemanalRepository planificaciones;
    private final MaquinaService maquinas;
    private final PartidaService partidas;
    private final PedidoService pedidos;
    private final UsuarioRepository usuarios;
    private final UsuarioActual usuarioActual;
    private final BitacoraService bitacora;

    public IncidenciaService(IncidenciaRepository incidencias, SemanaService semanas,
                             PlanificacionSemanalRepository planificaciones, MaquinaService maquinas,
                             PartidaService partidas, PedidoService pedidos, UsuarioRepository usuarios,
                             UsuarioActual usuarioActual, BitacoraService bitacora) {
        this.incidencias = incidencias;
        this.semanas = semanas;
        this.planificaciones = planificaciones;
        this.maquinas = maquinas;
        this.partidas = partidas;
        this.pedidos = pedidos;
        this.usuarios = usuarios;
        this.usuarioActual = usuarioActual;
        this.bitacora = bitacora;
    }

    public List<IncidenciaResponse> deLaSemana(LocalDate lunes) {
        return semanas.buscar(lunes).map(s -> incidencias.deLaSemana(s.getId()).stream().map(i -> aResponse(i, null)).toList())
                .orElse(List.of());
    }

    @Transactional
    public IncidenciaResponse registrar(LocalDate lunes, RegistrarIncidenciaRequest s) {
        Semana semana = semanas.buscar(lunes).orElseThrow(this::sinPlanVigente);
        PlanificacionSemanal vigente = planificaciones.findBySemanaIdAndEsVigenteTrue(semana.getId()).orElseThrow(this::sinPlanVigente);
        if (!vigente.abierta())
            throw new ConflictoException("SEMANA_CERRADA", "La planificación de esta semana ya se cerró; no admite incidencias.");
        var usuario = usuarios.getReferenceById(usuarioActual.id());
        String descripcion = s.descripcion() == null || s.descripcion().isBlank() ? null : s.descripcion().trim();

        Incidencia incidencia;
        LocalDate fechaActual = null;
        switch (s.tipo()) {
            case INCIDENCIA_MAQUINA -> {
                String codigo = exigir(s.codigoMaquina(), "Indica la máquina afectada.");
                exigirDisponibilidad(s);
                incidencia = Incidencia.deMaquina(usuario, vigente, maquinas.buscarPorCodigo(codigo), descripcion,
                        s.fechaHoraOcurrencia(), s.fechaHoraDisponibilidad());
            }
            case PROBLEMA_COMPLEMENTO -> {
                String numero = exigir(s.numeroPartida(), "Indica la partida afectada.");
                exigirDisponibilidad(s);
                incidencia = Incidencia.deComplemento(usuario, vigente, partidas.buscarPorNumero(numero), descripcion,
                        s.fechaHoraOcurrencia(), s.fechaHoraDisponibilidad());
            }
            case CAMBIO_PRIORIDAD -> {
                String numeroOp = exigir(s.numeroOp(), "Indica el pedido afectado.");
                if (s.nuevaFechaEntrega() == null)
                    throw new ReglaNegocioException("FALTA_NUEVA_FECHA", "Indica la nueva fecha de entrega del pedido.");
                Pedido pedido = pedidos.buscarPedidoPorNumero(numeroOp);
                incidencia = Incidencia.deCambioPrioridad(usuario, vigente, pedido, descripcion, s.fechaHoraOcurrencia(),
                        s.nuevaFechaEntrega());
                // D-13b: el pedido conserva su fecha hasta que se confirme la re-planificación que considera la incidencia.
                fechaActual = pedido.getFechaEntrega();
            }
            default -> throw new IllegalStateException(s.tipo().name());
        }
        Incidencia guardada = incidencias.save(incidencia);
        bitacora.registrar(TipoOperacion.REGISTRAR, "incidencia", guardada.getId(), s.tipo().name());
        return aResponse(guardada, fechaActual);
    }

    private static String exigir(String valor, String mensaje) {
        if (valor == null || valor.isBlank()) throw new ReglaNegocioException("FALTA_ENTIDAD_AFECTADA", mensaje);
        return valor.trim();
    }

    private static void exigirDisponibilidad(RegistrarIncidenciaRequest s) {
        if (s.fechaHoraDisponibilidad() == null)
            throw new ReglaNegocioException("FALTA_DISPONIBILIDAD", "Indica la fecha y hora prevista de disponibilidad.");
        if (s.fechaHoraDisponibilidad().isBefore(s.fechaHoraOcurrencia()))
            throw new ReglaNegocioException("DISPONIBILIDAD_INVALIDA",
                    "La fecha prevista de disponibilidad no puede ser anterior a la ocurrencia.");
    }

    private ReglaNegocioException sinPlanVigente() {
        return new ReglaNegocioException("SIN_PLANIFICACION_VIGENTE",
                "Las incidencias se registran sobre la planificación vigente; esta semana todavía no tiene una.");
    }

    private static IncidenciaResponse aResponse(Incidencia i, LocalDate fechaEntregaActual) {
        String entidad, referencia;
        switch (i.getTipoIncidencia()) {
            case INCIDENCIA_MAQUINA -> { entidad = "Máquina"; referencia = i.getMaquina().getCodigo(); }
            case PROBLEMA_COMPLEMENTO -> { entidad = "Partida"; referencia = i.getPartida().getNumeroPartida(); }
            default -> { entidad = "Pedido"; referencia = i.getPedido().getNumeroOp(); }
        }
        return new IncidenciaResponse(i.getId(), i.getTipoIncidencia(), entidad, referencia, i.getDescripcion(),
                i.getFechaHoraOcurrencia(), i.getFechaHoraDisponibilidad(), i.getNuevaFechaEntrega(), fechaEntregaActual,
                i.getEstado(), i.getUsuario().getNombreCompleto(), i.getPlanificacionOrigen().getNumeroVersion());
    }
}
