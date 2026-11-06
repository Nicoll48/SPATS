package pe.edu.pucp.spats.planificacion.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.VersionProyeccionEliminadaResponse;
import pe.edu.pucp.spats.planificacion.service.EliminacionVersionProyeccionService;

/** Elimina una versión de la proyección con sus planificaciones (D-44). */
@RestController
@RequestMapping("/api")
public class EliminacionVersionProyeccionController {

    private final EliminacionVersionProyeccionService servicio;

    public EliminacionVersionProyeccionController(EliminacionVersionProyeccionService servicio) {
        this.servicio = servicio;
    }

    @DeleteMapping("/proyecciones/{id}")
    public VersionProyeccionEliminadaResponse eliminar(@PathVariable Integer id,
                                                       @RequestParam(defaultValue = "false") boolean confirmado) {
        return servicio.eliminar(id, confirmado);
    }
}
