package pe.edu.pucp.spats.partida.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import pe.edu.pucp.spats.comun.error.ReglaNegocioException;
import pe.edu.pucp.spats.pedido.entity.Pedido;

/** Unidad de trabajo que se tiñe de una sola vez en una máquina. */
@Entity
@Table(name = "partida")
public class Partida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_partida")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_pedido", nullable = false)
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_color", nullable = false)
    private Color color;

    @Column(name = "numero_partida", nullable = false, unique = true, length = 50)
    private String numeroPartida;

    @Column(length = 80)
    private String barca;

    @Column(name = "peso_total", nullable = false, precision = 12, scale = 3)
    private BigDecimal pesoTotal;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_partida", nullable = false, length = 20)
    private EstadoPartida estadoPartida;

    @OneToMany(mappedBy = "partida", cascade = CascadeType.PERSIST)
    private List<PartidaComponente> componentes = new ArrayList<>();

    /** Carga que creó el registro; null si no vino de una carga o si esa carga ya se eliminó (D-40). */
    @Column(name = "id_carga")
    private Integer idCarga;

    protected Partida() { }

    /** Toda partida nueva entra como PENDIENTE (sección 6.1). */
    public Partida(Pedido pedido, Color color, String numeroPartida, String barca, BigDecimal pesoTotal,
                   LocalDateTime fechaRegistro) {
        this.pedido = pedido;
        this.color = color;
        this.numeroPartida = numeroPartida;
        this.barca = barca;
        this.pesoTotal = pesoTotal;
        this.fechaRegistro = fechaRegistro;
        this.estadoPartida = EstadoPartida.PENDIENTE;
    }

    public Integer getId() { return id; }
    public void asociarCarga(Integer idCarga) { this.idCarga = idCarga; }
    public Pedido getPedido() { return pedido; }
    public Color getColor() { return color; }
    public String getNumeroPartida() { return numeroPartida; }
    public String getBarca() { return barca; }
    public BigDecimal getPesoTotal() { return pesoTotal; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public EstadoPartida getEstadoPartida() { return estadoPartida; }
    public List<PartidaComponente> getComponentes() { return componentes; }

    public void agregarComponente(PartidaComponente componente) { componentes.add(componente); }

    public void modificar(Color nuevoColor, String nuevaBarca, BigDecimal nuevoPeso) {
        exigirPendiente();
        this.color = nuevoColor;
        this.barca = nuevaBarca;
        this.pesoTotal = nuevoPeso;
    }

    /** Resultado del cierre de semana (RF43, RF44). */
    public void confirmarCierre(boolean completada) {
        this.estadoPartida = completada ? EstadoPartida.COMPLETADA : EstadoPartida.ARRASTRADA;
    }

    /** Una ARRASTRADA solo pasa a COMPLETADA y nunca vuelve a PENDIENTE (RF49). */
    public void completarArrastrada() {
        if (estadoPartida != EstadoPartida.ARRASTRADA)
            throw new ReglaNegocioException("PARTIDA_NO_ARRASTRADA",
                    "La partida " + numeroPartida + " no está arrastrada.");
        this.estadoPartida = EstadoPartida.COMPLETADA;
    }

    public void exigirPendiente() {
        if (estadoPartida != EstadoPartida.PENDIENTE)
            throw new ReglaNegocioException("PARTIDA_NO_PENDIENTE",
                    "La partida " + numeroPartida + " ya no está pendiente; sus datos no se pueden modificar.");
    }
}
