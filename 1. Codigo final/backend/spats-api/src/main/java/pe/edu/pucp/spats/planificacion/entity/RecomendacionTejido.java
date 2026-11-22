package pe.edu.pucp.spats.planificacion.entity;

import java.math.BigDecimal;
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
import pe.edu.pucp.spats.partida.entity.TipoComponente;
import pe.edu.pucp.spats.pedido.entity.DetallePedido;

/**
 * Recomendación de tejido adicional para la misma semana de una versión de planificación (RF30, HU-13, D-56): un
 * componente de una partida que hay que tejer para llenar un día con espacio libre de una máquina.
 */
@Entity
@Table(name = "recomendacion_tejido")
public class RecomendacionTejido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_recomendacion")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_planificacion", nullable = false)
    private PlanificacionSemanal planificacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_maquina", nullable = false)
    private MaquinaTintoreria maquina;

    @Column(name = "dia_llena", nullable = false)
    private LocalDate diaLlena;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_partida", nullable = false)
    private Partida partida;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_detalle_pedido", nullable = false)
    private DetallePedido detallePedido;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_componente", nullable = false, length = 20)
    private TipoComponente tipoComponente;

    @Column(name = "kg_a_tejer", nullable = false, precision = 12, scale = 3)
    private BigDecimal kgATejer;

    @Column(name = "dia_tejido", nullable = false)
    private LocalDate diaTejido;

    @Column(name = "fecha_entrega")
    private LocalDate fechaEntrega;

    @Column(nullable = false)
    private int orden;

    protected RecomendacionTejido() { }

    public RecomendacionTejido(PlanificacionSemanal planificacion, MaquinaTintoreria maquina, LocalDate diaLlena, Partida partida,
                               DetallePedido detallePedido, TipoComponente tipoComponente, BigDecimal kgATejer,
                               LocalDate diaTejido, LocalDate fechaEntrega, int orden) {
        this.planificacion = planificacion;
        this.maquina = maquina;
        this.diaLlena = diaLlena;
        this.partida = partida;
        this.detallePedido = detallePedido;
        this.tipoComponente = tipoComponente;
        this.kgATejer = kgATejer;
        this.diaTejido = diaTejido;
        this.fechaEntrega = fechaEntrega;
        this.orden = orden;
    }

    public Integer getId() { return id; }
    public PlanificacionSemanal getPlanificacion() { return planificacion; }
    public MaquinaTintoreria getMaquina() { return maquina; }
    public LocalDate getDiaLlena() { return diaLlena; }
    public Partida getPartida() { return partida; }
    public DetallePedido getDetallePedido() { return detallePedido; }
    public TipoComponente getTipoComponente() { return tipoComponente; }
    public BigDecimal getKgATejer() { return kgATejer; }
    public LocalDate getDiaTejido() { return diaTejido; }
    public LocalDate getFechaEntrega() { return fechaEntrega; }
    public int getOrden() { return orden; }
}
