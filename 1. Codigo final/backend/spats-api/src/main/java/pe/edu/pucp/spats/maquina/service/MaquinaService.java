package pe.edu.pucp.spats.maquina.service;

import java.time.Clock;
import java.time.LocalDateTime;
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
import pe.edu.pucp.spats.comun.dominio.ClasificacionColor;
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

/** Registro, búsqueda y modificación de máquinas (HU-07) y de sus indisponibilidades (HU-08). */
@Service
@Transactional(readOnly = true)
public class MaquinaService {

    private final MaquinaRepository maquinas;
    private final IndisponibilidadRepository indisponibilidades;
    private final MaquinaMapper mapper;
    private final BitacoraService bitacora;
    private final Clock reloj;

    public MaquinaService(MaquinaRepository maquinas, IndisponibilidadRepository indisponibilidades,
                          MaquinaMapper mapper, BitacoraService bitacora, Clock reloj) {
        this.maquinas = maquinas;
        this.indisponibilidades = indisponibilidades;
        this.mapper = mapper;
        this.bitacora = bitacora;
        this.reloj = reloj;
    }

    /** Búsqueda por código o por clasificación de color, con su estado de disponibilidad actual (RF18). */
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

    @Transactional
    public MaquinaResponse registrar(GuardarMaquinaRequest solicitud) {
        if (solicitud.codigo() == null || solicitud.codigo().isBlank())
            throw new ReglaNegocioException("CODIGO_REQUERIDO", "Indica el código de la máquina.");
        String codigo = solicitud.codigo().trim();
        if (maquinas.existsByCodigoIgnoreCase(codigo))
            throw new ConflictoException("MAQUINA_DUPLICADA", "Ya existe una máquina con el código " + codigo + ".");
        MaquinaTintoreria maquina = maquinas.save(new MaquinaTintoreria(codigo, solicitud.capacidad(),
                solicitud.clasificacionColor(), solicitud.objetivoSemanal(),
                new Barca(solicitud.capacidad(), solicitud.clasificacionColor()).texto(), solicitud.estado()));
        bitacora.registrar(TipoOperacion.CREAR, "maquina_tintoreria", maquina.getId(), codigo);
        return mapper.aResponse(maquina, maquina.getEstado());
    }

    @Transactional
    public MaquinaResponse modificar(Integer id, GuardarMaquinaRequest s) {
        MaquinaTintoreria m = buscarEntidad(id);
        String barca = new Barca(s.capacidad(), s.clasificacionColor()).texto();
        String entidad = "maquina_tintoreria";
        bitacora.registrarCambio(entidad, id, "capacidad", m.getCapacidad(), s.capacidad());
        bitacora.registrarCambio(entidad, id, "clasificacion_color", m.getClasificacionColor(), s.clasificacionColor());
        bitacora.registrarCambio(entidad, id, "objetivo_semanal", m.getObjetivoSemanal(), s.objetivoSemanal());
        bitacora.registrarCambio(entidad, id, "barca", m.getBarca(), barca);
        bitacora.registrarCambio(entidad, id, "estado", m.getEstado(), s.estado());
        m.modificar(s.capacidad(), s.clasificacionColor(), s.objetivoSemanal(), barca, s.estado());
        return mapper.aResponse(m, m.getEstado());
    }

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

    /** Barcas de las máquinas registradas, en forma normalizada. */
    public Set<String> barcasRegistradas() {
        return maquinas.findAll().stream().map(MaquinaTintoreria::getBarca)
                .filter(b -> b != null && !b.isBlank()).map(Barca::normalizar).collect(Collectors.toSet());
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
                || m.getClasificacionColor().etiqueta().toLowerCase().startsWith(filtro);
    }
}
