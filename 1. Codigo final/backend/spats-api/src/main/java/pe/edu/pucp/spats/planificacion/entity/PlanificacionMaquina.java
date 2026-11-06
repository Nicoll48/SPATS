package pe.edu.pucp.spats.planificacion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import pe.edu.pucp.spats.maquina.entity.MaquinaTintoreria;

/** Resultado de una máquina en una versión (objetivo aplicado, reparto diario y diferencia, RF29). */
@Entity
@Table(name = "planificacion_maquina")
public class PlanificacionMaquina {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_planificacion_maquina")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_planificacion", nullable = false)
    private PlanificacionSemanal planificacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_maquina", nullable = false)
    private MaquinaTintoreria maquina;

    @Column(name = "objetivo_aplicado", nullable = false)
    private int objetivoAplicado;

    @Column(name = "minimo_diario", nullable = false)
    private int minimoDiario;

    @Column(name = "maximo_diario", nullable = false)
    private int maximoDiario;

    @Column(name = "partidas_asignadas", nullable = false)
    private int partidasAsignadas;

    @Column(name = "partidas_sobrantes", nullable = false)
    private int partidasSobrantes;

    @Column(name = "diferencia_objetivo", nullable = false)
    private int diferenciaObjetivo;

    @Column(name = "numero_lavados", nullable = false)
    private int numeroLavados;

    protected PlanificacionMaquina() { }

    public PlanificacionMaquina(PlanificacionSemanal planificacion, MaquinaTintoreria maquina, int objetivoAplicado,
                                int minimoDiario, int maximoDiario, int partidasAsignadas, int partidasSobrantes,
                                int diferenciaObjetivo, int numeroLavados) {
        this.planificacion = planificacion;
        this.maquina = maquina;
        this.objetivoAplicado = objetivoAplicado;
        this.minimoDiario = minimoDiario;
        this.maximoDiario = maximoDiario;
        this.partidasAsignadas = partidasAsignadas;
        this.partidasSobrantes = partidasSobrantes;
        this.diferenciaObjetivo = diferenciaObjetivo;
        this.numeroLavados = numeroLavados;
    }

    public Integer getId() { return id; }
    public PlanificacionSemanal getPlanificacion() { return planificacion; }
    public MaquinaTintoreria getMaquina() { return maquina; }
    public int getObjetivoAplicado() { return objetivoAplicado; }
    public int getMinimoDiario() { return minimoDiario; }
    public int getMaximoDiario() { return maximoDiario; }
    public int getPartidasAsignadas() { return partidasAsignadas; }
    public int getPartidasSobrantes() { return partidasSobrantes; }
    public int getDiferenciaObjetivo() { return diferenciaObjetivo; }
    public int getNumeroLavados() { return numeroLavados; }
}
