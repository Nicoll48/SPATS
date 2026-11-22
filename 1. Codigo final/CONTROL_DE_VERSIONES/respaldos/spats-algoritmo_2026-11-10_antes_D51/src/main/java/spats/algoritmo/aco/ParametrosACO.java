package spats.algoritmo.aco;

/**
 * Parámetros de la colonia de hormigas. Los valores por defecto son los obtenidos en la calibración
 * factorial 2³ del R2.2.
 */
public final class ParametrosACO {

    private int hormigas = 20;
    private double rho = 0.90;                // fracción de feromona que se conserva en cada iteración
    private double feromonaInicial = 0.50;    // tau0
    private double pesoFeromona = 1.0;        // a
    private double pesoHeuristica = 2.0;      // b
    private int evaluaciones = 6000;          // evaluaciones de la función objetivo por ejecución
    private double tiempoMaximoSegundos = 60; // RNF03
    private long semilla = 1L;

    public int getHormigas() { return hormigas; }
    public double getRho() { return rho; }
    public double getFeromonaInicial() { return feromonaInicial; }
    public double getPesoFeromona() { return pesoFeromona; }
    public double getPesoHeuristica() { return pesoHeuristica; }
    public int getEvaluaciones() { return evaluaciones; }
    public double getTiempoMaximoSegundos() { return tiempoMaximoSegundos; }
    public long getSemilla() { return semilla; }

    public ParametrosACO setHormigas(int v) { hormigas = v; return this; }
    public ParametrosACO setRho(double v) { rho = v; return this; }
    public ParametrosACO setFeromonaInicial(double v) { feromonaInicial = v; return this; }
    public ParametrosACO setPesoFeromona(double v) { pesoFeromona = v; return this; }
    public ParametrosACO setPesoHeuristica(double v) { pesoHeuristica = v; return this; }
    public ParametrosACO setEvaluaciones(int v) { evaluaciones = v; return this; }
    public ParametrosACO setTiempoMaximoSegundos(double v) { tiempoMaximoSegundos = v; return this; }
    public ParametrosACO setSemilla(long v) { semilla = v; return this; }

    public void validar() {
        if (hormigas < 1) throw new IllegalArgumentException("La cantidad de hormigas debe ser al menos 1");
        if (!(rho > 0 && rho < 1)) throw new IllegalArgumentException("rho debe estar entre 0 y 1");
        if (!(feromonaInicial > 0)) throw new IllegalArgumentException("La feromona inicial debe ser mayor que 0");
        if (pesoFeromona < 0 || pesoHeuristica < 0) throw new IllegalArgumentException("Los pesos de feromona y heurística no pueden ser negativos");
        if (evaluaciones < 1) throw new IllegalArgumentException("Las evaluaciones deben ser al menos 1");
        if (!(tiempoMaximoSegundos > 0)) throw new IllegalArgumentException("El tiempo máximo debe ser mayor que 0");
    }
}
