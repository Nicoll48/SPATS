package pe.edu.pucp.spats.planificacion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import pe.edu.pucp.spats.incidencia.entity.Incidencia;

/** Incidencia considerada en una re-planificación (RF54, {@code planificacion_incidencia}). */
@Entity
@Table(name = "planificacion_incidencia")
public class PlanificacionIncidencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_planificacion_incidencia")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_planificacion", nullable = false)
    private PlanificacionSemanal planificacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_incidencia", nullable = false)
    private Incidencia incidencia;

    protected PlanificacionIncidencia() { }

    public PlanificacionIncidencia(PlanificacionSemanal planificacion, Incidencia incidencia) {
        this.planificacion = planificacion;
        this.incidencia = incidencia;
    }

    public Integer getId() { return id; }
    public PlanificacionSemanal getPlanificacion() { return planificacion; }
    public Incidencia getIncidencia() { return incidencia; }
}
