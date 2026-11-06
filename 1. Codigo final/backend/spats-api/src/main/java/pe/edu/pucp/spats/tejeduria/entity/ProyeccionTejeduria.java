package pe.edu.pucp.spats.tejeduria.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Versión de la proyección de tejeduría de una semana. La de mayor número es la vigente (D-06). */
@Entity
@Table(name = "proyeccion_tejeduria")
public class ProyeccionTejeduria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_proyeccion")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_semana", nullable = false)
    private Semana semana;

    @Column(nullable = false)
    private int version;

    @Column(name = "fecha_carga", nullable = false)
    private LocalDateTime fechaCarga;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoProyeccion estado;

    /** Carga que creó el registro; null si no vino de una carga o si esa carga ya se eliminó (D-40). */
    @Column(name = "id_carga")
    private Integer idCarga;

    protected ProyeccionTejeduria() { }

    public ProyeccionTejeduria(Semana semana, int version, LocalDateTime fechaCarga) {
        this.semana = semana;
        this.version = version;
        this.fechaCarga = fechaCarga;
        this.estado = EstadoProyeccion.VIGENTE;
    }

    public Integer getId() { return id; }
    public Integer getIdCarga() { return idCarga; }
    public void asociarCarga(Integer idCarga) { this.idCarga = idCarga; }
    public Semana getSemana() { return semana; }
    public int getVersion() { return version; }
    public LocalDateTime getFechaCarga() { return fechaCarga; }
    public EstadoProyeccion getEstado() { return estado; }

    public void reemplazar() { this.estado = EstadoProyeccion.REEMPLAZADA; }
    public void reactivar() { this.estado = EstadoProyeccion.VIGENTE; }
    public void marcarEliminada() { this.estado = EstadoProyeccion.ELIMINADA; }
}
