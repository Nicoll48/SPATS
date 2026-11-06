package pe.edu.pucp.spats.incidencia.controller;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.pucp.spats.incidencia.dto.IncidenciaDtos.IncidenciaResponse;
import pe.edu.pucp.spats.incidencia.dto.IncidenciaDtos.RegistrarIncidenciaRequest;
import pe.edu.pucp.spats.incidencia.service.IncidenciaService;

@RestController
@RequestMapping("/api/semanas/{lunes}/incidencias")
public class IncidenciaController {

    private final IncidenciaService servicio;

    public IncidenciaController(IncidenciaService servicio) { this.servicio = servicio; }

    @GetMapping
    public List<IncidenciaResponse> listar(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate lunes) {
        return servicio.deLaSemana(lunes);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IncidenciaResponse registrar(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate lunes,
                                        @Valid @RequestBody RegistrarIncidenciaRequest solicitud) {
        return servicio.registrar(lunes, solicitud);
    }
}
