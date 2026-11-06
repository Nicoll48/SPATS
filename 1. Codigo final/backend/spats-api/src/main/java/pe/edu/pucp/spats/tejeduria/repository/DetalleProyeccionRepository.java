package pe.edu.pucp.spats.tejeduria.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import pe.edu.pucp.spats.tejeduria.entity.DetalleProyeccionTejeduria;
import pe.edu.pucp.spats.tejeduria.entity.ProyeccionTejeduria;

public interface DetalleProyeccionRepository extends JpaRepository<DetalleProyeccionTejeduria, Integer> {

    @Query("""
            select d from DetalleProyeccionTejeduria d
            join fetch d.detallePedido dp join fetch dp.pedido join fetch dp.articulo
            where d.proyeccion.id = :idProyeccion
            order by d.fechaProyectada, dp.articulo.codigo, dp.pedido.numeroOp, d.id
            """)
    List<DetalleProyeccionTejeduria> deLaProyeccion(Integer idProyeccion);

    long countByProyeccionId(Integer idProyeccion);

    List<DetalleProyeccionTejeduria> findByProyeccionId(Integer idProyeccion);

    /** Versiones eliminadas que usan estos artículos de pedido. */
    @Query("""
            select distinct d.proyeccion from DetalleProyeccionTejeduria d
            where d.detallePedido.id in :idsDetalle
              and d.proyeccion.estado = pe.edu.pucp.spats.tejeduria.entity.EstadoProyeccion.ELIMINADA
            """)
    List<ProyeccionTejeduria> eliminadasQueUsan(Collection<Integer> idsDetalle);
}
