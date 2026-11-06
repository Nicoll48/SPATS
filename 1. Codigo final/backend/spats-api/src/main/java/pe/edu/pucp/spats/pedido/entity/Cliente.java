package pe.edu.pucp.spats.pedido.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "cliente")
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cliente")
    private Integer id;

    @Column(nullable = false, length = 150)
    private String nombre;

    protected Cliente() { }

    public Cliente(String nombre) { this.nombre = nombre; }

    public Integer getId() { return id; }
    public String getNombre() { return nombre; }
}
