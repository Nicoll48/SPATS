package pe.edu.pucp.spats.carga.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import pe.edu.pucp.spats.auditoria.entity.TipoOperacion;
import pe.edu.pucp.spats.auditoria.service.BitacoraService;
import pe.edu.pucp.spats.carga.dto.CargaDtos.CargaHistorialResponse;
import pe.edu.pucp.spats.carga.dto.CargaDtos.CargaResponse;
import pe.edu.pucp.spats.carga.entity.CargaDatos;
import pe.edu.pucp.spats.carga.entity.TipoDato;
import pe.edu.pucp.spats.carga.hoja.Plantilla;
import pe.edu.pucp.spats.carga.hoja.ValidacionLibro;
import pe.edu.pucp.spats.carga.repository.CargaDatosRepository;
import pe.edu.pucp.spats.comun.error.ArchivoInvalidoException;
import pe.edu.pucp.spats.comun.error.ProblemaValidacion;
import pe.edu.pucp.spats.comun.error.ReglaNegocioException;
import pe.edu.pucp.spats.comun.hoja.LectorLibro;
import pe.edu.pucp.spats.seguridad.repository.UsuarioRepository;
import pe.edu.pucp.spats.seguridad.service.UsuarioActual;
import pe.edu.pucp.spats.tejeduria.repository.ProyeccionRepository;

/**
 * Importación de hojas de cálculo (HU-04). Cada archivo se valida completo y se aplica en una sola
 * transacción: si tiene un error bloqueante, no se guarda ningún registro (RF10). Las cargas se registran
 * con su fecha y hora (RF14).
 */
@Service
public class CargaService {

    private final ImportadorPedidos importadorPedidos;
    private final ImportadorPartidas importadorPartidas;
    private final ImportadorProyeccion importadorProyeccion;
    private final CargaDatosRepository cargas;
    private final ProyeccionRepository proyecciones;
    private final UsuarioRepository usuarios;
    private final UsuarioActual usuarioActual;
    private final BitacoraService bitacora;
    private final Clock reloj;

    public CargaService(ImportadorPedidos importadorPedidos, ImportadorPartidas importadorPartidas,
                        ImportadorProyeccion importadorProyeccion, CargaDatosRepository cargas, ProyeccionRepository proyecciones,
                        UsuarioRepository usuarios,
                        UsuarioActual usuarioActual, BitacoraService bitacora, Clock reloj) {
        this.importadorPedidos = importadorPedidos;
        this.importadorPartidas = importadorPartidas;
        this.importadorProyeccion = importadorProyeccion;
        this.cargas = cargas;
        this.proyecciones = proyecciones;
        this.usuarios = usuarios;
        this.usuarioActual = usuarioActual;
        this.bitacora = bitacora;
        this.reloj = reloj;
    }

    /**
     * @param lunes      semana de la proyección (solo para PROYECCION_TEJEDURIA)
     * @param confirmado el planificador aceptó que la planificación vigente de la semana deje de serlo (D-06)
     */
    @Transactional
    public CargaResponse importar(TipoDato tipo, MultipartFile archivo, LocalDate lunes, boolean confirmado) {
        String nombre = archivo.getOriginalFilename() == null ? "archivo.xlsx" : archivo.getOriginalFilename();
        ValidacionLibro validacion = new ValidacionLibro(Plantilla.de(tipo), leer(archivo, nombre));
        // Se registra primero para que los datos guarden su carga; si la validación falla, todo se revierte (RF10).
        CargaDatos carga = cargas.saveAndFlush(new CargaDatos(usuarios.getReferenceById(usuarioActual.id()), tipo, nombre,
                LocalDateTime.now(reloj)));
        ResultadoImportacion resultado = switch (tipo) {
            case PEDIDOS -> importadorPedidos.importar(validacion, carga.getId());
            case PARTIDAS -> importadorPartidas.importar(validacion, carga.getId());
            case PROYECCION_TEJEDURIA -> {
                if (lunes == null) throw new ReglaNegocioException("SEMANA_REQUERIDA", "Indica la semana de la proyección.");
                yield importadorProyeccion.importar(validacion, lunes, confirmado, nombre, carga.getId());
            }
        };
        if (tipo != TipoDato.PROYECCION_TEJEDURIA)   // la proyección ya se registra como versión nueva
            bitacora.registrar(TipoOperacion.IMPORTAR, "carga_datos", carga.getId(), tipo + " " + nombre + " " + resultado.nuevos());
        return new CargaResponse(carga.getId(), tipo, nombre, carga.getFechaHora(), null, resultado.nuevos(),
                resultado.existentes(), resultado.advertencias());
    }

    @Transactional(readOnly = true)
    public List<CargaHistorialResponse> historial() {
        Set<Integer> eliminadas = proyecciones.cargasEliminadas();
        return cargas.findTop100ByOrderByFechaHoraDescIdDesc().stream().map(c -> new CargaHistorialResponse(c.getId(),
                c.getTipoDato(), c.getNombreArchivo(), c.getFechaHora(), c.getUsuario().getNombreCompleto(),
                eliminadas.contains(c.getId()))).toList();
    }

    private static Map<String, LectorLibro.Hoja> leer(MultipartFile archivo, String nombre) {
        if (!nombre.toLowerCase().endsWith(".xlsx"))
            throw new ArchivoInvalidoException("El archivo no corresponde a la plantilla.",
                    List.of(ProblemaValidacion.error("", 0, "", "El archivo debe ser una hoja de cálculo .xlsx descargada de la plantilla.")));
        try (InputStream entrada = archivo.getInputStream()) {
            return LectorLibro.leer(entrada);
        } catch (IOException | RuntimeException e) {
            throw new ArchivoInvalidoException("El archivo no se pudo leer como hoja de cálculo.",
                    List.of(ProblemaValidacion.error("", 0, "", "El archivo está dañado o no es un libro de Excel válido.")));
        }
    }
}
