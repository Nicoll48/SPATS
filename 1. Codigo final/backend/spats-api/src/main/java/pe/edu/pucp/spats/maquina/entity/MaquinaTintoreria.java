package pe.edu.pucp.spats.maquina.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import pe.edu.pucp.spats.comun.dominio.ClasificacionColor;

/** Máquina de tintorería (RF15). Recibe partidas de su misma barca (RN-06). */
@Entity
@Table(name = "maquina_tintoreria")
public class MaquinaTintoreria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_maquina")
    private Integer id;

    @Column(nullable = false, unique = true, length = 50)
    private String codigo;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal capacidad;

    @Enumerated(EnumType.STRING)
    @Column(name = "clasificacion_color", nullable = false, length = 50)
    private ClasificacionColor clasificacionColor;

    @Column(name = "objetivo_semanal", nullable = false)
    private int objetivoSemanal;

    @Column(length = 80)
    private String barca;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoMaquina estado;

    protected MaquinaTintoreria() { }

    public MaquinaTintoreria(String codigo, BigDecimal capacidad, ClasificacionColor clasificacionColor,
                             int objetivoSemanal, String barca, EstadoMaquina estado) {
        this.codigo = codigo;
        modificar(capacidad, clasificacionColor, objetivoSemanal, barca, estado);
    }

    public final void modificar(BigDecimal capacidad, ClasificacionColor clasificacionColor,
                                int objetivoSemanal, String barca, EstadoMaquina estado) {
        this.capacidad = capacidad;
        this.clasificacionColor = clasificacionColor;
        this.objetivoSemanal = objetivoSemanal;
        this.barca = barca;
        this.estado = estado;
    }

    public Integer getId() { return id; }
    public String getCodigo() { return codigo; }
    public BigDecimal getCapacidad() { return capacidad; }
    public ClasificacionColor getClasificacionColor() { return clasificacionColor; }
    public int getObjetivoSemanal() { return objetivoSemanal; }
    public String getBarca() { return barca; }
    public EstadoMaquina getEstado() { return estado; }
}
