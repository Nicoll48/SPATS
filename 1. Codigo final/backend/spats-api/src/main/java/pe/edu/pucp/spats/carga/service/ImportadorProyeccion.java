package pe.edu.pucp.spats.carga.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import pe.edu.pucp.spats.auditoria.entity.TipoOperacion;
import pe.edu.pucp.spats.carga.hoja.Plantilla.Columnas;
import pe.edu.pucp.spats.carga.hoja.ValidacionLibro;
import pe.edu.pucp.spats.comun.fecha.SemanaPlanificacion;
import pe.edu.pucp.spats.comun.hoja.LectorLibro;
import pe.edu.pucp.spats.pedido.entity.DetallePedido;
import pe.edu.pucp.spats.pedido.repository.DetallePedidoRepository;
import pe.edu.pucp.spats.tejeduria.entity.ProyeccionTejeduria;
import pe.edu.pucp.spats.tejeduria.service.ProyeccionService;
import pe.edu.pucp.spats.tejeduria.service.ProyeccionService.NuevoDetalle;

/**
 * Importa la proyección de tejeduría de una semana (plantilla PROYECCION_TEJEDURIA). Cada carga crea una
 * versión nueva que pasa a ser la vigente; las anteriores se conservan (D-06). Los registros con fecha de
 * tejido fuera de la ventana sábado anterior–viernes se guardan, pero se avisa que no se considerarán.
 */
@Component
public class ImportadorProyeccion {

    private static final String HOJA = "Proyeccion";

    private final DetallePedidoRepository detalles;
    private final ProyeccionService proyecciones;

    public ImportadorProyeccion(DetallePedidoRepository detalles, ProyeccionService proyecciones) {
        this.detalles = detalles;
        this.proyecciones = proyecciones;
    }

    public ResultadoImportacion importar(ValidacionLibro v, LocalDate lunes, boolean confirmado, String archivo, Integer idCarga) {
        SemanaPlanificacion semana = new SemanaPlanificacion(lunes);
        v.exigirEstructura();
        Map<String, DetallePedido> detallePorPar = detalles.listarCompletos().stream()
                .collect(Collectors.toMap(d -> d.getPedido().getNumeroOp() + "|" + d.getArticulo().getCodigo(), Function.identity()));
        List<NuevoDetalle> nuevos = new ArrayList<>();
        int fueraDeVentana = 0;
        for (LectorLibro.Fila f : v.filas(HOJA)) {
            String numeroOp = v.texto(HOJA, f, Columnas.NUMERO_OP, 50);
            String articulo = v.codigoArticulo(HOJA, f, Columnas.CODIGO_ARTICULO);
            LocalDate fecha = v.fecha(HOJA, f, Columnas.FECHA_PROYECTADA);
            BigDecimal kg = v.positivo(HOJA, f, Columnas.KG_PROYECTADOS, 9, 3);
            if (numeroOp == null || articulo == null || fecha == null || kg == null) continue;
            DetallePedido detalle = detallePorPar.get(numeroOp + "|" + articulo);
            if (detalle == null) {
                v.error(HOJA, f.numero(), Columnas.CODIGO_ARTICULO, "El artículo " + articulo
                        + " no está registrado en el pedido " + numeroOp + ". Carga primero la plantilla de pedidos.");
                continue;
            }
            if (!semana.enVentanaProyeccion(fecha)) fueraDeVentana++;
            nuevos.add(new NuevoDetalle(detalle, fecha, kg));
        }
        if (v.filas(HOJA).isEmpty()) v.error(HOJA, 0, "", "La hoja no tiene registros.");
        if (fueraDeVentana > 0)
            v.advertencia(HOJA, 0, Columnas.FECHA_PROYECTADA, fueraDeVentana + " registros tienen fecha de tejido fuera de la ventana "
                    + semana.inicioVentanaProyeccion() + " a " + semana.finVentanaProyeccion() + " y no se considerarán al planificar.");
        v.rechazarSiHayErrores();

        ProyeccionTejeduria version = proyecciones.registrarVersion(lunes, nuevos, confirmado, TipoOperacion.IMPORTAR, archivo, idCarga);
        return new ResultadoImportacion.Constructor()
                .nuevos("registros de proyección (versión " + version.getVersion() + ")", nuevos.size())
                .construir(v.advertencias());
    }
}
