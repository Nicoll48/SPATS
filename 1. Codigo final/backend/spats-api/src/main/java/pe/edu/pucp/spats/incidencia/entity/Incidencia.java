package pe.edu.pucp.spats.incidencia.entity;

import java.time.LocalDate;
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

import pe.edu.pucp.spats.maquina.entity.MaquinaTintoreria;
import pe.edu.pucp.spats.partida.entity.Partida;
import pe.edu.pucp.spats.pedido.entity.Pedido;
import pe.edu.pucp.spats.planificacion.entity.PlanificacionSemanal;
import pe.edu.pucp.spats.seguridad.entity.Usuario;

/**
 * Evento registrado por el planificador que afecta la planificación vigente (HU-17). Tiene exactamente
 * una entidad afectada, la que corresponde a su tipo (la base lo exige con CHECK).
 */
@Entity
@Table(name = "incidencia")
public class Incidencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_incidencia")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_planificacion_origen", nullable = false)
    private PlanificacionSemanal planificacionOrigen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_pedido")
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_partida")
    private Partida partida;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_maquina")
    private MaquinaTintoreria maquina;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_incidencia", nullable = false, length = 40)
    private TipoIncidencia tipoIncidencia;

    @Column(length = 255)
    private String descripcion;

    @Column(name = "fecha_hora_ocurrencia", nullable = false)
    private LocalDateTime fechaHoraOcurrencia;

    @Column(name = "fecha_hora_disponibilidad")
    private LocalDateTime fechaHoraDisponibilidad;

    @Column(name = "nueva_fecha_entrega")
    private LocalDate nuevaFechaEntrega;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoIncidencia estado;

    protected Incidencia() { }

    private Incidencia(Usuario usuario, PlanificacionSemanal origen, TipoIncidencia tipo, String descripcion,
                       LocalDateTime ocurrencia) {
        this.usuario = usuario;
        this.planificacionOrigen = origen;
        this.tipoIncidencia = tipo;
        this.descripcion = descripcion;
        this.fechaHoraOcurrencia = ocurrencia;
        this.estado = EstadoIncidencia.PENDIENTE;
    }

    public static Incidencia deMaquina(Usuario u, PlanificacionSemanal origen, MaquinaTintoreria maquina, String descripcion,
                                       LocalDateTime ocurrencia, LocalDateTime disponibilidad) {
        Incidencia i = new Incidencia(u, origen, TipoIncidencia.INCIDENCIA_MAQUINA, descripcion, ocurrencia);
        i.maquina = maquina;
        i.fechaHoraDisponibilidad = disponibilidad;
        return i;
    }

    public static Incidencia deComplemento(Usuario u, PlanificacionSemanal origen, Partida partida, String descripcion,
                                           LocalDateTime ocurrencia, LocalDateTime disponibilidad) {
        Incidencia i = new Incidencia(u, origen, TipoIncidencia.PROBLEMA_COMPLEMENTO, descripcion, ocurrencia);
        i.partida = partida;
        i.fechaHoraDisponibilidad = disponibilidad;
        return i;
    }

    public static Incidencia deCambioPrioridad(Usuario u, PlanificacionSemanal origen, Pedido pedido, String descripcion,
                                               LocalDateTime ocurrencia, LocalDate nuevaFechaEntrega) {
        Incidencia i = new Incidencia(u, origen, TipoIncidencia.CAMBIO_PRIORIDAD, descripcion, ocurrencia);
        i.pedido = pedido;
        i.nuevaFechaEntrega = nuevaFechaEntrega;
        return i;
    }

    public Integer getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public PlanificacionSemanal getPlanificacionOrigen() { return planificacionOrigen; }
    public Pedido getPedido() { return pedido; }
    public Partida getPartida() { return partida; }
    public MaquinaTintoreria getMaquina() { return maquina; }
    public TipoIncidencia getTipoIncidencia() { return tipoIncidencia; }
    public String getDescripcion() { return descripcion; }
    public LocalDateTime getFechaHoraOcurrencia() { return fechaHoraOcurrencia; }
    public LocalDateTime getFechaHoraDisponibilidad() { return fechaHoraDisponibilidad; }
    public LocalDate getNuevaFechaEntrega() { return nuevaFechaEntrega; }
    public EstadoIncidencia getEstado() { return estado; }

    /** La incidencia quedó considerada en una re-planificación confirmada (RF54). */
    public void considerar() { this.estado = EstadoIncidencia.CONSIDERADA; }
}
