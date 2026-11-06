# =============================================================================
# 05_construir_instancias.R
# Instancias de la experimentación: las tres semanas de tintorería que cubren
# los archivos de planeamiento, cada una evaluada con la proyección de tejeduría
# de su semana (sábado a viernes) y con cuatro condiciones operativas.
#
#   S37: tintorería del lunes 7 al domingo 13 de setiembre; la proyección
#        registrada empieza el miércoles 9 (llega a tintorería jueves a sábado).
#   S38: tintorería del lunes 14 al domingo 20; proyección del sábado 12 al
#        viernes 18.
#   S39: tintorería del lunes 21 al domingo 27; proyección del sábado 19 al
#        jueves 24.
#   Cada semana parte de la memoria que deja la planificación de la anterior:
#   tela no utilizada de cada artículo y partidas pendientes. Los pedidos y partidas son los registrados para el mes.
#
#   Condiciones: B = base, I = una máquina indisponible un día,
#                R = retraso de tejeduría de un día en 30 % de las bolsas,
#                IR = ambas.
# =============================================================================

INDISPONIBILIDAD_EXP <- data.frame(maquina = "M300-1", dia = 3L)   # miércoles
RETRASO_EXP <- 0.30

construir_todas_las_instancias <- function(D, dir_salida = "instancias") {
  dir.create(dir_salida, showWarnings = FALSE, recursive = TRUE)
  semanas <- list(S37 = as.Date("2026-09-07"), S38 = as.Date("2026-09-14"),
                  S39 = as.Date("2026-09-21"))
  condiciones <- list(
    B  = list(ind = NULL, ret = 0),
    I  = list(ind = INDISPONIBILIDAD_EXP, ret = 0),
    R  = list(ind = NULL, ret = RETRASO_EXP),
    IR = list(ind = INDISPONIBILIDAD_EXP, ret = RETRASO_EXP))

  inst <- list()
  estado <- estado_inicial(D, semanas$S37)
  for (s in names(semanas)) {
    for (c in names(condiciones)) {
      nom <- paste0(s, "-", c)
      inst[[nom]] <- construir_instancia(D, estado, semanas[[s]], nom,
                                         condiciones[[c]]$ind, condiciones[[c]]$ret,
                                         semilla = 100L + match(s, names(semanas)))
    }
    # memoria para la semana siguiente: estado que deja la planificación base
    base <- inst[[paste0(s, "-B")]]
    estado <- estado_siguiente(D, base, regla_voraz(base)$sol)
  }
  saveRDS(inst, file.path(dir_salida, "instancias.rds"))
  res <- do.call(rbind, lapply(inst, resumen_instancia))
  write.csv(res, file.path(dir_salida, "resumen_instancias.csv"), row.names = FALSE,
            fileEncoding = "UTF-8")
  inst
}
