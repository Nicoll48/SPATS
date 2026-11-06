# =============================================================================
# 06_experimento.R
# Ejecuta la comparación: regla voraz (una vez, es determinista) y 20 corridas
# del algoritmo genético y de la colonia de hormigas por instancia, con el
# mismo presupuesto de evaluaciones. Cada corrida se guarda apenas termina,
# así que si el proceso se interrumpe se retoma sin perder lo avanzado.
# =============================================================================

semilla_corrida <- function(i_inst, corrida) 1000L * i_inst + corrida

ejecutar_corrida <- function(tarea, inst, dir_corridas) {
  archivo <- file.path(dir_corridas, sprintf("%s_%s_%02d.rds", tarea$instancia,
                                             tarea$algoritmo, tarea$corrida))
  if (file.exists(archivo)) return(readRDS(archivo))
  I <- inst[[tarea$instancia]]
  res <- if (tarea$algoritmo == "AG") algoritmo_genetico(I, tarea$semilla)
         else colonia_hormigas(I, tarea$semilla)
  errores <- validar(I, res$sol, res$ev)
  fila <- data.frame(instancia = tarea$instancia, algoritmo = tarea$algoritmo,
                     corrida = tarea$corrida, semilla = tarea$semilla,
                     F = res$F, E_S = res$ev$ES, E_D = res$ev$ED,
                     lavados = res$ev$lavados, conformadas = res$ev$conformadas,
                     asignadas = res$ev$asignadas, sobrantes = res$ev$sobrantes,
                     evaluaciones = res$evals, tiempo_s = res$tiempo,
                     factible = length(errores) == 0)
  out <- list(fila = fila, hist = res$hist, sol = res$sol)
  saveRDS(out, archivo)
  out
}

ejecutar_experimento <- function(inst, dir_res = "resultados",
                                 nucleos = max(1L, parallel::detectCores() - 1L)) {
  dir_corridas <- file.path(dir_res, "corridas")
  dir.create(dir_corridas, showWarnings = FALSE, recursive = TRUE)

  # Regla voraz (línea base)
  voraz <- do.call(rbind, lapply(names(inst), function(n) {
    g <- regla_voraz(inst[[n]])
    data.frame(instancia = n, F = g$F, E_S = g$ev$ES, E_D = g$ev$ED,
               lavados = g$ev$lavados, conformadas = g$ev$conformadas,
               asignadas = g$ev$asignadas, sobrantes = g$ev$sobrantes,
               factible = length(validar(inst[[n]], g$sol, g$ev)) == 0)
  }))
  write.csv(voraz, file.path(dir_res, "voraz.csv"), row.names = FALSE)

  tareas <- expand.grid(corrida = seq_len(CONFIG$corridas), algoritmo = c("AG", "ACO"),
                        instancia = names(inst), stringsAsFactors = FALSE)
  tareas$semilla <- semilla_corrida(match(tareas$instancia, names(inst)), tareas$corrida)
  lista <- split(tareas, seq_len(nrow(tareas)))

  if (nucleos > 1L) {
    cl <- parallel::makeCluster(nucleos)
    on.exit(parallel::stopCluster(cl))
    parallel::clusterCall(cl, function(arch) for (f in arch) source(f, encoding = "UTF-8"),
                          sort(list.files("R", pattern = "^0[0-4].*\\.R$", full.names = TRUE)))
    parallel::clusterExport(cl, "CONFIG", envir = globalenv())   # parámetros calibrados
    res <- parallel::parLapplyLB(cl, lista, ejecutar_corrida, inst = inst,
                                 dir_corridas = dir_corridas)
  } else {
    res <- lapply(lista, ejecutar_corrida, inst = inst, dir_corridas = dir_corridas)
  }
  corridas <- do.call(rbind, lapply(res, `[[`, "fila"))
  write.csv(corridas, file.path(dir_res, "corridas.csv"), row.names = FALSE)

  conv <- do.call(rbind, lapply(res, function(r) data.frame(
    instancia = r$fila$instancia, algoritmo = r$fila$algoritmo, corrida = r$fila$corrida,
    evaluaciones = seq_along(r$hist) * CONFIG$paso_convergencia, mejor_F = r$hist)))
  write.csv(conv, file.path(dir_res, "convergencia.csv"), row.names = FALSE)
  list(voraz = voraz, corridas = corridas, convergencia = conv)
}
