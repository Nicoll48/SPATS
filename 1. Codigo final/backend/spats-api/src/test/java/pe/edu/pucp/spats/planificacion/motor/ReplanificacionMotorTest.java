package pe.edu.pucp.spats.planificacion.motor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.edu.pucp.spats.comun.error.ReglaNegocioException;
import pe.edu.pucp.spats.incidencia.entity.TipoIncidencia;
import pe.edu.pucp.spats.planificacion.entity.Enumeraciones.ClasificacionResultado;
import pe.edu.pucp.spats.planificacion.motor.ArmadoReplanificacion.FilaVigente;
import pe.edu.pucp.spats.planificacion.motor.ArmadoReplanificacion.IncidenciaAplicada;
import pe.edu.pucp.spats.planificacion.motor.ResultadoTraducido.FilaBolsa;
import pe.edu.pucp.spats.planificacion.motor.ResultadoTraducido.FilaMaquina;
import pe.edu.pucp.spats.planificacion.motor.ResultadoTraducido.FilaPartida;
import spats.algoritmo.PlanificadorSemanal;
import spats.algoritmo.aco.ParametrosACO;
import spats.algoritmo.entrada.Componente;
import spats.algoritmo.entrada.DatosSemana;
import spats.algoritmo.entrada.Partida;
import spats.algoritmo.excel.LectorDatosExcel;
import spats.algoritmo.salida.Indicadores;
import spats.algoritmo.salida.ResultadoPlanificacion;

/**
 * Pruebas de la §6 de la propuesta de re-planificación (versión 2) sobre la semana 38 del R2.2, con el mismo
 * camino que usa el backend: armado de la entrada desde las filas de la vigente, algoritmo, traducción a filas
 * de la base y decisión de RF55. No usan la base; la prueba 6 (confirmar y descartar) está en
 * {@code FlujoPlanificacionIntegracionTest}.
 */
class ReplanificacionMotorTest {

    static final Path DATOS = RegresionInstanciasR22Test.MODULO.resolve("datos").resolve("S38-B.xlsx");

    static DatosSemana datos;
    static ResultadoPlanificacion vigente;
    static ResultadoTraducido vigenteTraducida;
    static LocalDate lunes;

    @BeforeAll
    static void planificarLaVigente() throws Exception {
        datos = LectorDatosExcel.leer(DATOS).datos();
        lunes = datos.parametros().getFechaInicioSemana();
        vigente = new PlanificadorSemanal().planificar(datos, new ParametrosACO());
        vigenteTraducida = TraductorResultado.traducir(datos, vigente);
    }

    // ------------------------------------------------------------- prueba 1
    @Test
    @DisplayName("1. D = lunes y sin incidencias: mismas filas y misma F que la generación normal")
    void lunesSinIncidenciasEsLaGeneracionNormal() {
        Corrida c = replanificar(lunes, List.of(), Set.of());
        assertThat(c.traducido.partidas()).isEqualTo(vigenteTraducida.partidas());
        assertThat(c.traducido.bloqueadas()).isEqualTo(vigenteTraducida.bloqueadas());
        assertThat(c.traducido.maquinas()).isEqualTo(vigenteTraducida.maquinas());
        assertThat(c.traducido.bolsas()).isEqualTo(vigenteTraducida.bolsas());
        assertThat(c.nueva.indicadores().funcionObjetivo()).isEqualTo(vigente.indicadores().funcionObjetivo());
    }

    // ------------------------------------------------------------- prueba 2
    @Test
    @DisplayName("2. Las filas anteriores a D quedan idénticas a las de la vigente")
    void filasAnterioresADIdenticas() {
        LocalDate D = lunes.plusDays(3);
        Corrida c = replanificar(D, List.of(), Set.of());
        assertThat(antesDe(c.traducido.partidas(), D)).isEqualTo(antesDe(vigenteTraducida.partidas(), D)).isNotEmpty();
        assertThat(c.traducido.bolsas().stream().filter(b -> b.fecha().isBefore(D)).toList())
                .isEqualTo(vigenteTraducida.bolsas().stream().filter(b -> b.fecha().isBefore(D)).toList());
        for (FilaPartida f : c.traducido.partidas())
            if (!f.diaAbastecimiento().isBefore(D) && f.fechaDisponible().isBefore(D))
                assertThat(conservadas(D)).contains(f.numeroPartida());   // lo conformado antes de D y ubicado desde D
        verificarTela(c, D);
    }

    @Test
    @DisplayName("2b. Partidas de hoy ya en máquina: quedan fijas; una partida que no es de hoy no se puede marcar")
    void partidasDeHoyEnMaquina() {
        LocalDate D = lunes.plusDays(2);
        List<FilaPartida> deHoy = vigenteTraducida.partidas().stream().filter(f -> f.diaAbastecimiento().equals(D)).limit(2).toList();
        assertThat(deHoy).isNotEmpty();
        Set<String> enMaquina = Set.copyOf(deHoy.stream().map(FilaPartida::numeroPartida).toList());
        Corrida c = replanificar(D, List.of(), enMaquina);
        Map<String, FilaPartida> nuevas = porPartida(c.traducido.partidas());
        for (FilaPartida f : deHoy) {
            FilaPartida n = nuevas.get(f.numeroPartida());
            assertThat(List.of(n.codigoMaquina(), n.diaAbastecimiento(), n.clasificacion(), n.fechaDisponible()))
                    .isEqualTo(List.of(f.codigoMaquina(), f.diaAbastecimiento(), f.clasificacion(), f.fechaDisponible()));
        }
        verificarTela(c, D);
        String otraDia = vigenteTraducida.partidas().stream().filter(f -> f.diaAbastecimiento().isAfter(D))
                .map(FilaPartida::numeroPartida).findFirst().orElseThrow();
        assertThatThrownBy(() -> replanificar(D, List.of(), Set.of(otraDia))).isInstanceOf(ReglaNegocioException.class);
    }

    // ------------------------------------------------------------- prueba 3
    @Test
    @DisplayName("3. Ninguna tela se consume dos veces: la bolsa cuadra y las conservadas no consumen tela nueva")
    void ningunaTelaSeConsumeDosVeces() {
        for (int dia = 2; dia <= 6; dia++) {
            LocalDate D = lunes.plusDays(dia - 1);
            verificarTela(replanificar(D, List.of(), Set.of()), D);
        }
    }

    // ------------------------------------------------------------- prueba 4
    @Test
    @DisplayName("4a. Incidencia de máquina: sin partidas en el periodo afectado y objetivo reducido en 2 por día")
    void incidenciaDeMaquina() {
        LocalDate D = lunes.plusDays(2);
        String maquina = maquinaConPartidasDesde(D);
        LocalDateTime vuelve = D.plusDays(2).atTime(10, 0);                     // miércoles a viernes (viernes parcial)
        Corrida c = replanificar(D, List.of(maquina(maquina, vuelve)), Set.of());
        for (FilaPartida f : c.traducido.partidas())
            if (f.codigoMaquina().equals(maquina) && !f.diaAbastecimiento().isBefore(D))
                assertThat(f.diaAbastecimiento()).isAfter(D.plusDays(2));
        assertThat(fila(c.traducido.maquinas(), maquina).objetivoAplicado())
                .isEqualTo(fila(vigenteTraducida.maquinas(), maquina).objetivoAplicado() - 6);
        verificarTela(c, D);
    }

    @Test
    @DisplayName("4b. Problema de complemento: no se ubica (conservada) ni se conforma (por decidir) antes de la fecha")
    void problemaDeComplemento() {
        LocalDate D = lunes.plusDays(2);
        String conservada = conservadas(D).get(0);
        String porDecidir = vigenteTraducida.partidas().stream().filter(f -> !f.fechaDisponible().isBefore(D))
                .map(FilaPartida::numeroPartida).findFirst().orElseThrow();
        LocalDateTime fecha = D.plusDays(1).atTime(15, 0);                      // a media jornada: cuenta desde el día siguiente
        Corrida c = replanificar(D, List.of(complemento(conservada, fecha), complemento(porDecidir, fecha)), Set.of());
        Map<String, FilaPartida> n = porPartida(c.traducido.partidas());
        assertThat(n.get(conservada).diaAbastecimiento()).isAfterOrEqualTo(D.plusDays(2));
        assertThat(n.get(conservada).fechaDisponible()).isEqualTo(porPartida(vigenteTraducida.partidas()).get(conservada).fechaDisponible());
        if (n.containsKey(porDecidir)) assertThat(n.get(porDecidir).fechaDisponible()).isAfterOrEqualTo(D.plusDays(2));
        verificarTela(c, D);
    }

    @Test
    @DisplayName("4c. Complemento después del domingo: Bloqueada y su tela vuelve a la bolsa el día D (kg_devueltos)")
    void complementoFueraDeLaSemana() {
        LocalDate D = lunes.plusDays(2);
        String conservada = conservadas(D).get(0);
        Corrida c = replanificar(D, List.of(complemento(conservada, lunes.plusDays(9).atStartOfDay())), Set.of());
        assertThat(c.traducido.bloqueadas()).contains(conservada);
        assertThat(porPartida(c.traducido.partidas())).doesNotContainKey(conservada);
        Map<String, BigDecimal> requerido = requerimientos(conservada);
        for (FilaBolsa b : c.traducido.bolsas())
            if (requerido.containsKey(b.codigoArticulo()))
                assertThat(b.devueltos()).isEqualByComparingTo(b.fecha().equals(D) ? requerido.get(b.codigoArticulo()) : BigDecimal.ZERO);
        verificarTela(c, D);
    }

    @Test
    @DisplayName("4d. Complemento sobre una partida ubicada antes de D: no se permite")
    void complementoSobreFija() {
        LocalDate D = lunes.plusDays(2);
        String fija = antesDe(vigenteTraducida.partidas(), D).get(0).numeroPartida();
        assertThatThrownBy(() -> replanificar(D, List.of(complemento(fija, D.plusDays(1).atStartOfDay())), Set.of()))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining(fija);
    }

    @Test
    @DisplayName("4e. Cambio de prioridad: la nueva fecha de entrega se usa desde D y el resultado es factible")
    void cambioDePrioridad() {
        LocalDate D = lunes.plusDays(2);
        String pedido = pedidoDe(vigenteTraducida.partidas().stream().filter(f -> !f.fechaDisponible().isBefore(D))
                .reduce((a, b) -> b).orElseThrow().numeroPartida());
        Corrida c = replanificar(D, List.of(prioridad(pedido, lunes.minusDays(30))), Set.of());
        assertThat(c.nueva.indicadores().incumplimientos()).isEmpty();          // el validador revisa la prioridad
        assertThat(c.armado.datos().pedidos().stream().filter(p -> p.codigo().equals(pedido)).findFirst().orElseThrow().fechaEntrega())
                .isEqualTo(lunes.minusDays(30));
        verificarTela(c, D);
    }

    @Test
    @DisplayName("4f. Varias incidencias juntas")
    void variasIncidencias() {
        LocalDate D = lunes.plusDays(2);
        String maquina = maquinaConPartidasDesde(D);
        List<String> cons = conservadas(D);
        String conservada = cons.get(cons.size() - 1);
        String pedido = pedidoDe(vigenteTraducida.partidas().get(vigenteTraducida.partidas().size() - 1).numeroPartida());
        Corrida c = replanificar(D, List.of(maquina(maquina, D.plusDays(1).atStartOfDay()),
                complemento(conservada, D.plusDays(2).atStartOfDay()), prioridad(pedido, lunes.minusDays(30))), Set.of());
        assertThat(c.traducido.partidas()).noneMatch(f -> f.codigoMaquina().equals(maquina) && f.diaAbastecimiento().equals(D));
        assertThat(porPartida(c.traducido.partidas()).get(conservada).diaAbastecimiento()).isAfterOrEqualTo(D.plusDays(2));
        verificarTela(c, D);
    }

    // ------------------------------------------------------------- prueba 5
    @Test
    @DisplayName("5a. RF55: si la vigente ya no es factible en el escenario nuevo, se propone la nueva")
    void vigenteNoFactibleSeProponeLaNueva() {
        LocalDate D = lunes.plusDays(2);
        String maquina = maquinaConPartidasDesde(D);
        Corrida c = replanificar(D, List.of(maquina(maquina, lunes.plusDays(7).atStartOfDay())), Set.of());   // hasta el domingo
        assertThat(c.evaluacionVigente.factible()).isFalse();
        assertThat(DecisionReplanificacion.decidir(c.nueva.indicadores(), c.evaluacionVigente))
                .isEqualTo(DecisionReplanificacion.Resultado.PROPONER_NUEVA);
    }

    @Test
    @DisplayName("5b. RF55: si la vigente es factible y la nueva no la mejora, se conserva la vigente")
    void vigenteFactibleSinMejoraSeConserva() {
        Corrida c = replanificar(lunes, List.of(), Set.of());
        assertThat(c.evaluacionVigente.factible()).isTrue();
        assertThat(c.evaluacionVigente.funcionObjetivo()).isEqualTo(vigente.indicadores().funcionObjetivo());
        assertThat(DecisionReplanificacion.decidir(c.nueva.indicadores(), c.evaluacionVigente))
                .isEqualTo(DecisionReplanificacion.Resultado.CONSERVAR_VIGENTE);
        // y con una mejora se propondría
        Indicadores mejor = conF(c.evaluacionVigente, c.evaluacionVigente.funcionObjetivo() - 1);
        assertThat(DecisionReplanificacion.decidir(mejor, c.evaluacionVigente)).isEqualTo(DecisionReplanificacion.Resultado.PROPONER_NUEVA);
    }

    // ------------------------------------------------------------- apoyo
    record Corrida(ArmadoReplanificacion.Armado armado, ResultadoPlanificacion nueva, ResultadoTraducido traducido,
                   Indicadores evaluacionVigente) { }

    static Corrida replanificar(LocalDate D, List<IncidenciaAplicada> incidencias, Set<String> enMaquina) {
        ArmadoReplanificacion.Armado a = ArmadoReplanificacion.armar(datos, D, filasVigente(), vigenteTraducida.bolsas(),
                incidencias, enMaquina);
        ResultadoPlanificacion n = new PlanificadorSemanal().planificar(a.datos(), new ParametrosACO());
        ResultadoTraducido t = TraductorResultado.traducirReplanificacion(a.datos(), n, D, vigenteTraducida.bolsas());
        Indicadores v = new PlanificadorSemanal().evaluar(a.datos(), a.alternativaVigente());
        RegresionInstanciasR22Test.verificarRestriccionesDeLaBase(t);
        return new Corrida(a, n, t, v);
    }

    static List<FilaVigente> filasVigente() {
        List<FilaVigente> l = new ArrayList<>();
        for (FilaPartida f : vigenteTraducida.partidas())
            l.add(new FilaVigente(f.numeroPartida(), pedidoDe(f.numeroPartida()), f.codigoMaquina(), f.diaAbastecimiento(),
                    f.clasificacion(), f.ordenSecuencia(), f.fechaDisponible()));
        for (String b : vigenteTraducida.bloqueadas())
            l.add(new FilaVigente(b, pedidoDe(b), null, null, ClasificacionResultado.BLOQUEADA, 0, null));
        return l;
    }

    /**
     * La bolsa cuadra día por día (con lo devuelto), lo anterior a D es la copia de la vigente, y en toda la
     * semana: consumido − devuelto = tela de las partidas ubicadas en la versión nueva, cada una una sola vez.
     * Las conservadas no consumen desde D.
     */
    static void verificarTela(Corrida c, LocalDate D) {
        Map<String, BigDecimal> consumido = new HashMap<>(), esperado = new HashMap<>();
        for (FilaBolsa b : c.traducido.bolsas()) {
            assertThat(b.saldoFinal()).isEqualByComparingTo(b.saldoInicial().add(b.ingresados()).add(b.devueltos()).subtract(b.consumidos()));
            consumido.merge(b.codigoArticulo(), b.consumidos().subtract(b.devueltos()), BigDecimal::add);
        }
        for (FilaPartida f : c.traducido.partidas()) requerimientos(f.numeroPartida()).forEach((k, kg) -> esperado.merge(k, kg, BigDecimal::add));
        consumido.forEach((k, kg) -> assertThat(kg).as("tela de %s", k).isEqualByComparingTo(esperado.getOrDefault(k, BigDecimal.ZERO)));
        esperado.forEach((k, kg) -> assertThat(consumido).containsKey(k));
        List<String> cons = conservadas(D);
        Map<LocalDate, Map<String, BigDecimal>> desdeD = new HashMap<>();
        for (FilaPartida f : c.traducido.partidas()) {
            if (cons.contains(f.numeroPartida())) assertThat(f.fechaDisponible()).isBefore(D);
            if (!f.fechaDisponible().isBefore(D))
                requerimientos(f.numeroPartida()).forEach((k, kg) -> desdeD.computeIfAbsent(f.fechaDisponible(), x -> new HashMap<>()).merge(k, kg, BigDecimal::add));
        }
        for (FilaBolsa b : c.traducido.bolsas())
            if (!b.fecha().isBefore(D))
                assertThat(b.consumidos()).isEqualByComparingTo(desdeD.getOrDefault(b.fecha(), Map.of()).getOrDefault(b.codigoArticulo(), BigDecimal.ZERO));
    }

    static List<FilaPartida> antesDe(List<FilaPartida> filas, LocalDate D) {
        return filas.stream().filter(f -> f.diaAbastecimiento().isBefore(D)).toList();
    }

    static List<String> conservadas(LocalDate D) {
        return vigenteTraducida.partidas().stream().filter(f -> !f.diaAbastecimiento().isBefore(D) && f.fechaDisponible().isBefore(D))
                .map(FilaPartida::numeroPartida).toList();
    }

    static String maquinaConPartidasDesde(LocalDate D) {
        Predicate<FilaPartida> desde = f -> !f.diaAbastecimiento().isBefore(D);
        return vigenteTraducida.partidas().stream().filter(desde).map(FilaPartida::codigoMaquina).findFirst().orElseThrow();
    }

    static Map<String, FilaPartida> porPartida(List<FilaPartida> l) {
        Map<String, FilaPartida> m = new HashMap<>();
        l.forEach(f -> m.put(f.numeroPartida(), f));
        return m;
    }

    static FilaMaquina fila(List<FilaMaquina> l, String maquina) {
        return l.stream().filter(m -> m.codigoMaquina().equals(maquina)).findFirst().orElseThrow();
    }

    static String pedidoDe(String partida) {
        return datos.partidas().stream().filter(p -> p.codigo().equals(partida)).map(Partida::pedido).findFirst().orElseThrow();
    }

    static Map<String, BigDecimal> requerimientos(String partida) {
        Map<String, BigDecimal> m = new HashMap<>();
        for (Componente c : datos.componentes())
            if (c.partida().equals(partida) && c.kgRequeridos() > 0)
                m.merge(c.claveArticulo().trim(), TraductorResultado.kg(c.kgRequeridos()), BigDecimal::add);
        return m;
    }

    static IncidenciaAplicada maquina(String maquina, LocalDateTime vuelve) {
        return new IncidenciaAplicada(1, TipoIncidencia.INCIDENCIA_MAQUINA, maquina, null, null, vuelve, null);
    }

    static IncidenciaAplicada complemento(String partida, LocalDateTime fecha) {
        return new IncidenciaAplicada(2, TipoIncidencia.PROBLEMA_COMPLEMENTO, null, partida, null, fecha, null);
    }

    static IncidenciaAplicada prioridad(String pedido, LocalDate fecha) {
        return new IncidenciaAplicada(3, TipoIncidencia.CAMBIO_PRIORIDAD, null, null, pedido, null, fecha);
    }

    static Indicadores conF(Indicadores i, double f) {
        return new Indicadores(f, i.desbalanceSemanal(), i.desbalanceDiario(), i.lavados(), i.objetivoTotal(), i.conformadas(),
                i.asignadas(), i.sobrantes(), i.bloqueadas(), i.evaluaciones(), i.iteraciones(), i.tiempoMs(),
                i.detenidaPorTiempo(), i.incumplimientos());
    }

}
