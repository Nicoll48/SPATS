package spats.algoritmo.excel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Lector mínimo de libros .xlsx (Office Open XML) con la biblioteca estándar de Java.
 * Devuelve cada hoja como una lista de filas, donde cada fila asocia el encabezado de la columna
 * con el valor de la celda en texto. Las fechas guardadas como número de serie de Excel se
 * convierten en {@link LectorDatosExcel}.
 */
public final class LectorXlsx implements AutoCloseable {

    private final ZipFile zip;
    private final Map<String, String> rutaHoja = new LinkedHashMap<>();
    private final List<String> compartidas = new ArrayList<>();

    public LectorXlsx(Path archivo) throws IOException {
        zip = new ZipFile(archivo.toFile());
        try {
            Document rels = xml("xl/_rels/workbook.xml.rels");
            Map<String, String> destino = new HashMap<>();
            for (Element r : elementos(rels.getDocumentElement(), "Relationship")) {
                String t = r.getAttribute("Target");
                if (t.startsWith("/")) t = t.substring(1); else if (!t.startsWith("xl/")) t = "xl/" + t;
                destino.put(r.getAttribute("Id"), t);
            }
            Document wb = xml("xl/workbook.xml");
            for (Element h : elementos(wb.getDocumentElement(), "sheet")) {
                String id = h.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
                rutaHoja.put(h.getAttribute("name"), destino.get(id));
            }
            if (zip.getEntry("xl/sharedStrings.xml") != null)
                for (Element si : elementos(xml("xl/sharedStrings.xml").getDocumentElement(), "si"))
                    compartidas.add(textoDe(si));
        } catch (IOException | RuntimeException ex) {
            zip.close();
            throw ex;
        }
    }

    public List<String> hojas() { return new ArrayList<>(rutaHoja.keySet()); }

    public boolean tieneHoja(String nombre) { return rutaHoja.containsKey(nombre); }

    /** Filas de la hoja (sin el encabezado); las celdas vacías no aparecen en el mapa. */
    public List<Map<String, String>> leerHoja(String nombre) throws IOException {
        String ruta = rutaHoja.get(nombre);
        if (ruta == null) throw new IOException("El libro no tiene la hoja " + nombre);
        Document doc = xml(ruta);
        List<Map<String, String>> filas = new ArrayList<>();
        Map<Integer, String> encabezado = null;
        for (Element row : elementos(doc.getDocumentElement(), "row")) {
            Map<Integer, String> celdas = new HashMap<>();
            int sinReferencia = 0;
            for (Element c : elementos(row, "c")) {
                String ref = c.getAttribute("r");
                int col = ref.isEmpty() ? sinReferencia : columna(ref);
                sinReferencia = col + 1;
                String v = valor(c);
                if (v != null && !v.isEmpty()) celdas.put(col, v);
            }
            if (encabezado == null) { encabezado = celdas; continue; }
            if (celdas.isEmpty()) continue;
            Map<String, String> fila = new LinkedHashMap<>();
            for (Map.Entry<Integer, String> e : celdas.entrySet()) {
                String h = encabezado.get(e.getKey());
                if (h != null) fila.put(h.trim(), e.getValue().trim());
            }
            filas.add(fila);
        }
        return filas;
    }

    private String valor(Element c) {
        String tipo = c.getAttribute("t");
        if (tipo.equals("inlineStr")) {
            List<Element> is = elementos(c, "is");
            return is.isEmpty() ? null : textoDe(is.get(0));
        }
        List<Element> v = elementos(c, "v");
        if (v.isEmpty()) return null;
        String x = v.get(0).getTextContent();
        if (tipo.equals("s")) return compartidas.get(Integer.parseInt(x.trim()));
        if (tipo.equals("b")) return x.trim().equals("1") ? "TRUE" : "FALSE";
        return x;
    }

    /** Texto de un elemento de cadena (con o sin formato enriquecido), sin la guía fonética. */
    private static String textoDe(Element e) {
        StringBuilder sb = new StringBuilder();
        for (Element t : elementos(e, "t")) {
            Node padre = t.getParentNode();
            if (padre != null && "rPh".equals(padre.getLocalName())) continue;
            sb.append(t.getTextContent());
        }
        return sb.toString();
    }

    /** Índice de columna (base 0) a partir de una referencia como "AB12". */
    static int columna(String ref) {
        int c = 0;
        for (char ch : ref.toCharArray()) {
            if (!Character.isLetter(ch)) break;
            c = c * 26 + (Character.toUpperCase(ch) - 'A' + 1);
        }
        return c - 1;
    }

    private static List<Element> elementos(Element raiz, String nombreLocal) {
        NodeList nl = raiz.getElementsByTagNameNS("*", nombreLocal);
        List<Element> r = new ArrayList<>(nl.getLength());
        for (int i = 0; i < nl.getLength(); i++) r.add((Element) nl.item(i));
        return r;
    }

    private Document xml(String ruta) throws IOException {
        ZipEntry e = zip.getEntry(ruta);
        if (e == null) throw new IOException("Archivo .xlsx incompleto: falta " + ruta);
        try (InputStream in = zip.getInputStream(e)) {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setExpandEntityReferences(false);
            DocumentBuilder b = f.newDocumentBuilder();
            return b.parse(in);
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException("No se pudo leer " + ruta + ": " + ex.getMessage(), ex);
        }
    }

    @Override
    public void close() throws IOException { zip.close(); }
}
