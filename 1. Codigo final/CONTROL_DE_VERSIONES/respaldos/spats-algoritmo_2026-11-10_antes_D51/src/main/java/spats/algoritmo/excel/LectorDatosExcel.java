package spats.algoritmo.excel;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import spats.algoritmo.aco.ParametrosACO;
import spats.algoritmo.entrada.Componente;
import spats.algoritmo.entrada.DatosSemana;
import spats.algoritmo.entrada.EstadoPartida;
import spats.algoritmo.entrada.Indisponibilidad;
import spats.algoritmo.entrada.Maquina;
import spats.algoritmo.entrada.ParametrosPlanificacion;
import spats.algoritmo.entrada.Partida;
import spats.algoritmo.entrada.Pedido;
import spats.algoritmo.entrada.RegistroProyeccion;
import spats.algoritmo.entrada.StockArticulo;
import spats.algoritmo.entrada.Tonalidad;

/**
 * Carga los datos de una semana desde un libro de Excel con las hojas Parametros, Maquinas,
 * Indisponibilidad, Pedidos, Partidas, Componentes, Proyeccion y StockInicial.
 * Es el adaptador de prueba del componente: en el sistema, estos datos provendrán de la base de datos.
 */
public final class LectorDatosExcel {

    /** Datos de la semana y parámetros de la colonia de hormigas leídos del libro. */
    public record Configuracion(DatosSemana datos, ParametrosACO parametrosACO) { }

    private LectorDatosExcel() { }

    public static Configuracion leer(Path archivo) throws IOException {
        try (LectorXlsx x = new LectorXlsx(archivo)) {
            ParametrosPlanificacion par = new ParametrosPlanificacion(null);
            ParametrosACO aco = new ParametrosACO();
            for (Map<String, String> f : x.leerHoja("Parametros")) {
                String clave = f.getOrDefault("parametro", "").trim(), v = f.get("valor");
                if (clave.isEmpty() || v == null) continue;
                Celda c = new Celda("Parametros", clave, f);
                switch (clave) {
                    case "fecha_inicio_semana" -> par.setFechaInicioSemana(c.fecha("valor"));
                    case "dias_semana" -> par.setDiasSemana(c.entero("valor"));
                    case "reduccion_objetivo_por_dia_indisponible" -> par.setReduccionObjetivoPorDiaIndisponible(c.entero("valor"));
                    case "peso_desbalance_semanal" -> par.setPesoDesbalanceSemanal(c.numero("valor"));
                    case "peso_desbalance_diario" -> par.setPesoDesbalanceDiario(c.numero("valor"));
                    case "peso_lavados" -> par.setPesoLavados(c.numero("valor"));
                    case "hormigas" -> aco.setHormigas(c.entero("valor"));
                    case "rho" -> aco.setRho(c.numero("valor"));
                    case "feromona_inicial" -> aco.setFeromonaInicial(c.numero("valor"));
                    case "peso_feromona" -> aco.setPesoFeromona(c.numero("valor"));
                    case "peso_heuristica" -> aco.setPesoHeuristica(c.numero("valor"));
                    case "evaluaciones" -> aco.setEvaluaciones(c.entero("valor"));
                    case "tiempo_maximo_segundos" -> aco.setTiempoMaximoSegundos(c.numero("valor"));
                    case "semilla" -> aco.setSemilla((long) c.numero("valor"));
                    default -> { }   // parámetros informativos (p. ej. agrupar_tela_por)
                }
            }
            List<Maquina> maquinas = new ArrayList<>();
            for (Celda c : filas(x, "Maquinas"))
                maquinas.add(new Maquina(c.codigo("maquina"), c.numero("capacidad"), c.texto("tipo"), c.texto("barca"),
                        c.entero("objetivo_semanal")));
            List<Indisponibilidad> indisp = new ArrayList<>();
            if (x.tieneHoja("Indisponibilidad"))
                for (Celda c : filas(x, "Indisponibilidad"))
                    indisp.add(new Indisponibilidad(c.codigo("maquina"), c.fecha("fecha"), c.opcional("motivo")));
            List<Pedido> pedidos = new ArrayList<>();
            for (Celda c : filas(x, "Pedidos"))
                pedidos.add(new Pedido(c.codigo("pedido"), c.opcional("cliente"), c.fechaOpcional("fecha_pedido"),
                        c.fechaOpcional("fecha_entrega")));
            List<Partida> partidas = new ArrayList<>();
            for (Celda c : filas(x, "Partidas"))
                partidas.add(new Partida(c.codigo("partida"), c.codigo("pedido"), c.opcional("cod_color"), c.opcional("color"),
                        c.numero("peso"), Tonalidad.desdeTexto(c.texto("tonalidad")), c.texto("barca"),
                        c.entero("orden_registro"), EstadoPartida.desdeTexto(c.opcional("estado"))));
            List<Componente> componentes = new ArrayList<>();
            for (Celda c : filas(x, "Componentes"))
                componentes.add(new Componente(c.codigo("partida"), c.opcional("cod_articulo"), c.opcional("articulo"),
                        c.texto("clave_articulo"), c.opcional("rol"), c.numero("kg_requeridos")));
            List<RegistroProyeccion> proyeccion = new ArrayList<>();
            for (Celda c : filas(x, "Proyeccion"))
                proyeccion.add(new RegistroProyeccion(c.opcional("maquina_tejeduria"), c.opcional("pedido"), c.opcional("articulo"),
                        c.texto("clave_articulo"), c.fecha("fecha_tejido"), c.numero("kg")));
            List<StockArticulo> stock = new ArrayList<>();
            if (x.tieneHoja("StockInicial"))
                for (Celda c : filas(x, "StockInicial"))
                    stock.add(new StockArticulo(c.texto("clave_articulo"), c.opcional("articulo"), c.numero("kg")));
            DatosSemana datos = new DatosSemana(par, maquinas, indisp, pedidos, partidas, componentes, proyeccion, stock);
            return new Configuracion(datos, aco);
        }
    }

    private static List<Celda> filas(LectorXlsx x, String hoja) throws IOException {
        List<Map<String, String>> fs = x.leerHoja(hoja);
        List<Celda> r = new ArrayList<>(fs.size());
        for (int i = 0; i < fs.size(); i++) r.add(new Celda(hoja, "fila " + (i + 2), fs.get(i)));
        return r;
    }

    /** Acceso a las celdas de una fila con conversión de tipos y mensajes de error claros. */
    private record Celda(String hoja, String ubicacion, Map<String, String> f) {
        private static final LocalDate BASE_EXCEL = LocalDate.of(1899, 12, 30);
        private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("d/M/yyyy");

        String texto(String col) {
            String v = f.get(col);
            if (v == null || v.isBlank()) throw error(col, "está vacía");
            return v.trim();
        }

        String opcional(String col) { String v = f.get(col); return v == null ? "" : v.trim(); }

        /** Códigos guardados como número en Excel ("314618.0") se leen como "314618". */
        String codigo(String col) { return texto(col).replaceAll("\\.0+$", ""); }

        double numero(String col) {
            String v = texto(col);
            try { return Double.parseDouble(v); }
            catch (NumberFormatException e) {
                try { return Double.parseDouble(v.replace(',', '.')); }
                catch (NumberFormatException e2) { throw error(col, "no es un número: " + v); }
            }
        }

        int entero(String col) {
            double d = numero(col);
            if (d != Math.rint(d)) throw error(col, "debe ser un número entero: " + d);
            return (int) d;
        }

        LocalDate fecha(String col) {
            String v = texto(col);
            try { return BASE_EXCEL.plusDays((long) Math.floor(Double.parseDouble(v))); }
            catch (NumberFormatException ignored) { }
            String s = v.length() >= 10 ? v.substring(0, 10) : v;
            try { return LocalDate.parse(s); } catch (DateTimeParseException ignored) { }
            try { return LocalDate.parse(v, DMY); } catch (DateTimeParseException ignored) { }
            throw error(col, "no es una fecha: " + v);
        }

        LocalDate fechaOpcional(String col) {
            String v = f.get(col);
            return v == null || v.isBlank() ? null : fecha(col);
        }

        IllegalArgumentException error(String col, String detalle) {
            return new IllegalArgumentException("Hoja " + hoja + ", " + ubicacion + ": la columna '" + col + "' " + detalle);
        }
    }
}
