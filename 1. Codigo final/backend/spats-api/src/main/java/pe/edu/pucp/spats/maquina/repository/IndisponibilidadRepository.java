package pe.edu.pucp.spats.maquina.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import pe.edu.pucp.spats.maquina.entity.IndisponibilidadMaquina;

public interface IndisponibilidadRepository extends JpaRepository<IndisponibilidadMaquina, Integer> {

    List<IndisponibilidadMaquina> findByMaquinaIdOrderByFechaHoraInicioDesc(Integer idMaquina);

    @Query("select i from IndisponibilidadMaquina i join fetch i.maquina order by i.maquina.codigo, i.fechaHoraInicio desc")
    List<IndisponibilidadMaquina> listarCompletas();

    /** Intervalos que se cruzan con [desde, hasta). */
    @Query("""
            select i from IndisponibilidadMaquina i join fetch i.maquina
            where i.fechaHoraInicio < :hasta and i.fechaHoraFin > :desde
            order by i.maquina.codigo, i.fechaHoraInicio
            """)
    List<IndisponibilidadMaquina> queSeCruzanCon(LocalDateTime desde, LocalDateTime hasta);
}
