package pe.edu.pucp.spats.tejeduria.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import pe.edu.pucp.spats.tejeduria.entity.EstadoProyeccion;

/** Contrato JSON del módulo de proyección de tejeduría. */
public final class ProyeccionDtos {

    private ProyeccionDtos() { }

    public record VersionProyeccionResponse(Integer id, LocalDate semana, int version, LocalDateTime fechaCarga,
                                            EstadoProyeccion estado, long registros, BigDecimal kgTotales) { }

    /**
     * @param fechaDisponible día en que esa tela queda disponible en tintorería (regla del algoritmo)
     * @param enVentana       true si la fecha de tejido cae entre el sábado anterior y el viernes de la semana
     */
    public record DetalleProyeccionResponse(Integer id, Integer idDetallePedido, String numeroOp,
                                            String codigoArticulo, String nombreArticulo, LocalDate fechaProyectada,
                                            LocalDate fechaDisponible, BigDecimal kgProyectados, boolean enVentana) { }

    public record ModificarDetalleProyeccionRequest(
            @NotNull(message = "Indica la fecha de tejido.") LocalDate fechaProyectada,
            @NotNull @Positive(message = "Los kilos deben ser mayores que cero.") @Digits(integer = 9, fraction = 3)
            BigDecimal kgProyectados) { }
}
