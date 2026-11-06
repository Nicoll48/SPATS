package pe.edu.pucp.spats.planificacion.motor;

import java.time.LocalDate;
import java.util.List;

import pe.edu.pucp.spats.planificacion.motor.ResultadoTraducido.FilaBolsa;
import spats.algoritmo.entrada.DatosSemana;
import spats.algoritmo.salida.AsignacionPartida;

/**
 * Datos de una re-planificación leídos de la base, listos para el algoritmo.
 *
 * @param entrada            identificadores para guardar (los de la generación más los artículos de la bolsa vigente)
 * @param dia                día D (fecha del servidor)
 * @param datos              entrada del algoritmo con la re-planificación
 * @param alternativaVigente decisiones de la vigente para evaluarla en el mismo escenario (RF55)
 * @param bolsaVigente       filas de {@code bolsa_articulo} de la vigente
 * @param idsIncidencias     incidencias consideradas
 */
public record EntradaReplanificacion(EntradaPlanificacion entrada,
                                     LocalDate dia,
                                     DatosSemana datos,
                                     List<AsignacionPartida> alternativaVigente,
                                     List<FilaBolsa> bolsaVigente,
                                     List<Integer> idsIncidencias,
                                     List<String> advertencias) { }
