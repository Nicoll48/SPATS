package pe.edu.pucp.spats.planificacion.entity;

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

import pe.edu.pucp.spats.pedido.entity.Articulo;

/**
 * Saldo de la bolsa de un artículo en un día de una versión (RN-01, RN-03). El saldo final del domingo de
 * la versión vigente es el stock inicial de la semana siguiente.
 */
@Entity
@Table(name = "bolsa_articulo")
public class BolsaArticulo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_bolsa")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_planificacion", nullable = false)
    private PlanificacionSemanal planificacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_articulo", nullable = false)
    private Articulo articulo;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "kg_saldo_inicial", nullable = false, precision = 12, scale = 3)
    private BigDecimal kgSaldoInicial;

    @Column(name = "kg_ingresados", nullable = false, precision = 12, scale = 3)
    private BigDecimal kgIngresados;

    @Column(name = "kg_consumidos", nullable = false, precision = 12, scale = 3)
    private BigDecimal kgConsumidos;

    /** Tela que vuelve a la bolsa el día D de una re-planificación (D-30); 0 en los demás días. */
    @Column(name = "kg_devueltos", nullable = false, precision = 12, scale = 3)
    private BigDecimal kgDevueltos;

    @Column(name = "kg_saldo_final", nullable = false, precision = 12, scale = 3)
    private BigDecimal kgSaldoFinal;

    protected BolsaArticulo() { }

    public BolsaArticulo(PlanificacionSemanal planificacion, Articulo articulo, LocalDate fecha,
                         BigDecimal kgSaldoInicial, BigDecimal kgIngresados, BigDecimal kgDevueltos,
                         BigDecimal kgConsumidos, BigDecimal kgSaldoFinal) {
        this.planificacion = planificacion;
        this.articulo = articulo;
        this.fecha = fecha;
        this.kgSaldoInicial = kgSaldoInicial;
        this.kgIngresados = kgIngresados;
        this.kgDevueltos = kgDevueltos;
        this.kgConsumidos = kgConsumidos;
        this.kgSaldoFinal = kgSaldoFinal;
    }

    public Integer getId() { return id; }
    public PlanificacionSemanal getPlanificacion() { return planificacion; }
    public Articulo getArticulo() { return articulo; }
    public LocalDate getFecha() { return fecha; }
    public BigDecimal getKgSaldoInicial() { return kgSaldoInicial; }
    public BigDecimal getKgIngresados() { return kgIngresados; }
    public BigDecimal getKgConsumidos() { return kgConsumidos; }
    public BigDecimal getKgDevueltos() { return kgDevueltos; }
    public BigDecimal getKgSaldoFinal() { return kgSaldoFinal; }
}
