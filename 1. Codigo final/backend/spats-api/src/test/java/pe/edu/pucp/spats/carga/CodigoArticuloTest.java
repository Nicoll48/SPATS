package pe.edu.pucp.spats.carga;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import pe.edu.pucp.spats.carga.hoja.Plantilla;
import pe.edu.pucp.spats.carga.hoja.ValidacionLibro;
import pe.edu.pucp.spats.comun.hoja.LectorLibro;

/** D-25: al importar se quita solo un prefijo numérico entre paréntesis al inicio del código de artículo. */
class CodigoArticuloTest {

    private static final String COLUMNA = Plantilla.Columnas.CODIGO_ARTICULO;

    private static LectorLibro.Fila fila(String codigo) {
        return new LectorLibro.Fila(2, Map.of(COLUMNA, codigo));
    }

    @Test
    void quitaElPrefijoNumericoYAvisaConAmbosCodigos() {
        ValidacionLibro v = new ValidacionLibro(Plantilla.PEDIDOS, Map.of());
        assertThat(v.codigoArticulo("Pedidos", fila("(313811)TW2021AA00P03"), COLUMNA)).isEqualTo("TW2021AA00P03");
        assertThat(v.advertencias()).singleElement().satisfies(a -> assertThat(a.motivo())
                .contains("«(313811)TW2021AA00P03»").contains("«TW2021AA00P03»"));
        assertThat(v.hayErrores()).isFalse();
    }

    @Test
    void conservaLosCodigosSinPrefijoNumericoAlInicio() {
        ValidacionLibro v = new ValidacionLibro(Plantilla.PEDIDOS, Map.of());
        assertThat(v.codigoArticulo("Pedidos", fila("TW2021AA00P03"), COLUMNA)).isEqualTo("TW2021AA00P03");
        assertThat(v.codigoArticulo("Pedidos", fila("(AB12)TW2021"), COLUMNA)).isEqualTo("(AB12)TW2021");
        assertThat(v.codigoArticulo("Pedidos", fila("TW(313811)2021"), COLUMNA)).isEqualTo("TW(313811)2021");
        assertThat(v.advertencias()).isEmpty();
    }

    @Test
    void elCodigoVacioSigueSiendoObligatorio() {
        ValidacionLibro v = new ValidacionLibro(Plantilla.PEDIDOS, Map.of());
        assertThat(v.codigoArticulo("Pedidos", fila(""), COLUMNA)).isNull();
        assertThat(v.hayErrores()).isTrue();
    }
}
