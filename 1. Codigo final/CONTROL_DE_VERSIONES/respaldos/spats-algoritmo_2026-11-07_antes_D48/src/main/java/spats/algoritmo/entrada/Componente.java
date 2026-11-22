package spats.algoritmo.entrada;

/**
 * Componente de una partida: tela cruda de un artículo que la partida requiere.
 * Una partida puede tener tela principal y complemento de distintos artículos.
 *
 * @param partida       partida a la que pertenece
 * @param codArticulo   código del artículo
 * @param articulo      descripción del artículo
 * @param claveArticulo clave normalizada con la que se acumula la tela del artículo
 * @param rol           Principal o Complemento
 * @param kgRequeridos  kg que la partida necesita de este artículo
 */
public record Componente(String partida, String codArticulo, String articulo, String claveArticulo,
                         String rol, double kgRequeridos) { }
