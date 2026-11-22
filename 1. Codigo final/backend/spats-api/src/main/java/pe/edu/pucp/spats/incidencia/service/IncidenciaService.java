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
    private final pe.edu.pucp.spats.planificacion.motor.PropuestasPendientes propuestas;

    private final pe.edu.pucp.spats.auditoria.repository.BitacoraAuditoriaRepository bitacoraConsultas;

    public IncidenciaService(IncidenciaRepository incidencias, SemanaService semanas,
                             PlanificacionSemanalRepository planificaciones, MaquinaService maquinas,
                             PartidaService partidas, PedidoService pedidos, UsuarioRepository usuarios,
                             UsuarioActual usuarioActual, BitacoraService bitacora,
                             pe.edu.pucp.spats.planificacion.motor.PropuestasPendientes propuestas,
                             pe.edu.pucp.spats.auditoria.repository.BitacoraAuditoriaRepository bitacoraConsultas) {
        this.bitacoraConsultas = bitacoraConsultas;
        this.propuestas = propuestas;
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

    /**
     * Cierra una incidencia PENDIENTE sin re-planificar (D-54): el planificador la resolvió en planta (por ejemplo,
     * tiñó las partidas en otra máquina) o ya no aplica. Pasa a ATENDIDA y ya no se considera al re-planificar ni al
     * generar. No se permite con una propuesta de re-planificación pendiente, que ya la incluye.
     */
    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('PLANIFICADOR')")
    public IncidenciaResponse atender(LocalDate lunes, Integer id, String motivo) {
        Semana semana = semanas.buscar(lunes).orElseThrow(this::sinPlanVigente);
        Incidencia i = incidencias.findById(id).orElseThrow(() -> new pe.edu.pucp.spats.comun.error.RecursoNoEncontradoException(
                "INCIDENCIA_NO_ENCONTRADA", "No existe la incidencia " + id + "."));
        if (!i.getPlanificacionOrigen().getSemana().getId().equals(semana.getId()))
            throw new pe.edu.pucp.spats.comun.error.ReglaNegocioException("INCIDENCIA_DE_OTRA_SEMANA", "La incidencia " + id + " es de otra semana.");
        if (i.getEstado() != pe.edu.pucp.spats.incidencia.entity.EstadoIncidencia.PENDIENTE)
            throw new pe.edu.pucp.spats.comun.error.ConflictoException("INCIDENCIA_NO_PENDIENTE",
                    "Solo se cierra una incidencia pendiente; esta ya está " + i.getEstado().name().toLowerCase() + ".");
        propuestas.exigirSinPendiente(semana.getId(), "cerrar una incidencia sin re-planificar");
        bitacora.registrarCambio("incidencia", i.getId(), "estado", i.getEstado(), pe.edu.pucp.spats.incidencia.entity.EstadoIncidencia.ATENDIDA);
        i.atender();
        String nota = motivo == null || motivo.isBlank() ? "sin motivo" : motivo.strip();
        bitacora.registrar(pe.edu.pucp.spats.auditoria.entity.TipoOperacion.MODIFICAR, "incidencia", i.getId(),
                "Cerrada sin re-planificar: " + (nota.length() > 200 ? nota.substring(0, 200) : nota));
        return aResponse(i, null);
    }

    /**
     * Anula una incidencia (D-57): registrada por error o que al final no ocurrió. Pasa a ANULADA y nunca más se aplica.
     * Si ya estaba CONSIDERADA, la planificación vigente no cambia sola: su efecto se deshace al volver a generar la
     * semana (si aún no empieza) o al re-planificar desde hoy. Un cambio de prioridad considerado devuelve al pedido su
     * fecha de entrega anterior. No se permite con una propuesta de re-planificación pendiente.
     */
    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('PLANIFICADOR')")
    public IncidenciaResponse anular(LocalDate lunes, Integer id, String motivo) {
        Semana semana = semanas.buscar(lunes).orElseThrow(this::sinPlanVigente);
        Incidencia i = incidencias.findById(id).orElseThrow(() -> new pe.edu.pucp.spats.comun.error.RecursoNoEncontradoException(
                "INCIDENCIA_NO_ENCONTRADA", "No existe la incidencia " + id + "."));
        if (!i.getPlanificacionOrigen().getSemana().getId().equals(semana.getId()))
            throw new pe.edu.pucp.spats.comun.error.ReglaNegocioException("INCIDENCIA_DE_OTRA_SEMANA", "La incidencia " + id + " es de otra semana.");
        if (i.getEstado() == pe.edu.pucp.spats.incidencia.entity.EstadoIncidencia.ANULADA)
            throw new pe.edu.pucp.spats.comun.error.ConflictoException("INCIDENCIA_ANULADA", "La incidencia ya está anulada.");
        if (planificaciones.findBySemanaIdAndEsVigenteTrue(semana.getId()).map(p -> !p.abierta()).orElse(false))
            throw new pe.edu.pucp.spats.comun.error.ConflictoException("SEMANA_CERRADA", "La semana ya está cerrada; sus incidencias quedan en solo lectura.");
        propuestas.exigirSinPendiente(semana.getId(), "anular una incidencia");
        boolean considerada = i.getEstado() == pe.edu.pucp.spats.incidencia.entity.EstadoIncidencia.CONSIDERADA;
        bitacora.registrarCambio("incidencia", i.getId(), "estado", i.getEstado(), pe.edu.pucp.spats.incidencia.entity.EstadoIncidencia.ANULADA);
        i.anular();
        if (considerada && i.getTipoIncidencia() == pe.edu.pucp.spats.incidencia.entity.TipoIncidencia.CAMBIO_PRIORIDAD
                && java.util.Objects.equals(i.getPedido().getFechaEntrega(), i.getNuevaFechaEntrega())) {
            List<String> anteriores = bitacoraConsultas.valoresAnteriores("pedido", i.getPedido().getId(), "fecha_entrega",
                    String.valueOf(i.getNuevaFechaEntrega()));
            if (!anteriores.isEmpty() && anteriores.get(0) != null && !anteriores.get(0).isBlank())
                pedidos.cambiarFechaEntregaPorPrioridad(i.getPedido(), LocalDate.parse(anteriores.get(0)));
        }
        String nota = motivo == null || motivo.isBlank() ? "sin motivo" : motivo.strip();
        bitacora.registrar(pe.edu.pucp.spats.auditoria.entity.TipoOperacion.MODIFICAR, "incidencia", i.getId(),
                "Anulada: " + (nota.length() > 200 ? nota.substring(0, 200) : nota));
        return aResponse(i, null);
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
