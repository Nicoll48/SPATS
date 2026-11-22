package pe.edu.pucp.spats.planificacion.recomendacion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import pe.edu.pucp.spats.comun.error.RecursoNoEncontradoException;
import pe.edu.pucp.spats.comun.error.ReglaNegocioException;
import pe.edu.pucp.spats.comun.fecha.SemanaPlanificacion;
import pe.edu.pucp.spats.comun.hoja.EscritorLibro;
import pe.edu.pucp.spats.maquina.entity.MaquinaTintoreria;
import pe.edu.pucp.spats.maquina.repository.MaquinaRepository;
import pe.edu.pucp.spats.partida.entity.Partida;
import pe.edu.pucp.spats.partida.entity.PartidaComponente;
import pe.edu.pucp.spats.partida.entity.TipoComponente;
import pe.edu.pucp.spats.partida.repository.PartidaComponenteRepository;
import pe.edu.pucp.spats.pedido.entity.DetallePedido;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.DiaMaquinaResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.MaquinaPlanResponse;
import pe.edu.pucp.spats.planificacion.dto.PlanificacionDtos.PlanificacionDetalleResponse;
import pe.edu.pucp.spats.planificacion.entity.BolsaArticulo;
import pe.edu.pucp.spats.planificacion.entity.Enumeraciones.ClasificacionResultado;
import pe.edu.pucp.spats.planificacion.entity.Enumeraciones.EstadoPlanificacion;
import pe.edu.pucp.spats.planificacion.entity.PlanificacionPartida;
import pe.edu.pucp.spats.planificacion.entity.PlanificacionSemanal;
import pe.edu.pucp.spats.planificacion.entity.RecomendacionTejido;
import pe.edu.pucp.spats.planificacion.recomendacion.CalculoRecomendaciones.Candidata;
import pe.edu.pucp.spats.planificacion.recomendacion.CalculoRecomendaciones.Componente;
import pe.edu.pucp.spats.planificacion.recomendacion.CalculoRecomendaciones.Dia;
import pe.edu.pucp.spats.planificacion.recomendacion.CalculoRecomendaciones.Entrada;
import pe.edu.pucp.spats.planificacion.recomendacion.CalculoRecomendaciones.Fila;
import pe.edu.pucp.spats.planificacion.repository.BolsaArticuloRepository;
import pe.edu.pucp.spats.planificacion.repository.CandidatasRepository;
import pe.edu.pucp.spats.planificacion.repository.PlanificacionPartidaRepository;
import pe.edu.pucp.spats.planificacion.repository.PlanificacionSemanalRepository;
import pe.edu.pucp.spats.planificacion.repository.RecomendacionTejidoRepository;
import pe.edu.pucp.spats.planificacion.service.ConsultaPlanificacionService;

/**
 * Recomendaciones de tejido adicional para la misma semana, por versión de planificación (RF30, HU-13, D-56). Se
 * calculan con el resultado ya guardado de cada versión, en la misma transacción que la genera o re-planifica (RN-19).
 * No usan ni cambian el algoritmo.
 */
@Service
@Transactional(readOnly = true)
public class RecomendacionesTejidoService {

    private final RecomendacionTejidoRepository recomendaciones;
    private final PlanificacionSemanalRepository planificaciones;
    private final PlanificacionPartidaRepository filasPlan;
    private final CandidatasRepository candidatas;
    private final PartidaComponenteRepository componentes;
    private final BolsaArticuloRepository bolsas;
    private final MaquinaRepository maquinas;
    private final ConsultaPlanificacionService consulta;
    private final EntityManager em;

    public RecomendacionesTejidoService(RecomendacionTejidoRepository recomendaciones, PlanificacionSemanalRepository planificaciones,
                                        PlanificacionPartidaRepository filasPlan, CandidatasRepository candidatas,
                                        PartidaComponenteRepository componentes, BolsaArticuloRepository bolsas,
                                        MaquinaRepository maquinas, ConsultaPlanificacionService consulta,
                                        EntityManager em) {
        this.recomendaciones = recomendaciones;
        this.planificaciones = planificaciones;
        this.filasPlan = filasPlan;
        this.candidatas = candidatas;
        this.componentes = componentes;
        this.bolsas = bolsas;
        this.maquinas = maquinas;
        this.consulta = consulta;
        this.em = em;
    }

    // ------------------------------------------------------------------ cálculo

    /** Calcula (o recalcula) las recomendaciones de una versión con su propio resultado. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void calcular(PlanificacionSemanal plan) {
        em.flush();
        recomendaciones.eliminarDe(plan.getId());
        PlanificacionDetalleResponse detalle = consulta.detalle(plan.getId());
        Map<String, MaquinaTintoreria> parque = parque();

        List<CalculoRecomendaciones.Maquina> maqs = new ArrayList<>();
        int posicion = 0;
        for (MaquinaPlanResponse m : detalle.maquinas()) {
            MaquinaTintoreria mt = parque.get(m.codigo());
            if (mt != null)   // la posición en el calendario es la m del desfase de la D-48
                maqs.add(new CalculoRecomendaciones.Maquina(mt.getId(), mt.getCodigo(), mt.getCapacidad(), mt.getTonalidadesAceptadas(),
                        m.objetivoAplicado(), m.asignadas(), diasLibres(m), posicion));
            posicion++;
        }

        Set<Integer> ubicadas = new HashSet<>();
        for (PlanificacionPartida f : filasPlan.deLaPlanificacion(plan.getId()))
            if (f.getClasificacionResultado() != ClasificacionResultado.BLOQUEADA) ubicadas.add(f.getPartida().getId());
        List<Partida> pendientes = candidatas.candidatas(plan.getSemana().getId()).stream()
                .filter(p -> !ubicadas.contains(p.getId()) && p.getBarca() != null && !p.getBarca().isBlank()).toList();
        Map<Integer, List<Componente>> comps = componentesDe(pendientes.stream().map(Partida::getId).toList());
        List<Candidata> cands = pendientes.stream().filter(p -> comps.containsKey(p.getId()))
                .map(p -> new Candidata(p.getId(), p.getNumeroPartida(), p.getBarca(), p.getColor().getTonalidad(),
                        p.getPedido().getFechaEntrega(), comps.get(p.getId()))).toList();

        SemanaPlanificacion periodo = new SemanaPlanificacion(detalle.lunes());
        List<Fila> filas = CalculoRecomendaciones.calcular(new Entrada(maqs, cands, sinUsar(plan.getId(), periodo),
                periodo.inicioVentanaProyeccion(), periodo.finVentanaProyeccion(), detalle.maquinas().size(),
                detalle.lunes().get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)));
        List<RecomendacionTejido> nuevas = new ArrayList<>();
        for (Fila f : filas)
            nuevas.add(new RecomendacionTejido(plan, em.getReference(MaquinaTintoreria.class, f.idMaquina()), f.dia(),
                    em.getReference(Partida.class, f.idPartida()), em.getReference(DetallePedido.class, f.idDetallePedido()),
                    TipoComponente.valueOf(f.tipo().name()), BigDecimal.valueOf(f.kg()).setScale(3, RoundingMode.HALF_UP),
                    f.diaTejido(), f.fechaEntrega(), f.orden()));
        recomendaciones.saveAll(nuevas);
    }

    /** Al cerrar la semana solo quedan las de la vigente (D-56). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void conservarSoloVigente(Integer idSemana, Integer idVigente) {
        recomendaciones.eliminarDeLaSemanaExcepto(idSemana, idVigente);
    }

    /** Espacio libre de cada día hábil: máximo diario − partidas del día (el «2/3» del calendario). */
    private static List<Dia> diasLibres(MaquinaPlanResponse m) {
        List<Dia> dias = new ArrayList<>();
        for (DiaMaquinaResponse d : m.dias())
            if (!d.indisponible()) dias.add(new Dia(d.fecha(), Math.max(0, m.maximoDiario() - d.regulares()), d.regulares()));
        return dias;
    }

    /**
     * Tela que la versión dejó sin usar y ya estaba disponible cada día: la que hay en la bolsa ese día (saldo inicial
     * más lo que ingresa) y no se consume hasta el domingo = mínimo entre eso y los saldos finales desde ese día.
     */
    private Map<Integer, Map<LocalDate, Double>> sinUsar(Integer idPlanificacion, SemanaPlanificacion periodo) {
        Map<Integer, TreeMap<LocalDate, BolsaArticulo>> porArticulo = new HashMap<>();
        for (BolsaArticulo b : bolsas.deLaPlanificacion(idPlanificacion))
            porArticulo.computeIfAbsent(b.getArticulo().getId(), k -> new TreeMap<>()).put(b.getFecha(), b);
        Map<Integer, Map<LocalDate, Double>> r = new HashMap<>();
        porArticulo.forEach((articulo, dias) -> {
            Map<LocalDate, Double> kg = new HashMap<>();
            double minimoFinal = Double.MAX_VALUE;
            for (LocalDate d : dias.descendingKeySet()) {
                BolsaArticulo b = dias.get(d);
                minimoFinal = Math.min(minimoFinal, b.getKgSaldoFinal().doubleValue());
                double enLaBolsa = b.getKgSaldoInicial().add(b.getKgIngresados()).doubleValue();
                kg.put(d, Math.max(0, Math.min(enLaBolsa, minimoFinal)));
            }
            r.put(articulo, kg);
        });
        return r;
    }

    private Map<Integer, List<Componente>> componentesDe(Collection<Integer> idsPartida) {
        Map<Integer, List<Componente>> r = new HashMap<>();
        if (idsPartida.isEmpty()) return r;
        for (PartidaComponente c : componentes.deLasPartidas(idsPartida)) {
            DetallePedido d = c.getDetallePedido();
            r.computeIfAbsent(c.getPartida().getId(), k -> new ArrayList<>()).add(new Componente(d.getId(), d.getPedido().getNumeroOp(),
                    d.getArticulo().getId(), d.getArticulo().getCodigo(),
                    CalculoRecomendaciones.TipoComponente.valueOf(c.getTipoComponente().name()), c.getPesoRequerido().doubleValue()));
        }
        return r;
    }

    private Map<String, MaquinaTintoreria> parque() {
        return maquinas.findAllByOrderByCodigoAsc().stream()
                .collect(Collectors.toMap(MaquinaTintoreria::getCodigo, m -> m, (a, b) -> a, LinkedHashMap::new));
    }

    // ------------------------------------------------------------------ consulta

    /** @param codigoColor, color, clasificacionColor, tonalidad y kgPartida: datos de la partida para verla en el calendario */
    public record RecomendacionFila(Integer id, LocalDate diaLlena, String partida, String numeroOp, String codigoArticulo,
                                    String nombreArticulo, TipoComponente componente, BigDecimal kg, LocalDate diaTejido,
                                    LocalDate fechaEntrega, String codigoColor, String color,
                                    pe.edu.pucp.spats.comun.dominio.ClasificacionColor clasificacionColor,
                                    pe.edu.pucp.spats.partida.entity.TonalidadColor tonalidad, BigDecimal kgPartida) { }

    /**
     * @param faltan       déficit: objetivo − Asignadas
     * @param espacioLibre suma del espacio libre de sus días hábiles
     * @param seCubren     partidas que completan sus recomendaciones
     * @param mensaje      por qué no se cubre todo el déficit, o null
     */
    public record MaquinaRecomendaciones(String maquina, int faltan, int espacioLibre, int seCubren, BigDecimal kg,
                                         String mensaje, List<RecomendacionFila> recomendaciones) { }

    public record RecomendacionesResponse(Integer idPlanificacion, int numeroVersion, int versionProyeccion, boolean esVigente,
                                          boolean cerrada, BigDecimal totalKg, int totalPartidas,
                                          List<MaquinaRecomendaciones> maquinas) { }

    /**
     * Recomendaciones de una versión. El usuario Consulta solo ve las de la vigente. Una versión generada antes de
     * D-56 (sin filas) las calcula la primera vez que se consultan.
     */
    @Transactional
    public RecomendacionesResponse consultar(Integer idPlanificacion) {
        PlanificacionSemanal plan = planificaciones.findWithSemanaById(idPlanificacion).orElseThrow(() ->
                new RecursoNoEncontradoException("PLANIFICACION_NO_ENCONTRADA", "No existe la versión de planificación indicada."));
        if (!plan.isEsVigente() && !esPlanificador())
            throw new ReglaNegocioException("SOLO_VIGENTE", "Solo puedes ver las recomendaciones de la planificación vigente.");
        List<RecomendacionTejido> filas = recomendaciones.deLaPlanificacion(idPlanificacion);
        // Versiones en uso: abierta, candidata o propuesta; de una semana cerrada, solo la vigente
        boolean enUso = Set.of(EstadoPlanificacion.ABIERTA, EstadoPlanificacion.CANDIDATA, EstadoPlanificacion.PROPUESTA).contains(plan.getEstado())
                || (plan.getEstado() == EstadoPlanificacion.CERRADA && plan.isEsVigente());
        if (filas.isEmpty() && enUso) {
            calcular(plan);
            filas = recomendaciones.deLaPlanificacion(idPlanificacion);
        }
        Map<String, List<RecomendacionFila>> porMaquina = new LinkedHashMap<>();
        for (RecomendacionTejido r : filas) {
            DetallePedido d = r.getDetallePedido();
            porMaquina.computeIfAbsent(r.getMaquina().getCodigo(), k -> new ArrayList<>()).add(new RecomendacionFila(r.getId(),
                    r.getDiaLlena(), r.getPartida().getNumeroPartida(), d.getPedido().getNumeroOp(), d.getArticulo().getCodigo(),
                    d.getArticulo().getNombre(), r.getTipoComponente(), r.getKgATejer(), r.getDiaTejido(), r.getFechaEntrega(),
                    r.getPartida().getColor().getCodigoColor(), r.getPartida().getColor().getNombre(),
                    r.getPartida().getColor().getClasificacionColor(), r.getPartida().getColor().getTonalidad(), r.getPartida().getPesoTotal()));
        }
        List<MaquinaRecomendaciones> resp = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int partidas = 0;
        for (MaquinaPlanResponse m : consulta.detalle(idPlanificacion).maquinas()) {
            int faltan = Math.max(0, m.objetivoAplicado() - m.asignadas());
            if (faltan == 0) continue;
            int libre = diasLibres(m).stream().mapToInt(Dia::libre).sum();
            List<RecomendacionFila> fs = porMaquina.getOrDefault(m.codigo(), List.of());
            int cubiertas = (int) fs.stream().map(RecomendacionFila::partida).distinct().count();
            BigDecimal kg = fs.stream().map(RecomendacionFila::kg).reduce(BigDecimal.ZERO, BigDecimal::add);
            int maximo = Math.min(faltan, libre);
            String mensaje = cubiertas < maximo
                    ? "Se cubren " + cubiertas + " de " + faltan + ": no hay más partidas pendientes compatibles."
                    : cubiertas < faltan ? "Se cubren " + cubiertas + " de " + faltan + ": no hay más espacio libre en su calendario esta semana." : null;
            resp.add(new MaquinaRecomendaciones(m.codigo(), faltan, libre, cubiertas, kg, mensaje, fs));
            total = total.add(kg);
            partidas += cubiertas;
        }
        resp.sort(Comparator.comparingInt(MaquinaRecomendaciones::faltan).reversed().thenComparing(MaquinaRecomendaciones::maquina));
        return new RecomendacionesResponse(plan.getId(), plan.getNumeroVersion(), plan.getProyeccion().getVersion(), plan.isEsVigente(),
                plan.getEstado() == EstadoPlanificacion.CERRADA, total, partidas, resp);
    }

    // ------------------------------------------------------------------ exportación

    public record Archivo(String nombre, byte[] contenido) { }

    /** Excel para tejeduría: siempre de la planificación vigente, ordenado por día de tejido. */
    @Transactional
    @PreAuthorize("hasRole('PLANIFICADOR')")
    public Archivo exportar(LocalDate lunes) {
        PlanificacionSemanal vigente = planificaciones.findBySemanaFechaInicioAndEsVigenteTrue(lunes).orElseThrow(() ->
                new RecursoNoEncontradoException("SIN_PLANIFICACION_VIGENTE", "La semana no tiene una planificación vigente."));
        RecomendacionesResponse r = consultar(vigente.getId());
        List<Object[]> filas = new ArrayList<>();
        for (MaquinaRecomendaciones m : r.maquinas())
            for (RecomendacionFila f : m.recomendaciones())
                filas.add(new Object[] { f.diaTejido(), f.numeroOp(), f.codigoArticulo(), f.nombreArticulo(),
                        f.componente() == TipoComponente.PRINCIPAL ? "Principal" : "Complemento", f.kg(), f.partida(), m.maquina(),
                        f.diaLlena(), f.fechaEntrega() });
        filas.sort(Comparator.comparing((Object[] x) -> (LocalDate) x[0]).thenComparing(x -> (String) x[7]).thenComparing(x -> (LocalDate) x[8]));
        List<List<Object>> hoja = filas.stream().map(Arrays::asList).toList();
        try (EscritorLibro libro = new EscritorLibro()) {
            libro.hoja("Tejido adicional", Arrays.asList("Tejer a más tardar el", "Pedido (OP)", "Artículo", "Nombre del artículo",
                    "Componente", "Kg a tejer", "Partida que completa", "Máquina", "Día que llena", "Entrega"), hoja);
            return new Archivo("tejido_adicional_" + lunes + "_v" + r.numeroVersion() + ".xlsx", libro.aBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean esPlanificador() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getAuthorities().stream().anyMatch(g -> "ROLE_PLANIFICADOR".equals(g.getAuthority()));
    }
}
