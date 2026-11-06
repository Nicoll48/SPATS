package pe.edu.pucp.spats.pedido.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Artículo dentro de un pedido (pedido-artículo). La proyección de tejeduría y los componentes lo referencian. */
@Entity
@Table(name = "detalle_pedido")
public class DetallePedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle_pedido")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_pedido", nullable = false)
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_articulo", nullable = false)
    private Articulo articulo;

    @Column(name = "peso_total_requerido", nullable = false, precision = 10, scale = 2)
    private BigDecimal pesoTotalRequerido;

    /** Carga que creó el registro; null si no vino de una carga o si esa carga ya se eliminó (D-40). */
    @Column(name = "id_carga")
    private Integer idCarga;

    protected DetallePedido() { }

    public DetallePedido(Pedido pedido, Articulo articulo, BigDecimal pesoTotalRequerido) {
        this.pedido = pedido;
        this.articulo = articulo;
        this.pesoTotalRequerido = pesoTotalRequerido;
    }

    public Integer getId() { return id; }
    public void asociarCarga(Integer idCarga) { this.idCarga = idCarga; }
    public Pedido getPedido() { return pedido; }
    public Articulo getArticulo() { return articulo; }
    public BigDecimal getPesoTotalRequerido() { return pesoTotalRequerido; }

    public void cambiarPesoTotalRequerido(BigDecimal nuevo) { this.pesoTotalRequerido = nuevo; }
}
