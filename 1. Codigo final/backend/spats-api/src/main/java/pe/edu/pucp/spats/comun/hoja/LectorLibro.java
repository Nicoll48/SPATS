package pe.edu.pucp.spats.comun.hoja;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 * Lee un libro .xlsx como texto, hoja por hoja. Los números se leen sin notación científica y las fechas
 * en ISO ({@code yyyy-MM-dd}); la validación de formato la hace cada importador.
 */
public final class LectorLibro {

    private LectorLibro() { }

    /** Fila leída: su número en la hoja (la cabecera es la fila 1) y el texto de cada columna por cabecera. */
    public record Fila(int numero, Map<String, String> valores) {
        public String valor(String columna) {
            String v = valores.get(columna);
            return v == null ? "" : v;
        }
    }

    public record Hoja(String nombre, List<String> cabeceras, List<Fila> filas) { }

    public static Map<String, Hoja> leer(InputStream entrada) throws IOException {
        Map<String, Hoja> hojas = new LinkedHashMap<>();
        DataFormatter formato = new DataFormatter();
        try (Workbook libro = WorkbookFactory.create(entrada)) {
            for (Sheet hoja : libro) hojas.put(normalizar(hoja.getSheetName()), leerHoja(hoja, formato));
        }
        return hojas;
    }

    public static Optional<Hoja> hoja(Map<String, Hoja> libro, String nombre) {
        return Optional.ofNullable(libro.get(normalizar(nombre)));
    }

    private static Hoja leerHoja(Sheet hoja, DataFormatter formato) {
        Row cabecera = hoja.getRow(hoja.getFirstRowNum());
        List<String> cabeceras = new ArrayList<>();
        if (cabecera != null)
            for (int c = 0; c < cabecera.getLastCellNum(); c++) cabeceras.add(normalizar(texto(cabecera.getCell(c), formato)));
        List<Fila> filas = new ArrayList<>();
        for (int r = hoja.getFirstRowNum() + 1; r <= hoja.getLastRowNum(); r++) {
            Row fila = hoja.getRow(r);
            if (fila == null) continue;
            Map<String, String> valores = new LinkedHashMap<>();
            boolean vacia = true;
            for (int c = 0; c < cabeceras.size(); c++) {
                String v = texto(fila.getCell(c), formato).trim();
                if (!v.isEmpty()) vacia = false;
                valores.put(cabeceras.get(c), v);
            }
            if (!vacia) filas.add(new Fila(r + 1, valores));
        }
        return new Hoja(hoja.getSheetName(), cabeceras, filas);
    }

    private static String texto(Cell celda, DataFormatter formato) {
        if (celda == null) return "";
        CellType tipo = celda.getCellType() == CellType.FORMULA ? celda.getCachedFormulaResultType() : celda.getCellType();
        return switch (tipo) {
            case NUMERIC -> DateUtil.isCellDateFormatted(celda)
                    ? LocalDate.from(celda.getLocalDateTimeCellValue()).toString()
                    : BigDecimal.valueOf(celda.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> celda.getBooleanCellValue() ? "SI" : "NO";
            case STRING -> celda.getStringCellValue();
            case BLANK, ERROR, _NONE -> "";
            default -> formato.formatCellValue(celda);
        };
    }

    /** Cabeceras y nombres de hoja se comparan sin mayúsculas ni espacios en los extremos. */
    public static String normalizar(String texto) {
        return texto == null ? "" : texto.trim().toLowerCase();
    }
}
