package pe.edu.pucp.spats.partida.entity;

import java.math.BigDecimal;

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

import pe.edu.pucp.spats.pedido.entity.DetallePedido;

/** Tela que una partida necesita de un artículo de su pedido. No guarda tela: la tela está en la bolsa del artículo. */
@Entity
@Table(name = "partida_componente")
public class PartidaComponente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_partida_componente")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_partida", nullable = false)
    private Partida partida;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_detalle_pedido", nullable = false)
    private DetallePedido detallePedido;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_componente", nullable = false, length = 20)
    private TipoComponente tipoComponente;

    @Column(name = "tubular_abierto", nullable = false)
    private boolean tubularAbierto;

    @Column(name = "peso_requerido", nullable = false, precision = 12, scale = 3)
    private BigDecimal pesoRequerido;

    protected PartidaComponente() { }

    public PartidaComponente(Partida partida, DetallePedido detallePedido, TipoComponente tipoComponente,
                             boolean tubularAbierto, BigDecimal pesoRequerido) {
        this.partida = partida;
        this.detallePedido = detallePedido;
        this.tipoComponente = tipoComponente;
        this.tubularAbierto = tubularAbierto;
        this.pesoRequerido = pesoRequerido;
    }

    public Integer getId() { return id; }
    public Partida getPartida() { return partida; }
    public DetallePedido getDetallePedido() { return detallePedido; }
    public TipoComponente getTipoComponente() { return tipoComponente; }
    public boolean isTubularAbierto() { return tubularAbierto; }
    public BigDecimal getPesoRequerido() { return pesoRequerido; }

    public void modificar(TipoComponente tipo, boolean tubular, BigDecimal peso) {
        partida.exigirPendiente();
        this.tipoComponente = tipo;
        this.tubularAbierto = tubular;
        this.pesoRequerido = peso;
    }
}
