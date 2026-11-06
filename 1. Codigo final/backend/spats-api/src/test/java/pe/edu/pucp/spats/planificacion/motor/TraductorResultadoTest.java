package pe.edu.pucp.spats.planificacion.motor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.edu.pucp.spats.comun.error.FalloPlanificacionException;
import pe.edu.pucp.spats.planificacion.entity.Enumeraciones.ClasificacionResultado;
import pe.edu.pucp.spats.planificacion.motor.ResultadoTraducido.FilaBolsa;
import spats.algoritmo.PlanificadorSemanal;
import spats.algoritmo.aco.ParametrosACO;
import spats.algoritmo.entrada.Componente;
import spats.algoritmo.entrada.DatosSemana;
import spats.algoritmo.entrada.EstadoPartida;
import spats.algoritmo.entrada.Maquina;
import spats.algoritmo.entrada.ParametrosPlanificacion;
import spats.algoritmo.entrada.Partida;
import spats.algoritmo.entrada.Pedido;
import spats.algoritmo.entrada.RegistroProyeccion;
import spats.algoritmo.entrada.StockArticulo;
import spats.algoritmo.entrada.Tonalidad;
import spats.algoritmo.salida.Indicadores;
import spats.algoritmo.salida.ResultadoPlanificacion;

class TraductorResultadoTest {

    static final LocalDate LUNES = LocalDate.of(2026, 9, 14);

    /**
     * Semana pequeña: una máquina 200 C y el artículo A con 100 kg en stock, que alcanzan para una sola
     * partida. P1 y P5 compiten por A (misma fecha); P2 necesita A y B (B sin kilos); P3 solo necesita C (sin
     * kilos); P4 es de una barca sin máquina.
     */
    static DatosSemana semana() {
        Pedido pedido = new Pedido("100", "Cliente", null, LUNES.plusDays(10));
        List<Partida> partidas = List.of(
                partida("P1", 1, "200 C"), partida("P2", 2, "200 C"), partida("P3", 3, "200 C"),
                partida("P4", 4, "300 C"), partida("P5", 5, "200 C"));
        List<Componente> componentes = List.of(
                componente("P1", "A", "Principal", 100), componente("P2", "A", "Principal", 100),
                componente("P2", "B", "Complemento", 50), componente("P3", "C", "Principal", 100),
                componente("P4", "A", "Principal", 100), componente("P5", "A", "Principal", 100));
        return new DatosSemana(new ParametrosPlanificacion(LUNES), List.of(new Maquina("M200", 200, "C", "200 C", 16)),
                List.of(), List.of(pedido), partidas, componentes, List.of(),
                List.of(new StockArticulo("A", "Artículo A", 100)));
    }

    static Partida partida(String codigo, int orden, String barca) {
        return new Partida(codigo, "100", "COL", "Color", 100, Tonalidad.CLARO, barca, orden, EstadoPartida.PENDIENTE);
    }

    static Componente componente(String partida, String articulo, String rol, double kg) {
        return new Componente(partida, articulo, "Artículo " + articulo, articulo, rol, kg);
    }

    static ResultadoTraducido traducir(DatosSemana datos) {
        ResultadoPlanificacion r = new PlanificadorSemanal().planificar(datos, new ParametrosACO());
        return TraductorResultado.traducir(datos, r);
    }

    @Test
    @DisplayName("Bloqueada: la tela priorizada para otra partida y la que tuvo kilos de algún artículo; sin ningún kilo no se registra")
    void clasificaBloqueadasSegunLaSeccion62() {
        ResultadoTraducido t = traducir(semana());

        assertThat(t.partidas()).hasSize(1);
        String conformada = t.partidas().getFirst().numeroPartida();
        assertThat(conformada).isIn("P1", "P5");
        String perdedora = conformada.equals("P1") ? "P5" : "P1";
        assertThat(t.bloqueadas()).containsExactlyInAnyOrder(perdedora, "P2");   // P3 sigue PENDIENTE sin registrarse
        assertThat(t.advertencias()).anyMatch(a -> a.contains("P4") && a.contains("sin máquina registrada"));
    }

    @Test
    @DisplayName("Bolsa reconstruida: el saldo cumple el balance día a día y el domingo coincide con el algoritmo")
    void reconstruyeLaBolsaDiaPorDia() {
        ResultadoTraducido t = traducir(semana());

        List<FilaBolsa> bolsaA = t.bolsas().stream().filter(b -> b.codigoArticulo().equals("A")).toList();
        assertThat(bolsaA).hasSize(7);
        for (FilaBolsa b : bolsaA)
            assertThat(b.saldoFinal()).isEqualByComparingTo(b.saldoInicial().add(b.ingresados()).subtract(b.consumidos()));
        assertThat(bolsaA.getFirst().saldoInicial()).isEqualByComparingTo("100");
        assertThat(bolsaA.getFirst().consumidos()).isEqualByComparingTo("100");
        assertThat(bolsaA.getLast().saldoFinal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Ingresos: lo tejido el sábado anterior llega el lunes y lo del viernes, el sábado")
    void usaLaReglaDeDisponibilidadDelAlgoritmo() {
        DatosSemana base = semana();
        List<RegistroProyeccion> tejido = List.of(
                new RegistroProyeccion("", "100", "Artículo C", "C", LUNES.minusDays(2), 40),
                new RegistroProyeccion("", "100", "Artículo C", "C", LUNES.plusDays(4), 25));
        DatosSemana datos = new DatosSemana(base.parametros(), base.maquinas(), base.indisponibilidades(), base.pedidos(),
                base.partidas(), base.componentes(), tejido, base.stockInicial());

        List<FilaBolsa> bolsaC = traducir(datos).bolsas().stream().filter(b -> b.codigoArticulo().equals("C")).toList();
        assertThat(bolsaC.get(0).ingresados()).isEqualByComparingTo("40");   // lunes
        assertThat(bolsaC.get(5).ingresados()).isEqualByComparingTo("25");   // sábado
        assertThat(bolsaC.getLast().saldoFinal()).isEqualByComparingTo("65");
    }

    @Test
    @DisplayName("orden_secuencia: posición dentro de la máquina y el día, primero Asignadas y luego Sobrantes")
    void numeraLaSecuenciaPorMaquinaYDia() {
        ResultadoTraducido t = traducir(semana());
        assertThat(t.partidas().getFirst().ordenSecuencia()).isEqualTo(1);
        assertThat(t.partidas().getFirst().clasificacion()).isEqualTo(ClasificacionResultado.ASIGNADA);
    }

    @Test
    @DisplayName("Una solución con incumplimientos del validador es un fallo y no se traduce")
    void rechazaSolucionNoFactible() {
        DatosSemana datos = semana();
        ResultadoPlanificacion r = new PlanificadorSemanal().planificar(datos, new ParametrosACO());
        Indicadores i = r.indicadores();
        Indicadores noFactible = new Indicadores(i.funcionObjetivo(), i.desbalanceSemanal(), i.desbalanceDiario(), i.lavados(),
                i.objetivoTotal(), i.conformadas(), i.asignadas(), i.sobrantes(), i.bloqueadas(), i.evaluaciones(),
                i.iteraciones(), i.tiempoMs(), i.detenidaPorTiempo(), List.of("Distribución: prueba"));
        ResultadoPlanificacion alterado = new ResultadoPlanificacion(r.dias(), r.asignaciones(), r.maquinas(), r.bloqueadas(),
                noFactible, r.estadoSiguiente(), r.advertencias());

        assertThatThrownBy(() -> TraductorResultado.traducir(datos, alterado))
                .isInstanceOf(FalloPlanificacionException.class);
    }
}
