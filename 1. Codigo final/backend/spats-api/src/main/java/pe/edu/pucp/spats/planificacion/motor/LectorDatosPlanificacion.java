package pe.edu.pucp.spats.planificacion.motor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.pucp.spats.comun.error.ConflictoException;
import pe.edu.pucp.spats.comun.error.ReglaNegocioException;
import pe.edu.pucp.spats.comun.fecha.SemanaPlanificacion;
import pe.edu.pucp.spats.maquina.entity.IndisponibilidadMaquina;
import pe.edu.pucp.spats.maquina.entity.MaquinaTintoreria;
import pe.edu.pucp.spats.maquina.repository.IndisponibilidadRepository;
import pe.edu.pucp.spats.maquina.repository.MaquinaRepository;
import pe.edu.pucp.spats.partida.entity.PartidaComponente;
import pe.edu.pucp.spats.partida.repository.PartidaComponenteRepository;
import pe.edu.pucp.spats.planificacion.entity.BolsaArticulo;
import pe.edu.pucp.spats.planificacion.entity.Enumeraciones.EstadoPlanificacion;
import pe.edu.pucp.spats.planificacion.entity.PlanificacionSemanal;
import pe.edu.pucp.spats.planificacion.repository.BolsaArticuloRepository;
import pe.edu.pucp.spats.planificacion.repository.CandidatasRepository;
import pe.edu.pucp.spats.planificacion.repository.PlanificacionSemanalRepository;
import pe.edu.pucp.spats.tejeduria.entity.DetalleProyeccionTejeduria;
import pe.edu.pucp.spats.tejeduria.entity.ProyeccionTejeduria;
import pe.edu.pucp.spats.tejeduria.entity.Semana;
import pe.edu.pucp.spats.tejeduria.service.ProyeccionService;
import pe.edu.pucp.spats.tejeduria.service.SemanaService;
import spats.algoritmo.entrada.Componente;
import spats.algoritmo.entrada.DatosSemana;
import spats.algoritmo.entrada.EstadoPartida;
import spats.algoritmo.entrada.Indisponibilidad;
import spats.algoritmo.entrada.Maquina;
import spats.algoritmo.entrada.ParametrosPlanificacion;
import spats.algoritmo.entrada.Partida;
import spats.algoritmo.entrada.Pedido;
import spats.algoritmo.entrada.RegistroProyeccion;
import spats.algoritmo.entrada.StockArticulo;
import spats.algoritmo.entrada.Tonalidad;

/**
 * Primer paso de la generación (transacción de solo lectura): lee de la base todo lo que necesita el
 * algoritmo y arma {@link DatosSemana} según la sección 9.2 del contexto. El algoritmo no consulta la base.
 */
@Component
public class LectorDatosPlanificacion {

    private final SemanaService semanas;
    private final ProyeccionService proyecciones;
    private final PlanificacionSemanalRepository planificaciones;
    private final MaquinaRepository maquinas;
    private final IndisponibilidadRepository indisponibilidades;
    private final CandidatasRepository candidatas;
    private final PartidaComponenteRepository componentes;
    private final BolsaArticuloRepository bolsas;
    private final ReglasVigencia reglasVigencia;
    private final PropuestasPendientes propuestas;

    public LectorDatosPlanificacion(SemanaService semanas, ProyeccionService proyecciones,
                                    PlanificacionSemanalRepository planificaciones, MaquinaRepository maquinas,
                                    IndisponibilidadRepository indisponibilidades, CandidatasRepository candidatas,
                                    PartidaComponenteRepository componentes, BolsaArticuloRepository bolsas,
                                    ReglasVigencia reglasVigencia, PropuestasPendientes propuestas) {
        this.semanas = semanas;
        this.proyecciones = proyecciones;
        this.planificaciones = planificaciones;
        this.maquinas = maquinas;
        this.indisponibilidades = indisponibilidades;
        this.candidatas = candidatas;
        this.componentes = componentes;
        this.bolsas = bolsas;
        this.reglasVigencia = reglasVigencia;
        this.propuestas = propuestas;
    }

    /** Entrada de la generación de la semana completa (HU-11). */
    @Transactional(readOnly = true)
    public EntradaPlanificacion leer(LocalDate lunes) {
        return leer(lunes, false);
    }

    /**
     * Misma lectura como base de una re-planificación: partidas candidatas, componentes, proyección y máquinas.
     * {@code ArmadoReplanificacion} reemplaza el stock y agrega lo fijo.
     */
    @Transactional(readOnly = true)
    public EntradaPlanificacion leerParaReplanificar(LocalDate lunes) {
        return leer(lunes, true);
    }

    private EntradaPlanificacion leer(LocalDate lunes, boolean replanificacion) {
        SemanaPlanificacion periodo = new SemanaPlanificacion(lunes);
        List<String> advertencias = new ArrayList<>();

        Semana semana = semanas.buscar(lunes).orElseThrow(() -> sinDatos("SIN_PROYECCION",
                "La semana no tiene proyección de tejeduría registrada. Cárgala en Carga de datos."));
        PlanificacionSemanal vigente = planificaciones.findBySemanaIdAndEsVigenteTrue(semana.getId()).orElse(null);
        if (vigente != null && vigente.getEstado() == EstadoPlanificacion.CERRADA)
            throw new ConflictoException("SEMANA_CERRADA", "La planificación de esta semana ya se cerró; no se puede volver a generar.");
        if (!replanificacion) {
            propuestas.exigirSinPendiente(semana.getId(), "volver a generar la semana");
            semanas.buscar(periodo.anterior().lunes())
                    .ifPresent(s -> propuestas.exigirSinPendiente(s.getId(), "generar la semana siguiente"));
        }
        reglasVigencia.advertenciaSemanasPosteriores(lunes).ifPresent(advertencias::add);
        ProyeccionTejeduria proyeccion = proyecciones.vigente(semana.getId()).orElseThrow(() -> sinDatos("SIN_PROYECCION",
                "La semana no tiene proyección de tejeduría registrada. Cárgala en Carga de datos."));

        List<MaquinaTintoreria> parque = maquinas.findAllByOrderByCodigoAsc();
        if (parque.isEmpty()) throw sinDatos("SIN_MAQUINAS", "No hay máquinas de tintorería registradas.");
        List<String> sinBarca = parque.stream().filter(m -> m.getBarca() == null || m.getBarca().isBlank())
                .map(MaquinaTintoreria::getCodigo).toList();
        if (!sinBarca.isEmpty())
            throw sinDatos("MAQUINA_SIN_BARCA", "Las máquinas " + String.join(", ", sinBarca)
                    + " no tienen barca registrada. Regístrala en Máquinas antes de generar.");

        List<Maquina> maquinasAlgoritmo = parque.stream().map(m -> new Maquina(m.getCodigo(), m.getCapacidad().doubleValue(),
                m.getClasificacionColor().name(), m.getBarca(), m.getObjetivoSemanal())).toList();
        List<Indisponibilidad> diasIndisponibles = diasIndisponibles(periodo);

        // ----- partidas candidatas (sección 9.2)
        List<pe.edu.pucp.spats.partida.entity.Partida> pendientes = candidatas.candidatas(semana.getId());
        Map<Integer, List<PartidaComponente>> componentesPorPartida = pendientes.isEmpty() ? Map.of()
                : componentes.deLasPartidas(pendientes.stream().map(pe.edu.pucp.spats.partida.entity.Partida::getId).toList())
                .stream().collect(Collectors.groupingBy(c -> c.getPartida().getId()));
        List<Partida> partidas = new ArrayList<>();
        List<Componente> componentesAlgoritmo = new ArrayList<>();
        Map<String, Pedido> pedidos = new LinkedHashMap<>();
        Map<String, Integer> idPartida = new HashMap<>();
        Map<String, Integer> idArticulo = new HashMap<>();
        int orden = 0;
        for (var p : pendientes) {
            if (p.getBarca() == null || p.getBarca().isBlank()) {
                advertencias.add("La partida " + p.getNumeroPartida() + " no tiene barca; no se envió al algoritmo.");
                continue;
            }
            List<PartidaComponente> suyos = componentesPorPartida.getOrDefault(p.getId(), List.of());
            if (suyos.isEmpty()) {
                advertencias.add("La partida " + p.getNumeroPartida() + " no tiene componentes; no se envió al algoritmo.");
                continue;
            }
            partidas.add(new Partida(p.getNumeroPartida(), p.getPedido().getNumeroOp(), p.getColor().getCodigoColor(),
                    p.getColor().getNombre(), p.getPesoTotal().doubleValue(), Tonalidad.valueOf(p.getColor().getTonalidad().name()),
                    p.getBarca(), ++orden, EstadoPartida.PENDIENTE));
            idPartida.put(p.getNumeroPartida(), p.getId());
            pedidos.putIfAbsent(p.getPedido().getNumeroOp(), new Pedido(p.getPedido().getNumeroOp(),
                    p.getPedido().getCliente().getNombre(), null, p.getPedido().getFechaEntrega()));
            for (PartidaComponente c : suyos) {
                var articulo = c.getDetallePedido().getArticulo();
                idArticulo.put(articulo.getCodigo(), articulo.getId());
                componentesAlgoritmo.add(new Componente(p.getNumeroPartida(), articulo.getCodigo(), articulo.getNombre(),
                        articulo.getCodigo(), c.getTipoComponente().rolAlgoritmo(), c.getPesoRequerido().doubleValue()));
            }
        }
        if (partidas.isEmpty())
            throw sinDatos("SIN_PARTIDAS", "No hay partidas pendientes con barca y componentes para planificar esta semana.");

        // ----- proyección de la semana: del sábado anterior al viernes (sección 5)
        List<RegistroProyeccion> registros = new ArrayList<>();
        int fueraDeVentana = 0;
        for (DetalleProyeccionTejeduria d : proyecciones.detallesEntidad(proyeccion.getId())) {
            if (!periodo.enVentanaProyeccion(d.getFechaProyectada())) { fueraDeVentana++; continue; }
            var articulo = d.getDetallePedido().getArticulo();
            idArticulo.put(articulo.getCodigo(), articulo.getId());
            registros.add(new RegistroProyeccion("", d.getDetallePedido().getPedido().getNumeroOp(), articulo.getNombre(),
                    articulo.getCodigo(), d.getFechaProyectada(), d.getKgProyectados().doubleValue()));
        }
        if (fueraDeVentana > 0)
            advertencias.add(fueraDeVentana + " registros de la proyección están fuera de la ventana de tejido ("
                    + periodo.inicioVentanaProyeccion() + " a " + periodo.finVentanaProyeccion() + ") y no se consideraron.");

        // ----- stock inicial: saldo final del domingo anterior en la versión vigente (RN-03)
        List<StockArticulo> stock = new ArrayList<>();
        SemanaPlanificacion anterior = periodo.anterior();
        semanas.buscar(anterior.lunes())
                .flatMap(s -> planificaciones.findBySemanaIdAndEsVigenteTrue(s.getId()))
                .ifPresentOrElse(plan -> {
                    for (BolsaArticulo b : bolsas.deLaPlanificacionEn(plan.getId(), anterior.domingo())) {
                        if (b.getKgSaldoFinal().signum() <= 0) continue;
                        idArticulo.put(b.getArticulo().getCodigo(), b.getArticulo().getId());
                        stock.add(new StockArticulo(b.getArticulo().getCodigo(), b.getArticulo().getNombre(),
                                b.getKgSaldoFinal().doubleValue()));
                    }
                }, () -> advertencias.add("La semana anterior no tiene planificación vigente: las bolsas empiezan la semana en 0 kg."));

        DatosSemana datos = new DatosSemana(new ParametrosPlanificacion(periodo.lunes()), maquinasAlgoritmo,
                diasIndisponibles, List.copyOf(pedidos.values()), partidas, componentesAlgoritmo, registros, stock);
        Map<String, Integer> idMaquina = parque.stream().collect(Collectors.toMap(MaquinaTintoreria::getCodigo, MaquinaTintoreria::getId));
        return new EntradaPlanificacion(semana.getId(), periodo.lunes(), proyeccion.getId(),
                vigente == null ? null : vigente.getId(), datos, idMaquina, idPartida, idArticulo, List.copyOf(advertencias));
    }

    /** Un registro por cada día de la semana que toca un intervalo de indisponibilidad (D-08). */
    private List<Indisponibilidad> diasIndisponibles(SemanaPlanificacion periodo) {
        List<Indisponibilidad> dias = new ArrayList<>();
        for (IndisponibilidadMaquina i : indisponibilidades.queSeCruzanCon(periodo.lunes().atStartOfDay(),
                periodo.domingo().plusDays(1).atStartOfDay()))
            for (LocalDate dia : periodo.diasTocados(i.getFechaHoraInicio(), i.getFechaHoraFin()))
                dias.add(new Indisponibilidad(i.getMaquina().getCodigo(), dia, i.getMotivo()));
        return dias;
    }

    private static ReglaNegocioException sinDatos(String codigo, String mensaje) {
        return new ReglaNegocioException(codigo, mensaje);
    }
}
