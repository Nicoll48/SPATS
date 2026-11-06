package pe.edu.pucp.spats.maquina.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import pe.edu.pucp.spats.comun.dominio.ClasificacionColor;
import pe.edu.pucp.spats.maquina.entity.EstadoMaquina;
import pe.edu.pucp.spats.maquina.entity.TipoIndisponibilidad;

/** Contrato JSON del módulo de máquinas. */
public final class MaquinaDtos {

    private MaquinaDtos() { }

    /**
     * @param estado       estado registrado de la máquina
     * @param estadoActual estado en este momento: si hay un intervalo de indisponibilidad vigente, el que
     *                     corresponde a su tipo (RF17); si no, el registrado
     */
    public record MaquinaResponse(Integer id, String codigo, BigDecimal capacidad,
                                  ClasificacionColor clasificacionColor, int objetivoSemanal, String barca, EstadoMaquina estado,
                                  EstadoMaquina estadoActual) { }

    /** La barca no se recibe: sale de la capacidad y la clasificación de color (D-41). */
    public record GuardarMaquinaRequest(
            @Size(max = 50) String codigo,
            @NotNull(message = "Indica la capacidad.") @Positive(message = "La capacidad debe ser mayor que cero.")
            @Digits(integer = 9, fraction = 3) BigDecimal capacidad,
            @NotNull(message = "Indica la clasificación de color.") ClasificacionColor clasificacionColor,
            @Positive(message = "El objetivo semanal debe ser mayor que cero.") int objetivoSemanal,
            @NotNull(message = "Indica el estado.") EstadoMaquina estado) { }

    public record IndisponibilidadResponse(Integer id, Integer idMaquina, String codigoMaquina,
                                           LocalDateTime fechaHoraInicio, LocalDateTime fechaHoraFin,
                                           TipoIndisponibilidad tipo, String motivo,
                                           EstadoMaquina estadoResultante) { }

    public record RegistrarIndisponibilidadRequest(
            @NotNull(message = "Indica el inicio.") LocalDateTime fechaHoraInicio,
            @NotNull(message = "Indica el fin.") LocalDateTime fechaHoraFin,
            @NotNull(message = "Indica el tipo de motivo.") TipoIndisponibilidad tipo,
            @NotBlank(message = "Indica el motivo.") @Size(max = 255) String motivo) { }
}
