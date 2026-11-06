package pe.edu.pucp.spats.auditoria.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.edu.pucp.spats.auditoria.dto.EntradaBitacoraResponse;
import pe.edu.pucp.spats.auditoria.service.BitacoraService;
import pe.edu.pucp.spats.comun.dto.PaginaResponse;

/** Trazabilidad de operaciones (HU-21). */
@RestController
@RequestMapping("/api/bitacora")
public class BitacoraController {

    private final BitacoraService servicio;

    public BitacoraController(BitacoraService servicio) { this.servicio = servicio; }

    @GetMapping
    public PaginaResponse<EntradaBitacoraResponse> consultar(@RequestParam(required = false) String entidad,
                                                             @RequestParam(defaultValue = "0") int pagina,
                                                             @RequestParam(defaultValue = "50") int tamanio) {
        return servicio.consultar(entidad, pagina, tamanio);
    }
}
