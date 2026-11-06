package pe.edu.pucp.spats.planificacion.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.pucp.spats.comun.dominio.ClasificacionColor;
import pe.edu.pucp.spats.comun.error.RecursoNoEncontradoException;
import pe.edu.pucp.spats.comun.fecha.SemanaPlanificacion;
import pe.edu.pucp.spats.maquina.entity.IndisponibilidadMaquina;
import pe.edu.pucp.spats.maquina.repository.IndisponibilidadRepository;
import pe.edu.pucp.spats.partida.entity.PartidaComponente;
import pe.edu.pucp.spats.partida.repository.PartidaComponenteRepository;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.AlertaResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.BolsaResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.CambioPartidaResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.ComparacionMaquinaResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.ComparacionResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.ComponenteBloqueadaResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.DiaMaquinaResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.DistribucionColorResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.EstadoSemanaResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.MaquinaPlanResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.PartidaBloqueadaResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.PartidaCalendarioResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.PlanificacionDetalleResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.PlanificacionResumen;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.ProyeccionResumen;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.ResumenResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.TipoAlerta;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.UbicacionResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.VersionResponse;
import pe.edu.pucp.spats.planificacion.entity.BolsaArticulo;
import pe.edu.pucp.spats.planificacion.entity.Enumeraciones.ClasificacionResultado;
import pe.edu.pucp.spats.planificacion.entity.PlanificacionMaquina;
import pe.edu.pucp.spats.planificacion.entity.PlanificacionPartida;
import pe.edu.pucp.spats.planificacion.entity.PlanificacionSemanal;
import pe.edu.pucp.spats.planificacion.mapper.PlanificacionMapper;
import pe.edu.pucp.spats.incidencia.entity.Incidencia;
import pe.edu.pucp.spats.incidencia.entity.TipoIncidencia;
import pe.edu.pucp.spats.planificacion.motor.PropuestasPendientes;
import pe.edu.pucp.spats.planificacion.motor.ReglasVigencia;
import pe.edu.pucp.spats.planificacion.repository.PlanificacionIncidenciaRepository;
import pe.edu.pucp.spats.planificacion.repository.BolsaArticuloRepository;
import pe.edu.pucp.spats.planificacion.repository.PlanificacionMaquinaRepository;
import pe.edu.pucp.spats.planificacion.repository.PlanificacionPartidaRepository;
import pe.edu.pucp.spats.planificacion.repository.PlanificacionSemanalRepository;
import pe.edu.pucp.spats.tejeduria.service.ProyeccionService;
import pe.edu.pucp.spats.tejeduria.service.SemanaService;

/**
 * Lectura de la planificación guardada: calendario por máquina y día (HU-12), clasificación de partidas,
 * desviaciones (HU-13), versiones y comparación (HU-19). Solo arma lo guardado; no recalcula indicadores.
 */
@Service
@Transactional(readOnly = true)
public class ConsultaPlanificacionService {

    private static final Locale ES = Locale.of("es", "PE");

    private final SemanaService semanas;
    private final ProyeccionService proyecciones;
    private final PlanificacionSemanalRepository planificaciones;
    private final PlanificacionPartidaRepository partidas;
    private final PlanificacionMaquinaRepository maquinas;
    private final BolsaArticuloRepository bolsas;
    private final PartidaComponenteRepository componentes;
    private final IndisponibilidadRepository indisponibilidades;
    private final PlanificacionMapper mapper;
    private final ReglasVigencia reglasVigencia;
    private final PropuestasPendientes propuestas;
    private final PlanificacionIncidenciaRepository planificacionIncidencias;

    public ConsultaPlanificacionService(SemanaService semanas, ProyeccionService proyecciones,
                                        PlanificacionSemanalRepository planificaciones,
                                        PlanificacionPartidaRepository partidas, PlanificacionMaquinaRepository maquinas,
                                        BolsaArticuloRepository bolsas, PartidaComponenteRepository componentes,
                                        IndisponibilidadRepository indisponibilidades, PlanificacionMapper mapper,
                                        ReglasVigencia reglasVigencia, PropuestasPendientes propuestas,
                                        PlanificacionIncidenciaRepository planificacionIncidencias) {
        this.semanas = semanas;
        this.proyecciones = proyecciones;
        this.planificaciones = planificaciones;
        this.partidas = partidas;
        this.maquinas = maquinas;
        this.bolsas = bolsas;
        this.componentes = componentes;
        this.indisponibilidades = indisponibilidades;
        this.mapper = mapper;
        this.reglasVigencia = reglasVigencia;
        this.propuestas = propuestas;
        this.planificacionIncidencias = planificacionIncidencias;
    }

    public EstadoSemanaResponse estadoSemana(LocalDate lunes) {
        SemanaPlanificacion periodo = new SemanaPlanificacion(lunes);
        return semanas.buscar(lunes).map(s -> {
            ProyeccionResumen proyeccion = proyecciones.vigente(s.getId())
                    .map(p -> new ProyeccionResumen(p.getId(), p.getVersion(), p.getFechaCarga())).orElse(null);
            PlanificacionSemanal vigente = planificaciones.findBySemanaIdAndEsVigenteTrue(s.getId()).orElse(null);
            PlanificacionResumen plan = vigente == null ? null : new PlanificacionResumen(vigente.getId(),
                    vigente.getNumeroVersion(), vigente.getEstado(), vigente.getFechaGeneracion(), vigente.getProyeccion().getId());
            boolean desactualizada = vigente != null && proyeccion != null && !vigente.getProyeccion().getId().equals(proyeccion.id());
            PlanificacionResumen propuesta = propuestas.pendiente(s.getId()).map(p -> new PlanificacionResumen(p.getId(),
                    p.getNumeroVersion(), p.getEstado(), p.getFechaGeneracion(), p.getProyeccion().getId())).orElse(null);
            return new EstadoSemanaResponse(periodo.lunes(), periodo.domingo(), proyeccion, plan,
                    historial(s.getId()).size(), desactualizada,
                    vigente == null ? null : reglasVigencia.stockDesactualizado(vigente).orElse(null), propuesta);
        }).orElse(new EstadoSemanaResponse(periodo.lunes(), periodo.domingo(), null, null, 0, false, null, null));
    }

    public PlanificacionDetalleResponse vigente(LocalDate lunes) {
        Integer id = semanas.buscar(lunes).flatMap(s -> planificaciones.findBySemanaIdAndEsVigenteTrue(s.getId()))
                .map(PlanificacionSemanal::getId)
                .orElseThrow(() -> new RecursoNoEncontradoException("SIN_PLANIFICACION_VIGENTE",
                        "La semana no tiene una planificación vigente."));
        return detalle(id);
    }

    public PlanificacionDetalleResponse detalle(Integer idPlanificacion) {
        PlanificacionSemanal plan = buscar(idPlanificacion);
        SemanaPlanificacion periodo = plan.getSemana().periodo();
        List<PlanificacionPartida> filas = partidas.deLaPlanificacion(idPlanificacion);
        List<PlanificacionMaquina> resumenMaquinas = maquinas.deLaPlanificacion(idPlanificacion);
        Map<String, Map<LocalDate, String>> indisponible = diasIndisponibles(periodo);
        diasDeIncidencias(plan, periodo).forEach((maquina, dias) ->
                dias.forEach((dia, motivo) -> indisponible.computeIfAbsent(maquina, k -> new LinkedHashMap<>()).putIfAbsent(dia, motivo)));

        List<MaquinaPlanResponse> maquinasResp = new ArrayList<>();
        List<AlertaResponse> alertas = new ArrayList<>();
        for (PlanificacionMaquina pm : resumenMaquinas) {
            String codigo = pm.getMaquina().getCodigo();
            Map<LocalDate, String> motivos = indisponible.getOrDefault(codigo, Map.of());
            List<DiaMaquinaResponse> dias = new ArrayList<>();
            for (LocalDate dia : periodo.dias()) {
                List<PartidaCalendarioResponse> delDia = filas.stream()
                        .filter(f -> f.conformada() && f.getMaquina().getId().equals(pm.getMaquina().getId())
                                && dia.equals(f.getDiaAbastecimiento()))
                        .map(mapper::partida).toList();
                int regulares = (int) delDia.stream().filter(p -> p.clasificacion() == ClasificacionResultado.ASIGNADA).count();
                boolean diaIndisponible = motivos.containsKey(dia);
                dias.add(new DiaMaquinaResponse(dia, diaIndisponible, motivos.get(dia), regulares, delDia));
                if (!diaIndisponible && pm.getObjetivoAplicado() > 0)
                    alertaDiaria(codigo, dia, regulares, pm).ifPresent(alertas::add);
            }
            maquinasResp.add(new MaquinaPlanResponse(codigo, pm.getMaquina().getBarca(), pm.getMaquina().getClasificacionColor(),
                    pm.getMaquina().getCapacidad(), pm.getObjetivoAplicado(), pm.getMinimoDiario(), pm.getMaximoDiario(),
                    pm.getPartidasAsignadas(), pm.getPartidasSobrantes(), pm.getDiferenciaObjetivo(), pm.getNumeroLavados(), dias));
            alertaSemanal(pm).ifPresent(alertas::add);
        }
        alertas.sort((x, y) -> Integer.compare(x.tipo().ordinal(), y.tipo().ordinal()));

        int asignadas = contar(filas, ClasificacionResultado.ASIGNADA);
        int sobrantes = contar(filas, ClasificacionResultado.SOBRANTE);
        List<PartidaBloqueadaResponse> bloqueadas = bloqueadas(plan, filas, periodo);
        int enObjetivo = (int) resumenMaquinas.stream().filter(m -> m.getDiferenciaObjetivo() == 0).count();
        ResumenResponse resumen = new ResumenResponse(asignadas, sobrantes, bloqueadas.size(), enObjetivo,
                resumenMaquinas.size(), plan.getNumeroLavados() == null ? 0 : plan.getNumeroLavados());

        return new PlanificacionDetalleResponse(plan.getId(), periodo.lunes(), periodo.domingo(), plan.getNumeroVersion(),
                plan.getTipoGeneracion(), plan.getFechaGeneracion(), plan.isEsVigente(), plan.getEstado(), plan.getFechaCierre(),
                plan.getProyeccion().getVersion(), mapper.indicadores(plan), resumen, periodo.dias(), maquinasResp, bloqueadas,
                distribucionColor(filas), alertas);
    }

    /** Versiones del historial: una propuesta sin confirmar no forma parte de él (D-26). */
    private List<PlanificacionSemanal> historial(Integer idSemana) {
        return planificaciones.findBySemanaIdOrderByNumeroVersionDesc(idSemana).stream().filter(p -> !p.propuesta()).toList();
    }

    public List<VersionResponse> versiones(LocalDate lunes) {
        List<PlanificacionSemanal> lista = semanas.buscar(lunes).map(s -> historial(s.getId())).orElse(List.of());
        Map<Integer, Map<ClasificacionResultado, Long>> conteos = conteos(lista.stream().map(PlanificacionSemanal::getId).toList());
        return lista.stream().map(p -> mapper.version(p, conteos.getOrDefault(p.getId(), Map.of()))).toList();
    }

    public List<BolsaResponse> bolsas(Integer idPlanificacion) {
        buscar(idPlanificacion);
        return bolsas.deLaPlanificacion(idPlanificacion).stream().map(b -> new BolsaResponse(b.getArticulo().getCodigo(),
                b.getArticulo().getNombre(), b.getFecha(), b.getKgSaldoInicial(), b.getKgIngresados(), b.getKgDevueltos(),
                b.getKgConsumidos(), b.getKgSaldoFinal())).toList();
    }

    /** Comparación de dos versiones (RF57): indicadores, carga por máquina y partidas que cambiaron. */
    public ComparacionResponse comparar(Integer idA, Integer idB) {
        PlanificacionSemanal a = buscar(idA), b = buscar(idB);
        Map<Integer, Map<ClasificacionResultado, Long>> conteos = conteos(List.of(idA, idB));
        Map<String, PlanificacionMaquina> maqA = porCodigo(maquinas.deLaPlanificacion(idA));
        Map<String, PlanificacionMaquina> maqB = porCodigo(maquinas.deLaPlanificacion(idB));
        TreeSet<String> codigos = new TreeSet<>(maqA.keySet());
        codigos.addAll(maqB.keySet());
        List<ComparacionMaquinaResponse> porMaquina = codigos.stream().map(c -> {
            PlanificacionMaquina x = maqA.get(c), y = maqB.get(c);
            return new ComparacionMaquinaResponse(c, x == null ? null : x.getPartidasAsignadas(), x == null ? null : x.getPartidasSobrantes(),
                    x == null ? null : x.getDiferenciaObjetivo(), y == null ? null : y.getPartidasAsignadas(),
                    y == null ? null : y.getPartidasSobrantes(), y == null ? null : y.getDiferenciaObjetivo());
        }).toList();

        Map<String, UbicacionResponse> ubiA = ubicaciones(partidas.deLaPlanificacion(idA));
        Map<String, UbicacionResponse> ubiB = ubicaciones(partidas.deLaPlanificacion(idB));
        TreeSet<String> numeros = new TreeSet<>(ubiA.keySet());
        numeros.addAll(ubiB.keySet());
        List<CambioPartidaResponse> cambios = numeros.stream().filter(n -> !Objects.equals(ubiA.get(n), ubiB.get(n)))
                .map(n -> new CambioPartidaResponse(n, ubiA.get(n), ubiB.get(n))).toList();
        return new ComparacionResponse(mapper.version(a, conteos.getOrDefault(idA, Map.of())),
                mapper.version(b, conteos.getOrDefault(idB, Map.of())), porMaquina, cambios);
    }

    public PlanificacionSemanal buscar(Integer id) {
        return planificaciones.findWithSemanaById(id).orElseThrow(() -> new RecursoNoEncontradoException(
                "PLANIFICACION_NO_ENCONTRADA", "No existe la versión de planificación indicada."));
    }

    // ------------------------------------------------------------------ apoyo
    private List<PartidaBloqueadaResponse> bloqueadas(PlanificacionSemanal plan, List<PlanificacionPartida> filas,
                                                      SemanaPlanificacion periodo) {
        List<PlanificacionPartida> bloqueadas = filas.stream()
                .filter(f -> f.getClasificacionResultado() == ClasificacionResultado.BLOQUEADA).toList();
        if (bloqueadas.isEmpty()) return List.of();
        Map<Integer, List<PartidaComponente>> comps = componentes.deLasPartidas(
                bloqueadas.stream().map(f -> f.getPartida().getId()).toList()).stream()
                .collect(Collectors.groupingBy(c -> c.getPartida().getId()));
        Map<String, BigDecimal> saldoDomingo = bolsas.deLaPlanificacionEn(plan.getId(), periodo.domingo()).stream()
                .collect(Collectors.toMap(b -> b.getArticulo().getCodigo(), BolsaArticulo::getKgSaldoFinal));
        return bloqueadas.stream().map(f -> {
            var p = f.getPartida();
            List<ComponenteBloqueadaResponse> suyos = comps.getOrDefault(p.getId(), List.of()).stream()
                    .map(c -> new ComponenteBloqueadaResponse(c.getDetallePedido().getArticulo().getCodigo(),
                            c.getDetallePedido().getArticulo().getNombre(), c.getTipoComponente(), c.getPesoRequerido(),
                            saldoDomingo.getOrDefault(c.getDetallePedido().getArticulo().getCodigo(), BigDecimal.ZERO)))
                    .toList();
            return new PartidaBloqueadaResponse(p.getNumeroPartida(), p.getPedido().getNumeroOp(), p.getPedido().getFechaEntrega(),
                    p.getColor().getNombre(), p.getColor().getTonalidad(), p.getBarca(), p.getPesoTotal(), suyos);
        }).toList();
    }

    private static List<DistribucionColorResponse> distribucionColor(List<PlanificacionPartida> filas) {
        Map<ClasificacionColor, int[]> conteo = new EnumMap<>(ClasificacionColor.class);
        for (ClasificacionColor c : ClasificacionColor.values()) conteo.put(c, new int[2]);
        for (PlanificacionPartida f : filas) {
            if (!f.conformada()) continue;
            int[] c = conteo.get(f.getPartida().getColor().getClasificacionColor());
            c[f.getClasificacionResultado() == ClasificacionResultado.ASIGNADA ? 0 : 1]++;
        }
        return conteo.entrySet().stream().map(e -> new DistribucionColorResponse(e.getKey(), e.getValue()[0], e.getValue()[1])).toList();
    }

    private static java.util.Optional<AlertaResponse> alertaSemanal(PlanificacionMaquina pm) {
        int dif = pm.getDiferenciaObjetivo();
        String codigo = pm.getMaquina().getCodigo();
        int total = pm.getPartidasAsignadas() + pm.getPartidasSobrantes();
        if (dif < 0) return java.util.Optional.of(new AlertaResponse(TipoAlerta.FALTANTE, codigo, -dif,
                codigo + ": faltan " + -dif + " partidas para su objetivo semanal (" + total + " de " + pm.getObjetivoAplicado() + ")."));
        if (dif > 0) return java.util.Optional.of(new AlertaResponse(TipoAlerta.EXCEDENTE, codigo, dif,
                codigo + ": excede en " + dif + " partidas su objetivo semanal (" + total + " de " + pm.getObjetivoAplicado() + ")."));
        return java.util.Optional.empty();
    }

    private static java.util.Optional<AlertaResponse> alertaDiaria(String codigo, LocalDate dia, int regulares, PlanificacionMaquina pm) {
        String nombreDia = dia.getDayOfWeek().getDisplayName(TextStyle.FULL, ES) + " " + dia.getDayOfMonth();
        if (regulares < pm.getMinimoDiario())
            return java.util.Optional.of(new AlertaResponse(TipoAlerta.DESBALANCE_DIARIO, codigo, pm.getMinimoDiario() - regulares,
                    codigo + ": el " + nombreDia + " recibe " + regulares + " partidas (mínimo diario " + pm.getMinimoDiario() + ")."));
        if (regulares > pm.getMaximoDiario())
            return java.util.Optional.of(new AlertaResponse(TipoAlerta.DESBALANCE_DIARIO, codigo, regulares - pm.getMaximoDiario(),
                    codigo + ": el " + nombreDia + " recibe " + regulares + " partidas (máximo diario " + pm.getMaximoDiario() + ")."));
        return java.util.Optional.empty();
    }

    /**
     * Días en que una re-planificación tuvo una máquina fuera por una incidencia: desde su día D hasta la fecha
     * prevista de disponibilidad (D-08). No se guardan en {@code indisponibilidad_maquina}; salen de
     * {@code planificacion_incidencia}.
     */
    private Map<String, Map<LocalDate, String>> diasDeIncidencias(PlanificacionSemanal plan, SemanaPlanificacion periodo) {
        Map<String, Map<LocalDate, String>> r = new HashMap<>();
        if (plan.getTipoGeneracion() != pe.edu.pucp.spats.planificacion.entity.Enumeraciones.TipoGeneracion.REPLANIFICACION) return r;
        LocalDate D = plan.getFechaGeneracion().toLocalDate();
        for (Incidencia i : planificacionIncidencias.incidenciasDe(plan.getId()))
            if (i.getTipoIncidencia() == TipoIncidencia.INCIDENCIA_MAQUINA && i.getFechaHoraDisponibilidad() != null)
                for (LocalDate dia : periodo.diasTocados(D.atStartOfDay(), i.getFechaHoraDisponibilidad()))
                    r.computeIfAbsent(i.getMaquina().getCodigo(), k -> new LinkedHashMap<>())
                     .putIfAbsent(dia, "Incidencia: " + (i.getDescripcion() == null ? "máquina fuera de servicio" : i.getDescripcion()));
        return r;
    }

    /** Días con indisponibilidad registrada por máquina (D-08). */
    private Map<String, Map<LocalDate, String>> diasIndisponibles(SemanaPlanificacion periodo) {
        Map<String, Map<LocalDate, String>> r = new HashMap<>();
        for (IndisponibilidadMaquina i : indisponibilidades.queSeCruzanCon(periodo.lunes().atStartOfDay(),
                periodo.domingo().plusDays(1).atStartOfDay()))
            for (LocalDate dia : periodo.diasTocados(i.getFechaHoraInicio(), i.getFechaHoraFin()))
                r.computeIfAbsent(i.getMaquina().getCodigo(), k -> new LinkedHashMap<>())
                 .putIfAbsent(dia, i.getMotivo());
        return r;
    }

    private Map<Integer, Map<ClasificacionResultado, Long>> conteos(List<Integer> ids) {
        Map<Integer, Map<ClasificacionResultado, Long>> r = new HashMap<>();
        if (ids.isEmpty()) return r;
        for (var c : partidas.contarPorClasificacion(ids))
            r.computeIfAbsent(c.getIdPlanificacion(), k -> new EnumMap<>(ClasificacionResultado.class))
             .put(c.getClasificacion(), c.getCantidad());
        return r;
    }

    private static int contar(List<PlanificacionPartida> filas, ClasificacionResultado c) {
        return (int) filas.stream().filter(f -> f.getClasificacionResultado() == c).count();
    }

    private static Map<String, PlanificacionMaquina> porCodigo(List<PlanificacionMaquina> lista) {
        return lista.stream().collect(Collectors.toMap(m -> m.getMaquina().getCodigo(), m -> m));
    }

    private static Map<String, UbicacionResponse> ubicaciones(List<PlanificacionPartida> filas) {
        Map<String, UbicacionResponse> r = new HashMap<>();
        for (PlanificacionPartida f : filas)
            r.put(f.getPartida().getNumeroPartida(), new UbicacionResponse(
                    f.getMaquina() == null ? null : f.getMaquina().getCodigo(), f.getDiaAbastecimiento(), f.getClasificacionResultado()));
        return r;
    }
}
