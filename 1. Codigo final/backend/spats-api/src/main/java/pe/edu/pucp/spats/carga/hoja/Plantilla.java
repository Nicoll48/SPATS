package pe.edu.pucp.spats.carga.hoja;

import java.util.List;

import pe.edu.pucp.spats.carga.entity.TipoDato;

/**
 * Plantillas de importación (RF08, RF09). Sus columnas salen de las tablas del script de la base y de lo
 * que el algoritmo necesita (sección 9.2 del contexto); la documentación está en {@code docs/plantillas.md}.
 */
public enum Plantilla {

    PEDIDOS(TipoDato.PEDIDOS, "plantilla_pedidos.xlsx", List.of(
            new Hoja("Pedidos", List.of(Columnas.NUMERO_OP, Columnas.CLIENTE, Columnas.FECHA_ENTREGA,
                    Columnas.CODIGO_ARTICULO, Columnas.NOMBRE_ARTICULO, Columnas.PESO_TOTAL_REQUERIDO),
                    List.of(List.of("34187", "INTRATESA S.A.C.", "2026-09-16", "G050/1AL00P03", "GAMUZA 50/1 ALG. PEI. - 03", 1200),
                            List.of("34187", "INTRATESA S.A.C.", "2026-09-16", "RZ30/1ALC2P01", "RIB 1X1 30/1 ALG. LYC. - 01", 150))))),

    PARTIDAS(TipoDato.PARTIDAS, "plantilla_partidas.xlsx", List.of(
            new Hoja("Partidas", List.of(Columnas.NUMERO_PARTIDA, Columnas.NUMERO_OP, Columnas.CODIGO_COLOR,
                    Columnas.NOMBRE_COLOR, Columnas.CLASIFICACION_COLOR, Columnas.TONALIDAD, Columnas.BARCA, Columnas.PESO_TOTAL),
                    List.of(List.of("314618", "34187", "B0198700", "BLANCO NEUTRO", "C", "BLANCO", "200 C", 180))),
            new Hoja("Componentes", List.of(Columnas.NUMERO_PARTIDA, Columnas.CODIGO_ARTICULO, Columnas.TIPO_COMPONENTE,
                    Columnas.TUBULAR_ABIERTO, Columnas.PESO_REQUERIDO),
                    List.of(List.of("314618", "G050/1AL00P03", "PRINCIPAL", "NO", 160),
                            List.of("314618", "RZ30/1ALC2P01", "COMPLEMENTO", "NO", 20))))),

    PROYECCION_TEJEDURIA(TipoDato.PROYECCION_TEJEDURIA, "plantilla_proyeccion_tejeduria.xlsx", List.of(
            new Hoja("Proyeccion", List.of(Columnas.NUMERO_OP, Columnas.CODIGO_ARTICULO, Columnas.FECHA_PROYECTADA,
                    Columnas.KG_PROYECTADOS),
                    List.of(List.of("34187", "G050/1AL00P03", "2026-09-12", 600),
                            List.of("34187", "RZ30/1ALC2P01", "2026-09-14", 75)))));

    /** Hoja de la plantilla: nombre, columnas obligatorias (en orden) y filas de ejemplo. */
    public record Hoja(String nombre, List<String> columnas, List<List<Object>> ejemplo) { }

    private final TipoDato tipo;
    private final String archivo;
    private final List<Hoja> hojas;

    Plantilla(TipoDato tipo, String archivo, List<Hoja> hojas) {
        this.tipo = tipo;
        this.archivo = archivo;
        this.hojas = hojas;
    }

    public TipoDato tipo() { return tipo; }
    public String archivo() { return archivo; }
    public List<Hoja> hojas() { return hojas; }

    public Hoja hoja(String nombre) {
        return hojas.stream().filter(h -> h.nombre().equals(nombre)).findFirst().orElseThrow();
    }

    public static Plantilla de(TipoDato tipo) {
        for (Plantilla p : values()) if (p.tipo == tipo) return p;
        throw new IllegalArgumentException(tipo.name());
    }

    /** Nombres de columna de las plantillas. */
    public static final class Columnas {
        public static final String NUMERO_OP = "numero_op";
        public static final String CLIENTE = "cliente";
        public static final String FECHA_ENTREGA = "fecha_entrega";
        public static final String CODIGO_ARTICULO = "codigo_articulo";
        public static final String NOMBRE_ARTICULO = "nombre_articulo";
        public static final String PESO_TOTAL_REQUERIDO = "peso_total_requerido";
        public static final String NUMERO_PARTIDA = "numero_partida";
        public static final String CODIGO_COLOR = "codigo_color";
        public static final String NOMBRE_COLOR = "nombre_color";
        public static final String CLASIFICACION_COLOR = "clasificacion_color";
        public static final String TONALIDAD = "tonalidad";
        public static final String BARCA = "barca";
        public static final String PESO_TOTAL = "peso_total";
        public static final String TIPO_COMPONENTE = "tipo_componente";
        public static final String TUBULAR_ABIERTO = "tubular_abierto";
        public static final String PESO_REQUERIDO = "peso_requerido";
        public static final String FECHA_PROYECTADA = "fecha_proyectada";
        public static final String KG_PROYECTADOS = "kg_proyectados";

        private Columnas() { }
    }
}
