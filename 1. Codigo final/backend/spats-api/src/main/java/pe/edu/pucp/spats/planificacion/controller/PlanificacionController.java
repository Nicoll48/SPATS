package pe.edu.pucp.spats.planificacion.controller;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.pucp.spats.comun.hoja.EscritorLibro;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.ArrastradaResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.BolsaResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.CambioVigenteRequest;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.CambioVigenteResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.ContextoCambioVigenteResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.VersionesSemanaResponse;
import pe.edu.pucp.spats.planificacion.service.CambioVigenteService;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.CierreRequest;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.CierreResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.ComparacionResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.EstadoSemanaResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.GeneracionResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.PlanificacionDetalleResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.VersionResponse;
import pe.edu.pucp.spats.planificacion.service.CierreSemanaService;
import pe.edu.pucp.spats.planificacion.service.ConsultaPlanificacionService;
import pe.edu.pucp.spats.planificacion.service.ExportacionPlanificacionService;
import pe.edu.pucp.spats.planificacion.service.GeneracionPlanificacionService;

/** Planificación semanal: generación (HU-11), consulta (HU-12, HU-13), exportación (HU-14), cierre (HU-15, HU-16) e historial (HU-19). */
@RestController
@RequestMapping("/api")
public class PlanificacionController {

    private final GeneracionPlanificacionService generacion;
    private final ConsultaPlanificacionService consulta;
    private final CierreSemanaService cierre;
    private final ExportacionPlanificacionService exportacion;
    private final CambioVigenteService cambioVigente;

    private final pe.edu.pucp.spats.planificacion.recomendacion.RecomendacionesTejidoService recomendacionesTejido;

    public PlanificacionController(GeneracionPlanificacionService generacion, ConsultaPlanificacionService consulta,
                                   CierreSemanaService cierre, ExportacionPlanificacionService exportacion,
                                   CambioVigenteService cambioVigente,
            pe.edu.pucp.spats.planificacion.recomendacion.RecomendacionesTejidoService recomendacionesTejido) {
        this.recomendacionesTejido = recomendacionesTejido;
        this.generacion = generacion;
        this.consulta = consulta;
        this.cierre = cierre;
        this.exportacion = exportacion;
        this.cambioVigente = cambioVigente;
    }

    @GetMapping("/semanas/{lunes}/estado")
    public EstadoSemanaResponse estadoSemana(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate lunes) {
        return consulta.estadoSemana(lunes);
    }

    @GetMapping("/semanas/{lunes}/planificacion")
    public PlanificacionDetalleResponse vigente(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate lunes) {
        return consulta.vigente(lunes);
    }

    @GetMapping("/semanas/{lunes}/versiones")
    public List<VersionResponse> versiones(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate lunes) {
        return consulta.versiones(lunes);
    }

    /**
     * Genera una versión nueva con el algoritmo. El frontend pide confirmación antes (RF59).
     *
     * @param proyeccion versión de la proyección que se planifica (D-52); sin ella, la VIGENTE. Si la semana tiene una
     *                   vigente generada con otra versión, la nueva queda como candidata y la vigente no cambia.
     */
    @PostMapping("/semanas/{lunes}/planificaciones")
    @ResponseStatus(HttpStatus.CREATED)
    public GeneracionResponse generar(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate lunes,
                                      @RequestParam(required = false) Integer proyeccion) {
        return generacion.generar(lunes, proyeccion);
    }

    /** Versiones de la proyección de la semana con su planificación en uso (panel de versiones, D-52). */
    @GetMapping("/semanas/{lunes}/versiones-proyeccion")
    public VersionesSemanaResponse versionesProyeccion(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate lunes) {
        return consulta.versionesProyeccion(lunes);
    }

    /** Avisos antes de usar una candidata como vigente (D-52). */
    @GetMapping("/planificaciones/{id}/cambio-vigente")
    public ContextoCambioVigenteResponse contextoCambioVigente(@PathVariable Integer id) {
        return cambioVigente.contexto(id);
    }

    /** «Usar como vigente»: directo si la semana no empezó; si ya empezó, re-planificación propuesta desde hoy (D-52). */
    @PostMapping("/planificaciones/{id}/cambio-vigente")
    public CambioVigenteResponse usarComoVigente(@PathVariable Integer id, @RequestBody(required = false) CambioVigenteRequest solicitud) {
        return cambioVigente.usar(id, solicitud == null ? List.of() : solicitud.partidasEnMaquina());
    }

    @GetMapping("/planificaciones/{id}")
    public PlanificacionDetalleResponse detalle(@PathVariable Integer id) {
        return consulta.detalle(id);
    }

    @GetMapping("/planificaciones/{id}/bolsas")
    public List<BolsaResponse> bolsas(@PathVariable Integer id) {
        return consulta.bolsas(id);
    }

    @GetMapping("/planificaciones/comparacion")
    public ComparacionResponse comparar(@RequestParam Integer a, @RequestParam Integer b) {
        return consulta.comparar(a, b);
    }

    @GetMapping("/planificaciones/{id}/exportacion")
    public ResponseEntity<byte[]> exportar(@PathVariable Integer id) {
        ExportacionPlanificacionService.Archivo archivo = exportacion.exportar(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(archivo.nombre()).build().toString())
                .contentType(MediaType.parseMediaType(EscritorLibro.TIPO_XLSX))
                .body(archivo.contenido());
    }

    /** RF30 (D-56): tejido adicional recomendado para la misma semana de una versión. */
    @GetMapping("/planificaciones/{id}/recomendaciones")
    public pe.edu.pucp.spats.planificacion.recomendacion.RecomendacionesTejidoService.RecomendacionesResponse recomendaciones(@PathVariable Integer id) {
        return recomendacionesTejido.consultar(id);
    }

    /** RF30 (D-56): Excel para tejeduría, siempre de la vigente de la semana, ordenado por día de tejido. */
    @GetMapping("/semanas/{lunes}/recomendaciones/exportacion")
    public ResponseEntity<byte[]> exportarRecomendaciones(@PathVariable @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate lunes) {
        var archivo = recomendacionesTejido.exportar(lunes);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(archivo.nombre()).build().toString())
                .contentType(MediaType.parseMediaType(EscritorLibro.TIPO_XLSX))
                .body(archivo.contenido());
    }

    @PostMapping("/planificaciones/{id}/cierre")
    public CierreResponse cerrar(@PathVariable Integer id, @Valid @RequestBody CierreRequest solicitud) {
        return cierre.cerrar(id, solicitud.partidasCompletadas());
    }

    @GetMapping("/partidas/arrastradas")
    public List<ArrastradaResponse> arrastradas() {
        return cierre.arrastradas();
    }

    @PostMapping("/partidas/arrastradas/{numeroPartida}/completado")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void completarArrastrada(@PathVariable String numeroPartida) {
        cierre.completarArrastrada(numeroPartida);
    }
}
