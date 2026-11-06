package pe.edu.pucp.spats.planificacion.entity;

import java.math.BigDecimal;
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

import pe.edu.pucp.spats.planificacion.entity.Enumeraciones.EstadoPlanificacion;
import pe.edu.pucp.spats.planificacion.entity.Enumeraciones.TipoGeneracion;
import pe.edu.pucp.spats.tejeduria.entity.ProyeccionTejeduria;
import pe.edu.pucp.spats.tejeduria.entity.Semana;

/**
 * Versión de la planificación de una semana con los indicadores que devolvió el algoritmo. Solo una
 * versión por semana es vigente (RF58; la base lo garantiza con {@code vigente_semana}, columna generada
 * que no se mapea).
 */
@Entity
@Table(name = "planificacion_semanal")
public class PlanificacionSemanal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_planificacion")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_semana", nullable = false)
    private Semana semana;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_proyeccion", nullable = false)
    private ProyeccionTejeduria proyeccion;

    @Column(name = "numero_version", nullable = false)
    private int numeroVersion;

    @Column(name = "fecha_generacion", nullable = false)
    private LocalDateTime fechaGeneracion;

    @Column(name = "es_vigente", nullable = false)
    private boolean esVigente;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_generacion", nullable = false, length = 20)
    private TipoGeneracion tipoGeneracion;

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoPlanificacion estado;

    /** D-19: cambió la vigente de la semana anterior después de generar esta versión. */
    @Column(name = "stock_desactualizado", nullable = false)
    private boolean stockDesactualizado;

    @Column(name = "valor_funcion_objetivo", precision = 12, scale = 3)
    private BigDecimal valorFuncionObjetivo;

    @Column(name = "desbalance_semanal", precision = 12, scale = 3)
    private BigDecimal desbalanceSemanal;

    @Column(name = "desbalance_diario", precision = 12, scale = 3)
    private BigDecimal desbalanceDiario;

    @Column(name = "numero_lavados")
    private Integer numeroLavados;

    @Column
    private Integer evaluaciones;

    @Column(name = "tiempo_ejecucion_ms")
    private Long tiempoEjecucionMs;

    protected PlanificacionSemanal() { }

    /** Una versión recién generada pasa a vigente y queda abierta (D-14). */
    public PlanificacionSemanal(Semana semana, ProyeccionTejeduria proyeccion, int numeroVersion,
                                LocalDateTime fechaGeneracion, TipoGeneracion tipoGeneracion,
                                BigDecimal valorFuncionObjetivo, BigDecimal desbalanceSemanal,
                                BigDecimal desbalanceDiario, int numeroLavados, int evaluaciones,
                                long tiempoEjecucionMs) {
        this.semana = semana;
        this.proyeccion = proyeccion;
        this.numeroVersion = numeroVersion;
        this.fechaGeneracion = fechaGeneracion;
        this.tipoGeneracion = tipoGeneracion;
        this.esVigente = true;
        this.estado = EstadoPlanificacion.ABIERTA;
        this.valorFuncionObjetivo = valorFuncionObjetivo;
        this.desbalanceSemanal = desbalanceSemanal;
        this.desbalanceDiario = desbalanceDiario;
        this.numeroLavados = numeroLavados;
        this.evaluaciones = evaluaciones;
        this.tiempoEjecucionMs = tiempoEjecucionMs;
    }

    /** Re-planificación propuesta (HU-18): no es vigente hasta que el planificador la confirma (D-13b). */
    public static PlanificacionSemanal propuesta(Semana semana, ProyeccionTejeduria proyeccion, int numeroVersion,
                                                 LocalDateTime fechaGeneracion, BigDecimal valorFuncionObjetivo,
                                                 BigDecimal desbalanceSemanal, BigDecimal desbalanceDiario,
                                                 int numeroLavados, int evaluaciones, long tiempoEjecucionMs) {
        PlanificacionSemanal p = new PlanificacionSemanal(semana, proyeccion, numeroVersion, fechaGeneracion,
                TipoGeneracion.REPLANIFICACION, valorFuncionObjetivo, desbalanceSemanal, desbalanceDiario, numeroLavados,
                evaluaciones, tiempoEjecucionMs);
        p.esVigente = false;
        p.estado = EstadoPlanificacion.PROPUESTA;
        return p;
    }

    public Integer getId() { return id; }
    public Semana getSemana() { return semana; }
    public ProyeccionTejeduria getProyeccion() { return proyeccion; }
    public int getNumeroVersion() { return numeroVersion; }
    public LocalDateTime getFechaGeneracion() { return fechaGeneracion; }
    public boolean isEsVigente() { return esVigente; }
    public TipoGeneracion getTipoGeneracion() { return tipoGeneracion; }
    public LocalDateTime getFechaCierre() { return fechaCierre; }
    public EstadoPlanificacion getEstado() { return estado; }
    public boolean isStockDesactualizado() { return stockDesactualizado; }
    public BigDecimal getValorFuncionObjetivo() { return valorFuncionObjetivo; }
    public BigDecimal getDesbalanceSemanal() { return desbalanceSemanal; }
    public BigDecimal getDesbalanceDiario() { return desbalanceDiario; }
    public Integer getNumeroLavados() { return numeroLavados; }
    public Integer getEvaluaciones() { return evaluaciones; }
    public Long getTiempoEjecucionMs() { return tiempoEjecucionMs; }

    public boolean abierta() { return estado == EstadoPlanificacion.ABIERTA; }

    public boolean propuesta() { return estado == EstadoPlanificacion.PROPUESTA; }

    /** El planificador confirma la re-planificación: pasa a vigente y abierta. */
    public void confirmar() {
        this.esVigente = true;
        this.estado = EstadoPlanificacion.ABIERTA;
    }

    public void marcarStockDesactualizado() { this.stockDesactualizado = true; }

    /** Deja de ser vigente; la versión se conserva en el historial (RF56). */
    public void quitarVigencia(EstadoPlanificacion motivo) {
        this.esVigente = false;
        this.estado = motivo;
    }

    public void cerrar(LocalDateTime fecha) {
        this.estado = EstadoPlanificacion.CERRADA;
        this.fechaCierre = fecha;
    }
}
