# =============================================================================
# 10_analisis.R
# Indicadores y pruebas estadísticas (Derrac et al., 2011):
#   - valor de la función objetivo (mejor, promedio, desviación estándar)
#   - RPD = 100 (F - F*) / F*, con F* el mejor valor conocido de la instancia
#   - Mann-Whitney por instancia y A12 de Vargha y Delaney
#   - Wilcoxon de rangos con signo sobre los promedios y las desviaciones
#   - reducción frente a la regla voraz y tiempo de cómputo
# =============================================================================

suppressMessages(library(ggplot2))

a12 <- function(x, y) {          # probabilidad de que x obtenga un F menor que y
  m <- outer(x, y, "-")
  (sum(m < 0) + 0.5 * sum(m == 0)) / length(m)
}

analizar <- function(dir_res = "resultados") {
  corr <- read.csv(file.path(dir_res, "corridas.csv"))
  voraz <- read.csv(file.path(dir_res, "voraz.csv"))
  conv <- read.csv(file.path(dir_res, "convergencia.csv"))
  orden_inst <- unique(corr$instancia)
  corr$instancia <- factor(corr$instancia, orden_inst)

  mejor_conocido <- tapply(corr$F, corr$instancia, min)
  mejor_conocido <- pmin(mejor_conocido, voraz$F[match(names(mejor_conocido), voraz$instancia)])
  corr$RPD <- 100 * (corr$F - mejor_conocido[as.character(corr$instancia)]) /
    mejor_conocido[as.character(corr$instancia)]

  res <- do.call(rbind, lapply(orden_inst, function(n) {
    ag <- corr[corr$instancia == n & corr$algoritmo == "AG", ]
    aco <- corr[corr$instancia == n & corr$algoritmo == "ACO", ]
    p <- if (sd(ag$F) == 0 && sd(aco$F) == 0 && mean(ag$F) == mean(aco$F)) 1 else
      suppressWarnings(wilcox.test(ag$F, aco$F, exact = FALSE)$p.value)
    A <- a12(ag$F, aco$F)
    gana <- if (p < 0.05) ifelse(mean(aco$F) < mean(ag$F), "Gana ACO", "Gana AG") else "Sin diferencia significativa"
    data.frame(instancia = n, voraz = voraz$F[voraz$instancia == n],
               AG_mejor = min(ag$F), AG_prom = mean(ag$F), AG_desv = sd(ag$F),
               ACO_mejor = min(aco$F), ACO_prom = mean(aco$F), ACO_desv = sd(aco$F),
               RPD_AG = mean(ag$RPD), RPD_ACO = mean(aco$RPD),
               red_AG = 100 * (1 - mean(ag$F) / voraz$F[voraz$instancia == n]),
               red_ACO = 100 * (1 - mean(aco$F) / voraz$F[voraz$instancia == n]),
               p_MW = p, A12_AG = A, resultado = gana,
               t_AG = mean(ag$tiempo_s), t_ACO = mean(aco$tiempo_s),
               factibles = mean(c(ag$factible, aco$factible)) * 100)
  }))
  write.csv(res, file.path(dir_res, "resumen_por_instancia.csv"), row.names = FALSE)

  w_F   <- suppressWarnings(wilcox.test(res$ACO_prom, res$AG_prom, paired = TRUE, exact = FALSE))
  w_RPD <- suppressWarnings(wilcox.test(res$RPD_ACO, res$RPD_AG, paired = TRUE, exact = FALSE))
  w_SD  <- suppressWarnings(wilcox.test(res$ACO_desv, res$AG_desv, paired = TRUE, exact = FALSE))
  glob <- data.frame(
    indicador = c("Valor promedio de la función objetivo", "RPD promedio (%)",
                  "Desviación estándar promedio entre corridas", "Tiempo promedio por corrida en R (s)"),
    ACO = c(mean(res$ACO_prom), mean(res$RPD_ACO), mean(res$ACO_desv), mean(res$t_ACO)),
    AG  = c(mean(res$AG_prom), mean(res$RPD_AG), mean(res$AG_desv), mean(res$t_AG)),
    V   = c(w_F$statistic, w_RPD$statistic, w_SD$statistic, NA),
    p   = c(w_F$p.value, w_RPD$p.value, w_SD$p.value, NA))
  glob$mejora_ACO_sobre_AG <- 100 * (glob$AG - glob$ACO) / glob$AG
  write.csv(glob, file.path(dir_res, "indicadores_globales.csv"), row.names = FALSE)

  # ---------------------------------------------------------------- Figuras --
  dir.create(file.path(dir_res, "figuras"), showWarnings = FALSE)
  red <- rbind(data.frame(instancia = res$instancia, algoritmo = "AG", red = res$red_AG),
               data.frame(instancia = res$instancia, algoritmo = "ACO", red = res$red_ACO))
  red$instancia <- factor(red$instancia, orden_inst)
  g1 <- ggplot(red, aes(instancia, red, fill = algoritmo)) +
    geom_col(position = position_dodge(0.8), width = 0.75) +
    scale_fill_manual(values = c(ACO = "#2E6F9E", AG = "#C9772B")) +
    labs(x = "Instancia", y = "Reducción de F frente a la regla voraz (%)", fill = "Algoritmo") +
    theme_minimal(base_size = 11) + theme(legend.position = "top")
  ggsave(file.path(dir_res, "figuras", "figura_reduccion_voraz.png"), g1, width = 7.5, height = 4, dpi = 200, bg = "white")

  g2 <- ggplot(corr, aes(instancia, RPD, fill = algoritmo)) +
    geom_boxplot(outlier.size = 0.8, position = position_dodge(0.8), width = 0.7) +
    scale_fill_manual(values = c(ACO = "#2E6F9E", AG = "#C9772B")) +
    labs(x = "Instancia", y = "RPD (%)", fill = "Algoritmo") +
    theme_minimal(base_size = 11) + theme(legend.position = "top")
  ggsave(file.path(dir_res, "figuras", "figura_rpd.png"), g2, width = 7.5, height = 4, dpi = 200, bg = "white")

  cm <- aggregate(mejor_F ~ instancia + algoritmo + evaluaciones, data = conv, FUN = mean)
  cm <- cm[cm$instancia %in% c("S37-B", "S38-B", "S39-B"), ]
  g3 <- ggplot(cm, aes(evaluaciones, mejor_F, colour = algoritmo)) + geom_line(linewidth = 0.9) +
    facet_wrap(~instancia, scales = "free_y") +
    scale_colour_manual(values = c(ACO = "#2E6F9E", AG = "#C9772B")) +
    labs(x = "Evaluaciones de la función objetivo", y = "Mejor F promedio", colour = "Algoritmo") +
    theme_minimal(base_size = 11) + theme(legend.position = "top")
  ggsave(file.path(dir_res, "figuras", "figura_convergencia.png"), g3, width = 8, height = 3.6, dpi = 200, bg = "white")

  list(por_instancia = res, global = glob, wilcoxon = list(F = w_F, RPD = w_RPD, SD = w_SD))
}
