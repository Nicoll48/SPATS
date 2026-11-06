package pe.edu.pucp.spats.planificacion.motor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.pucp.spats.auditoria.entity.TipoOperacion;
import pe.edu.pucp.spats.auditoria.service.BitacoraService;
import pe.edu.pucp.spats.comun.error.ConflictoException;
import pe.edu.pucp.spats.maquina.entity.MaquinaTintoreria;
import pe.edu.pucp.spats.partida.entity.Partida;
import pe.edu.pucp.spats.pedido.entity.Articulo;
import pe.edu.pucp.spats.planificacion.entity.BolsaArticulo;
import pe.edu.pucp.spats.planificacion.entity.Enumeraciones.EstadoPlanificacion;
import pe.edu.pucp.spats.planificacion.entity.Enumeraciones.TipoGeneracion;
import pe.edu.pucp.spats.planificacion.entity.PlanificacionMaquina;
import pe.edu.pucp.spats.planificacion.entity.PlanificacionPartida;
import pe.edu.pucp.spats.planificacion.entity.PlanificacionSemanal;
import pe.edu.pucp.spats.planificacion.repository.BolsaArticuloRepository;
import pe.edu.pucp.spats.planificacion.repository.PlanificacionMaquinaRepository;
import pe.edu.pucp.spats.planificacion.repository.PlanificacionPartidaRepository;
import pe.edu.pucp.spats.planificacion.repository.PlanificacionSemanalRepository;
import pe.edu.pucp.spats.tejeduria.entity.ProyeccionTejeduria;
import pe.edu.pucp.spats.tejeduria.entity.Semana;
import pe.edu.pucp.spats.tejeduria.service.ProyeccionService;
import spats.algoritmo.salida.Indicadores;

/**
 * Último paso de la generación: guarda todo el resultado en una sola transacción (RN-19). Si algo falla,
 * no queda ninguna versión parcial y la vigente sigue como estaba. La versión nueva pasa a vigente y la
 * anterior deja de serlo en la misma transacción (RN-22, D-14).
 */
@Component
public class GuardadoPlanificacion {

    private final PlanificacionSemanalRepository planificaciones;
    private final PlanificacionPartidaRepository partidas;
    private final PlanificacionMaquinaRepository maquinas;
    private final BolsaArticuloRepository bolsas;
    private final ProyeccionService proyecciones;
    private final EntityManager em;
    private final BitacoraService bitacora;
    private final PropuestasPendientes propuestas;
    private final ReglasVigencia reglasVigencia;
    private final Clock reloj;

    public GuardadoPlanificacion(PlanificacionSemanalRepository planificaciones, PlanificacionPartidaRepository partidas,
                                 PlanificacionMaquinaRepository maquinas, BolsaArticuloRepository bolsas,
                                 ProyeccionService proyecciones, EntityManager em, BitacoraService bitacora,
                                 PropuestasPendientes propuestas, ReglasVigencia reglasVigencia, Clock reloj) {
        this.planificaciones = planificaciones;
        this.partidas = partidas;
        this.maquinas = maquinas;
        this.bolsas = bolsas;
        this.proyecciones = proyecciones;
        this.em = em;
        this.bitacora = bitacora;
        this.propuestas = propuestas;
        this.reglasVigencia = reglasVigencia;
        this.reloj = reloj;
    }

    @Transactional
    public PlanificacionSemanal guardar(EntradaPlanificacion entrada, ResultadoTraducido r) {
        exigirDatosSinCambios(entrada);
        propuestas.eliminarVencidas(entrada.idSemana());
        planificaciones.findBySemanaIdAndEsVigenteTrue(entrada.idSemana()).ifPresent(anterior -> {
            anterior.quitarVigencia(EstadoPlanificacion.REEMPLAZADA);
            bitacora.registrarCambio("planificacion_semanal", anterior.getId(), "es_vigente", true, false);
            planificaciones.flush();   // libera uq_vigente_por_semana antes de insertar la nueva vigente
        });

        Indicadores ind = r.indicadores();
        PlanificacionSemanal plan = planificaciones.save(new PlanificacionSemanal(
                em.getReference(Semana.class, entrada.idSemana()),
                em.getReference(ProyeccionTejeduria.class, entrada.idProyeccion()),
                planificaciones.ultimaVersion(entrada.idSemana()) + 1, LocalDateTime.now(reloj), TipoGeneracion.INICIAL,
                decimal(ind.funcionObjetivo()), decimal(ind.desbalanceSemanal()), decimal(ind.desbalanceDiario()),
                ind.lavados(), ind.evaluaciones(), ind.tiempoMs()));

        guardarFilas(plan, entrada, r);
        reglasVigencia.marcarSemanaSiguiente(entrada.lunes());
        bitacora.registrar(TipoOperacion.GENERAR, "planificacion_semanal", plan.getId(),
                "Semana " + entrada.lunes() + ", versión " + plan.getNumeroVersion());
        return plan;
    }

    /** Filas de una versión: partidas (ubicadas y Bloqueadas), máquinas y bolsas. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void guardarFilas(PlanificacionSemanal plan, EntradaPlanificacion entrada, ResultadoTraducido r) {
        partidas.saveAll(r.partidas().stream().map(f -> PlanificacionPartida.ubicada(plan,
                partida(entrada, f.numeroPartida()), maquina(entrada, f.codigoMaquina()), f.diaAbastecimiento(),
                f.ordenSecuencia(), f.clasificacion(), f.requiereLavado(), f.fechaDisponible())).toList());
        partidas.saveAll(r.bloqueadas().stream()
                .map(numero -> PlanificacionPartida.bloqueada(plan, partida(entrada, numero))).toList());
        maquinas.saveAll(r.maquinas().stream().map(m -> new PlanificacionMaquina(plan, maquina(entrada, m.codigoMaquina()),
                m.objetivoAplicado(), m.minimoDiario(), m.maximoDiario(), m.asignadas(), m.sobrantes(), m.diferencia(),
                m.lavados())).toList());
        bolsas.saveAll(r.bolsas().stream().map(b -> new BolsaArticulo(plan, articulo(entrada, b.codigoArticulo()),
                b.fecha(), b.saldoInicial(), b.ingresados(), b.devueltos(), b.consumidos(), b.saldoFinal())).toList());
    }

    /** La versión vigente y la proyección no deben haber cambiado desde la lectura de los datos. */
    void exigirDatosSinCambios(EntradaPlanificacion entrada) {
        Integer vigenteActual = planificaciones.findBySemanaIdAndEsVigenteTrue(entrada.idSemana())
                .map(PlanificacionSemanal::getId).orElse(null);
        Integer proyeccionActual = proyecciones.vigente(entrada.idSemana()).map(ProyeccionTejeduria::getId).orElse(null);
        if (!Objects.equals(vigenteActual, entrada.idVigenteAnterior()) || !Objects.equals(proyeccionActual, entrada.idProyeccion()))
            throw new ConflictoException("DATOS_CAMBIARON",
                    "La planificación o la proyección de la semana cambiaron mientras se generaba. Vuelve a intentarlo.");
    }

    private Partida partida(EntradaPlanificacion e, String numero) {
        return em.getReference(Partida.class, requerido(e.idPartidaPorNumero().get(numero), "partida", numero));
    }

    private MaquinaTintoreria maquina(EntradaPlanificacion e, String codigo) {
        return em.getReference(MaquinaTintoreria.class, requerido(e.idMaquinaPorCodigo().get(codigo), "máquina", codigo));
    }

    private Articulo articulo(EntradaPlanificacion e, String codigo) {
        return em.getReference(Articulo.class, requerido(e.idArticuloPorCodigo().get(codigo), "artículo", codigo));
    }

    private static Integer requerido(Integer id, String que, String codigo) {
        if (id == null) throw new pe.edu.pucp.spats.comun.error.FalloPlanificacionException(
                "El resultado del algoritmo menciona un dato que no se envió.", List.of(que + " " + codigo));
        return id;
    }

    private static BigDecimal decimal(double valor) {
        return BigDecimal.valueOf(valor).setScale(3, RoundingMode.HALF_UP);
    }
}
