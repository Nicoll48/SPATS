package pe.edu.pucp.spats.planificacion.recomendacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.edu.pucp.spats.partida.entity.TonalidadColor;
import pe.edu.pucp.spats.planificacion.recomendacion.CalculoRecomendaciones.Candidata;
import pe.edu.pucp.spats.planificacion.recomendacion.CalculoRecomendaciones.Componente;
import pe.edu.pucp.spats.planificacion.recomendacion.CalculoRecomendaciones.Dia;
import pe.edu.pucp.spats.planificacion.recomendacion.CalculoRecomendaciones.Entrada;
import pe.edu.pucp.spats.planificacion.recomendacion.CalculoRecomendaciones.Fila;
import pe.edu.pucp.spats.planificacion.recomendacion.CalculoRecomendaciones.Maquina;
import pe.edu.pucp.spats.planificacion.recomendacion.CalculoRecomendaciones.TipoComponente;

/** RF30 (D-56): tejido adicional para la misma semana, caso por caso, sin base de datos. */
class CalculoRecomendacionesTest {

    static final LocalDate LUNES = LocalDate.of(2026, 11, 9);
    static final LocalDate SABADO_ANTES = LUNES.minusDays(2), VIERNES = LUNES.plusDays(4);
    static final Set<TonalidadColor> TODAS = EnumSet.allOf(TonalidadColor.class);
    static final BigDecimal C400 = new BigDecimal("400"), C300 = new BigDecimal("300");

    static LocalDate dia(int i) { return LUNES.plusDays(i); }   // 0 = lunes … 6 = domingo

    /** Máquina con espacio libre en los días indicados (desde el lunes). */
    static Maquina maquina(int id, String codigo, BigDecimal cap, Set<TonalidadColor> acepta, int objetivo, int asignadas, int... libre) {
        List<Dia> dias = new java.util.ArrayList<>();
        for (int i = 0; i < libre.length; i++) dias.add(new Dia(dia(i), libre[i]));
        return new Maquina(id, codigo, cap, acepta, objetivo, asignadas, dias);
    }

    static Candidata partida(int id, String numero, String barca, TonalidadColor t, int diaEntrega, Componente... comps) {
        return new Candidata(id, numero, barca, t, LocalDate.of(2026, 11, diaEntrega), List.of(comps));
    }

    static Componente principal(int idArticulo, double kg) { return new Componente(10 + idArticulo, "OP1", idArticulo, "ART-" + idArticulo, TipoComponente.PRINCIPAL, kg); }

    static Componente complemento(int idArticulo, double kg) { return new Componente(10 + idArticulo, "OP1", idArticulo, "ART-" + idArticulo, TipoComponente.COMPLEMENTO, kg); }

    static List<Fila> calcular(List<Maquina> m, List<Candidata> c, Map<Integer, Map<LocalDate, Double>> sinUsar) {
        return CalculoRecomendaciones.calcular(new Entrada(m, c, sinUsar, SABADO_ANTES, VIERNES));
    }

    static List<Candidata> varias(int n, String barca, TonalidadColor t) {
        List<Candidata> r = new java.util.ArrayList<>();
        for (int i = 1; i <= n; i++) r.add(partida(i, "P" + i, barca, t, 20 + i, principal(1, 100)));
        return r;
    }

    /** Máquina de 7 días con máximo diario 3 y las regulares indicadas por día. */
    static Maquina conRegulares(int posicion, int objetivo, int... regulares) {
        List<Dia> dias = new java.util.ArrayList<>();
        int total = 0;
        for (int i = 0; i < regulares.length; i++) { dias.add(new Dia(dia(i), 3 - regulares[i], regulares[i])); total += regulares[i]; }
        return new Maquina(1, "M1", C400, TODAS, objetivo, total, dias, posicion);
    }

    @Test
    @DisplayName("El día sigue la regla de llenado del calendario (D-48), no carga los primeros días")
    void diaComoElCalendario() {
        // 14 regulares (2 por día) + 2 recomendadas = 16 en 7 días; con desfase 0.5 la cuota es 2,3,2,2,2,3,2: martes y sábado
        List<Fila> r = CalculoRecomendaciones.calcular(new Entrada(List.of(conRegulares(0, 16, 2, 2, 2, 2, 2, 2, 2)),
                varias(2, "400 C", TonalidadColor.CLARO), Map.of(), SABADO_ANTES, VIERNES, 1, 0));
        assertThat(r).extracting(Fila::dia).containsExactlyInAnyOrder(dia(1), dia(5));
        // Antes (día con más espacio, el más temprano) salían lunes y martes: 3, 3, 2, 2, 2, 2, 2
        assertThat(r).extracting(Fila::dia).doesNotContain(dia(0));
    }

    @Test
    @DisplayName("El desfase rota entre máquinas y semanas como en el algoritmo")
    void desfaseComoElAlgoritmo() {
        // Caso real M10, semana del 16/11 (ISO 47), posición 9 de 10: el calendario guardado es 2 1 1 2 1 2 1
        Maquina m = conRegulares(9, 10, 2, 1, 1, 2, 1, 2, 1);
        Map<LocalDate, Integer> cuota = CalculoRecomendaciones.cuotaPorDia(new Maquina(m.id(), m.codigo(), m.capacidad(),
                m.aceptadas(), 10, 10, m.dias(), 9), 10, 47);
        assertThat(java.util.stream.IntStream.range(0, 7).map(i -> cuota.get(dia(i)))).containsExactly(2, 1, 1, 2, 1, 2, 1);
    }

    @Test
    @DisplayName("Con varias recomendadas, el reparto final tiene la forma de la D-48")
    void repartoFinalParejo() {
        // 7 regulares (1 por día) + 9 recomendadas = 16; ningún día pasa de 3 y quedan dos días con 3
        List<Fila> r = CalculoRecomendaciones.calcular(new Entrada(List.of(conRegulares(0, 16, 1, 1, 1, 1, 1, 1, 1)),
                varias(9, "400 C", TonalidadColor.CLARO), Map.of(), SABADO_ANTES, VIERNES, 1, 0));
        int[] porDia = new int[7];
        r.forEach(f -> porDia[(int) (f.dia().toEpochDay() - LUNES.toEpochDay())]++);
        assertThat(porDia).containsExactly(1, 2, 1, 1, 1, 2, 1);   // + 1 regular = 2,3,2,2,2,3,2
    }

    @Test
    @DisplayName("No supera el déficit ni el espacio libre de la semana")
    void limites() {
        // Déficit 2 con espacio 5: dos partidas
        assertThat(calcular(List.of(maquina(1, "M1", C400, TODAS, 16, 14, 2, 3)), varias(5, "400 C", TonalidadColor.CLARO), Map.of())
                .stream().map(Fila::partida).distinct()).hasSize(2);
        // Déficit 8 con espacio 3: tres partidas
        assertThat(calcular(List.of(maquina(1, "M1", C400, TODAS, 16, 8, 1, 1, 1)), varias(5, "400 C", TonalidadColor.CLARO), Map.of())
                .stream().map(Fila::partida).distinct()).hasSize(3);
    }

    @Test
    @DisplayName("Día de tejido: lunes → sábado anterior; martes → lunes; domingo → viernes")
    void diaDeTejido() {
        assertThat(CalculoRecomendaciones.diaTejido(dia(0), SABADO_ANTES, VIERNES)).isEqualTo(SABADO_ANTES);
        assertThat(CalculoRecomendaciones.diaTejido(dia(1), SABADO_ANTES, VIERNES)).isEqualTo(dia(0));
        assertThat(CalculoRecomendaciones.diaTejido(dia(6), SABADO_ANTES, VIERNES)).isEqualTo(VIERNES);
        assertThat(CalculoRecomendaciones.diaTejido(dia(5), SABADO_ANTES, VIERNES)).isEqualTo(VIERNES);   // sábado → viernes
        List<Fila> r = calcular(List.of(maquina(1, "M1", C400, TODAS, 16, 15, 1)), varias(1, "400 C", TonalidadColor.CLARO), Map.of());
        assertThat(r).singleElement().satisfies(f -> assertThat(f.diaTejido()).isEqualTo(SABADO_ANTES));
    }

    @Test
    @DisplayName("Un día sin fecha de tejido posible no se recomienda")
    void sinFechaDeTejido() {
        // Con la ventana empezando el lunes, el lunes no tiene ningún día de tejido anterior
        assertThat(CalculoRecomendaciones.diaTejido(dia(0), dia(0), VIERNES)).isNull();
        List<Fila> r = CalculoRecomendaciones.calcular(new Entrada(List.of(maquina(1, "M1", C400, TODAS, 16, 14, 2, 1)),
                varias(3, "400 C", TonalidadColor.CLARO), Map.of(), dia(0), VIERNES));
        assertThat(r).extracting(Fila::dia).containsExactly(dia(1));   // el lunes queda fuera aunque tenga más espacio
    }

    @Test
    @DisplayName("La tela sin usar se descuenta una sola vez, en el orden del reparto")
    void saldoDescontado() {
        Map<Integer, Map<LocalDate, Double>> sinUsar = Map.of(1, Map.of(dia(0), 150.0, dia(1), 150.0));
        List<Fila> r = calcular(List.of(maquina(1, "M1", C400, TODAS, 16, 13, 1, 1, 1)), List.of(
                partida(1, "P1", "400 C", TonalidadColor.CLARO, 20, principal(1, 100)),
                partida(2, "P2", "400 C", TonalidadColor.CLARO, 21, principal(1, 100)),
                partida(3, "P3", "400 C", TonalidadColor.CLARO, 22, principal(1, 100))), sinUsar);
        // P1 ya tiene su tela (150 sin usar): no se recomienda. P2 usa los 50 que quedan y teje 50; P3 teje 100
        assertThat(r).extracting(Fila::partida, Fila::kg).containsExactly(org.assertj.core.groups.Tuple.tuple("P2", 50.0),
                org.assertj.core.groups.Tuple.tuple("P3", 100.0));
    }

    @Test
    @DisplayName("Partida que solo necesita el complemento: una fila del complemento")
    void soloComplemento() {
        Map<Integer, Map<LocalDate, Double>> sinUsar = Map.of(1, Map.of(dia(0), 500.0));
        List<Fila> r = calcular(List.of(maquina(1, "M1", C400, TODAS, 16, 15, 1)),
                List.of(partida(1, "P1", "400 C", TonalidadColor.MEDIO, 20, principal(1, 300), complemento(2, 20))), sinUsar);
        assertThat(r).singleElement().satisfies(f -> {
            assertThat(f.tipo()).isEqualTo(TipoComponente.COMPLEMENTO);
            assertThat(f.kg()).isEqualTo(20);
        });
    }

    @Test
    @DisplayName("Una partida compatible con dos máquinas se recomienda una sola vez, a la de mayor déficit")
    void unaSolaVez() {
        List<Fila> r = calcular(List.of(maquina(1, "M1", C400, TODAS, 16, 15, 1), maquina(2, "M2", C400, TODAS, 16, 12, 1)),
                varias(1, "400 C", TonalidadColor.CLARO), Map.of());
        assertThat(r).singleElement().satisfies(f -> assertThat(f.maquina()).isEqualTo("M2"));
    }

    @Test
    @DisplayName("RN-06: no se recomienda a una máquina que no acepta la tonalidad")
    void tonalidadNoAceptada() {
        assertThat(calcular(List.of(maquina(1, "M1", C300, EnumSet.of(TonalidadColor.BLANCO), 16, 10, 3)),
                varias(2, "300 B", TonalidadColor.OSCURO), Map.of())).isEmpty();
    }

    @Test
    @DisplayName("HU-13 C4: la máquina que llegó a su objetivo no recibe nada")
    void sinDeficit() {
        assertThat(calcular(List.of(maquina(1, "M1", C400, TODAS, 16, 16, 3)), varias(2, "400 C", TonalidadColor.CLARO), Map.of())).isEmpty();
    }

    @Test
    @DisplayName("HU-13 C5: sin candidatas no se recomienda nada")
    void sinCandidatas() {
        assertThat(calcular(List.of(maquina(1, "M1", C400, TODAS, 16, 8, 3, 3)), List.of(), Map.of())).isEmpty();
    }

    @Test
    @DisplayName("Cubierto en parte: se recomienda lo que haya")
    void cubiertoEnParte() {
        List<Fila> r = calcular(List.of(maquina(1, "M1", C400, TODAS, 16, 10, 3, 3)), varias(2, "400 C", TonalidadColor.CLARO), Map.of());
        assertThat(r.stream().map(Fila::partida).distinct()).hasSize(2);   // déficit 6, espacio 6, solo 2 candidatas
    }

    @Test
    @DisplayName("Prioridad: entrega más próxima primero")
    void prioridadPorEntrega() {
        List<Fila> r = calcular(List.of(maquina(1, "M1", C400, TODAS, 16, 15, 1)), List.of(
                partida(1, "TARDE", "400 C", TonalidadColor.CLARO, 30, principal(1, 100)),
                partida(2, "PRONTO", "400 C", TonalidadColor.CLARO, 15, principal(1, 100))), Map.of());
        assertThat(r).extracting(Fila::partida).containsExactly("PRONTO");
    }
}
