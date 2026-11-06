package pe.edu.pucp.spats.partida.service;

/**
 * Se publica, dentro de la transacción, justo antes de modificar los datos de una partida o de uno de sus
 * componentes, y solo si algo cambia. El módulo de planificación lo escucha: si la partida está Asignada o
 * Sobrante en una planificación vigente abierta, exige la confirmación y deja esa planificación sin vigencia
 * (D-24, como D-06). Si el oyente lanza una excepción, el cambio no se aplica.
 *
 * @param confirmado true si el planificador ya confirmó que acepta perder la planificación vigente (RF59)
 */
public record PartidaPorCambiarEvent(Integer idPartida, String numeroPartida, boolean confirmado) { }
