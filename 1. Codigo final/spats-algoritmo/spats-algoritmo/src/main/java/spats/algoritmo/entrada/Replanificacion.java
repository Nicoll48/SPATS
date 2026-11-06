package spats.algoritmo.entrada;

import java.time.LocalDate;
import java.util.List;

/**
 * Datos adicionales para re-planificar la semana desde el día {@code diaInicio} (D) (HU-18, RN-25).
 * Lo anterior a D no se decide: llega como dato.
 *
 * <ul>
 *   <li>{@code fijas}: partidas ya ubicadas que no cambian (máquina, día, clasificación y orden).</li>
 *   <li>{@code conservadas}: partidas cuya tela se separó antes de D; no consumen tela nueva y solo se
 *       elige su máquina (dentro de su barca) y su día, desde D.</li>
 *   <li>{@code disponibilidades}: partidas que todavía no se conforman y que no pueden conformarse antes
 *       de una fecha (problema de complemento).</li>
 * </ul>
 * <p>En re-planificación, {@link DatosSemana#stockInicial()} es el saldo de cada bolsa al iniciar el día D, y
 * la proyección que quedó disponible antes de D ya está dentro de ese saldo.</p>
 */
public record Replanificacion(LocalDate diaInicio,
                              List<AsignacionFija> fijas,
                              List<ConformacionConservada> conservadas,
                              List<DisponibilidadPartida> disponibilidades) {

    public Replanificacion {
        fijas = List.copyOf(fijas);
        conservadas = List.copyOf(conservadas);
        disponibilidades = List.copyOf(disponibilidades);
    }
}
