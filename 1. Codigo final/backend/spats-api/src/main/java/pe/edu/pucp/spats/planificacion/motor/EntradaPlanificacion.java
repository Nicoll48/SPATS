package pe.edu.pucp.spats.planificacion.motor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import spats.algoritmo.entrada.DatosSemana;

/**
 * Datos de una semana ya leídos de la base, listos para el algoritmo, y los identificadores necesarios
 * para guardar su resultado.
 *
 * @param idVigenteAnterior versión vigente al momento de leer (o null); al guardar se verifica que no cambió
 * @param idProyeccion      versión de la proyección usada; al guardar se verifica que sigue siendo la vigente
 * @param idsIncidencias    incidencias PENDIENTE de una semana que aún no empieza aplicadas al generarla; al guardarla
 *                          como vigente pasan a CONSIDERADA
 */
public record EntradaPlanificacion(Integer idSemana,
                                   LocalDate lunes,
                                   Integer idProyeccion,
                                   Integer idVigenteAnterior,
                                   DatosSemana datos,
                                   Map<String, Integer> idMaquinaPorCodigo,
                                   Map<String, Integer> idPartidaPorNumero,
                                   Map<String, Integer> idArticuloPorCodigo,
                                   List<String> advertencias,
                                   List<Integer> idsIncidencias) {

    public EntradaPlanificacion {
        idsIncidencias = idsIncidencias == null ? List.of() : List.copyOf(idsIncidencias);
    }

    public EntradaPlanificacion(Integer idSemana, LocalDate lunes, Integer idProyeccion, Integer idVigenteAnterior,
                                DatosSemana datos, Map<String, Integer> idMaquinaPorCodigo,
                                Map<String, Integer> idPartidaPorNumero, Map<String, Integer> idArticuloPorCodigo,
                                List<String> advertencias) {
        this(idSemana, lunes, idProyeccion, idVigenteAnterior, datos, idMaquinaPorCodigo, idPartidaPorNumero,
                idArticuloPorCodigo, advertencias, List.of());
    }
}
