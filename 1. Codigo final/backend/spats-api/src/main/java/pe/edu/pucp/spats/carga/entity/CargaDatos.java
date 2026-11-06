package pe.edu.pucp.spats.carga.entity;

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

import pe.edu.pucp.spats.seguridad.entity.Usuario;

/** Registro de cada importación exitosa con su fecha y hora (RF14). */
@Entity
@Table(name = "carga_datos")
public class CargaDatos {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_carga")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_dato", nullable = false, length = 40)
    private TipoDato tipoDato;

    @Column(name = "nombre_archivo", nullable = false, length = 255)
    private String nombreArchivo;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    protected CargaDatos() { }

    public CargaDatos(Usuario usuario, TipoDato tipoDato, String nombreArchivo, LocalDateTime fechaHora) {
        this.usuario = usuario;
        this.tipoDato = tipoDato;
        this.nombreArchivo = nombreArchivo;
        this.fechaHora = fechaHora;
    }

    public Integer getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public TipoDato getTipoDato() { return tipoDato; }
    public String getNombreArchivo() { return nombreArchivo; }
    public LocalDateTime getFechaHora() { return fechaHora; }
}
