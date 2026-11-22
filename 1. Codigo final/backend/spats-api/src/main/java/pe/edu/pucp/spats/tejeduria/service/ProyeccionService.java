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
 * Versiones de la proyección de tejeduría de cada semana (D-52). Cargar o restaurar crea una versión nueva sin
 * cambiar la planificación vigente; todas se conservan (RF56) y cada una se puede planificar aparte. Corregir un
 * registro sí deja sin vigencia las planificaciones generadas con esa versión (D-06), con confirmación.
 */
@Service
@Transactional(readOnly = true)
public class ProyeccionService {

    /**
     * Máximo de versiones no eliminadas de la proyección por semana: el calendario tiene 5 colores de versión y con
     * más se repetirían. Para cargar otra hay que eliminar alguna (las eliminadas no cuentan).
     */
    public static final int MAXIMO_VERSIONES = 5;

    private final ProyeccionRepository proyecciones;
    private final DetalleProyeccionRepository detalles;
    private final SemanaService semanas;
    private final ApplicationEventPublisher eventos;
    private final BitacoraService bitacora;
    private final PlanificacionesDeLaSemana planificaciones;
    private final Clock reloj;

    public ProyeccionService(ProyeccionRepository proyecciones, DetalleProyeccionRepository detalles,
                             SemanaService semanas, ApplicationEventPublisher eventos, BitacoraService bitacora,
                             PlanificacionesDeLaSemana planificaciones, Clock reloj) {
        this.proyecciones = proyecciones;
        this.detalles = detalles;
        this.semanas = semanas;
        this.eventos = eventos;
        this.bitacora = bitacora;
        this.planificaciones = planificaciones;
        this.reloj = reloj;
    }

    public record NuevoDetalle(DetallePedido detallePedido, LocalDate fechaProyectada, BigDecimal kg) { }

    /**
     * Registra una versión nueva de la proyección de la semana (D-52). No cambia la planificación vigente: si la
     * semana tiene una, la versión nueva queda CARGADA y se planifica aparte; si no, pasa a ser la VIGENTE (la que
     * se usa por omisión al generar). No se carga en una semana cerrada: sus versiones quedan en solo lectura.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ProyeccionTejeduria registrarVersion(LocalDate lunes, List<NuevoDetalle> nuevos, TipoOperacion operacion,
                                                String detalleBitacora, Integer idCarga) {
        Semana semana = semanas.obtenerOCrear(lunes);
        planificaciones.exigirSemanaAbierta(semana.getId());
        if (noEliminadas(semana.getId()).size() >= MAXIMO_VERSIONES)
            throw new ReglaNegocioException("MAXIMO_VERSIONES_PROYECCION", "La semana ya tiene " + MAXIMO_VERSIONES
                    + " versiones de la proyección, el máximo. Para cargar otra, elimina alguna versión en Carga de datos.");
        boolean conVigente = planificaciones.tieneVigente(semana.getId());
        int version = proyecciones.findFirstBySemanaIdOrderByVersionDesc(semana.getId()).map(p -> p.getVersion() + 1).orElse(1);
        ProyeccionTejeduria nueva = new ProyeccionTejeduria(semana, version, LocalDateTime.now(reloj));
        if (conVigente) nueva.dejarDeSerVigente();
        else proyecciones.findBySemanaIdAndEstadoOrderByVersionDesc(semana.getId(), EstadoProyeccion.VIGENTE)
                .forEach(ProyeccionTejeduria::dejarDeSerVigente);
        nueva.asociarCarga(idCarga);
        ProyeccionTejeduria proyeccion = proyecciones.save(nueva);
        detalles.saveAll(nuevos.stream()
                .map(n -> new DetalleProyeccionTejeduria(proyeccion, n.detallePedido(), n.fechaProyectada(), n.kg()))
                .toList());
        bitacora.registrar(operacion, "proyeccion_tejeduria", proyeccion.getId(),
                "Semana " + lunes + ", versión V" + version + (detalleBitacora == null ? "" : " — " + detalleBitacora)
                        + (conVigente ? " (la planificación vigente no cambia)" : ""));
        return proyeccion;
    }

    /**
     * La versión pasa a ser la de la planificación vigente (D-52) y la que lo era queda CARGADA. La llama el módulo
     * de planificación cuando cambia su vigente.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void hacerVigente(ProyeccionTejeduria version) {
        if (version.getEstado() == EstadoProyeccion.VIGENTE) return;
        for (ProyeccionTejeduria otra : proyecciones.findBySemanaIdAndEstadoOrderByVersionDesc(version.getSemana().getId(),
                EstadoProyeccion.VIGENTE)) {
            otra.dejarDeSerVigente();
            bitacora.registrarCambio("proyeccion_tejeduria", otra.getId(), "estado", EstadoProyeccion.VIGENTE, EstadoProyeccion.CARGADA);
        }
        bitacora.registrarCambio("proyeccion_tejeduria", version.getId(), "estado", version.getEstado(), EstadoProyeccion.VIGENTE);
        version.reactivar();
    }

    /** Versión que se puede planificar en la semana: es de esa semana y no está eliminada. */
    public ProyeccionTejeduria planificable(Integer idSemana, Integer idProyeccion) {
        ProyeccionTejeduria p = buscar(idProyeccion);
        if (!p.getSemana().getId().equals(idSemana))
            throw new ReglaNegocioException("PROYECCION_DE_OTRA_SEMANA", "La versión V" + p.getVersion() + " es de otra semana.");
        if (p.getEstado() == EstadoProyeccion.ELIMINADA)
            throw new ReglaNegocioException("PROYECCION_ELIMINADA", "La versión V" + p.getVersion()
                    + " está eliminada. Vuelve a ella en Carga de datos antes de planificarla.");
        return p;
    }

    public List<VersionProyeccionResponse> versiones(LocalDate lunes) {
        return semanas.buscar(lunes).map(s -> proyecciones.findBySemanaIdOrderByVersionDesc(s.getId()).stream()
                .map(this::aResponse).toList()).orElse(List.of());
    }

    /** La versión VIGENTE de la semana (D-52); si ninguna lo está, la última no eliminada. */
    public Optional<ProyeccionTejeduria> vigente(Integer idSemana) {
        return proyecciones.findBySemanaIdAndEstadoOrderByVersionDesc(idSemana, EstadoProyeccion.VIGENTE).stream().findFirst()
                .or(() -> proyecciones.findFirstBySemanaIdAndEstadoNotOrderByVersionDesc(idSemana, EstadoProyeccion.ELIMINADA));
    }

    /** Versiones no eliminadas de la semana, de la más antigua a la más nueva (V1, V2, ...). */
    public List<ProyeccionTejeduria> noEliminadas(Integer idSemana) {
        return proyecciones.findBySemanaIdOrderByVersionDesc(idSemana).reversed().stream()
                .filter(p -> p.getEstado() != EstadoProyeccion.ELIMINADA).toList();
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

    /** Corrección manual de un registro de la proyección vigente (RF13). Deja sin vigencia las planificaciones que la usaron. */
    @Transactional
    public DetalleProyeccionResponse modificarDetalle(Integer id, ModificarDetalleProyeccionRequest s, boolean confirmado) {
        DetalleProyeccionTejeduria d = detalles.findById(id).orElseThrow(() -> new RecursoNoEncontradoException(
                "DETALLE_PROYECCION_NO_ENCONTRADO", "No existe el registro de proyección indicado."));
        ProyeccionTejeduria p = d.getProyeccion();
        if (p.getEstado() != EstadoProyeccion.VIGENTE)
            throw new ReglaNegocioException("PROYECCION_NO_VIGENTE",
                    "Solo se corrige la versión vigente de la proyección. Para cambiar otra, carga una versión nueva.");
        eventos.publishEvent(new ProyeccionPorCambiarEvent(p.getSemana().getId(), p.getSemana().getFechaInicio(), p.getId(), confirmado));
        bitacora.registrarCambio("detalle_proyeccion_tejeduria", id, "fecha_proyectada", d.getFechaProyectada(), s.fechaProyectada());
        bitacora.registrarCambio("detalle_proyeccion_tejeduria", id, "kg_proyectados", d.getKgProyectados(), s.kgProyectados());
        d.modificar(s.fechaProyectada(), s.kgProyectados());
        return detalle(p.getId()).stream().filter(x -> x.id().equals(id)).findFirst().orElseThrow();
    }

    /** Elimina un registro de la proyección vigente. Deja sin vigencia las planificaciones que la usaron, como una corrección. */
    @Transactional
    public void eliminarDetalle(Integer id, boolean confirmado) {
        DetalleProyeccionTejeduria d = detalles.findById(id).orElseThrow(() -> new RecursoNoEncontradoException(
                "DETALLE_PROYECCION_NO_ENCONTRADO", "No existe el registro de proyección indicado."));
        ProyeccionTejeduria p = d.getProyeccion();
        if (p.getEstado() != EstadoProyeccion.VIGENTE)
            throw new ReglaNegocioException("PROYECCION_NO_VIGENTE",
                    "Solo se elimina de la versión vigente de la proyección.");
        eventos.publishEvent(new ProyeccionPorCambiarEvent(p.getSemana().getId(), p.getSemana().getFechaInicio(), p.getId(), confirmado));
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

    /**
     * Vuelve a una versión anterior: copia sus registros en una versión nueva (D-06). Como cualquier carga, no
     * cambia la planificación vigente (D-52).
     */
    @Transactional
    public VersionProyeccionResponse restaurar(Integer idProyeccion) {
        ProyeccionTejeduria origen = buscar(idProyeccion);
        if (origen.getEstado() == EstadoProyeccion.VIGENTE)
            throw new ReglaNegocioException("PROYECCION_YA_VIGENTE", "Esa versión ya es la vigente.");
        List<NuevoDetalle> copia = detalles.deLaProyeccion(idProyeccion).stream()
                .map(d -> new NuevoDetalle(d.getDetallePedido(), d.getFechaProyectada(), d.getKgProyectados())).toList();
        ProyeccionTejeduria nueva = registrarVersion(origen.getSemana().getFechaInicio(), copia,
                TipoOperacion.RESTAURAR, "copia de la versión V" + origen.getVersion(), null);
        return aResponse(nueva);
    }

    public ProyeccionTejeduria buscar(Integer id) {
        return proyecciones.findById(id).orElseThrow(() -> new RecursoNoEncontradoException(
                "PROYECCION_NO_ENCONTRADA", "No existe la proyección indicada."));
    }

    /** Kilos totales de una versión. */
    public BigDecimal kgTotales(Integer idProyeccion) {
        return detalles.deLaProyeccion(idProyeccion).stream().map(DetalleProyeccionTejeduria::getKgProyectados)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private VersionProyeccionResponse aResponse(ProyeccionTejeduria p) {
        List<DetalleProyeccionTejeduria> suyos = detalles.deLaProyeccion(p.getId());
        BigDecimal total = suyos.stream().map(DetalleProyeccionTejeduria::getKgProyectados)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new VersionProyeccionResponse(p.getId(), p.getSemana().getFechaInicio(), p.getVersion(),
                p.getFechaCarga(), p.getEstado(), suyos.size(), total);
    }
}
