package pe.edu.pucp.spats.auditoria.entity;

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

/**
 * Registro de una operación o corrección manual (RF60, HU-21). Guarda quién la hizo, cuándo, sobre qué
 * fila ({@code entidadAfectada} + {@code idEntidad}, referencia polimórfica) y los valores anterior y nuevo.
 */
@Entity
@Table(name = "bitacora_auditoria")
public class BitacoraAuditoria {

    static final int MAX_VALOR = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_bitacora")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @Column(name = "entidad_afectada", nullable = false, length = 80)
    private String entidadAfectada;

    @Column(name = "id_entidad")
    private Integer idEntidad;

    @Column(name = "campo_modificado", length = 100)
    private String campoModificado;

    @Column(name = "valor_anterior", length = MAX_VALOR)
    private String valorAnterior;

    @Column(name = "valor_nuevo", length = MAX_VALOR)
    private String valorNuevo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_operacion", nullable = false, length = 30)
    private TipoOperacion tipoOperacion;

    protected BitacoraAuditoria() { }

    public BitacoraAuditoria(Usuario usuario, LocalDateTime fechaHora, String entidadAfectada, Integer idEntidad,
                             String campoModificado, String valorAnterior, String valorNuevo,
                             TipoOperacion tipoOperacion) {
        this.usuario = usuario;
        this.fechaHora = fechaHora;
        this.entidadAfectada = entidadAfectada;
        this.idEntidad = idEntidad;
        this.campoModificado = campoModificado;
        this.valorAnterior = recortar(valorAnterior);
        this.valorNuevo = recortar(valorNuevo);
        this.tipoOperacion = tipoOperacion;
    }

    private static String recortar(String valor) {
        return valor == null || valor.length() <= MAX_VALOR ? valor : valor.substring(0, MAX_VALOR - 1) + "…";
    }

    public Integer getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public LocalDateTime getFechaHora() { return fechaHora; }
    public String getEntidadAfectada() { return entidadAfectada; }
    public Integer getIdEntidad() { return idEntidad; }
    public String getCampoModificado() { return campoModificado; }
    public String getValorAnterior() { return valorAnterior; }
    public String getValorNuevo() { return valorNuevo; }
    public TipoOperacion getTipoOperacion() { return tipoOperacion; }
}
