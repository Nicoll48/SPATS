package pe.edu.pucp.spats.maquina.entity;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import pe.edu.pucp.spats.comun.dominio.ClasificacionColor;
import pe.edu.pucp.spats.partida.entity.TonalidadColor;

/**
 * Máquina de tintorería (RF15). Recibe partidas de su misma capacidad (el número de la barca) cuya tonalidad
 * acepta (D-51, RN-06). La clasificación de color y la letra de la barca son informativas.
 */
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

    /** D-51: tonalidades que puede teñir; el servicio exige al menos una. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "maquina_tonalidad_aceptada", joinColumns = @JoinColumn(name = "id_maquina"))
    @Enumerated(EnumType.STRING)
    @Column(name = "tonalidad", nullable = false, length = 10)
    private Set<TonalidadColor> tonalidadesAceptadas = new HashSet<>();

    protected MaquinaTintoreria() { }

    public MaquinaTintoreria(String codigo, BigDecimal capacidad, ClasificacionColor clasificacionColor,
                             int objetivoSemanal, String barca, EstadoMaquina estado, Collection<TonalidadColor> tonalidadesAceptadas) {
        this.codigo = codigo;
        modificar(capacidad, clasificacionColor, objetivoSemanal, barca, estado, tonalidadesAceptadas);
    }

    public final void modificar(BigDecimal capacidad, ClasificacionColor clasificacionColor,
                                int objetivoSemanal, String barca, EstadoMaquina estado,
                                Collection<TonalidadColor> tonalidadesAceptadas) {
        this.capacidad = capacidad;
        this.clasificacionColor = clasificacionColor;
        this.objetivoSemanal = objetivoSemanal;
        this.barca = barca;
        this.estado = estado;
        this.tonalidadesAceptadas.clear();
        this.tonalidadesAceptadas.addAll(tonalidadesAceptadas);
    }

    public Integer getId() { return id; }
    public String getCodigo() { return codigo; }
    public BigDecimal getCapacidad() { return capacidad; }
    public ClasificacionColor getClasificacionColor() { return clasificacionColor; }
    public int getObjetivoSemanal() { return objetivoSemanal; }
    public String getBarca() { return barca; }
    public EstadoMaquina getEstado() { return estado; }

    /** Copia ordenada de Blanco a Oscuro. */
    public Set<TonalidadColor> getTonalidadesAceptadas() {
        return tonalidadesAceptadas.isEmpty() ? EnumSet.noneOf(TonalidadColor.class) : EnumSet.copyOf(tonalidadesAceptadas);
    }
}
