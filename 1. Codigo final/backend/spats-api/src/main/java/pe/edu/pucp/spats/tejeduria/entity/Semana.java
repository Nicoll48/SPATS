package pe.edu.pucp.spats.tejeduria.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import pe.edu.pucp.spats.comun.fecha.SemanaPlanificacion;

/** Semana de planificación: de lunes a domingo (la base lo garantiza con restricciones CHECK). */
@Entity
@Table(name = "semana")
public class Semana {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_semana")
    private Integer id;

    @Column(name = "fecha_inicio", nullable = false, unique = true)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    protected Semana() { }

    public Semana(SemanaPlanificacion semana) {
        this.fechaInicio = semana.lunes();
        this.fechaFin = semana.domingo();
    }

    public Integer getId() { return id; }
    public LocalDate getFechaInicio() { return fechaInicio; }
    public LocalDate getFechaFin() { return fechaFin; }

    public SemanaPlanificacion periodo() {
        return new SemanaPlanificacion(fechaInicio);
    }
}
