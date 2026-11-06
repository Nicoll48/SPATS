package pe.edu.pucp.spats.tejeduria.service;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.pucp.spats.comun.fecha.SemanaPlanificacion;
import pe.edu.pucp.spats.tejeduria.entity.Semana;
import pe.edu.pucp.spats.tejeduria.repository.SemanaRepository;

@Service
@Transactional(readOnly = true)
public class SemanaService {

    private final SemanaRepository semanas;

    public SemanaService(SemanaRepository semanas) { this.semanas = semanas; }

    public Optional<Semana> buscar(LocalDate lunes) {
        return semanas.findByFechaInicio(new SemanaPlanificacion(lunes).lunes());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Semana obtenerOCrear(LocalDate lunes) {
        SemanaPlanificacion periodo = new SemanaPlanificacion(lunes);
        return semanas.findByFechaInicio(periodo.lunes()).orElseGet(() -> semanas.save(new Semana(periodo)));
    }
}
