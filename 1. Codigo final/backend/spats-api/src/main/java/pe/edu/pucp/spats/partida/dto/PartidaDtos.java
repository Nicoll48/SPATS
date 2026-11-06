package pe.edu.pucp.spats.partida.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import pe.edu.pucp.spats.comun.dominio.ClasificacionColor;
import pe.edu.pucp.spats.partida.entity.EstadoPartida;
import pe.edu.pucp.spats.partida.entity.TipoComponente;
import pe.edu.pucp.spats.partida.entity.TonalidadColor;

/** Contrato JSON del módulo de partidas y colores. */
public final class PartidaDtos {

    private PartidaDtos() { }

    public record ComponenteResponse(Integer id, Integer idDetallePedido, String codigoArticulo, String nombreArticulo,
                                     TipoComponente tipoComponente, boolean tubularAbierto, BigDecimal pesoRequerido) { }

    public record PartidaResponse(Integer id, String numeroPartida, Integer idPedido, String numeroOp, String cliente,
                                  LocalDate fechaEntrega, String codigoColor, String color,
                                  ClasificacionColor clasificacionColor, TonalidadColor tonalidad, String barca,
                                  BigDecimal pesoTotal, LocalDateTime fechaRegistro, EstadoPartida estado,
                                  List<ComponenteResponse> componentes) { }

    public record ColorResponse(Integer id, String codigoColor, String nombre, ClasificacionColor clasificacionColor,
                                TonalidadColor tonalidad) { }

    /** @param advertencia aviso no bloqueante, p. ej. que la barca no tiene máquina registrada (D-12) */
    public record PartidaModificadaResponse(PartidaResponse partida, String advertencia) { }

    public record ModificarPartidaRequest(
            @NotBlank(message = "Indica el código de color.") String codigoColor,
            @Size(max = 80) String barca,
            @NotNull @Positive(message = "El peso debe ser mayor que cero.") @Digits(integer = 9, fraction = 3)
            BigDecimal pesoTotal) { }

    public record ModificarComponenteRequest(
            @NotNull(message = "Indica el tipo de componente.") TipoComponente tipoComponente,
            boolean tubularAbierto,
            @NotNull @Positive(message = "El peso debe ser mayor que cero.") @Digits(integer = 9, fraction = 3)
            BigDecimal pesoRequerido) { }

    public record ModificarColorRequest(
            @NotBlank(message = "Indica el nombre del color.") @Size(max = 100) String nombre,
            @NotNull(message = "Indica la clasificación de color.") ClasificacionColor clasificacionColor,
            @NotNull(message = "Indica la tonalidad.") TonalidadColor tonalidad) { }
}
