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
 */
public record EntradaPlanificacion(Integer idSemana,
                                   LocalDate lunes,
                                   Integer idProyeccion,
                                   Integer idVigenteAnterior,
                                   DatosSemana datos,
                                   Map<String, Integer> idMaquinaPorCodigo,
                                   Map<String, Integer> idPartidaPorNumero,
                                   Map<String, Integer> idArticuloPorCodigo,
                                   List<String> advertencias) { }
