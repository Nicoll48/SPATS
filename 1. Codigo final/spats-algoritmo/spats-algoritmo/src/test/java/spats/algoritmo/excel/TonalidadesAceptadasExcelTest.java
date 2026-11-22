package spats.algoritmo.excel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.EnumSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import spats.algoritmo.entrada.Maquina;
import spats.algoritmo.entrada.Tonalidad;

/** D-51: columna opcional tonalidades_aceptadas de la hoja Maquinas. */
class TonalidadesAceptadasExcelTest {

    @Test
    @DisplayName("PU-46 Columna tonalidades_aceptadas: lista por nombre o rango; vacía o ausente, las cuatro")
    void columnaOpcional() {
        assertEquals(EnumSet.of(Tonalidad.BLANCO, Tonalidad.CLARO), LectorDatosExcel.tonalidades("Blanco, Claro"));
        assertEquals(EnumSet.of(Tonalidad.BLANCO, Tonalidad.MEDIO), LectorDatosExcel.tonalidades("1,3.0"));
        assertNull(LectorDatosExcel.tonalidades(""));
        assertEquals(EnumSet.allOf(Tonalidad.class),
                new Maquina("M", 200, "C", "200 C", 16, LectorDatosExcel.tonalidades("")).tonalidadesAceptadas());
    }
}
