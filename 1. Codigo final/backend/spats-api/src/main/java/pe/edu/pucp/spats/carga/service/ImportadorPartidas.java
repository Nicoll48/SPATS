package pe.edu.pucp.spats.carga.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import pe.edu.pucp.spats.carga.hoja.Plantilla.Columnas;
import pe.edu.pucp.spats.carga.hoja.ValidacionLibro;
import pe.edu.pucp.spats.comun.dominio.Barca;
import pe.edu.pucp.spats.comun.dominio.ClasificacionColor;
import pe.edu.pucp.spats.comun.hoja.LectorLibro;
import pe.edu.pucp.spats.maquina.entity.MaquinaTintoreria;
import pe.edu.pucp.spats.maquina.service.CompatibilidadMaquinas;
import pe.edu.pucp.spats.maquina.service.MaquinaService;
import pe.edu.pucp.spats.pedido.entity.DetallePedido;
import pe.edu.pucp.spats.pedido.entity.Pedido;
import pe.edu.pucp.spats.pedido.repository.DetallePedidoRepository;
import pe.edu.pucp.spats.pedido.repository.PedidoRepository;
import pe.edu.pucp.spats.partida.entity.Color;
import pe.edu.pucp.spats.partida.entity.Partida;
import pe.edu.pucp.spats.partida.entity.PartidaComponente;
import pe.edu.pucp.spats.partida.entity.TipoComponente;
import pe.edu.pucp.spats.partida.entity.TonalidadColor;
import pe.edu.pucp.spats.partida.repository.ColorRepository;
import pe.edu.pucp.spats.partida.repository.PartidaRepository;

/**
 * Importa partidas, colores y componentes (plantilla PARTIDAS). Toda partida nueva entra como PENDIENTE.
 * Reglas de validación:
 * <ul>
 *   <li>el pedido de la partida ya debe estar registrado; cada componente, ser un artículo de ese pedido;</li>
 *   <li>la barca empieza con un número, la capacidad (D-51); su letra es informativa y no se valida;</li>
 *   <li>cada partida tiene al menos un componente PRINCIPAL;</li>
 *   <li>si ninguna máquina de su capacidad acepta su tonalidad, la partida se acepta con un aviso: al generar
 *       quedará Bloqueada mientras siga así, porque la configuración de las máquinas puede cambiar (D-51).</li>
 * </ul>
 */
@Component
public class ImportadorPartidas {

    private static final String HOJA_PARTIDAS = "Partidas";
    private static final String HOJA_COMPONENTES = "Componentes";
    private static final Map<String, ClasificacionColor> CLASIFICACION = Map.of("COLOR", ClasificacionColor.C, "BLANCO", ClasificacionColor.B);

    private final PartidaRepository partidas;
    private final ColorRepository colores;
    private final PedidoRepository pedidos;
    private final DetallePedidoRepository detalles;
    private final MaquinaService maquinas;
    private final Clock reloj;

    public ImportadorPartidas(PartidaRepository partidas, ColorRepository colores, PedidoRepository pedidos,
                              DetallePedidoRepository detalles, MaquinaService maquinas, Clock reloj) {
        this.partidas = partidas;
        this.colores = colores;
        this.pedidos = pedidos;
        this.detalles = detalles;
        this.maquinas = maquinas;
        this.reloj = reloj;
    }

    private record FilaPartida(int numero, String numeroPartida, String numeroOp, String codigoColor, String nombreColor,
                               ClasificacionColor clasificacion, TonalidadColor tonalidad, String barca, BigDecimal peso) { }

    private record FilaComponente(int numero, String numeroPartida, String codigoArticulo, TipoComponente tipo,
                                  boolean tubular, BigDecimal peso) { }

    public ResultadoImportacion importar(ValidacionLibro v, Integer idCarga) {
        v.exigirEstructura();
        List<FilaPartida> filasPartida = leerPartidas(v);
        List<FilaComponente> filasComponente = leerComponentes(v);
        v.rechazarSiHayErrores();

        Map<String, Partida> existentes = partidas.findByNumeroPartidaIn(filasPartida.stream().map(FilaPartida::numeroPartida).toList())
                .stream().collect(Collectors.toMap(Partida::getNumeroPartida, Function.identity()));
        Map<String, Pedido> pedidosRegistrados = pedidos.findByNumeroOpIn(filasPartida.stream().map(FilaPartida::numeroOp).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Pedido::getNumeroOp, Function.identity()));
        Map<String, DetallePedido> detallePorPar = detalles.listarCompletos().stream()
                .collect(Collectors.toMap(d -> d.getPedido().getNumeroOp() + "|" + d.getArticulo().getCodigo(), Function.identity()));
        Map<String, Color> coloresRegistrados = colores.findByCodigoColorIn(filasPartida.stream().map(FilaPartida::codigoColor).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Color::getCodigoColor, Function.identity()));
        List<MaquinaTintoreria> parque = maquinas.todas();

        // ----- validación cruzada
        Map<String, FilaPartida> nuevas = new LinkedHashMap<>();
        Map<String, FilaPartida> colorEnArchivo = new HashMap<>();
        for (FilaPartida f : filasPartida) {
            if (nuevas.containsKey(f.numeroPartida())) {
                v.error(HOJA_PARTIDAS, f.numero(), Columnas.NUMERO_PARTIDA, "La partida " + f.numeroPartida() + " se repite en el archivo.");
                continue;
            }
            if (existentes.containsKey(f.numeroPartida())) {
                v.advertencia(HOJA_PARTIDAS, f.numero(), Columnas.NUMERO_PARTIDA, "La partida " + f.numeroPartida()
                        + " ya estaba registrada; se conservó sin cambios.");
                continue;
            }
            if (!pedidosRegistrados.containsKey(f.numeroOp()))
                v.error(HOJA_PARTIDAS, f.numero(), Columnas.NUMERO_OP, "El pedido " + f.numeroOp()
                        + " no está registrado. Carga primero la plantilla de pedidos.");
            validarColor(v, f, coloresRegistrados.get(f.codigoColor()), colorEnArchivo);
            avisarSinMaquinaCompatible(v, f, coloresRegistrados.get(f.codigoColor()), parque);
            nuevas.put(f.numeroPartida(), f);
        }
        Map<String, List<FilaComponente>> componentesPorPartida = new LinkedHashMap<>();
        for (FilaComponente c : filasComponente) {
            if (existentes.containsKey(c.numeroPartida())) continue;   // la partida ya existía: se conserva tal cual
            FilaPartida partida = nuevas.get(c.numeroPartida());
            if (partida == null) {
                v.error(HOJA_COMPONENTES, c.numero(), Columnas.NUMERO_PARTIDA, "La partida " + c.numeroPartida()
                        + " no está en la hoja Partidas.");
                continue;
            }
            if (!detallePorPar.containsKey(partida.numeroOp() + "|" + c.codigoArticulo()))
                v.error(HOJA_COMPONENTES, c.numero(), Columnas.CODIGO_ARTICULO, "El artículo " + c.codigoArticulo()
                        + " no está registrado en el pedido " + partida.numeroOp() + ".");
            componentesPorPartida.computeIfAbsent(c.numeroPartida(), k -> new ArrayList<>()).add(c);
        }
        for (FilaPartida f : nuevas.values()) {
            List<FilaComponente> suyos = componentesPorPartida.getOrDefault(f.numeroPartida(), List.of());
            if (suyos.stream().noneMatch(c -> c.tipo() == TipoComponente.PRINCIPAL))
                v.error(HOJA_PARTIDAS, f.numero(), Columnas.NUMERO_PARTIDA, "La partida " + f.numeroPartida()
                        + " no tiene un componente PRINCIPAL en la hoja Componentes.");
        }
        v.rechazarSiHayErrores();

        // ----- aplicación
        ResultadoImportacion.Constructor resultado = new ResultadoImportacion.Constructor();
        for (int i = 0; i < existentes.size(); i++) resultado.existente();
        LocalDateTime registro = LocalDateTime.now(reloj);
        for (FilaPartida f : nuevas.values()) {
            Color color = coloresRegistrados.computeIfAbsent(f.codigoColor(), c -> {
                resultado.nuevo("colores");
                return colores.save(new Color(c, f.nombreColor(), f.clasificacion(), f.tonalidad()));
            });
            Partida partida = new Partida(pedidosRegistrados.get(f.numeroOp()), color, f.numeroPartida(), f.barca(), f.peso(), registro);
            partida.asociarCarga(idCarga);
            for (FilaComponente c : componentesPorPartida.get(f.numeroPartida()))
                partida.agregarComponente(new PartidaComponente(partida, detallePorPar.get(f.numeroOp() + "|" + c.codigoArticulo()),
                        c.tipo(), c.tubular(), c.peso()));
            partidas.save(partida);   // en orden de archivo: el id define el orden de registro dentro de la carga
            resultado.nuevo("partidas").nuevos("componentes", partida.getComponentes().size());
        }
        return resultado.construir(v.advertencias());
    }

    /** D-51: sin máquina de su capacidad que acepte su tonalidad, se acepta con aviso. Si el color ya existe, manda su tonalidad registrada. */
    private static void avisarSinMaquinaCompatible(ValidacionLibro v, FilaPartida f, Color registrado, List<MaquinaTintoreria> parque) {
        if (f.barca() == null) return;
        TonalidadColor tonalidad = registrado != null ? registrado.getTonalidad() : f.tonalidad();
        if (!CompatibilidadMaquinas.hayCompatible(parque, f.barca(), tonalidad))
            v.advertencia(HOJA_PARTIDAS, f.numero(), Columnas.BARCA,
                    CompatibilidadMaquinas.avisoSinCompatible(f.numeroPartida(), tonalidad, f.barca()));
    }

    private static void validarColor(ValidacionLibro v, FilaPartida f, Color registrado, Map<String, FilaPartida> colorEnArchivo) {
        if (registrado != null) {
            if (registrado.getClasificacionColor() != f.clasificacion())
                v.error(HOJA_PARTIDAS, f.numero(), Columnas.CLASIFICACION_COLOR, "El color " + f.codigoColor()
                        + " está registrado con clasificación " + registrado.getClasificacionColor().name() + ".");
            else if (registrado.getTonalidad() != f.tonalidad() || !registrado.getNombre().equals(f.nombreColor()))
                v.advertencia(HOJA_PARTIDAS, f.numero(), Columnas.CODIGO_COLOR, "El color " + f.codigoColor()
                        + " ya estaba registrado con otro nombre o tonalidad; se conservó lo registrado.");
            return;
        }
        FilaPartida primera = colorEnArchivo.putIfAbsent(f.codigoColor(), f);
        if (primera != null && (primera.clasificacion() != f.clasificacion() || primera.tonalidad() != f.tonalidad()
                || !primera.nombreColor().equals(f.nombreColor())))
            v.error(HOJA_PARTIDAS, f.numero(), Columnas.CODIGO_COLOR, "El color " + f.codigoColor()
                    + " tiene otros datos que en la fila " + primera.numero() + ".");
    }

    private List<FilaPartida> leerPartidas(ValidacionLibro v) {
        List<FilaPartida> filas = new ArrayList<>();
        for (LectorLibro.Fila f : v.filas(HOJA_PARTIDAS)) {
            String barcaTexto = f.valor(Columnas.BARCA);
            String barca = null;
            if (!barcaTexto.isEmpty()) {
                if (Barca.capacidadDe(barcaTexto).isPresent()) barca = Barca.normalizar(barcaTexto);   // D-51
                else v.error(HOJA_PARTIDAS, f.numero(), Columnas.BARCA,
                        "«" + barcaTexto + "» debe empezar con la capacidad en kg, por ejemplo «400 C».");
            } else {
                v.advertencia(HOJA_PARTIDAS, f.numero(), Columnas.BARCA, "La partida no tiene barca; no se podrá planificar hasta registrarla.");
            }
            FilaPartida fila = new FilaPartida(f.numero(), v.texto(HOJA_PARTIDAS, f, Columnas.NUMERO_PARTIDA, 50),
                    v.texto(HOJA_PARTIDAS, f, Columnas.NUMERO_OP, 50), v.texto(HOJA_PARTIDAS, f, Columnas.CODIGO_COLOR, 50),
                    v.texto(HOJA_PARTIDAS, f, Columnas.NOMBRE_COLOR, 100),
                    v.opcion(HOJA_PARTIDAS, f, Columnas.CLASIFICACION_COLOR, ClasificacionColor.class, CLASIFICACION),
                    v.opcion(HOJA_PARTIDAS, f, Columnas.TONALIDAD, TonalidadColor.class, Map.of()),
                    barca, v.positivo(HOJA_PARTIDAS, f, Columnas.PESO_TOTAL, 9, 3));
            if (fila.numeroPartida() != null && fila.numeroOp() != null && fila.codigoColor() != null && fila.nombreColor() != null
                    && fila.clasificacion() != null && fila.tonalidad() != null && fila.peso() != null) filas.add(fila);
        }
        if (v.filas(HOJA_PARTIDAS).isEmpty()) v.error(HOJA_PARTIDAS, 0, "", "La hoja no tiene registros.");
        return filas;
    }

    private List<FilaComponente> leerComponentes(ValidacionLibro v) {
        List<FilaComponente> filas = new ArrayList<>();
        for (LectorLibro.Fila f : v.filas(HOJA_COMPONENTES)) {
            String numero = v.texto(HOJA_COMPONENTES, f, Columnas.NUMERO_PARTIDA, 50);
            String articulo = v.codigoArticulo(HOJA_COMPONENTES, f, Columnas.CODIGO_ARTICULO);
            TipoComponente tipo = v.opcion(HOJA_COMPONENTES, f, Columnas.TIPO_COMPONENTE, TipoComponente.class, Map.of());
            Boolean tubular = v.siNo(HOJA_COMPONENTES, f, Columnas.TUBULAR_ABIERTO);
            BigDecimal peso = v.positivo(HOJA_COMPONENTES, f, Columnas.PESO_REQUERIDO, 9, 3);
            if (numero != null && articulo != null && tipo != null && tubular != null && peso != null)
                filas.add(new FilaComponente(f.numero(), numero, articulo, tipo, tubular, peso));
        }
        return filas;
    }
}
