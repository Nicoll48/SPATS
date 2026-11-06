package pe.edu.pucp.spats.planificacion.motor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import pe.edu.pucp.spats.comun.error.ReglaNegocioException;
import pe.edu.pucp.spats.comun.fecha.SemanaPlanificacion;
import pe.edu.pucp.spats.incidencia.entity.TipoIncidencia;
import pe.edu.pucp.spats.planificacion.entity.Enumeraciones.ClasificacionResultado;
import pe.edu.pucp.spats.planificacion.motor.ResultadoTraducido.FilaBolsa;
import spats.algoritmo.entrada.AsignacionFija;
import spats.algoritmo.entrada.ConformacionConservada;
import spats.algoritmo.entrada.DatosSemana;
import spats.algoritmo.entrada.DisponibilidadPartida;
import spats.algoritmo.entrada.Indisponibilidad;
import spats.algoritmo.entrada.Pedido;
import spats.algoritmo.entrada.Replanificacion;
import spats.algoritmo.entrada.StockArticulo;
import spats.algoritmo.salida.AsignacionPartida;
import spats.algoritmo.salida.EstadoAsignacion;

/**
 * Arma la entrada de una re-planificación desde el día D (HU-18, RN-25) a partir de la versión vigente, sus
 * bolsas y las incidencias seleccionadas. Es Java puro: no lee la base ni decide nada que le corresponda al
 * algoritmo.
 *
 * <ul>
 *   <li><b>Fijas (grupo A)</b>: filas con {@code dia_abastecimiento < D} y las del día D que el planificador
 *       marcó como ya en máquina (D-31).</li>
 *   <li><b>Conservadas (grupo B)</b>: el resto de las filas con {@code fecha_disponible < D}.</li>
 *   <li><b>Se vuelven a decidir (grupo C)</b>: las demás partidas candidatas de la sección 9.2.</li>
 *   <li>Stock: {@code kg_saldo_inicial} del día D en la vigente.</li>
 *   <li>INCIDENCIA_MAQUINA: días indisponibles desde D hasta la fecha prevista (D-08).</li>
 *   <li>PROBLEMA_COMPLEMENTO: la partida no se ubica (B) ni se conforma (C) antes de la fecha prevista; un día
 *       parcial no cuenta (D-29). Sobre una fija no se permite (§5, punto 8).</li>
 *   <li>CAMBIO_PRIORIDAD: el pedido va con {@code nueva_fecha_entrega} (D-13b).</li>
 * </ul>
 */
public final class ArmadoReplanificacion {

    private ArmadoReplanificacion() { }

    /** Fila de la versión vigente (ASIGNADA o SOBRANTE). */
    public record FilaVigente(String partida, String pedido, String maquina, LocalDate dia,
                              ClasificacionResultado clasificacion, int ordenSecuencia, LocalDate fechaDisponible) { }

    /** Incidencia seleccionada, con su entidad afectada. */
    public record IncidenciaAplicada(Integer id, TipoIncidencia tipo, String maquina, String partida, String pedido,
                                     LocalDateTime fechaHoraDisponibilidad, LocalDate nuevaFechaEntrega) { }

    /**
     * @param datos              entrada del algoritmo con la re-planificación
     * @param alternativaVigente decisiones de la vigente para evaluarlas en el mismo escenario (RF55)
     */
    public record Armado(DatosSemana datos, List<AsignacionPartida> alternativaVigente, List<String> advertencias) { }

    public static Armado armar(DatosSemana base, LocalDate D, List<FilaVigente> vigente, List<FilaBolsa> bolsaVigente,
                               List<IncidenciaAplicada> incidencias, Set<String> enMaquina) {
        SemanaPlanificacion periodo = new SemanaPlanificacion(base.parametros().getFechaInicioSemana());
        if (!periodo.contiene(D))
            throw new ReglaNegocioException("FUERA_DE_LA_SEMANA",
                    "Solo se re-planifica la semana en curso: hoy (" + D + ") no está en la semana del " + periodo.lunes() + ".");
        List<String> advertencias = new ArrayList<>();
        List<FilaVigente> ubicadas = vigente.stream().filter(f -> f.clasificacion() != ClasificacionResultado.BLOQUEADA).toList();
        Map<String, FilaVigente> porPartida = new HashMap<>();
        ubicadas.forEach(f -> porPartida.put(f.partida(), f));
        for (String p : enMaquina) {
            FilaVigente f = porPartida.get(p);
            if (f == null || !f.dia().equals(D))
                throw new ReglaNegocioException("PARTIDA_NO_ES_DE_HOY",
                        "La partida " + p + " no está ubicada hoy en la planificación vigente; solo se marcan las de hoy.");
        }

        List<AsignacionFija> fijas = new ArrayList<>();
        Map<String, FilaVigente> conservadas = new TreeMap<>();
        for (FilaVigente f : ubicadas) {
            if (f.dia().isBefore(D) || enMaquina.contains(f.partida()))
                fijas.add(new AsignacionFija(f.partida(), f.maquina(), f.dia(), f.clasificacion() == ClasificacionResultado.SOBRANTE,
                        f.fechaDisponible(), f.ordenSecuencia()));
            else if (f.fechaDisponible().isBefore(D)) conservadas.put(f.partida(), f);
        }
        Set<String> fijasPorPartida = new LinkedHashSet<>();
        fijas.forEach(f -> fijasPorPartida.add(f.partida()));
        Set<String> candidatas = new LinkedHashSet<>();
        base.partidas().forEach(p -> candidatas.add(p.codigo()));

        // ----- incidencias
        List<Indisponibilidad> indisponibilidades = new ArrayList<>(base.indisponibilidades());
        Map<String, LocalDate> nuevaEntrega = new HashMap<>();
        Map<String, LocalDate> disponibleDesde = new HashMap<>();
        for (IncidenciaAplicada i : incidencias) {
            switch (i.tipo()) {
                case INCIDENCIA_MAQUINA -> {
                    for (LocalDate dia : periodo.diasTocados(D.atStartOfDay(), i.fechaHoraDisponibilidad()))
                        indisponibilidades.add(new Indisponibilidad(i.maquina(), dia, "Incidencia de máquina"));
                }
                case PROBLEMA_COMPLEMENTO -> {
                    if (fijasPorPartida.contains(i.partida()))
                        throw new ReglaNegocioException("INCIDENCIA_SOBRE_PARTIDA_FIJA", "La incidencia de la partida "
                                + i.partida() + " no puede considerarse: la partida ya está ubicada antes de hoy o ya entró a "
                                + "máquina, y lo anterior a hoy no cambia. Quítala de la selección.");
                    if (!candidatas.contains(i.partida())) {
                        advertencias.add("La partida " + i.partida() + " de una incidencia no está entre las que se planifican; no tiene efecto.");
                        continue;
                    }
                    disponibleDesde.merge(i.partida(), diaCompleto(i.fechaHoraDisponibilidad()), (a, b) -> a.isAfter(b) ? a : b);
                }
                case CAMBIO_PRIORIDAD -> nuevaEntrega.put(i.pedido(), i.nuevaFechaEntrega());
            }
        }
        List<Pedido> pedidos = new ArrayList<>();
        Map<String, LocalDate> entregaOriginal = new HashMap<>();
        for (Pedido p : base.pedidos()) {
            entregaOriginal.put(p.codigo(), p.fechaEntrega());
            LocalDate nueva = nuevaEntrega.get(p.codigo());
            pedidos.add(nueva == null ? p : new Pedido(p.codigo(), p.cliente(), p.fechaPedido(), nueva));
        }
        nuevaEntrega.keySet().stream().filter(p -> !entregaOriginal.containsKey(p)).forEach(p ->
                advertencias.add("El pedido " + p + " del cambio de prioridad no tiene partidas por decidir desde hoy."));

        List<ConformacionConservada> cons = new ArrayList<>();
        List<DisponibilidadPartida> disponibilidades = new ArrayList<>();
        conservadas.forEach((p, f) -> {
            LocalDate desde = disponibleDesde.getOrDefault(p, D);
            cons.add(new ConformacionConservada(p, f.fechaDisponible(), desde.isAfter(D) ? desde : D));
        });
        disponibleDesde.forEach((p, fecha) -> { if (!conservadas.containsKey(p)) disponibilidades.add(new DisponibilidadPartida(p, fecha)); });

        // ----- stock al iniciar el día D (saldo de la vigente)
        List<StockArticulo> stock = new ArrayList<>();
        for (FilaBolsa b : bolsaVigente)
            if (b.fecha().equals(D) && b.saldoInicial().signum() > 0)
                stock.add(new StockArticulo(b.codigoArticulo(), b.codigoArticulo(), b.saldoInicial().doubleValue()));

        DatosSemana datos = new DatosSemana(base.parametros(), base.maquinas(), indisponibilidades, pedidos, base.partidas(),
                base.componentes(), base.proyeccion(), stock, new Replanificacion(D, fijas, cons, disponibilidades));
        return new Armado(datos, alternativaVigente(ubicadas, entregaOriginal), List.copyOf(advertencias));
    }

    /**
     * Decisiones de la vigente con un orden de conformación reconstruido: por día de conformación y fecha de
     * entrega original (el orden global no se guarda; §5, punto 1 de la propuesta).
     */
    static List<AsignacionPartida> alternativaVigente(List<FilaVigente> ubicadas, Map<String, LocalDate> entrega) {
        LocalDate lejana = LocalDate.of(9999, 12, 31);
        List<FilaVigente> orden = new ArrayList<>(ubicadas);
        orden.sort(Comparator.comparing(FilaVigente::fechaDisponible)
                .thenComparing(f -> Objects.requireNonNullElse(entrega.get(f.pedido()), lejana))
                .thenComparing(FilaVigente::maquina).thenComparing(FilaVigente::dia).thenComparingInt(FilaVigente::ordenSecuencia));
        List<AsignacionPartida> r = new ArrayList<>();
        int k = 0;
        for (FilaVigente f : orden)
            r.add(new AsignacionPartida(f.partida(), f.pedido(), entrega.get(f.pedido()), null, f.maquina(), f.dia(),
                    f.clasificacion() == ClasificacionResultado.SOBRANTE ? EstadoAsignacion.SOBRANTE : EstadoAsignacion.ASIGNADA,
                    f.fechaDisponible(), ++k, null, false));
        return r;
    }

    /** Primer día completo desde una fecha y hora: si es a media jornada, el día siguiente (D-29). */
    static LocalDate diaCompleto(LocalDateTime fechaHora) {
        return fechaHora.toLocalTime().equals(LocalTime.MIDNIGHT) ? fechaHora.toLocalDate() : fechaHora.toLocalDate().plusDays(1);
    }
}
