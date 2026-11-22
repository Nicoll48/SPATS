package spats.algoritmo.entrada;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Fila de la proyección de tejeduría: kg de un artículo tejidos en una fecha.
 *
 * @param maquinaTejeduria máquina de tejeduría
 * @param pedido           pedido para el que se teje
 * @param articulo         descripción del artículo
 * @param claveArticulo    clave normalizada del artículo
 * @param fechaTejido      fecha de tejido
 * @param kg               kg proyectados
 */
public record RegistroProyeccion(String maquinaTejeduria, String pedido, String articulo, String claveArticulo,
                                 LocalDate fechaTejido, double kg) {

    /** Fecha desde la que la tela está disponible para tintorería (RD-08). */
    public LocalDate fechaDisponible() { return fechaDisponible(fechaTejido); }

    /** Lo tejido de lunes a viernes queda disponible al día siguiente; lo tejido el sábado o domingo, el lunes. */
    public static LocalDate fechaDisponible(LocalDate fechaTejido) {
        return fechaTejido.getDayOfWeek() == DayOfWeek.SATURDAY ? fechaTejido.plusDays(2) : fechaTejido.plusDays(1);
    }
}
