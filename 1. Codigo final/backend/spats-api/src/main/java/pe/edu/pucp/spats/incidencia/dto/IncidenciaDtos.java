package pe.edu.pucp.spats.incidencia.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import pe.edu.pucp.spats.incidencia.entity.EstadoIncidencia;
import pe.edu.pucp.spats.incidencia.entity.TipoIncidencia;

public final class IncidenciaDtos {

    private IncidenciaDtos() { }

    /**
     * Solicitud de registro. Según el tipo se usa una sola entidad afectada: {@code codigoMaquina},
     * {@code numeroPartida} o {@code numeroOp} (HU-17 C4).
     */
    public record RegistrarIncidenciaRequest(
            @NotNull(message = "Indica el tipo de incidencia.") TipoIncidencia tipo,
            String codigoMaquina,
            String numeroPartida,
            String numeroOp,
            @NotNull(message = "Indica la fecha y hora de ocurrencia.") LocalDateTime fechaHoraOcurrencia,
            LocalDateTime fechaHoraDisponibilidad,
            LocalDate nuevaFechaEntrega,
            @Size(max = 255, message = "La descripción admite hasta 255 caracteres.") String descripcion) { }

    /**
     * @param fechaEntregaActual solo al registrar un CAMBIO_PRIORIDAD: fecha que el pedido conserva hasta que se
     *                           confirme la re-planificación (D-13b)
     */
    public record IncidenciaResponse(Integer id, TipoIncidencia tipo, String entidadAfectada, String referencia,
                                     String descripcion, LocalDateTime fechaHoraOcurrencia,
                                     LocalDateTime fechaHoraDisponibilidad, LocalDate nuevaFechaEntrega,
                                     LocalDate fechaEntregaActual, EstadoIncidencia estado, String registradaPor,
                                     int versionPlanificacionOrigen) { }
}
