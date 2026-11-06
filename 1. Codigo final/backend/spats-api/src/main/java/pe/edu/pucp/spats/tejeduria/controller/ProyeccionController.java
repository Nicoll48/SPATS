package pe.edu.pucp.spats.tejeduria.controller;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.pucp.spats.tejeduria.dto.ProyeccionDtos.DetalleProyeccionResponse;
import pe.edu.pucp.spats.tejeduria.dto.ProyeccionDtos.ModificarDetalleProyeccionRequest;
import pe.edu.pucp.spats.tejeduria.dto.ProyeccionDtos.VersionProyeccionResponse;
import pe.edu.pucp.spats.tejeduria.service.ProyeccionService;

/**
 * Consulta, corrección y restauración de la proyección de tejeduría. Las operaciones que cambian la
 * proyección vigente aceptan {@code confirmado=true} cuando la semana tiene una planificación vigente
 * abierta, que dejará de ser vigente (D-06, RF59).
 */
@RestController
@RequestMapping("/api")
public class ProyeccionController {

    private final ProyeccionService servicio;

    public ProyeccionController(ProyeccionService servicio) { this.servicio = servicio; }

    @GetMapping("/semanas/{lunes}/proyecciones")
    public List<VersionProyeccionResponse> versiones(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate lunes) {
        return servicio.versiones(lunes);
    }

    @GetMapping("/proyecciones/{id}/detalles")
    public List<DetalleProyeccionResponse> detalle(@PathVariable Integer id) {
        return servicio.detalle(id);
    }

    @PutMapping("/detalles-proyeccion/{id}")
    public DetalleProyeccionResponse modificarDetalle(@PathVariable Integer id,
            @Valid @RequestBody ModificarDetalleProyeccionRequest solicitud,
            @RequestParam(defaultValue = "false") boolean confirmado) {
        return servicio.modificarDetalle(id, solicitud, confirmado);
    }

    @DeleteMapping("/detalles-proyeccion/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarDetalle(@PathVariable Integer id, @RequestParam(defaultValue = "false") boolean confirmado) {
        servicio.eliminarDetalle(id, confirmado);
    }

    @PostMapping("/proyecciones/{id}/restauracion")
    public VersionProyeccionResponse restaurar(@PathVariable Integer id,
                                               @RequestParam(defaultValue = "false") boolean confirmado) {
        return servicio.restaurar(id, confirmado);
    }
}
