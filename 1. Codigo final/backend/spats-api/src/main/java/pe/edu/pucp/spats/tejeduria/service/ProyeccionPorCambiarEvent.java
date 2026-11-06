package pe.edu.pucp.spats.tejeduria.service;

import java.time.LocalDate;

/**
 * Se publica, dentro de la transacción, justo antes de cambiar la proyección vigente de una semana (carga
 * de una versión nueva, corrección manual o restauración). El módulo de planificación lo escucha para
 * exigir la confirmación y dejar sin vigencia la planificación que se generó con la proyección anterior
 * (D-06). Si el oyente lanza una excepción, el cambio no se aplica.
 *
 * @param confirmado true si el planificador ya confirmó que acepta perder la planificación vigente (RF59)
 */
public record ProyeccionPorCambiarEvent(Integer idSemana, LocalDate lunes, boolean confirmado) { }
