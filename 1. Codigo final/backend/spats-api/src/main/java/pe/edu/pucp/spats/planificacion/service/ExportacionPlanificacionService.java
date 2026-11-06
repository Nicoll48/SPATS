package pe.edu.pucp.spats.planificacion.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.pucp.spats.comun.hoja.EscritorLibro;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.AlertaResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.ComponenteBloqueadaResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.DiaMaquinaResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.MaquinaPlanResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.PartidaBloqueadaResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.PartidaCalendarioResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.PlanificacionDetalleResponse;

/** Exportación de una versión de la planificación a .xlsx (RF41, HU-14). */
@Service
@Transactional(readOnly = true)
public class ExportacionPlanificacionService {

    private final ConsultaPlanificacionService consulta;

    public ExportacionPlanificacionService(ConsultaPlanificacionService consulta) { this.consulta = consulta; }

    public record Archivo(String nombre, byte[] contenido) { }

    public Archivo exportar(Integer idPlanificacion) {
        PlanificacionDetalleResponse plan = consulta.detalle(idPlanificacion);
        List<List<Object>> calendario = new ArrayList<>();
        List<List<Object>> resumen = new ArrayList<>();
        for (MaquinaPlanResponse m : plan.maquinas()) {
            for (DiaMaquinaResponse d : m.dias())
                for (PartidaCalendarioResponse p : d.partidas())
                    calendario.add(Arrays.asList(m.codigo(), m.barca(), d.fecha(), p.ordenSecuencia(), p.numeroPartida(),
                            p.numeroOp(), etiqueta(p.clasificacion().name()), p.color(), p.tonalidad().name(), p.kg(),
                            p.requiereLavado(), p.fechaDisponible()));
            resumen.add(Arrays.asList(m.codigo(), m.barca(), m.objetivoAplicado(), m.minimoDiario(), m.maximoDiario(),
                    m.asignadas(), m.sobrantes(), m.diferencia(), m.lavados()));
        }
        List<List<Object>> bloqueadas = new ArrayList<>();
        for (PartidaBloqueadaResponse b : plan.bloqueadas())
            for (ComponenteBloqueadaResponse c : b.componentes())
                bloqueadas.add(Arrays.asList(b.numeroPartida(), b.numeroOp(), b.fechaEntrega(), b.color(), b.barca(), b.kg(),
                        c.codigoArticulo(), c.tipoComponente().name(), c.kgRequeridos(), c.kgSaldoFinalArticulo()));
        List<List<Object>> alertas = new ArrayList<>();
        for (AlertaResponse a : plan.alertas()) alertas.add(Arrays.asList(a.maquina(), a.tipo().name(), a.cantidad(), a.mensaje()));
        var ind = plan.indicadores();
        List<List<Object>> indicadores = Arrays.asList(
                Arrays.asList("Semana", plan.lunes() + " a " + plan.domingo()),
                Arrays.asList("Versión", plan.numeroVersion()),
                Arrays.asList("Estado", plan.estado().name()),
                Arrays.asList("Fecha de generación", plan.fechaGeneracion()),
                Arrays.asList("Versión de la proyección", plan.versionProyeccion()),
                Arrays.asList("Lavados", ind.numeroLavados()),
                Arrays.asList("Partidas asignadas", plan.resumen().asignadas()),
                Arrays.asList("Partidas sobrantes", plan.resumen().sobrantes()),
                Arrays.asList("Partidas bloqueadas", plan.resumen().bloqueadas()));

        try (EscritorLibro libro = new EscritorLibro()) {
            libro.hoja("Calendario", Arrays.asList("Máquina", "Barca", "Día de abastecimiento", "Orden", "Partida", "Pedido",
                            "Clasificación", "Color", "Tonalidad", "Kg", "Requiere lavado", "Fecha disponible"), calendario)
                 .hoja("Resumen por máquina", Arrays.asList("Máquina", "Barca", "Objetivo aplicado", "Mínimo diario",
                            "Máximo diario", "Asignadas", "Sobrantes", "Diferencia con el objetivo", "Lavados"), resumen)
                 .hoja("Bloqueadas", Arrays.asList("Partida", "Pedido", "Fecha de entrega", "Color", "Barca", "Kg", "Artículo",
                            "Componente", "Kg requeridos", "Saldo final de la bolsa"), bloqueadas)
                 .hoja("Alertas", Arrays.asList("Máquina", "Tipo", "Cantidad", "Detalle"), alertas)
                 .hoja("Indicadores", Arrays.asList("Indicador", "Valor"), indicadores);
            String nombre = "planificacion_" + plan.lunes() + "_v" + plan.numeroVersion() + ".xlsx";
            return new Archivo(nombre, libro.aBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String etiqueta(String clasificacion) {
        return clasificacion.charAt(0) + clasificacion.substring(1).toLowerCase();
    }
}
