package pe.edu.pucp.spats.tejeduria.service;

import java.time.LocalDate;

/**
 * Se publica, dentro de la transacción, justo antes de corregir o eliminar un registro de una versión de la
 * proyección. El módulo de planificación lo escucha para exigir la confirmación y dejar sin vigencia las
 * planificaciones que se generaron con esa versión (D-06). Cargar una versión nueva ya no lo publica: no
 * cambia la planificación vigente (D-52). Si el oyente lanza una excepción, el cambio no se aplica.
 *
 * @param idProyeccion versión que cambia
 * @param confirmado   true si el planificador ya confirmó que acepta perder esas planificaciones (RF59)
 */
public record ProyeccionPorCambiarEvent(Integer idSemana, LocalDate lunes, Integer idProyeccion, boolean confirmado) { }
