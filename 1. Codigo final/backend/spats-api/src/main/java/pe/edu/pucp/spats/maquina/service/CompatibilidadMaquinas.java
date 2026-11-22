package pe.edu.pucp.spats.maquina.service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import pe.edu.pucp.spats.comun.dominio.Barca;
import pe.edu.pucp.spats.maquina.entity.MaquinaTintoreria;
import pe.edu.pucp.spats.partida.entity.TonalidadColor;

/**
 * Compatibilidad entre partida y máquina (D-51, RN-06): la capacidad de la máquina es igual al número de la barca de
 * la partida y la máquina acepta la tonalidad de la partida. La letra de la barca no se usa. Es la misma regla que
 * aplica {@code spats.algoritmo.nucleo.Compatibilidad}; aquí se usa para avisar al registrar datos.
 */
public final class CompatibilidadMaquinas {

    public static final String MOTIVO_SIN_COMPATIBLE = "Ninguna máquina de su capacidad acepta su tonalidad";

    private CompatibilidadMaquinas() { }

    public static boolean compatible(MaquinaTintoreria m, String barca, TonalidadColor tonalidad) {
        return compatible(m.getCapacidad(), m.getTonalidadesAceptadas(), barca, tonalidad);
    }

    public static boolean compatible(BigDecimal capacidad, Set<TonalidadColor> aceptadas, String barca, TonalidadColor tonalidad) {
        Optional<BigDecimal> cap = Barca.capacidadDe(barca);
        return cap.isPresent() && capacidad != null && cap.get().compareTo(capacidad) == 0
                && tonalidad != null && aceptadas.contains(tonalidad);
    }

    public static List<MaquinaTintoreria> compatibles(Collection<MaquinaTintoreria> maquinas, String barca, TonalidadColor tonalidad) {
        return maquinas.stream().filter(m -> compatible(m, barca, tonalidad)).toList();
    }

    public static boolean hayCompatible(Collection<MaquinaTintoreria> maquinas, String barca, TonalidadColor tonalidad) {
        return maquinas.stream().anyMatch(m -> compatible(m, barca, tonalidad));
    }

    /** Aviso para una partida sin máquina compatible: indica partida, tonalidad y barca. */
    public static String avisoSinCompatible(String partida, TonalidadColor tonalidad, String barca) {
        String capacidad = Barca.capacidadDe(barca).map(c -> c.stripTrailingZeros().toPlainString()).orElse("?");
        return "La partida " + partida + " (tonalidad " + texto(tonalidad) + ", barca " + barca + "): ninguna máquina de "
                + capacidad + " kg acepta la tonalidad " + texto(tonalidad) + ". Se registró, pero al generar quedará "
                + "Bloqueada hasta que alguna máquina de esa capacidad la acepte.";
    }

    /** «Blanco, Claro y Medio»; «Todas» si son las cuatro. */
    public static String textoTonalidades(Collection<TonalidadColor> tonalidades) {
        Set<TonalidadColor> t = tonalidades.isEmpty() ? EnumSet.noneOf(TonalidadColor.class) : EnumSet.copyOf(tonalidades);
        if (t.size() == TonalidadColor.values().length) return "Todas";
        if (t.isEmpty()) return "Ninguna";
        List<String> nombres = t.stream().map(CompatibilidadMaquinas::texto).toList();
        if (nombres.size() == 1) return nombres.getFirst();
        return String.join(", ", nombres.subList(0, nombres.size() - 1)) + " y " + nombres.getLast();
    }

    public static String texto(TonalidadColor t) {
        return t.name().charAt(0) + t.name().substring(1).toLowerCase();
    }

    /** Lista en texto de bitácora: «BLANCO,CLARO». */
    public static String codigos(Collection<TonalidadColor> tonalidades) {
        return tonalidades.isEmpty() ? "" : EnumSet.copyOf(tonalidades).stream().map(Enum::name).collect(Collectors.joining(","));
    }
}
