package pe.edu.pucp.spats.carga.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import pe.edu.pucp.spats.carga.dto.CargaDtos.CargaEliminadaResponse;
import pe.edu.pucp.spats.carga.dto.CargaDtos.CargaHistorialResponse;
import pe.edu.pucp.spats.carga.dto.CargaDtos.CargaResponse;
import pe.edu.pucp.spats.carga.entity.TipoDato;
import pe.edu.pucp.spats.carga.service.CargaService;
import pe.edu.pucp.spats.carga.service.EliminacionCargaService;
import pe.edu.pucp.spats.carga.service.PlantillaService;
import pe.edu.pucp.spats.comun.hoja.EscritorLibro;

/** Carga de datos (HU-04), plantillas (RF09) y descarga de los datos registrados (HU-05). */
@RestController
@RequestMapping("/api")
public class CargaController {

    private final CargaService cargas;
    private final PlantillaService plantillas;
    private final EliminacionCargaService eliminacion;

    public CargaController(CargaService cargas, PlantillaService plantillas, EliminacionCargaService eliminacion) {
        this.cargas = cargas;
        this.plantillas = plantillas;
        this.eliminacion = eliminacion;
    }

    @PostMapping(value = "/cargas/{tipo}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CargaResponse importar(@PathVariable TipoDato tipo, @RequestPart("archivo") MultipartFile archivo,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate semana,
                                  @RequestParam(defaultValue = "false") boolean confirmado) {
        return cargas.importar(tipo, archivo, semana, confirmado);
    }

    @GetMapping("/cargas")
    public List<CargaHistorialResponse> historial() {
        return cargas.historial();
    }

    @DeleteMapping("/cargas/{id}")
    public CargaEliminadaResponse eliminar(@PathVariable Integer id) {
        return eliminacion.eliminar(id);
    }

    @GetMapping("/plantillas/{tipo}")
    public ResponseEntity<byte[]> plantilla(@PathVariable TipoDato tipo) {
        return descarga(plantillas.plantilla(tipo));
    }

    @GetMapping("/exportaciones/{tipo}")
    public ResponseEntity<byte[]> exportar(@PathVariable TipoDato tipo,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate semana) {
        return descarga(plantillas.exportar(tipo, semana));
    }

    private static ResponseEntity<byte[]> descarga(PlantillaService.Archivo archivo) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(archivo.nombre()).build().toString())
                .contentType(MediaType.parseMediaType(EscritorLibro.TIPO_XLSX))
                .body(archivo.contenido());
    }
}
