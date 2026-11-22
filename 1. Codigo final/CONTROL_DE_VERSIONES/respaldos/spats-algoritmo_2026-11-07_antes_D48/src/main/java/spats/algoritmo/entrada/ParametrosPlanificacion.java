package spats.algoritmo.entrada;

import java.time.DayOfWeek;
import java.time.LocalDate;

/** Parámetros operativos de la semana y pesos de la función objetivo. */
public final class ParametrosPlanificacion {

    private LocalDate fechaInicioSemana;
    private int diasSemana = 7;
    private int reduccionObjetivoPorDiaIndisponible = 2;
    private double pesoDesbalanceSemanal = 1.0;   // alfa
    private double pesoDesbalanceDiario = 1.0;    // beta
    private double pesoLavados = 0.25;            // gamma

    public ParametrosPlanificacion(LocalDate fechaInicioSemana) { this.fechaInicioSemana = fechaInicioSemana; }

    public LocalDate getFechaInicioSemana() { return fechaInicioSemana; }
    public int getDiasSemana() { return diasSemana; }
    public int getReduccionObjetivoPorDiaIndisponible() { return reduccionObjetivoPorDiaIndisponible; }
    public double getPesoDesbalanceSemanal() { return pesoDesbalanceSemanal; }
    public double getPesoDesbalanceDiario() { return pesoDesbalanceDiario; }
    public double getPesoLavados() { return pesoLavados; }

    public ParametrosPlanificacion setFechaInicioSemana(LocalDate v) { fechaInicioSemana = v; return this; }
    public ParametrosPlanificacion setDiasSemana(int v) { diasSemana = v; return this; }
    public ParametrosPlanificacion setReduccionObjetivoPorDiaIndisponible(int v) { reduccionObjetivoPorDiaIndisponible = v; return this; }
    public ParametrosPlanificacion setPesoDesbalanceSemanal(double v) { pesoDesbalanceSemanal = v; return this; }
    public ParametrosPlanificacion setPesoDesbalanceDiario(double v) { pesoDesbalanceDiario = v; return this; }
    public ParametrosPlanificacion setPesoLavados(double v) { pesoLavados = v; return this; }

    /** Verifica que los parámetros tengan valores admisibles. */
    public void validar() {
        if (fechaInicioSemana == null) throw new IllegalArgumentException("Falta la fecha de inicio de la semana");
        if (fechaInicioSemana.getDayOfWeek() != DayOfWeek.MONDAY)
            throw new IllegalArgumentException("La semana de tintorería debe iniciar en lunes: " + fechaInicioSemana);
        if (diasSemana < 1) throw new IllegalArgumentException("Los días de la semana deben ser al menos 1");
        if (reduccionObjetivoPorDiaIndisponible < 0)
            throw new IllegalArgumentException("La reducción del objetivo no puede ser negativa");
        if (pesoDesbalanceSemanal < 0 || pesoDesbalanceDiario < 0 || pesoLavados < 0)
            throw new IllegalArgumentException("Los pesos de la función objetivo no pueden ser negativos");
    }
}
