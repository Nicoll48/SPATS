package spats.algoritmo.excel;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import spats.algoritmo.entrada.StockArticulo;
import spats.algoritmo.salida.AsignacionPartida;
import spats.algoritmo.salida.EstadoAsignacion;
import spats.algoritmo.salida.Indicadores;
import spats.algoritmo.salida.PartidaBloqueada;
import spats.algoritmo.salida.ResultadoPlanificacion;
import spats.algoritmo.salida.ResumenMaquina;

/** Guarda el resultado de la planificación en un libro de Excel. */
public final class EscritorResultadoExcel {

    public static final String[] DIAS = { "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom" };

    private EscritorResultadoExcel() { }

    static String nombreDia(LocalDate[] dias, int d) {
        return (d < DIAS.length ? DIAS[d] : "D" + (d + 1)) + " " + dias[d].getDayOfMonth() + "/" + dias[d].getMonthValue();
    }

    public static void guardar(ResultadoPlanificacion r, Path archivo) throws IOException {
        LocalDate[] dias = r.dias();
        EscritorXlsx x = new EscritorXlsx();

        // Calendario: partidas por máquina y día (las Sobrantes con prefijo S:)
        List<String> enc = new ArrayList<>(List.of("Máquina", "Barca"));
        for (int d = 0; d < dias.length; d++) enc.add(nombreDia(dias, d));
        enc.add("Total / objetivo");
        List<List<Object>> filas = new ArrayList<>();
        for (ResumenMaquina m : r.maquinas()) {
            List<Object> f = new ArrayList<>(List.of(m.maquina(), m.barca()));
            for (int d = 0; d < dias.length; d++) {
                if (m.partidasPorDia()[d] < 0) { f.add("Indisponible"); continue; }
                List<String> cod = new ArrayList<>();
                for (AsignacionPartida a : r.asignaciones())
                    if (a.maquina().equals(m.maquina()) && a.fecha().equals(dias[d]))
                        cod.add((a.estado() == EstadoAsignacion.SOBRANTE ? "S:" : "") + a.partida() + (a.requiereLavado() ? " (L)" : ""));
                f.add(String.join(", ", cod));
            }
            f.add(m.total() + " / " + m.objetivo());
            filas.add(f);
        }
        x.hoja("Calendario", enc, filas);

        filas = new ArrayList<>();
        for (AsignacionPartida a : r.asignaciones())
            filas.add(List.of(a.maquina(), a.fecha().toString(), a.partida(), a.pedido(), a.fechaEntrega().toString(),
                    a.barca(), a.estado() == EstadoAsignacion.SOBRANTE ? "Sobrante" : "Asignada", a.fechaDisponible().toString(),
                    a.ordenConformacion(), a.tonalidad().nombre(), a.requiereLavado() ? "Sí" : "No"));
        x.hoja("Asignaciones", List.of("maquina", "fecha", "partida", "pedido", "fecha_entrega", "barca", "estado",
                "fecha_disponible", "orden_conformacion", "tonalidad", "requiere_lavado"), filas);

        enc = new ArrayList<>(List.of("maquina", "barca", "objetivo", "minimo_diario", "maximo_diario"));
        for (int d = 0; d < dias.length; d++) enc.add(nombreDia(dias, d));
        enc.addAll(List.of("asignadas", "sobrantes", "total", "diferencia"));
        filas = new ArrayList<>();
        for (ResumenMaquina m : r.maquinas()) {
            List<Object> f = new ArrayList<>(List.of(m.maquina(), m.barca(), m.objetivo(), m.minimoDiario(), m.maximoDiario()));
            for (int v : m.partidasPorDia()) f.add(v < 0 ? "X" : (Object) v);
            f.addAll(List.of(m.asignadas(), m.sobrantes(), m.total(), m.diferencia()));
            filas.add(f);
        }
        x.hoja("Resumen por maquina", enc, filas);

        filas = new ArrayList<>();
        for (PartidaBloqueada p : r.bloqueadas())
            filas.add(new ArrayList<>(List.of(p.partida(), p.pedido(),
                    p.fechaEntrega() == null ? "" : p.fechaEntrega().toString(), p.barca(), p.motivo())));
        x.hoja("Bloqueadas", List.of("partida", "pedido", "fecha_entrega", "barca", "motivo"), filas);

        Indicadores i = r.indicadores();
        filas = new ArrayList<>();
        filas.add(List.of("Función objetivo (F)", i.funcionObjetivo()));
        filas.add(List.of("Desbalance semanal (E_S)", i.desbalanceSemanal()));
        filas.add(List.of("Desbalance diario (E_D)", i.desbalanceDiario()));
        filas.add(List.of("Lavados (Λ)", i.lavados()));
        filas.add(List.of("Objetivo total de partidas", i.objetivoTotal()));
        filas.add(List.of("Partidas conformadas", i.conformadas()));
        filas.add(List.of("Partidas asignadas", i.asignadas()));
        filas.add(List.of("Partidas Sobrantes", i.sobrantes()));
        filas.add(List.of("Partidas bloqueadas", i.bloqueadas()));
        filas.add(List.of("Evaluaciones", i.evaluaciones()));
        filas.add(List.of("Iteraciones", i.iteraciones()));
        filas.add(List.of("Tiempo de ejecución (ms)", i.tiempoMs()));
        filas.add(List.of("Detenida por tiempo máximo", i.detenidaPorTiempo() ? "Sí" : "No"));
        filas.add(List.of("Factible", i.factible() ? "Sí" : "No"));
        for (String inc : i.incumplimientos()) filas.add(List.of("Incumplimiento", inc));
        for (String adv : r.advertencias()) filas.add(List.of("Advertencia", adv));
        x.hoja("Indicadores", List.of("indicador", "valor"), filas);

        filas = new ArrayList<>();
        for (StockArticulo s : r.estadoSiguiente().stockFinal())
            filas.add(new ArrayList<>(List.of(s.claveArticulo(), s.articulo() == null ? "" : s.articulo(), s.kg())));
        x.hoja("StockFinal", List.of("clave_articulo", "articulo", "kg"), filas);

        x.guardar(archivo);
    }
}
