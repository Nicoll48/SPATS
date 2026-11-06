package pe.edu.pucp.spats.seguridad.dto;

import jakarta.validation.constraints.NotNull;

import pe.edu.pucp.spats.seguridad.entity.ModoInteraccion;

public record CambiarModoRequest(@NotNull(message = "Indica el modo.") ModoInteraccion modoInteraccion) { }
