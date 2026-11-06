# =============================================================================
# ejecutar_todo.R  —  Experimentación numérica del R2.2
# Abrir la carpeta del proyecto en RStudio (setwd en esta carpeta) y ejecutar:
#     source("ejecutar_todo.R", encoding = "UTF-8")
# Paquetes: readxl, ggplot2 (install.packages(c("readxl", "ggplot2")))
# =============================================================================

for (f in sort(list.files("R", pattern = "^[01][0-9]_.*\\.R$", full.names = TRUE)))
  source(f, encoding = "UTF-8")

NUCLEOS <- if (exists("NUCLEOS_FORZAR")) NUCLEOS_FORZAR else
  max(1L, parallel::detectCores() - 1L)   # núcleos para correr en paralelo

# 1) Datos de planeamiento ---------------------------------------------------
D <- suppressWarnings(leer_datos_planeamiento("datos"))
guardar_datos(D, "instancias")

# 2) Instancias semanales (S37, S38, S39 x condiciones B, I, R, IR) ----------
inst <- construir_todas_las_instancias(D, "instancias")
exportar_todas_csv(inst, "instancias/csv")          # para la versión en Java
print(read.csv("instancias/resumen_instancias.csv"))

# 3) Calibración 2^3 de parámetros (se reutiliza si ya existe) ---------------
dir.create("resultados", showWarnings = FALSE)
if (file.exists("resultados/calibracion.rds")) {
  cal <- readRDS("resultados/calibracion.rds")
} else {
  cal <- calibrar(instancia_calibracion(D), corridas = 5L, nucleos = NUCLEOS)
  saveRDS(cal, "resultados/calibracion.rds")
}
CONFIG$ag  <- modifyList(CONFIG$ag,  cal$mejor_ag[names(cal$mejor_ag)])
CONFIG$aco <- modifyList(CONFIG$aco, cal$mejor_aco[names(cal$mejor_aco)])
str(CONFIG$ag); str(CONFIG$aco)

# 4) Comparación: regla voraz + 20 corridas de AG y ACO por instancia --------
exp <- ejecutar_experimento(inst, "resultados", nucleos = NUCLEOS)

# 5) Análisis estadístico y figuras -------------------------------------------
an <- analizar("resultados")
print(an$por_instancia, digits = 4)
print(an$global, digits = 4)

# 6) Calendario de la mejor solución de cada algoritmo en la semana 38 base ---
corr <- read.csv("resultados/corridas.csv")
for (alg in c("ACO", "AG")) {
  x <- corr[corr$instancia == "S38-B" & corr$algoritmo == alg, ]
  x <- x[which.min(x$F), ]
  r <- readRDS(sprintf("resultados/corridas/S38-B_%s_%02d.rds", alg, x$corrida))
  I <- inst[["S38-B"]]
  ev <- evaluar(I, r$sol)
  write.csv(tabla_calendario(I, r$sol, ev), sprintf("resultados/calendario_S38-B_%s.csv", alg))
  write.csv(detalle_solucion(I, r$sol, ev), sprintf("resultados/detalle_S38-B_%s.csv", alg),
            row.names = FALSE)
}
