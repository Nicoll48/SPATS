package pe.edu.pucp.spats.partida.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.pucp.spats.auditoria.entity.TipoOperacion;
import pe.edu.pucp.spats.auditoria.service.BitacoraService;
import pe.edu.pucp.spats.comun.dominio.Barca;
import pe.edu.pucp.spats.comun.error.ConflictoException;
import pe.edu.pucp.spats.comun.error.RecursoNoEncontradoException;
import pe.edu.pucp.spats.comun.error.ReglaNegocioException;
import pe.edu.pucp.spats.maquina.service.MaquinaService;
import pe.edu.pucp.spats.partida.dto.PartidaDtos.ColorResponse;
import pe.edu.pucp.spats.partida.dto.PartidaDtos.ComponenteResponse;
import pe.edu.pucp.spats.partida.dto.PartidaDtos.ModificarColorRequest;
import pe.edu.pucp.spats.partida.dto.PartidaDtos.ModificarComponenteRequest;
import pe.edu.pucp.spats.partida.dto.PartidaDtos.ModificarPartidaRequest;
import pe.edu.pucp.spats.partida.dto.PartidaDtos.PartidaModificadaResponse;
import pe.edu.pucp.spats.partida.dto.PartidaDtos.PartidaResponse;
import pe.edu.pucp.spats.partida.entity.Color;
import pe.edu.pucp.spats.partida.entity.EstadoPartida;
import pe.edu.pucp.spats.partida.entity.Partida;
import pe.edu.pucp.spats.partida.entity.PartidaComponente;
import pe.edu.pucp.spats.partida.mapper.PartidaMapper;
import pe.edu.pucp.spats.partida.repository.ColorRepository;
import pe.edu.pucp.spats.partida.repository.PartidaComponenteRepository;
import pe.edu.pucp.spats.partida.repository.PartidaRepository;

/**
 * Consulta y modificación manual de partidas, componentes y colores (RF13, HU-06). Solo se modifican partidas
 * PENDIENTE (D-24). Si una partida modificada está Asignada o Sobrante en una planificación vigente abierta,
 * se publica {@link PartidaPorCambiarEvent}: el cambio exige confirmación y esa planificación deja de ser vigente.
 */
@Service
@Transactional(readOnly = true)
public class PartidaService {

    private final PartidaRepository partidas;
    private final PartidaComponenteRepository componentes;
    private final ColorRepository colores;
    private final MaquinaService maquinas;
    private final PartidaMapper mapper;
    private final BitacoraService bitacora;
    private final ApplicationEventPublisher eventos;

    public PartidaService(PartidaRepository partidas, PartidaComponenteRepository componentes,
                          ColorRepository colores, MaquinaService maquinas, PartidaMapper mapper,
                          BitacoraService bitacora, ApplicationEventPublisher eventos) {
        this.partidas = partidas;
        this.componentes = componentes;
        this.colores = colores;
        this.maquinas = maquinas;
        this.mapper = mapper;
        this.bitacora = bitacora;
        this.eventos = eventos;
    }

    public List<PartidaResponse> listar(EstadoPartida estado) {
        Map<Integer, List<PartidaComponente>> porPartida = componentes.listarCompletos().stream()
                .collect(Collectors.groupingBy(c -> c.getPartida().getId()));
        return partidas.listarCompletas().stream()
                .filter(p -> estado == null || p.getEstadoPartida() == estado)
                .map(p -> mapper.aResponse(p, porPartida.getOrDefault(p.getId(), List.of())))
                .toList();
    }

    public List<ColorResponse> listarColores() {
        return colores.findAllByOrderByCodigoColorAsc().stream().map(mapper::aResponse).toList();
    }

    @Transactional
    public PartidaModificadaResponse modificarPartida(Integer id, ModificarPartidaRequest s, boolean confirmado) {
        Partida partida = buscar(id);
        Color color = colores.findByCodigoColor(s.codigoColor().trim()).orElseThrow(() ->
                new ReglaNegocioException("COLOR_NO_REGISTRADO", "No existe el color " + s.codigoColor() + "."));
        String barca = Barca.validada(s.barca(), color.getClasificacionColor(), "el color");
        partida.exigirPendiente();
        boolean cambia = !color.getId().equals(partida.getColor().getId()) || !Objects.equals(barca, partida.getBarca())
                || s.pesoTotal().compareTo(partida.getPesoTotal()) != 0;
        if (cambia) eventos.publishEvent(new PartidaPorCambiarEvent(id, partida.getNumeroPartida(), confirmado));
        bitacora.registrarCambio("partida", id, "id_color", partida.getColor().getCodigoColor(), color.getCodigoColor());
        bitacora.registrarCambio("partida", id, "barca", partida.getBarca(), barca);
        bitacora.registrarCambio("partida", id, "peso_total", partida.getPesoTotal(), s.pesoTotal());
        partida.modificar(color, barca, s.pesoTotal());
        String advertencia = barca != null && !maquinas.barcasRegistradas().contains(barca)
                ? "La partida " + partida.getNumeroPartida() + " tiene la barca " + barca + " sin máquina registrada."
                : null;
        return new PartidaModificadaResponse(mapper.aResponse(partida, componentes.deLasPartidas(List.of(id))), advertencia);
    }

    @Transactional
    public ComponenteResponse modificarComponente(Integer id, ModificarComponenteRequest s, boolean confirmado) {
        PartidaComponente c = componentes.findById(id).orElseThrow(() -> new RecursoNoEncontradoException(
                "COMPONENTE_NO_ENCONTRADO", "No existe el componente indicado."));
        Partida partida = c.getPartida();
        partida.exigirPendiente();
        boolean cambia = s.tipoComponente() != c.getTipoComponente() || s.tubularAbierto() != c.isTubularAbierto()
                || s.pesoRequerido().compareTo(c.getPesoRequerido()) != 0;
        if (cambia) eventos.publishEvent(new PartidaPorCambiarEvent(partida.getId(), partida.getNumeroPartida(), confirmado));
        String entidad = "partida_componente";
        bitacora.registrarCambio(entidad, id, "tipo_componente", c.getTipoComponente(), s.tipoComponente());
        bitacora.registrarCambio(entidad, id, "tubular_abierto", c.isTubularAbierto(), s.tubularAbierto());
        bitacora.registrarCambio(entidad, id, "peso_requerido", c.getPesoRequerido(), s.pesoRequerido());
        c.modificar(s.tipoComponente(), s.tubularAbierto(), s.pesoRequerido());
        return mapper.aResponse(componentes.deLasPartidas(List.of(c.getPartida().getId())).stream()
                .filter(x -> x.getId().equals(id)).findFirst().orElse(c));
    }

    /**
     * Modifica un color. Si cambia su clasificación, las partidas pendientes que lo usan deben tener una
     * barca con la misma letra (D-07); si no, el cambio se rechaza.
     */
    @Transactional
    public ColorResponse modificarColor(Integer id, ModificarColorRequest s) {
        Color color = colores.findById(id).orElseThrow(() -> new RecursoNoEncontradoException(
                "COLOR_NO_ENCONTRADO", "No existe el color indicado."));
        if (s.clasificacionColor() != color.getClasificacionColor()) {
            Set<String> enConflicto = partidas.findByEstadoPartidaOrderByNumeroPartida(EstadoPartida.PENDIENTE).stream()
                    .filter(p -> p.getColor().getId().equals(id) && p.getBarca() != null)
                    .filter(p -> Barca.interpretar(p.getBarca()).map(b -> b.clasificacion() != s.clasificacionColor()).orElse(false))
                    .map(Partida::getNumeroPartida).collect(Collectors.toCollection(java.util.TreeSet::new));
            if (!enConflicto.isEmpty())
                throw new ReglaNegocioException("BARCA_INCOMPATIBLE", "Las partidas pendientes " + String.join(", ", enConflicto)
                        + " usan este color con una barca de otra clasificación. Corrige primero su barca.");
        }
        bitacora.registrarCambio("color", id, "nombre", color.getNombre(), s.nombre().trim());
        bitacora.registrarCambio("color", id, "clasificacion_color", color.getClasificacionColor(), s.clasificacionColor());
        bitacora.registrarCambio("color", id, "tonalidad", color.getTonalidad(), s.tonalidad());
        color.modificar(s.nombre().trim(), s.clasificacionColor(), s.tonalidad());
        return mapper.aResponse(color);
    }

    /** Elimina una partida PENDIENTE con sus componentes. Si ya aparece en una planificación o incidencia, se conserva (D-42). */
    @Transactional
    public void eliminarPartida(Integer id) {
        Partida partida = buscar(id);
        partida.exigirPendiente();
        try {
            componentes.deleteAll(componentes.findByPartidaIdIn(List.of(id)));
            partidas.delete(partida);
            partidas.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictoException("PARTIDA_EN_USO", "La partida " + partida.getNumeroPartida()
                    + " aparece en una planificación o incidencia y no se puede eliminar.");
        }
        bitacora.registrar(TipoOperacion.ELIMINAR, "partida", id, partida.getNumeroPartida());
    }

    /** Elimina un color que ninguna partida usa (D-42). */
    @Transactional
    public void eliminarColor(Integer id) {
        Color color = colores.findById(id).orElseThrow(() -> new RecursoNoEncontradoException(
                "COLOR_NO_ENCONTRADO", "No existe el color indicado."));
        try {
            colores.delete(color);
            colores.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictoException("COLOR_EN_USO", "El color " + color.getCodigoColor()
                    + " lo usan partidas registradas. Elimínalas primero.");
        }
        bitacora.registrar(TipoOperacion.ELIMINAR, "color", id, color.getCodigoColor());
    }

    public Partida buscar(Integer id) {
        return partidas.findById(id).orElseThrow(() -> new RecursoNoEncontradoException(
                "PARTIDA_NO_ENCONTRADA", "No existe la partida indicada."));
    }

    public Partida buscarPorNumero(String numero) {
        return partidas.findByNumeroPartida(numero.trim()).orElseThrow(() -> new RecursoNoEncontradoException(
                "PARTIDA_NO_ENCONTRADA", "No existe la partida " + numero + "."));
    }

}
