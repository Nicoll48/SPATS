package spats.algoritmo.excel;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Escritor mínimo de libros .xlsx con la biblioteca estándar de Java: hojas con encabezado en negrita,
 * fila de encabezado inmovilizada y ancho de columnas según su contenido.
 */
public final class EscritorXlsx {

    private record Hoja(String nombre, List<String> encabezado, List<List<Object>> filas) { }

    private final List<Hoja> hojas = new ArrayList<>();

    /** Agrega una hoja; los valores numéricos se guardan como número y el resto como texto. */
    public EscritorXlsx hoja(String nombre, List<String> encabezado, List<List<Object>> filas) {
        hojas.add(new Hoja(nombre, encabezado, filas));
        return this;
    }

    public void guardar(Path archivo) throws IOException {
        if (archivo.getParent() != null) Files.createDirectories(archivo.getParent());
        try (OutputStream os = Files.newOutputStream(archivo); ZipOutputStream z = new ZipOutputStream(os)) {
            StringBuilder tipos = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                    + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
            StringBuilder wb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                    + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
            StringBuilder rels = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
            for (int i = 1; i <= hojas.size(); i++) {
                tipos.append("<Override PartName=\"/xl/worksheets/sheet").append(i)
                     .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
                wb.append("<sheet name=\"").append(esc(hojas.get(i - 1).nombre())).append("\" sheetId=\"").append(i)
                  .append("\" r:id=\"rId").append(i).append("\"/>");
                rels.append("<Relationship Id=\"rId").append(i)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
                    .append(i).append(".xml\"/>");
            }
            int n = hojas.size() + 1;
            rels.append("<Relationship Id=\"rId").append(n)
                .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>");
            tipos.append("</Types>");
            wb.append("</sheets></workbook>");
            escribir(z, "[Content_Types].xml", tipos.toString());
            escribir(z, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                    + "</Relationships>");
            escribir(z, "xl/workbook.xml", wb.toString());
            escribir(z, "xl/_rels/workbook.xml.rels", rels.toString());
            escribir(z, "xl/styles.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                    + "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                    + "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
                    + "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>"
                    + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
                    + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                    + "<cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                    + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/></cellXfs>"
                    + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
                    + "</styleSheet>");
            for (int i = 1; i <= hojas.size(); i++) escribir(z, "xl/worksheets/sheet" + i + ".xml", hojaXml(hojas.get(i - 1)));
        }
    }

    private static String hojaXml(Hoja h) {
        int nc = h.encabezado().size();
        int[] ancho = new int[nc];
        for (int c = 0; c < nc; c++) ancho[c] = h.encabezado().get(c).length();
        for (List<Object> f : h.filas())
            for (int c = 0; c < Math.min(nc, f.size()); c++)
                if (f.get(c) != null) ancho[c] = Math.max(ancho[c], texto(f.get(c)).length());
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>"
                + "</sheetView></sheetViews><cols>");
        for (int c = 0; c < nc; c++)
            sb.append("<col min=\"").append(c + 1).append("\" max=\"").append(c + 1).append("\" width=\"")
              .append(Math.min(80, ancho[c] + 3)).append("\" customWidth=\"1\"/>");
        sb.append("</cols><sheetData>");
        fila(sb, 1, new ArrayList<>(h.encabezado()), true);
        int r = 2;
        for (List<Object> f : h.filas()) fila(sb, r++, f, false);
        sb.append("</sheetData></worksheet>");
        return sb.toString();
    }

    private static void fila(StringBuilder sb, int r, List<?> valores, boolean negrita) {
        sb.append("<row r=\"").append(r).append("\">");
        for (int c = 0; c < valores.size(); c++) {
            Object v = valores.get(c);
            if (v == null) continue;
            String ref = referencia(c) + r;
            String estilo = negrita ? " s=\"1\"" : "";
            if (v instanceof Number num && Double.isFinite(num.doubleValue()))
                sb.append("<c r=\"").append(ref).append('"').append(estilo).append("><v>").append(texto(num)).append("</v></c>");
            else
                sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"").append(estilo)
                  .append("><is><t>").append(esc(texto(v))).append("</t></is></c>");
        }
        sb.append("</row>");
    }

    private static String texto(Object v) {
        if (v instanceof Double d && d == Math.rint(d) && Math.abs(d) < 1e15) return String.valueOf(d.longValue());
        return String.valueOf(v);
    }

    static String referencia(int c) {
        StringBuilder s = new StringBuilder();
        for (int x = c + 1; x > 0; x = (x - 1) / 26) s.insert(0, (char) ('A' + (x - 1) % 26));
        return s.toString();
    }

    private static String esc(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                default -> { if (ch >= 0x20 || ch == '\t' || ch == '\n' || ch == '\r') sb.append(ch); }
            }
        }
        return sb.toString();
    }

    private static void escribir(ZipOutputStream z, String nombre, String contenido) throws IOException {
        z.putNextEntry(new ZipEntry(nombre));
        z.write(contenido.getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
    }
}
