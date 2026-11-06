package pe.edu.pucp.spats.comun.hoja;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Arma un libro .xlsx con una o varias hojas de tabla (plantillas, descargas y exportaciones). */
public final class EscritorLibro implements AutoCloseable {

    public static final String TIPO_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final XSSFWorkbook libro = new XSSFWorkbook();
    private final CellStyle cabecera;
    private final CellStyle fecha;
    private final CellStyle fechaHora;

    public EscritorLibro() {
        Font negrita = libro.createFont();
        negrita.setBold(true);
        cabecera = libro.createCellStyle();
        cabecera.setFont(negrita);
        CreationHelper ayuda = libro.getCreationHelper();
        fecha = libro.createCellStyle();
        fecha.setDataFormat(ayuda.createDataFormat().getFormat("yyyy-mm-dd"));
        fechaHora = libro.createCellStyle();
        fechaHora.setDataFormat(ayuda.createDataFormat().getFormat("yyyy-mm-dd hh:mm"));
    }

    public EscritorLibro hoja(String nombre, List<String> cabeceras, List<? extends List<?>> filas) {
        Sheet hoja = libro.createSheet(nombre);
        Row fila0 = hoja.createRow(0);
        for (int c = 0; c < cabeceras.size(); c++) {
            Cell celda = fila0.createCell(c);
            celda.setCellValue(cabeceras.get(c));
            celda.setCellStyle(cabecera);
        }
        for (int f = 0; f < filas.size(); f++) {
            Row fila = hoja.createRow(f + 1);
            List<?> valores = filas.get(f);
            for (int c = 0; c < valores.size(); c++) escribir(fila.createCell(c), valores.get(c));
        }
        for (int c = 0; c < cabeceras.size(); c++) hoja.setColumnWidth(c, Math.min(60, Math.max(12, cabeceras.get(c).length() + 4)) * 256);
        hoja.createFreezePane(0, 1);
        return this;
    }

    public byte[] aBytes() {
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void escribir(Cell celda, Object valor) {
        switch (valor) {
            case null -> celda.setBlank();
            case Number n when n instanceof BigDecimal bd -> celda.setCellValue(bd.doubleValue());
            case Number n -> celda.setCellValue(n.doubleValue());
            case Boolean b -> celda.setCellValue(b ? "SI" : "NO");
            case LocalDate d -> { celda.setCellValue(d); celda.setCellStyle(fecha); }
            case LocalDateTime d -> { celda.setCellValue(d); celda.setCellStyle(fechaHora); }
            default -> celda.setCellValue(valor.toString());
        }
    }

    @Override
    public void close() throws IOException {
        libro.close();
    }
}
