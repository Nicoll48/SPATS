package spats.algoritmo.entrada;

/**
 * Tela cruda de un artículo en almacén al iniciar la semana: kg que no alcanzaron
 * para completar una partida en semanas anteriores (memoria entre semanas).
 */
public record StockArticulo(String claveArticulo, String articulo, double kg) { }
