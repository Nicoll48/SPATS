package pe.edu.pucp.spats.seguridad.entity;

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

/** Cuenta individual de acceso (RF01). La contraseña se guarda cifrada con BCrypt. */
@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_rol", nullable = false)
    private Rol rol;

    @Column(name = "nombre_completo", nullable = false, length = 150)
    private String nombreCompleto;

    @Column(name = "usuario_login", nullable = false, unique = true, length = 80)
    private String usuarioLogin;

    @Column(nullable = false, length = 255)
    private String contrasena;

    @Enumerated(EnumType.STRING)
    @Column(name = "modo_interaccion", nullable = false, length = 20)
    private ModoInteraccion modoInteraccion;

    @Column(name = "fecha_creacion", nullable = false, insertable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoUsuario estado;

    protected Usuario() { }

    public Integer getId() { return id; }
    public Rol getRol() { return rol; }
    public String getNombreCompleto() { return nombreCompleto; }
    public String getUsuarioLogin() { return usuarioLogin; }
    public String getContrasena() { return contrasena; }
    public ModoInteraccion getModoInteraccion() { return modoInteraccion; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public EstadoUsuario getEstado() { return estado; }

    public boolean activo() {
        return estado == EstadoUsuario.ACTIVO;
    }

    public void cambiarModo(ModoInteraccion nuevo) {
        this.modoInteraccion = nuevo;
    }
}
