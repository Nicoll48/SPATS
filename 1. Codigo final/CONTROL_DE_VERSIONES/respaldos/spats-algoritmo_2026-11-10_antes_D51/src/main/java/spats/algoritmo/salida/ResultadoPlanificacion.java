package spats.algoritmo.salida;

import java.time.LocalDate;
import java.util.List;

/** Planificación semanal que devuelve el algoritmo. */
public record ResultadoPlanificacion(LocalDate[] dias,
                                     List<AsignacionPartida> asignaciones,
                                     List<ResumenMaquina> maquinas,
                                     List<PartidaBloqueada> bloqueadas,
                                     Indicadores indicadores,
                                     EstadoSiguiente estadoSiguiente,
                                     List<String> advertencias) { }
