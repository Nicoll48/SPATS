package pe.edu.pucp.spats.tejeduria.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.pucp.spats.tejeduria.entity.Semana;

public interface SemanaRepository extends JpaRepository<Semana, Integer> {

    Optional<Semana> findByFechaInicio(LocalDate fechaInicio);
}
