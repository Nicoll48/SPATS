package pe.edu.pucp.spats.maquina.entity;

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

/** Intervalo en que una máquina no recibe partidas (RF16, RN-11). */
@Entity
@Table(name = "indisponibilidad_maquina")
public class IndisponibilidadMaquina {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_indisponibilidad")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_maquina", nullable = false)
    private MaquinaTintoreria maquina;

    @Column(name = "fecha_hora_inicio", nullable = false)
    private LocalDateTime fechaHoraInicio;

    @Column(name = "fecha_hora_fin", nullable = false)
    private LocalDateTime fechaHoraFin;

    @Column(nullable = false, length = 255)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_indisponibilidad", nullable = false, length = 20)
    private TipoIndisponibilidad tipo;

    protected IndisponibilidadMaquina() { }

    public IndisponibilidadMaquina(MaquinaTintoreria maquina, LocalDateTime fechaHoraInicio,
                                   LocalDateTime fechaHoraFin, TipoIndisponibilidad tipo, String motivo) {
        this.maquina = maquina;
        this.fechaHoraInicio = fechaHoraInicio;
        this.fechaHoraFin = fechaHoraFin;
        this.tipo = tipo;
        this.motivo = motivo;
    }

    public Integer getId() { return id; }
    public MaquinaTintoreria getMaquina() { return maquina; }
    public LocalDateTime getFechaHoraInicio() { return fechaHoraInicio; }
    public LocalDateTime getFechaHoraFin() { return fechaHoraFin; }
    public String getMotivo() { return motivo; }
    public TipoIndisponibilidad getTipo() { return tipo; }

    public boolean vigenteEn(LocalDateTime instante) {
        return !instante.isBefore(fechaHoraInicio) && instante.isBefore(fechaHoraFin);
    }
}
