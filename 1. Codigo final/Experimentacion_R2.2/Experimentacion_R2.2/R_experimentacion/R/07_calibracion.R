# =============================================================================
# 07_calibracion.R
# Calibración de parámetros con un diseño factorial 2^3 por algoritmo, sobre una
# instancia de calibración que NO forma parte de la comparación (semana 38 con
# retraso de tejeduría en 50 % de las bolsas). Cinco corridas por combinación y
# el mismo presupuesto de evaluaciones de la comparación.
# =============================================================================

DISENO_AG <- expand.grid(poblacion = c(30L, 50L), p_mutacion = c(0.10, 0.30),
                         elitismo = c(0.05, 0.10))
DISENO_ACO <- expand.grid(hormigas = c(10L, 20L), rho = c(0.50, 0.90), b = c(1, 2))

instancia_calibracion <- function(D) {
  e <- estado_inicial(D, as.Date("2026-09-07"))
  s37 <- construir_instancia(D, e, as.Date("2026-09-07"), "S37-B")
  e <- estado_siguiente(D, s37, regla_voraz(s37)$sol)
  construir_instancia(D, e, as.Date("2026-09-14"), "CALIB", retraso = 0.5, semilla = 999L)
}

calibrar <- function(I, corridas = 5L, dir_res = "resultados",
                     nucleos = max(1L, parallel::detectCores() - 1L)) {
  tareas <- rbind(
    data.frame(alg = "AG", conf = seq_len(nrow(DISENO_AG)))[rep(seq_len(nrow(DISENO_AG)), each = corridas), ],
    data.frame(alg = "ACO", conf = seq_len(nrow(DISENO_ACO)))[rep(seq_len(nrow(DISENO_ACO)), each = corridas), ])
  tareas$corrida <- ave(tareas$conf, tareas$alg, tareas$conf, FUN = seq_along)
  correr <- function(i) {
    t <- tareas[i, ]
    sem <- 50000L + 100L * t$conf + t$corrida
    if (t$alg == "AG") {
      par <- modifyList(CONFIG$ag, as.list(DISENO_AG[t$conf, ]))
      r <- algoritmo_genetico(I, sem, par = par)
    } else {
      par <- modifyList(CONFIG$aco, as.list(DISENO_ACO[t$conf, ]))
      r <- colonia_hormigas(I, sem, par = par)
    }
    data.frame(t, F = r$F)
  }
  if (nucleos > 1L) {
    cl <- parallel::makeCluster(nucleos); on.exit(parallel::stopCluster(cl))
    parallel::clusterCall(cl, function(arch) for (f in arch) source(f, encoding = "UTF-8"),
                          sort(list.files("R", pattern = "^0[0-4].*\\.R$", full.names = TRUE)))
    parallel::clusterExport(cl, c("tareas", "DISENO_AG", "DISENO_ACO", "I"), envir = environment())
    res <- parallel::parLapplyLB(cl, seq_len(nrow(tareas)), correr)
  } else res <- lapply(seq_len(nrow(tareas)), correr)
  res <- do.call(rbind, res)
  ag <- cbind(DISENO_AG, F_promedio = tapply(res$F[res$alg == "AG"], res$conf[res$alg == "AG"], mean))
  aco <- cbind(DISENO_ACO, F_promedio = tapply(res$F[res$alg == "ACO"], res$conf[res$alg == "ACO"], mean))
  write.csv(ag, file.path(dir_res, "calibracion_AG.csv"), row.names = FALSE)
  write.csv(aco, file.path(dir_res, "calibracion_ACO.csv"), row.names = FALSE)
  list(ag = ag, aco = aco,
       mejor_ag = as.list(DISENO_AG[which.min(ag$F_promedio), ]),
       mejor_aco = as.list(DISENO_ACO[which.min(aco$F_promedio), ]))
}
