package pe.edu.pucp.spats.planificacion.controller;

import java.time.LocalDate;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.ConfirmacionResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.ContextoReplanificacionResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.ReplanificacionRequest;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.ReplanificacionResponse;
import pe.edu.pucp.spats.planificacion.service.ReplanificacionService;

/** Re-planificación ante incidencias (HU-18): proponer, confirmar y descartar. */
@RestController
@RequestMapping("/api")
public class ReplanificacionController {

    private final ReplanificacionService replanificacion;

    public ReplanificacionController(ReplanificacionService replanificacion) {
        this.replanificacion = replanificacion;
    }

    @GetMapping("/semanas/{lunes}/replanificacion")
    public ContextoReplanificacionResponse contexto(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate lunes) {
        return replanificacion.contexto(lunes);
    }

    @PostMapping("/semanas/{lunes}/replanificacion")
    public ReplanificacionResponse replanificar(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate lunes,
                                                @Valid @RequestBody ReplanificacionRequest solicitud) {
        return replanificacion.replanificar(lunes, solicitud);
    }

    @PostMapping("/planificaciones/{id}/confirmacion")
    public ConfirmacionResponse confirmar(@PathVariable Integer id) {
        return replanificacion.confirmar(id);
    }

    @DeleteMapping("/planificaciones/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void descartar(@PathVariable Integer id) {
        replanificacion.descartar(id);
    }
}
