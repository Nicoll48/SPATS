package pe.edu.pucp.spats.demo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.pucp.spats.comun.dominio.Barca;
import pe.edu.pucp.spats.comun.fecha.SemanaPlanificacion;
import pe.edu.pucp.spats.maquina.entity.EstadoMaquina;
import pe.edu.pucp.spats.maquina.entity.IndisponibilidadMaquina;
import pe.edu.pucp.spats.maquina.entity.MaquinaTintoreria;
import pe.edu.pucp.spats.maquina.entity.TipoIndisponibilidad;
import pe.edu.pucp.spats.maquina.repository.MaquinaRepository;
import pe.edu.pucp.spats.partida.entity.Color;
import pe.edu.pucp.spats.partida.entity.Partida;
import pe.edu.pucp.spats.partida.entity.PartidaComponente;
import pe.edu.pucp.spats.partida.entity.TipoComponente;
import pe.edu.pucp.spats.partida.entity.TonalidadColor;
import pe.edu.pucp.spats.pedido.entity.Articulo;
import pe.edu.pucp.spats.pedido.entity.Cliente;
import pe.edu.pucp.spats.pedido.entity.DetallePedido;
import pe.edu.pucp.spats.pedido.entity.Pedido;
import pe.edu.pucp.spats.tejeduria.entity.DetalleProyeccionTejeduria;
import pe.edu.pucp.spats.tejeduria.entity.ProyeccionTejeduria;
import pe.edu.pucp.spats.tejeduria.entity.Semana;
import spats.algoritmo.entrada.Componente;
import spats.algoritmo.entrada.DatosSemana;
import spats.algoritmo.entrada.RegistroProyeccion;
import spats.algoritmo.excel.LectorDatosExcel;

/**
 * Perfil {@code demo}: carga en una base vacía los datos de las semanas del R2.2 (libros de
 * {@code spats-algoritmo/datos}) para probar el sistema. No genera planificaciones: eso se hace desde la
 * aplicación. Los datos maestros (máquinas, pedidos, partidas) se toman del primer libro; de cada libro, su
 * proyección de tejeduría.
 *
 * <p>Adaptaciones de los datos de prueba: el código del artículo es la clave de bolsa del libro
 * ({@code clave_articulo}), porque en el R2.2 varios códigos comparten bolsa; {@code tubular_abierto} = false.</p>
 */
@Component
@Profile("demo")
public class CargadorDatosDemo implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(CargadorDatosDemo.class);

    private final MaquinaRepository maquinas;
    private final EntityManager em;
    private final Path carpeta;
    private final List<String> libros;

    public CargadorDatosDemo(MaquinaRepository maquinas, EntityManager em,
                             @Value("${spats.demo.carpeta-datos}") String carpeta,
                             @Value("${spats.demo.libros}") List<String> libros) {
        this.maquinas = maquinas;
        this.em = em;
        this.carpeta = Path.of(carpeta);
        this.libros = libros;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (maquinas.count() > 0) {
            LOG.info("Datos de demostración: la base ya tiene máquinas; no se carga nada.");
            return;
        }
        List<DatosSemana> semanas = new ArrayList<>();
        for (String libro : libros) {
            Path archivo = carpeta.resolve(libro.trim());
            if (!Files.exists(archivo)) {
                LOG.warn("Datos de demostración: no se encontró {}; no se carga nada.", archivo.toAbsolutePath());
                return;
            }
            semanas.add(LectorDatosExcel.leer(archivo).datos());
        }
        cargar(semanas);
    }

    void cargar(List<DatosSemana> semanas) {
        DatosSemana base = semanas.getFirst();
        Map<String, MaquinaTintoreria> maquinasPorCodigo = new HashMap<>();
        for (var m : base.maquinas()) {
            Barca barca = Barca.interpretar(m.barca()).orElseThrow();
            MaquinaTintoreria maquina = new MaquinaTintoreria(m.codigo(), BigDecimal.valueOf(m.capacidad()), barca.clasificacion(),
                    m.objetivoSemanal(), barca.texto(), EstadoMaquina.OPERATIVA);
            em.persist(maquina);
            maquinasPorCodigo.put(m.codigo(), maquina);
        }

        Map<String, Cliente> clientes = new HashMap<>();
        Map<String, Pedido> pedidos = new HashMap<>();
        for (var p : base.pedidos()) {
            Cliente cliente = clientes.computeIfAbsent(p.cliente(), n -> { Cliente c = new Cliente(n); em.persist(c); return c; });
            Pedido pedido = new Pedido(cliente, p.codigo(), p.fechaEntrega());
            em.persist(pedido);
            pedidos.put(p.codigo(), pedido);
        }

        Map<String, String> pedidoDePartida = new HashMap<>();
        base.partidas().forEach(p -> pedidoDePartida.put(p.codigo(), p.pedido()));
        Map<String, Articulo> articulos = new HashMap<>();
        Map<String, BigDecimal> pesoPorPar = new LinkedHashMap<>();
        for (Componente c : base.componentes()) {
            articulo(articulos, c.claveArticulo(), c.articulo());
            pesoPorPar.merge(pedidoDePartida.get(c.partida()) + "|" + c.claveArticulo().trim(), kg(c.kgRequeridos()), BigDecimal::add);
        }
        for (DatosSemana s : semanas)
            for (RegistroProyeccion r : s.proyeccion()) {
                articulo(articulos, r.claveArticulo(), r.articulo());
                pesoPorPar.merge(r.pedido() + "|" + r.claveArticulo().trim(), BigDecimal.ZERO, BigDecimal::add);
            }
        Map<String, DetallePedido> detalles = new HashMap<>();
        Map<String, BigDecimal> proyectadoPorPar = new HashMap<>();
        for (DatosSemana s : semanas)
            for (RegistroProyeccion r : s.proyeccion())
                proyectadoPorPar.merge(r.pedido() + "|" + r.claveArticulo().trim(), kg(r.kg()), BigDecimal::add);
        for (var e : pesoPorPar.entrySet()) {
            String[] par = e.getKey().split("\\|", 2);
            BigDecimal peso = e.getValue().signum() > 0 ? e.getValue() : proyectadoPorPar.getOrDefault(e.getKey(), BigDecimal.ONE);
            DetallePedido d = new DetallePedido(pedidos.get(par[0]), articulos.get(par[1]), peso.setScale(2, RoundingMode.HALF_UP));
            em.persist(d);
            detalles.put(e.getKey(), d);
        }

        Map<String, Color> colores = new HashMap<>();
        Map<String, Partida> partidas = new HashMap<>();
        LocalDateTime registroBase = LocalDateTime.of(2026, 8, 31, 8, 0);
        for (var p : base.partidas().stream().sorted(java.util.Comparator.comparingInt(spats.algoritmo.entrada.Partida::ordenRegistro)).toList()) {
            Barca barca = Barca.interpretar(p.barca()).orElseThrow();
            Color color = colores.computeIfAbsent(p.codColor(), c -> {
                Color nuevo = new Color(c, p.color(), barca.clasificacion(), TonalidadColor.valueOf(p.tonalidad().name()));
                em.persist(nuevo);
                return nuevo;
            });
            Partida partida = new Partida(pedidos.get(p.pedido()), color, p.codigo(), barca.texto(), kg(p.peso()),
                    registroBase.plusSeconds(p.ordenRegistro()));
            partidas.put(p.codigo(), partida);
        }
        for (Componente c : base.componentes()) {
            Partida partida = partidas.get(c.partida());
            partida.agregarComponente(new PartidaComponente(partida, detalles.get(partida.getPedido().getNumeroOp() + "|" + c.claveArticulo().trim()),
                    c.rol().equalsIgnoreCase("Principal") ? TipoComponente.PRINCIPAL : TipoComponente.COMPLEMENTO, false, kg(c.kgRequeridos())));
        }
        partidas.values().stream().sorted(java.util.Comparator.comparing(Partida::getFechaRegistro)).forEach(em::persist);

        for (DatosSemana s : semanas) {
            SemanaPlanificacion periodo = new SemanaPlanificacion(s.parametros().getFechaInicioSemana());
            Semana semana = new Semana(periodo);
            em.persist(semana);
            ProyeccionTejeduria proyeccion = new ProyeccionTejeduria(semana, 1, LocalDateTime.of(periodo.lunes().minusDays(3), java.time.LocalTime.of(9, 0)));
            em.persist(proyeccion);
            for (RegistroProyeccion r : s.proyeccion())
                if (r.kg() > 0)
                    em.persist(new DetalleProyeccionTejeduria(proyeccion, detalles.get(r.pedido() + "|" + r.claveArticulo().trim()),
                            r.fechaTejido(), kg(r.kg())));
            for (var i : s.indisponibilidades()) {
                LocalDate dia = i.fecha();
                em.persist(new IndisponibilidadMaquina(maquinasPorCodigo.get(i.maquina()), dia.atStartOfDay(),
                        dia.plusDays(1).atStartOfDay(), TipoIndisponibilidad.INDISPONIBLE, i.motivo()));
            }
        }
        LOG.info("Datos de demostración cargados: {} máquinas, {} pedidos, {} partidas, {} semanas.",
                maquinasPorCodigo.size(), pedidos.size(), partidas.size(), semanas.size());
    }

    private Articulo articulo(Map<String, Articulo> articulos, String clave, String nombre) {
        return articulos.computeIfAbsent(clave.trim(), c -> {
            Articulo a = new Articulo(c, nombre.length() > 150 ? nombre.substring(0, 150) : nombre);
            em.persist(a);
            return a;
        });
    }

    private static BigDecimal kg(double valor) {
        return BigDecimal.valueOf(valor).setScale(3, RoundingMode.HALF_UP);
    }
}
