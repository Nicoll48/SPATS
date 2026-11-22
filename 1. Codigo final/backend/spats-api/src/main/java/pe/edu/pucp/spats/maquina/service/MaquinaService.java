package pe.edu.pucp.spats.maquina.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.pucp.spats.auditoria.entity.TipoOperacion;
import pe.edu.pucp.spats.auditoria.service.BitacoraService;
import pe.edu.pucp.spats.comun.dominio.Barca;
import pe.edu.pucp.spats.comun.error.ConflictoException;
import pe.edu.pucp.spats.comun.error.RecursoNoEncontradoException;
import pe.edu.pucp.spats.comun.error.ReglaNegocioException;
import pe.edu.pucp.spats.maquina.dto.MaquinaDtos.GuardarMaquinaRequest;
import pe.edu.pucp.spats.maquina.dto.MaquinaDtos.IndisponibilidadResponse;
import pe.edu.pucp.spats.maquina.dto.MaquinaDtos.MaquinaResponse;
import pe.edu.pucp.spats.maquina.dto.MaquinaDtos.RegistrarIndisponibilidadRequest;
import pe.edu.pucp.spats.maquina.entity.EstadoMaquina;
import pe.edu.pucp.spats.maquina.entity.IndisponibilidadMaquina;
import pe.edu.pucp.spats.maquina.entity.MaquinaTintoreria;
import pe.edu.pucp.spats.maquina.mapper.MaquinaMapper;
import pe.edu.pucp.spats.maquina.repository.IndisponibilidadRepository;
import pe.edu.pucp.spats.maquina.repository.MaquinaRepository;
import pe.edu.pucp.spats.partida.entity.EstadoPartida;
import pe.edu.pucp.spats.partida.entity.Partida;
import pe.edu.pucp.spats.partida.entity.TonalidadColor;
import pe.edu.pucp.spats.partida.repository.PartidaRepository;
import pe.edu.pucp.spats.planificacion.entity.PlanificacionPartida;
import pe.edu.pucp.spats.planificacion.repository.PlanificacionPartidaRepository;

/** Registro, búsqueda y modificación de máquinas (HU-07) y de sus indisponibilidades (HU-08). */
@Service
@Transactional(readOnly = true)
public class MaquinaService {

    static final String SIN_TONALIDADES = "Marca al menos una tonalidad. Si la máquina acepta todos los colores, deja todas marcadas";

    private final MaquinaRepository maquinas;
    private final IndisponibilidadRepository indisponibilidades;
    private final PartidaRepository partidas;
    private final PlanificacionPartidaRepository filasPlan;
    private final MaquinaMapper mapper;
    private final BitacoraService bitacora;
    private final Clock reloj;

    public MaquinaService(MaquinaRepository maquinas, IndisponibilidadRepository indisponibilidades,
                          PartidaRepository partidas, PlanificacionPartidaRepository filasPlan, MaquinaMapper mapper,
                          BitacoraService bitacora, Clock reloj) {
        this.maquinas = maquinas;
        this.indisponibilidades = indisponibilidades;
        this.partidas = partidas;
        this.filasPlan = filasPlan;
        this.mapper = mapper;
        this.bitacora = bitacora;
        this.reloj = reloj;
    }

    /**
     * Búsqueda por código, por clasificación de color (C/B) o por tonalidad aceptada («Oscuro», «oscuros», «cla»…),
     * con su estado de disponibilidad actual (RF18, HU-07 C2, CP021).
     */
    public List<MaquinaResponse> buscar(String texto) {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        Map<Integer, EstadoMaquina> estadoPorIndisponibilidad = indisponibilidades.queSeCruzanCon(ahora, ahora.plusSeconds(1))
                .stream().collect(Collectors.toMap(i -> i.getMaquina().getId(), i -> i.getTipo().estadoResultante(),
                        (a, b) -> a == EstadoMaquina.EN_MANTENIMIENTO ? a : b));
        String filtro = texto == null ? "" : texto.trim().toLowerCase();
        return maquinas.findAllByOrderByCodigoAsc().stream()
                .filter(m -> filtro.isEmpty() || coincide(m, filtro))
                .map(m -> mapper.aResponse(m, estadoPorIndisponibilidad.getOrDefault(m.getId(), m.getEstado())))
                .toList();
    }

    /** Una máquina nueva solo agrega compatibilidades, así que no pide confirmación (D-51). */
    @Transactional
    public MaquinaResponse registrar(GuardarMaquinaRequest solicitud) {
        if (solicitud.codigo() == null || solicitud.codigo().isBlank())
            throw new ReglaNegocioException("CODIGO_REQUERIDO", "Indica el código de la máquina.");
        String codigo = solicitud.codigo().trim();
        if (maquinas.existsByCodigoIgnoreCase(codigo))
            throw new ConflictoException("MAQUINA_DUPLICADA", "Ya existe una máquina con el código " + codigo + ".");
        Set<TonalidadColor> tonalidades = tonalidades(solicitud, EnumSet.allOf(TonalidadColor.class));
        MaquinaTintoreria maquina = maquinas.save(new MaquinaTintoreria(codigo, solicitud.capacidad(),
                solicitud.clasificacionColor(), solicitud.objetivoSemanal(),
                new Barca(solicitud.capacidad(), solicitud.clasificacionColor()).texto(), solicitud.estado(), tonalidades));
        bitacora.registrar(TipoOperacion.CREAR, "maquina_tintoreria", maquina.getId(),
                codigo + " (acepta: " + CompatibilidadMaquinas.textoTonalidades(tonalidades) + ")");
        return mapper.aResponse(maquina, maquina.getEstado());
    }

    /**
     * Modifica una máquina. Si el cambio de capacidad o de tonalidades aceptadas deja partidas sin máquina
     * compatible, o saca partidas de esta máquina en una planificación vigente abierta, pide confirmación con la
     * lista (D-51). La planificación vigente nunca se modifica aquí: el planificador re-planifica desde hoy o el
     * cambio se aplica desde la próxima planificación.
     *
     * @param confirmado el planificador ya vio el aviso
     */
    @Transactional
    public MaquinaResponse modificar(Integer id, GuardarMaquinaRequest s, boolean confirmado) {
        MaquinaTintoreria m = buscarEntidad(id);
        String barca = new Barca(s.capacidad(), s.clasificacionColor()).texto();
        Set<TonalidadColor> tonalidades = tonalidades(s, m.getTonalidadesAceptadas());
        if (!confirmado) exigirConfirmacion(m, s.capacidad(), tonalidades);
        String entidad = "maquina_tintoreria";
        bitacora.registrarCambio(entidad, id, "capacidad", m.getCapacidad(), s.capacidad());
        bitacora.registrarCambio(entidad, id, "clasificacion_color", m.getClasificacionColor(), s.clasificacionColor());
        bitacora.registrarCambio(entidad, id, "objetivo_semanal", m.getObjetivoSemanal(), s.objetivoSemanal());
        bitacora.registrarCambio(entidad, id, "barca", m.getBarca(), barca);
        bitacora.registrarCambio(entidad, id, "estado", m.getEstado(), s.estado());
        bitacora.registrarCambio(entidad, id, "tonalidades_aceptadas",
                CompatibilidadMaquinas.codigos(m.getTonalidadesAceptadas()), CompatibilidadMaquinas.codigos(tonalidades));
        m.modificar(s.capacidad(), s.clasificacionColor(), s.objetivoSemanal(), barca, s.estado(), tonalidades);
        return mapper.aResponse(m, m.getEstado());
    }

    /** Tonalidades de la solicitud; si no vienen, las indicadas. Al menos una (D-51). */
    private static Set<TonalidadColor> tonalidades(GuardarMaquinaRequest s, Set<TonalidadColor> siFaltan) {
        if (s.tonalidadesAceptadas() == null) return siFaltan;
        if (s.tonalidadesAceptadas().isEmpty()) throw new ReglaNegocioException("TONALIDADES_REQUERIDAS", SIN_TONALIDADES + ".");
        return EnumSet.copyOf(s.tonalidadesAceptadas());
    }

    private void exigirConfirmacion(MaquinaTintoreria m, BigDecimal capacidad, Set<TonalidadColor> tonalidades) {
        // 1. Partidas PENDIENTE que tenían alguna máquina compatible y se quedan sin ninguna
        List<MaquinaTintoreria> antes = maquinas.findAll();
        List<String> sinCompatible = new ArrayList<>();
        for (Partida p : partidas.findByEstadoPartidaOrderByNumeroPartida(EstadoPartida.PENDIENTE)) {
            TonalidadColor t = p.getColor().getTonalidad();
            if (!CompatibilidadMaquinas.compatible(m, p.getBarca(), t)) continue;   // esta máquina no la tenía
            if (CompatibilidadMaquinas.compatible(capacidad, tonalidades, p.getBarca(), t)) continue;
            boolean otra = antes.stream().anyMatch(o -> !o.getId().equals(m.getId())
                    && CompatibilidadMaquinas.compatible(o, p.getBarca(), t));
            if (!otra) sinCompatible.add(p.getNumeroPartida());
        }
        // 2. Partidas ubicadas en esta máquina, desde hoy, en una planificación vigente abierta, que dejan de ser compatibles
        LocalDate hoy = LocalDate.now(reloj);
        List<PlanificacionPartida> fuera = filasPlan.enVigentesAbiertasDeLaMaquina(m.getId()).stream()
                .filter(f -> f.getDiaAbastecimiento() == null || !f.getDiaAbastecimiento().isBefore(hoy))
                .filter(f -> !CompatibilidadMaquinas.compatible(capacidad, tonalidades, f.getPartida().getBarca(),
                        f.getPartida().getColor().getTonalidad()))
                .toList();
        if (sinCompatible.isEmpty() && fuera.isEmpty()) return;

        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("sinMaquinaCompatible", sinCompatible);
        datos.put("enPlanificacionVigente", fuera.stream().map(f -> Map.of(
                "partida", f.getPartida().getNumeroPartida(),
                "tonalidad", f.getPartida().getColor().getTonalidad().name(),
                "semana", f.getPlanificacion().getSemana().getFechaInicio().toString(),
                "dia", f.getDiaAbastecimiento() == null ? "" : f.getDiaAbastecimiento().toString())).toList());

        List<String> partes = new ArrayList<>();
        if (!fuera.isEmpty())
            partes.add("La planificación vigente tiene en " + m.getCodigo() + " " + cantidad(fuera.size())
                    + " que dejarían de aceptarse: " + fuera.stream().map(f -> f.getPartida().getNumeroPartida())
                    .distinct().collect(Collectors.joining(", ")) + ". La planificación vigente no cambia sola.");
        if (!sinCompatible.isEmpty())
            partes.add(cantidad(sinCompatible.size()) + " pendientes se quedarían sin ninguna máquina compatible: "
                    + String.join(", ", sinCompatible) + ". Al generar quedarán Bloqueadas.");
        throw new ConflictoException(fuera.isEmpty() ? "CONFIRMACION_REQUERIDA" : "CONFIRMACION_PLAN_VIGENTE",
                String.join(" ", partes), datos);
    }

    private static String cantidad(int n) { return n == 1 ? "1 partida" : n + " partidas"; }

    /** Elimina una máquina y sus indisponibilidades. Si ya aparece en una planificación o incidencia, se conserva (D-42). */
    @Transactional
    public void eliminar(Integer id) {
        MaquinaTintoreria m = buscarEntidad(id);
        indisponibilidades.deleteAll(indisponibilidades.findByMaquinaIdOrderByFechaHoraInicioDesc(id));
        try {
            maquinas.delete(m);
            maquinas.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictoException("MAQUINA_CON_HISTORIAL", "La máquina " + m.getCodigo()
                    + " aparece en una planificación o incidencia y no se puede eliminar. Para dejarla fuera, regístrale una indisponibilidad.");
        }
        bitacora.registrar(TipoOperacion.ELIMINAR, "maquina_tintoreria", id, m.getCodigo());
    }

    /** Indisponibilidades de todas las máquinas. */
    public List<IndisponibilidadResponse> todasLasIndisponibilidades() {
        return indisponibilidades.listarCompletas().stream().map(mapper::aResponse).toList();
    }

    public List<IndisponibilidadResponse> indisponibilidades(Integer idMaquina) {
        buscarEntidad(idMaquina);
        return indisponibilidades.findByMaquinaIdOrderByFechaHoraInicioDesc(idMaquina).stream()
                .map(mapper::aResponse).toList();
    }

    @Transactional
    public IndisponibilidadResponse registrarIndisponibilidad(Integer idMaquina, RegistrarIndisponibilidadRequest s) {
        if (!s.fechaHoraFin().isAfter(s.fechaHoraInicio()))
            throw new ReglaNegocioException("INTERVALO_INVALIDO", "El fin de la indisponibilidad debe ser posterior a su inicio.");
        MaquinaTintoreria maquina = buscarEntidad(idMaquina);
        IndisponibilidadMaquina registro = indisponibilidades.save(new IndisponibilidadMaquina(maquina,
                s.fechaHoraInicio(), s.fechaHoraFin(), s.tipo(), s.motivo().trim()));
        bitacora.registrar(TipoOperacion.REGISTRAR, "indisponibilidad_maquina", registro.getId(),
                maquina.getCodigo() + " " + s.fechaHoraInicio() + " → " + s.fechaHoraFin());
        return mapper.aResponse(registro);
    }

    @Transactional
    public void eliminarIndisponibilidad(Integer id) {
        IndisponibilidadMaquina i = indisponibilidades.findById(id).orElseThrow(() -> new RecursoNoEncontradoException(
                "INDISPONIBILIDAD_NO_ENCONTRADA", "No existe la indisponibilidad indicada."));
        bitacora.registrar(TipoOperacion.ELIMINAR, "indisponibilidad_maquina", id,
                i.getMaquina().getCodigo() + " " + i.getFechaHoraInicio() + " → " + i.getFechaHoraFin());
        indisponibilidades.delete(i);
    }

    /** Todas las máquinas registradas, para revisar la compatibilidad de las partidas (D-51). */
    public List<MaquinaTintoreria> todas() {
        return maquinas.findAll();
    }

    public Map<String, MaquinaTintoreria> porCodigo() {
        return maquinas.findAll().stream().collect(Collectors.toMap(MaquinaTintoreria::getCodigo, Function.identity()));
    }

    public MaquinaTintoreria buscarEntidad(Integer id) {
        return maquinas.findById(id).orElseThrow(() -> new RecursoNoEncontradoException(
                "MAQUINA_NO_ENCONTRADA", "No existe la máquina indicada."));
    }

    public MaquinaTintoreria buscarPorCodigo(String codigo) {
        return maquinas.findByCodigo(codigo.trim()).orElseThrow(() -> new RecursoNoEncontradoException(
                "MAQUINA_NO_ENCONTRADA", "No existe la máquina " + codigo + "."));
    }

    private static boolean coincide(MaquinaTintoreria m, String filtro) {
        return m.getCodigo().toLowerCase().contains(filtro)
                || m.getClasificacionColor().name().equalsIgnoreCase(filtro)
                || m.getClasificacionColor().etiqueta().toLowerCase().startsWith(filtro)
                || aceptaTonalidad(m, filtro);
    }

    /** Desde 3 letras, para que «C» y «B» sigan siendo la clasificación; acepta el plural («Oscuros»). */
    private static boolean aceptaTonalidad(MaquinaTintoreria m, String filtro) {
        if (filtro.length() < 3) return false;
        return m.getTonalidadesAceptadas().stream().map(t -> t.name().toLowerCase())
                .anyMatch(t -> t.startsWith(filtro) || filtro.startsWith(t));
    }
}
