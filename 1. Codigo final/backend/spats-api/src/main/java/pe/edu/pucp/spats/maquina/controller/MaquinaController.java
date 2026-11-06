package pe.edu.pucp.spats.maquina.controller;

import java.util.List;

import jakarta.validation.Valid;

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

import pe.edu.pucp.spats.maquina.dto.MaquinaDtos.GuardarMaquinaRequest;
import pe.edu.pucp.spats.maquina.dto.MaquinaDtos.IndisponibilidadResponse;
import pe.edu.pucp.spats.maquina.dto.MaquinaDtos.MaquinaResponse;
import pe.edu.pucp.spats.maquina.dto.MaquinaDtos.RegistrarIndisponibilidadRequest;
import pe.edu.pucp.spats.maquina.service.MaquinaService;

@RestController
@RequestMapping("/api/maquinas")
public class MaquinaController {

    private final MaquinaService servicio;

    public MaquinaController(MaquinaService servicio) { this.servicio = servicio; }

    @GetMapping
    public List<MaquinaResponse> buscar(@RequestParam(required = false) String busqueda) {
        return servicio.buscar(busqueda);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MaquinaResponse registrar(@Valid @RequestBody GuardarMaquinaRequest solicitud) {
        return servicio.registrar(solicitud);
    }

    @PutMapping("/{id}")
    public MaquinaResponse modificar(@PathVariable Integer id, @Valid @RequestBody GuardarMaquinaRequest solicitud) {
        return servicio.modificar(id, solicitud);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Integer id) {
        servicio.eliminar(id);
    }

    @GetMapping("/indisponibilidades")
    public List<IndisponibilidadResponse> todasLasIndisponibilidades() {
        return servicio.todasLasIndisponibilidades();
    }

    @DeleteMapping("/indisponibilidades/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarIndisponibilidad(@PathVariable Integer id) {
        servicio.eliminarIndisponibilidad(id);
    }

    @GetMapping("/{id}/indisponibilidades")
    public List<IndisponibilidadResponse> indisponibilidades(@PathVariable Integer id) {
        return servicio.indisponibilidades(id);
    }

    @PostMapping("/{id}/indisponibilidades")
    @ResponseStatus(HttpStatus.CREATED)
    public IndisponibilidadResponse registrarIndisponibilidad(@PathVariable Integer id,
            @Valid @RequestBody RegistrarIndisponibilidadRequest solicitud) {
        return servicio.registrarIndisponibilidad(id, solicitud);
    }
}
