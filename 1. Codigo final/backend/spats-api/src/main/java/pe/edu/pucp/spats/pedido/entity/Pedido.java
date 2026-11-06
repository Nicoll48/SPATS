package pe.edu.pucp.spats.pedido.entity;

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

/** Pedido de un cliente. Su fecha de entrega define la prioridad de sus partidas (RN-04). */
@Entity
@Table(name = "pedido")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pedido")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;

    @Column(name = "numero_op", nullable = false, unique = true, length = 50)
    private String numeroOp;

    @Column(name = "fecha_entrega", nullable = false)
    private LocalDate fechaEntrega;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoPedido estado;

    /** Carga que creó el registro; null si no vino de una carga o si esa carga ya se eliminó (D-40). */
    @Column(name = "id_carga")
    private Integer idCarga;

    protected Pedido() { }

    public Pedido(Cliente cliente, String numeroOp, LocalDate fechaEntrega) {
        this.cliente = cliente;
        this.numeroOp = numeroOp;
        this.fechaEntrega = fechaEntrega;
        this.estado = EstadoPedido.PENDIENTE;
    }

    public Integer getId() { return id; }
    public void asociarCarga(Integer idCarga) { this.idCarga = idCarga; }
    public Cliente getCliente() { return cliente; }
    public String getNumeroOp() { return numeroOp; }
    public LocalDate getFechaEntrega() { return fechaEntrega; }
    public EstadoPedido getEstado() { return estado; }

    public void cambiarFechaEntrega(LocalDate nueva) { this.fechaEntrega = nueva; }
    public void cambiarCliente(Cliente nuevo) { this.cliente = nuevo; }
    public void completar() { this.estado = EstadoPedido.COMPLETADO; }
}
