package pe.edu.pucp.spats.tejeduria.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import pe.edu.pucp.spats.pedido.entity.DetallePedido;

/** Kilos de un pedido-artículo que tejeduría tejerá en una fecha. */
@Entity
@Table(name = "detalle_proyeccion_tejeduria")
public class DetalleProyeccionTejeduria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle_proyeccion")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_proyeccion", nullable = false)
    private ProyeccionTejeduria proyeccion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_detalle_pedido", nullable = false)
    private DetallePedido detallePedido;

    @Column(name = "fecha_proyectada", nullable = false)
    private LocalDate fechaProyectada;

    @Column(name = "kg_proyectados", nullable = false, precision = 12, scale = 3)
    private BigDecimal kgProyectados;

    protected DetalleProyeccionTejeduria() { }

    public DetalleProyeccionTejeduria(ProyeccionTejeduria proyeccion, DetallePedido detallePedido,
                                      LocalDate fechaProyectada, BigDecimal kgProyectados) {
        this.proyeccion = proyeccion;
        this.detallePedido = detallePedido;
        this.fechaProyectada = fechaProyectada;
        this.kgProyectados = kgProyectados;
    }

    public Integer getId() { return id; }
    public ProyeccionTejeduria getProyeccion() { return proyeccion; }
    public DetallePedido getDetallePedido() { return detallePedido; }
    public LocalDate getFechaProyectada() { return fechaProyectada; }
    public BigDecimal getKgProyectados() { return kgProyectados; }

    public void modificar(LocalDate fecha, BigDecimal kg) {
        this.fechaProyectada = fecha;
        this.kgProyectados = kg;
    }
}
