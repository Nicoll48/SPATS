package pe.edu.pucp.spats.tejeduria.service;

/**
 * Lo que la proyección necesita saber de la planificación de su semana (D-52). Lo implementa el módulo de
 * planificación, que depende de este y no al revés.
 */
public interface PlanificacionesDeLaSemana {

    /** true si la semana tiene una planificación vigente (abierta o cerrada). */
    boolean tieneVigente(Integer idSemana);

    /** Lanza un conflicto si la planificación vigente de la semana ya se cerró: sus versiones quedan en solo lectura. */
    void exigirSemanaAbierta(Integer idSemana);
}
