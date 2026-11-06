package pe.edu.pucp.spats.pedido.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Tipo de tela que se teje. Su {@code codigo} es la clave de su bolsa: la tela de un artículo abastece
 * partidas de cualquier pedido (RN-01).
 */
@Entity
@Table(name = "articulo")
public class Articulo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_articulo")
    private Integer id;

    @Column(nullable = false, unique = true, length = 80)
    private String codigo;

    @Column(nullable = false, length = 150)
    private String nombre;

    protected Articulo() { }

    public Articulo(String codigo, String nombre) {
        this.codigo = codigo;
        this.nombre = nombre;
    }

    public Integer getId() { return id; }
    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }

    public void cambiarNombre(String nuevo) { this.nombre = nuevo; }
}
