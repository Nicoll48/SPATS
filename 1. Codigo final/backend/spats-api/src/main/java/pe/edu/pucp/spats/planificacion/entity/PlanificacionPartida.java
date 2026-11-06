package pe.edu.pucp.spats.planificacion.entity;

import java.time.LocalDate;

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

import pe.edu.pucp.spats.maquina.entity.MaquinaTintoreria;
import pe.edu.pucp.spats.partida.entity.Partida;
import pe.edu.pucp.spats.planificacion.entity.Enumeraciones.ClasificacionResultado;
import pe.edu.pucp.spats.planificacion.entity.Enumeraciones.EstadoConfirmacionCierre;

/**
 * Resultado de una partida dentro de una versión. ASIGNADA y SOBRANTE tienen máquina y día; BLOQUEADA no
 * (la base lo exige con {@code ck_planpart_bloqueada_sin_maquina}).
 */
@Entity
@Table(name = "planificacion_partida")
public class PlanificacionPartida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_planificacion_partida")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_planificacion", nullable = false)
    private PlanificacionSemanal planificacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_partida", nullable = false)
    private Partida partida;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_maquina")
    private MaquinaTintoreria maquina;

    @Column(name = "dia_abastecimiento")
    private LocalDate diaAbastecimiento;

    @Column(name = "orden_secuencia")
    private Integer ordenSecuencia;

    @Enumerated(EnumType.STRING)
    @Column(name = "clasificacion_resultado", nullable = false, length = 20)
    private ClasificacionResultado clasificacionResultado;

    @Column(name = "requiere_lavado_previo", nullable = false)
    private boolean requiereLavadoPrevio;

    @Column(name = "fecha_disponible")
    private LocalDate fechaDisponible;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_confirmacion_cierre", length = 20)
    private EstadoConfirmacionCierre estadoConfirmacionCierre;

    protected PlanificacionPartida() { }

    public static PlanificacionPartida ubicada(PlanificacionSemanal planificacion, Partida partida,
                                               MaquinaTintoreria maquina, LocalDate dia, int orden,
                                               ClasificacionResultado clasificacion, boolean lavado,
                                               LocalDate fechaDisponible) {
        PlanificacionPartida p = new PlanificacionPartida();
        p.planificacion = planificacion;
        p.partida = partida;
        p.maquina = maquina;
        p.diaAbastecimiento = dia;
        p.ordenSecuencia = orden;
        p.clasificacionResultado = clasificacion;
        p.requiereLavadoPrevio = lavado;
        p.fechaDisponible = fechaDisponible;
        return p;
    }

    public static PlanificacionPartida bloqueada(PlanificacionSemanal planificacion, Partida partida) {
        PlanificacionPartida p = new PlanificacionPartida();
        p.planificacion = planificacion;
        p.partida = partida;
        p.clasificacionResultado = ClasificacionResultado.BLOQUEADA;
        return p;
    }

    public Integer getId() { return id; }
    public PlanificacionSemanal getPlanificacion() { return planificacion; }
    public Partida getPartida() { return partida; }
    public MaquinaTintoreria getMaquina() { return maquina; }
    public LocalDate getDiaAbastecimiento() { return diaAbastecimiento; }
    public Integer getOrdenSecuencia() { return ordenSecuencia; }
    public ClasificacionResultado getClasificacionResultado() { return clasificacionResultado; }
    public boolean isRequiereLavadoPrevio() { return requiereLavadoPrevio; }
    public LocalDate getFechaDisponible() { return fechaDisponible; }
    public EstadoConfirmacionCierre getEstadoConfirmacionCierre() { return estadoConfirmacionCierre; }

    public boolean conformada() {
        return clasificacionResultado != ClasificacionResultado.BLOQUEADA;
    }

    public void confirmarCierre(EstadoConfirmacionCierre estado) {
        this.estadoConfirmacionCierre = estado;
    }
}
