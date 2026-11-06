package pe.edu.pucp.spats.tejeduria.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.pucp.spats.auditoria.entity.TipoOperacion;
import pe.edu.pucp.spats.auditoria.service.BitacoraService;
import pe.edu.pucp.spats.comun.error.RecursoNoEncontradoException;
import pe.edu.pucp.spats.comun.error.ReglaNegocioException;
import pe.edu.pucp.spats.comun.fecha.SemanaPlanificacion;
import pe.edu.pucp.spats.pedido.entity.DetallePedido;
import pe.edu.pucp.spats.tejeduria.dto.ProyeccionDtos.DetalleProyeccionResponse;
import pe.edu.pucp.spats.tejeduria.dto.ProyeccionDtos.ModificarDetalleProyeccionRequest;
import pe.edu.pucp.spats.tejeduria.dto.ProyeccionDtos.VersionProyeccionResponse;
import pe.edu.pucp.spats.tejeduria.entity.DetalleProyeccionTejeduria;
import pe.edu.pucp.spats.tejeduria.entity.EstadoProyeccion;
import pe.edu.pucp.spats.tejeduria.entity.ProyeccionTejeduria;
import pe.edu.pucp.spats.tejeduria.entity.Semana;
import pe.edu.pucp.spats.tejeduria.repository.DetalleProyeccionRepository;
import pe.edu.pucp.spats.tejeduria.repository.ProyeccionRepository;
import spats.algoritmo.entrada.RegistroProyeccion;

/**
 * Versiones de la proyección de tejeduría de cada semana (D-06). Cargar, corregir o restaurar la
 * proyección crea o cambia la versión vigente; las anteriores se conservan como historial (RF56).
 */
@Service
@Transactional(readOnly = true)
public class ProyeccionService {

    private final ProyeccionRepository proyecciones;
    private final DetalleProyeccionRepository detalles;
    private final SemanaService semanas;
    private final ApplicationEventPublisher eventos;
    private final BitacoraService bitacora;
    private final Clock reloj;

    public ProyeccionService(ProyeccionRepository proyecciones, DetalleProyeccionRepository detalles,
                             SemanaService semanas, ApplicationEventPublisher eventos, BitacoraService bitacora,
                             Clock reloj) {
        this.proyecciones = proyecciones;
        this.detalles = detalles;
        this.semanas = semanas;
        this.eventos = eventos;
        this.bitacora = bitacora;
        this.reloj = reloj;
    }

    public record NuevoDetalle(DetallePedido detallePedido, LocalDate fechaProyectada, BigDecimal kg) { }

    /** Registra una versión nueva de la proyección de la semana; pasa a ser la vigente. */
    @Transactional(propagation = Propagation.MANDATORY)
    public ProyeccionTejeduria registrarVersion(LocalDate lunes, List<NuevoDetalle> nuevos, boolean confirmado,
                                                TipoOperacion operacion, String detalleBitacora, Integer idCarga) {
        Semana semana = semanas.obtenerOCrear(lunes);
        eventos.publishEvent(new ProyeccionPorCambiarEvent(semana.getId(), semana.getFechaInicio(), confirmado));
        vigente(semana.getId()).ifPresent(ProyeccionTejeduria::reemplazar);
        int version = proyecciones.findFirstBySemanaIdOrderByVersionDesc(semana.getId()).map(p -> p.getVersion() + 1).orElse(1);
        ProyeccionTejeduria nueva = new ProyeccionTejeduria(semana, version, LocalDateTime.now(reloj));
        nueva.asociarCarga(idCarga);
        ProyeccionTejeduria proyeccion = proyecciones.save(nueva);
        detalles.saveAll(nuevos.stream()
                .map(n -> new DetalleProyeccionTejeduria(proyeccion, n.detallePedido(), n.fechaProyectada(), n.kg()))
                .toList());
        bitacora.registrar(operacion, "proyeccion_tejeduria", proyeccion.getId(),
                "Semana " + lunes + ", versión " + version + (detalleBitacora == null ? "" : " — " + detalleBitacora));
        return proyeccion;
    }

    public List<VersionProyeccionResponse> versiones(LocalDate lunes) {
        return semanas.buscar(lunes).map(s -> proyecciones.findBySemanaIdOrderByVersionDesc(s.getId()).stream()
                .map(this::aResponse).toList()).orElse(List.of());
    }

    public Optional<ProyeccionTejeduria> vigente(Integer idSemana) {
        return proyecciones.findFirstBySemanaIdAndEstadoNotOrderByVersionDesc(idSemana, EstadoProyeccion.ELIMINADA);
    }

    public List<DetalleProyeccionResponse> detalle(Integer idProyeccion) {
        ProyeccionTejeduria p = buscar(idProyeccion);
        SemanaPlanificacion periodo = p.getSemana().periodo();
        return detalles.deLaProyeccion(idProyeccion).stream().map(d -> new DetalleProyeccionResponse(d.getId(),
                d.getDetallePedido().getId(), d.getDetallePedido().getPedido().getNumeroOp(),
                d.getDetallePedido().getArticulo().getCodigo(), d.getDetallePedido().getArticulo().getNombre(),
                d.getFechaProyectada(), RegistroProyeccion.fechaDisponible(d.getFechaProyectada()), d.getKgProyectados(),
                periodo.enVentanaProyeccion(d.getFechaProyectada()))).toList();
    }

    public List<DetalleProyeccionTejeduria> detallesEntidad(Integer idProyeccion) {
        return detalles.deLaProyeccion(idProyeccion);
    }

    /** Corrección manual de un registro de la proyección vigente (RF13). Deja sin vigencia la planificación que la usó. */
    @Transactional
    public DetalleProyeccionResponse modificarDetalle(Integer id, ModificarDetalleProyeccionRequest s, boolean confirmado) {
        DetalleProyeccionTejeduria d = detalles.findById(id).orElseThrow(() -> new RecursoNoEncontradoException(
                "DETALLE_PROYECCION_NO_ENCONTRADO", "No existe el registro de proyección indicado."));
        ProyeccionTejeduria p = d.getProyeccion();
        if (p.getEstado() != EstadoProyeccion.VIGENTE)
            throw new ReglaNegocioException("PROYECCION_REEMPLAZADA",
                    "Solo se corrige la versión vigente de la proyección. Para volver a una anterior, restáurala.");
        eventos.publishEvent(new ProyeccionPorCambiarEvent(p.getSemana().getId(), p.getSemana().getFechaInicio(), confirmado));
        bitacora.registrarCambio("detalle_proyeccion_tejeduria", id, "fecha_proyectada", d.getFechaProyectada(), s.fechaProyectada());
        bitacora.registrarCambio("detalle_proyeccion_tejeduria", id, "kg_proyectados", d.getKgProyectados(), s.kgProyectados());
        d.modificar(s.fechaProyectada(), s.kgProyectados());
        return detalle(p.getId()).stream().filter(x -> x.id().equals(id)).findFirst().orElseThrow();
    }

    /** Elimina un registro de la proyección vigente. Deja sin vigencia la planificación que la usó, como una corrección. */
    @Transactional
    public void eliminarDetalle(Integer id, boolean confirmado) {
        DetalleProyeccionTejeduria d = detalles.findById(id).orElseThrow(() -> new RecursoNoEncontradoException(
                "DETALLE_PROYECCION_NO_ENCONTRADO", "No existe el registro de proyección indicado."));
        ProyeccionTejeduria p = d.getProyeccion();
        if (p.getEstado() != EstadoProyeccion.VIGENTE)
            throw new ReglaNegocioException("PROYECCION_REEMPLAZADA",
                    "Solo se elimina de la versión vigente de la proyección.");
        eventos.publishEvent(new ProyeccionPorCambiarEvent(p.getSemana().getId(), p.getSemana().getFechaInicio(), confirmado));
        bitacora.registrar(TipoOperacion.ELIMINAR, "detalle_proyeccion_tejeduria", id,
                "Semana " + p.getSemana().getFechaInicio() + ", versión " + p.getVersion() + ": " + d.getFechaProyectada() + ", " + d.getKgProyectados() + " kg");
        detalles.delete(d);
    }

    /**
     * Marca una versión como eliminada: conserva sus registros para poder volver a ella (D-46). Si era la vigente,
     * vuelve a serlo la de mayor número que quede. Quien llama debe haber eliminado antes las planificaciones que
     * la usan. Devuelve cuántos registros tiene.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int marcarEliminada(ProyeccionTejeduria version) {
        boolean eraVigente = version.getEstado() == EstadoProyeccion.VIGENTE;
        version.marcarEliminada();
        proyecciones.flush();
        if (eraVigente) vigente(version.getSemana().getId()).ifPresent(ProyeccionTejeduria::reactivar);
        return (int) detalles.countByProyeccionId(version.getId());
    }

    /**
     * Borra del todo las versiones eliminadas que usan estos artículos de pedido: sin el pedido ya no se podría
     * volver a ellas. Devuelve las cargas de esas versiones.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Integer> purgarEliminadasQueUsan(Collection<Integer> idsDetalle) {
        if (idsDetalle.isEmpty()) return List.of();
        List<ProyeccionTejeduria> versiones = detalles.eliminadasQueUsan(idsDetalle);
        for (ProyeccionTejeduria version : versiones) {
            detalles.deleteAll(detalles.findByProyeccionId(version.getId()));
            proyecciones.delete(version);
        }
        proyecciones.flush();
        return versiones.stream().map(ProyeccionTejeduria::getIdCarga).filter(Objects::nonNull).toList();
    }

    /** Vuelve a una versión anterior: copia sus registros en una versión nueva, que pasa a vigente (D-06). */
    @Transactional
    public VersionProyeccionResponse restaurar(Integer idProyeccion, boolean confirmado) {
        ProyeccionTejeduria origen = buscar(idProyeccion);
        if (origen.getEstado() == EstadoProyeccion.VIGENTE)
            throw new ReglaNegocioException("PROYECCION_YA_VIGENTE", "Esa versión ya es la vigente.");
        List<NuevoDetalle> copia = detalles.deLaProyeccion(idProyeccion).stream()
                .map(d -> new NuevoDetalle(d.getDetallePedido(), d.getFechaProyectada(), d.getKgProyectados())).toList();
        ProyeccionTejeduria nueva = registrarVersion(origen.getSemana().getFechaInicio(), copia, confirmado,
                TipoOperacion.RESTAURAR, "copia de la versión " + origen.getVersion(), null);
        return aResponse(nueva);
    }

    public ProyeccionTejeduria buscar(Integer id) {
        return proyecciones.findById(id).orElseThrow(() -> new RecursoNoEncontradoException(
                "PROYECCION_NO_ENCONTRADA", "No existe la proyección indicada."));
    }

    private VersionProyeccionResponse aResponse(ProyeccionTejeduria p) {
        List<DetalleProyeccionTejeduria> suyos = detalles.deLaProyeccion(p.getId());
        BigDecimal total = suyos.stream().map(DetalleProyeccionTejeduria::getKgProyectados)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new VersionProyeccionResponse(p.getId(), p.getSemana().getFechaInicio(), p.getVersion(),
                p.getFechaCarga(), p.getEstado(), suyos.size(), total);
    }
}
