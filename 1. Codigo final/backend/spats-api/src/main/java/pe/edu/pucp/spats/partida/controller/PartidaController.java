package pe.edu.pucp.spats.partida.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.pucp.spats.partida.dto.PartidaDtos.ColorResponse;
import pe.edu.pucp.spats.partida.dto.PartidaDtos.ComponenteResponse;
import pe.edu.pucp.spats.partida.dto.PartidaDtos.ModificarColorRequest;
import pe.edu.pucp.spats.partida.dto.PartidaDtos.ModificarComponenteRequest;
import pe.edu.pucp.spats.partida.dto.PartidaDtos.ModificarPartidaRequest;
import pe.edu.pucp.spats.partida.dto.PartidaDtos.PartidaModificadaResponse;
import pe.edu.pucp.spats.partida.dto.PartidaDtos.PartidaResponse;
import pe.edu.pucp.spats.partida.entity.EstadoPartida;
import pe.edu.pucp.spats.partida.service.PartidaService;

@RestController
@RequestMapping("/api")
public class PartidaController {

    private final PartidaService servicio;

    public PartidaController(PartidaService servicio) { this.servicio = servicio; }

    @GetMapping("/partidas")
    public List<PartidaResponse> listar(@RequestParam(required = false) EstadoPartida estado) {
        return servicio.listar(estado);
    }

    @PutMapping("/partidas/{id}")
    public PartidaModificadaResponse modificar(@PathVariable Integer id,
                                               @Valid @RequestBody ModificarPartidaRequest solicitud,
                                               @RequestParam(defaultValue = "false") boolean confirmado) {
        return servicio.modificarPartida(id, solicitud, confirmado);
    }

    @DeleteMapping("/partidas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Integer id) { servicio.eliminarPartida(id); }

    @PutMapping("/componentes/{id}")
    public ComponenteResponse modificarComponente(@PathVariable Integer id,
                                                  @Valid @RequestBody ModificarComponenteRequest solicitud,
                                                  @RequestParam(defaultValue = "false") boolean confirmado) {
        return servicio.modificarComponente(id, solicitud, confirmado);
    }

    @GetMapping("/colores")
    public List<ColorResponse> listarColores() { return servicio.listarColores(); }

    @DeleteMapping("/colores/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarColor(@PathVariable Integer id) { servicio.eliminarColor(id); }

    @PutMapping("/colores/{id}")
    public ColorResponse modificarColor(@PathVariable Integer id, @Valid @RequestBody ModificarColorRequest solicitud) {
        return servicio.modificarColor(id, solicitud);
    }
}
