package pe.edu.pucp.spats.comun.dto;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/** Página de resultados con forma estable para el frontend (en vez de serializar {@code PageImpl}). */
public record PaginaResponse<T>(List<T> contenido, int pagina, int tamanio, long total) {

    public static <E, T> PaginaResponse<T> de(Page<E> pagina, Function<E, T> conversion) {
        return new PaginaResponse<>(pagina.getContent().stream().map(conversion).toList(),
                pagina.getNumber(), pagina.getSize(), pagina.getTotalElements());
    }
}
