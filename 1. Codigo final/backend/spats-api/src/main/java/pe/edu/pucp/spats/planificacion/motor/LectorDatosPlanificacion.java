package pe.edu.pucp.spats.planificacion.motor;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import pe.edu.pucp.spats.incidencia.entity.EstadoIncidencia;
import pe.edu.pucp.spats.incidencia.entity.Incidencia;
import pe.edu.pucp.spats.incidencia.repository.IncidenciaRepository;
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
import spats.algoritmo.entrada.DisponibilidadPartida;
import spats.algoritmo.entrada.EstadoPartida;
import spats.algoritmo.entrada.Indisponibilidad;
import spats.algoritmo.entrada.Maquina;
import spats.algoritmo.entrada.ParametrosPlanificacion;
import spats.algoritmo.entrada.Partida;
import spats.algoritmo.entrada.Pedido;
import spats.algoritmo.entrada.RegistroProyeccion;
import spats.algoritmo.entrada.Replanificacion;
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
    private final IncidenciaRepository incidencias;
    private final Clock reloj;

    public LectorDatosPlanificacion(SemanaService semanas, ProyeccionService proyecciones,
                                    PlanificacionSemanalRepository planificaciones, MaquinaRepository maquinas,
                                    IndisponibilidadRepository indisponibilidades, CandidatasRepository candidatas,
                                    PartidaComponenteRepository componentes, BolsaArticuloRepository bolsas,
                                    ReglasVigencia reglasVigencia, PropuestasPendientes propuestas,
                                    IncidenciaRepository incidencias, Clock reloj) {
        this.incidencias = incidencias;
        this.reloj = reloj;
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

    /** Entrada de la generación de la semana completa (HU-11) con la versión VIGENTE de la proyección. */
    @Transactional(readOnly = true)
    public EntradaPlanificacion leer(LocalDate lunes) {
        return leer(lunes, false, null);
    }

    /**
     * Entrada de la generación de la semana completa con una versión de la proyección (D-52). Todas las versiones
     * se planifican con el mismo algoritmo, los mismos parámetros, las mismas partidas y el mismo stock inicial.
     *
     * @param idProyeccion versión que se planifica; null usa la VIGENTE
     */
    @Transactional(readOnly = true)
    public EntradaPlanificacion leer(LocalDate lunes, Integer idProyeccion) {
        return leer(lunes, false, idProyeccion);
    }

    /**
     * Misma lectura como base de una re-planificación: partidas candidatas, componentes, proyección y máquinas.
     * {@code ArmadoReplanificacion} reemplaza el stock y agrega lo fijo.
     *
     * @param idProyeccion versión de la proyección desde D: la de la vigente o, al usar otra versión como vigente
     *                     con la semana ya empezada, esa versión (D-52)
     */
    @Transactional(readOnly = true)
    public EntradaPlanificacion leerParaReplanificar(LocalDate lunes, Integer idProyeccion) {
        return leer(lunes, true, idProyeccion);
    }

    private EntradaPlanificacion leer(LocalDate lunes, boolean replanificacion, Integer idProyeccion) {
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
        ProyeccionTejeduria proyeccion = idProyeccion != null ? proyecciones.planificable(semana.getId(), idProyeccion)
                : proyecciones.vigente(semana.getId()).orElseThrow(() -> sinDatos("SIN_PROYECCION",
                        "La semana no tiene proyección de tejeduría registrada. Cárgala en Carga de datos."));

        List<MaquinaTintoreria> parque = maquinas.findAllByOrderByCodigoAsc();
        if (parque.isEmpty()) throw sinDatos("SIN_MAQUINAS", "No hay máquinas de tintorería registradas.");
        List<String> sinBarca = parque.stream().filter(m -> m.getBarca() == null || m.getBarca().isBlank())
                .map(MaquinaTintoreria::getCodigo).toList();
        if (!sinBarca.isEmpty())
            throw sinDatos("MAQUINA_SIN_BARCA", "Las máquinas " + String.join(", ", sinBarca)
                    + " no tienen barca registrada. Regístrala en Máquinas antes de generar.");

        // D-51: el algoritmo decide la compatibilidad por capacidad y tonalidades aceptadas
        List<Maquina> maquinasAlgoritmo = parque.stream().map(m -> new Maquina(m.getCodigo(), m.getCapacidad().doubleValue(),
                m.getClasificacionColor().name(), m.getBarca(), m.getObjetivoSemanal(),
                m.getTonalidadesAceptadas().stream().map(t -> Tonalidad.valueOf(t.name()))
                        .collect(Collectors.toCollection(() -> java.util.EnumSet.noneOf(Tonalidad.class))))).toList();
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

        // ----- incidencias pendientes de la semana (solo al generar la semana completa)
        List<Pedido> listaPedidos = new ArrayList<>(pedidos.values());
        List<DisponibilidadPartida> disponibilidades = new ArrayList<>();
        List<Integer> aplicadas = new ArrayList<>();
        if (!replanificacion)
            aplicadas = aplicarIncidenciasPendientes(semana.getId(), periodo, diasIndisponibles, listaPedidos,
                    disponibilidades, idPartida.keySet(), advertencias);

        // Una partida con complemento tardío no se ubica antes de su fecha: se envía como re-planificación desde el
        // lunes, sin partidas fijas ni conservadas, que el algoritmo trata igual que la semana completa.
        DatosSemana datos = new DatosSemana(new ParametrosPlanificacion(periodo.lunes()), maquinasAlgoritmo,
                diasIndisponibles, listaPedidos, partidas, componentesAlgoritmo, registros, stock,
                disponibilidades.isEmpty() ? null : new Replanificacion(periodo.lunes(), List.of(), List.of(), disponibilidades));
        Map<String, Integer> idMaquina = parque.stream().collect(Collectors.toMap(MaquinaTintoreria::getCodigo, MaquinaTintoreria::getId));
        return new EntradaPlanificacion(semana.getId(), periodo.lunes(), proyeccion.getId(),
                vigente == null ? null : vigente.getId(), datos, idMaquina, idPartida, idArticulo, List.copyOf(advertencias),
                aplicadas);
    }

    /**
     * Incidencias PENDIENTE de la semana al generarla completa. Si la semana aún no empieza, se aplican: la máquina no
     * recibe partidas los días que toca su incidencia (D-08), la partida con complemento tardío no se ubica antes del
     * primer día completo desde su fecha (D-29) y el pedido con cambio de prioridad usa su fecha nueva. Si la semana ya
     * empezó no se aplican (volver a generar rehace también los días pasados): se re-planifican desde hoy (§9.5).
     *
     * @return ids de las incidencias aplicadas
     */
    private List<Integer> aplicarIncidenciasPendientes(Integer idSemana, SemanaPlanificacion periodo, List<Indisponibilidad> dias,
                                                       List<Pedido> pedidos, List<DisponibilidadPartida> disponibilidades,
                                                       java.util.Set<String> candidatas, List<String> advertencias) {
        List<Incidencia> deLaSemana = incidencias.deLaSemana(idSemana);
        List<Incidencia> pendientes = deLaSemana.stream().filter(i -> i.getEstado() == EstadoIncidencia.PENDIENTE).toList();
        if (!LocalDate.now(reloj).isBefore(periodo.lunes())) {
            if (pendientes.isEmpty()) return List.of();
            advertencias.add("Hay " + pendientes.size() + (pendientes.size() == 1 ? " incidencia pendiente" : " incidencias pendientes")
                    + " que no se aplicaron: la semana ya empezó, así que se consideran al re-planificar desde hoy.");
            return List.of();
        }
        // D-57: también las ya consideradas al generarla antes (si no, volver a generar las «perdería»); nunca las
        // anuladas ni las cerradas sin re-planificar
        List<Incidencia> aplicables = deLaSemana.stream().filter(i -> i.getEstado() == EstadoIncidencia.PENDIENTE
                || i.getEstado() == EstadoIncidencia.CONSIDERADA).toList();
        if (aplicables.isEmpty()) return List.of();
        LocalDateTime finSemana = periodo.domingo().plusDays(1).atStartOfDay();
        List<Integer> aplicadas = new ArrayList<>();
        for (Incidencia i : aplicables) {
            LocalDateTime hasta = i.getFechaHoraDisponibilidad() == null ? finSemana : i.getFechaHoraDisponibilidad();
            switch (i.getTipoIncidencia()) {
                case INCIDENCIA_MAQUINA -> {
                    LocalDateTime desde = i.getFechaHoraOcurrencia().isBefore(periodo.lunes().atStartOfDay())
                            ? periodo.lunes().atStartOfDay() : i.getFechaHoraOcurrencia();
                    String maquina = i.getMaquina().getCodigo();
                    for (LocalDate dia : periodo.diasTocados(desde, hasta))   // un día indisponible cuenta una sola vez
                        if (dias.stream().noneMatch(x -> x.maquina().equals(maquina) && x.fecha().equals(dia)))
                            dias.add(new Indisponibilidad(maquina, dia, "Incidencia de máquina"));
                }
                case PROBLEMA_COMPLEMENTO -> {
                    String partida = i.getPartida().getNumeroPartida();
                    if (!candidatas.contains(partida)) {
                        advertencias.add("La partida " + partida + " de una incidencia no está entre las que se planifican; no tiene efecto.");
                        continue;
                    }
                    disponibilidades.add(new DisponibilidadPartida(partida, ArmadoReplanificacion.diaCompleto(hasta)));
                }
                case CAMBIO_PRIORIDAD -> {
                    String op = i.getPedido().getNumeroOp();
                    pedidos.replaceAll(p -> p.codigo().equals(op)
                            ? new Pedido(p.codigo(), p.cliente(), p.fechaPedido(), i.getNuevaFechaEntrega()) : p);
                }
            }
            aplicadas.add(i.getId());
        }
        advertencias.add("Se aplicaron " + aplicadas.size() + (aplicadas.size() == 1 ? " incidencia" : " incidencias")
                + " de la semana; al guardar la planificación como vigente las pendientes quedan como consideradas.");
        return aplicadas;
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
