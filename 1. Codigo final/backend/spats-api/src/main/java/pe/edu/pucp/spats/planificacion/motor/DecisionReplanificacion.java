package pe.edu.pucp.spats.planificacion.motor;

import spats.algoritmo.salida.Indicadores;

/**
 * Regla de RF55 (HU-18 C4). La vigente y la nueva se evalúan sobre la misma instancia (incidencias aplicadas,
 * desde D) y con la misma función objetivo de la semana completa (D-28).
 */
public final class DecisionReplanificacion {

    public enum Resultado { PROPONER_NUEVA, CONSERVAR_VIGENTE }

    private DecisionReplanificacion() { }

    /**
     * Si la vigente ya no es factible en el escenario nuevo, se propone la nueva. Si es factible, se propone la
     * nueva solo si su F es menor; si no, se conserva la vigente.
     */
    public static Resultado decidir(Indicadores nueva, Indicadores vigente) {
        if (!vigente.factible()) return Resultado.PROPONER_NUEVA;
        return nueva.funcionObjetivo() < vigente.funcionObjetivo() ? Resultado.PROPONER_NUEVA : Resultado.CONSERVAR_VIGENTE;
    }
}
