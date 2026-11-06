package pe.edu.pucp.spats.partida.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import pe.edu.pucp.spats.comun.dominio.ClasificacionColor;

/** Color de teñido. La tonalidad detecta lavados (RN-16); la clasificación decide las máquinas (RN-06). */
@Entity
@Table(name = "color")
public class Color {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_color")
    private Integer id;

    @Column(name = "codigo_color", nullable = false, unique = true, length = 50)
    private String codigoColor;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "clasificacion_color", nullable = false, length = 50)
    private ClasificacionColor clasificacionColor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TonalidadColor tonalidad;

    protected Color() { }

    public Color(String codigoColor, String nombre, ClasificacionColor clasificacionColor, TonalidadColor tonalidad) {
        this.codigoColor = codigoColor;
        this.nombre = nombre;
        this.clasificacionColor = clasificacionColor;
        this.tonalidad = tonalidad;
    }

    public Integer getId() { return id; }
    public String getCodigoColor() { return codigoColor; }
    public String getNombre() { return nombre; }
    public ClasificacionColor getClasificacionColor() { return clasificacionColor; }
    public TonalidadColor getTonalidad() { return tonalidad; }

    public void modificar(String nombre, ClasificacionColor clasificacion, TonalidadColor tonalidad) {
        this.nombre = nombre;
        this.clasificacionColor = clasificacion;
        this.tonalidad = tonalidad;
    }
}
