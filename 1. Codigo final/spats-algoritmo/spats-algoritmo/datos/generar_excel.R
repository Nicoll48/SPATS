# =============================================================================
# generar_excel.R
# Genera los libros de Excel de prueba del algoritmo a partir de los datos de
# planeamiento procesados en la experimentación numérica (R2.2).
#
# Cada libro representa la información de una semana de tintorería tal como la
# recibiría el algoritmo desde el sistema: parámetros, máquinas,
# indisponibilidades, pedidos, partidas (con su estado al inicio de la semana),
# componentes, proyección de tejeduría de la semana y tela en almacén.
#
# Uso: ejecutar desde la carpeta del proyecto de experimentación (r22), después
# de ejecutar_todo.R:
#   Rscript ../spats-algoritmo/datos/generar_excel.R ../spats-algoritmo/datos
# =============================================================================
suppressMessages(library(openxlsx))
args <- commandArgs(TRUE)
salida <- if (length(args)) args[1] else "datos_excel"
dir.create(salida, showWarnings = FALSE, recursive = TRUE)
for (f in sort(list.files("R", pattern = "^0[0-5]_.*\\.R$", full.names = TRUE))) source(f, encoding = "UTF-8")

D <- leer_datos_planeamiento("datos")
inst <- readRDS("instancias/instancias.rds")
nombres_ton <- c("Blanco", "Claro", "Medio", "Oscuro")
art_nombre <- unique(rbind(D$componentes[, c("clave_articulo", "articulo")],
                           D$proyeccion[, c("clave_articulo", "articulo")]))
art_nombre <- art_nombre[!duplicated(art_nombre$clave_articulo), ]

for (n in names(inst)) {
  I <- inst[[n]]; est <- I$estado_entrada; lunes <- I$lunes; domingo <- lunes + 6

  # proyección de la semana (con el retraso de tejeduría si corresponde)
  pr <- D$proyeccion
  pr$bolsa <- clave_bolsa(pr$pedido, pr$clave_articulo)
  if (I$retraso > 0) {
    set.seed(100L + match(substr(n, 1, 3), c("S37", "S38", "S39")))
    activas <- unique(pr$bolsa[pr$fecha_disponible >= lunes & pr$fecha_disponible <= domingo])
    afectadas <- sample(activas, round(I$retraso * length(activas)))
    pr <- aplicar_retraso_tejeduria(pr, afectadas, lunes)
  }
  pr <- pr[pr$fecha_disponible >= lunes & pr$fecha_disponible <= domingo, ]

  parametros <- data.frame(
    parametro = c("fecha_inicio_semana", "dias_semana", "reduccion_objetivo_por_dia_indisponible",
                  "agrupar_tela_por", "peso_desbalance_semanal", "peso_desbalance_diario", "peso_lavados",
                  "hormigas", "rho", "feromona_inicial", "peso_feromona", "peso_heuristica",
                  "evaluaciones", "tiempo_maximo_segundos", "semilla"),
    valor = c(format(lunes), "7", "2", "articulo", "1", "1", "0.25",
              "20", "0.9", "0.5", "1", "2", "6000", "60", "1"),
    descripcion = c("Lunes de la semana de tintorería", "Días del calendario (lunes a domingo)",
                    "Partidas que se descuentan del objetivo semanal por cada día indisponible",
                    "articulo o pedido_articulo", "Peso del desbalance semanal (alfa)",
                    "Peso del desbalance diario (beta)", "Peso de los lavados (gamma)",
                    "Cantidad de hormigas por iteración", "Fracción de feromona que se conserva en cada iteración",
                    "Feromona inicial de cada par partida-máquina", "Peso de la feromona (a)",
                    "Peso de la información heurística (b)", "Evaluaciones de la función objetivo por ejecución",
                    "Tiempo máximo de ejecución (RNF03)", "Semilla del generador aleatorio"))
  maquinas <- D$maquinas[, c("maquina", "capacidad", "tipo", "barca", "objetivo")]
  names(maquinas)[5] <- "objetivo_semanal"
  indisp <- if (is.null(I$indisponibilidad)) data.frame(maquina = character(), fecha = as.Date(character()), motivo = character())
            else data.frame(maquina = I$indisponibilidad$maquina, fecha = lunes + I$indisponibilidad$dia - 1,
                            motivo = "Mantenimiento programado")
  pedidos <- D$pedidos[, c("pedido", "cliente", "fecha_pedido", "fecha_entrega")]
  partidas <- D$partidas[, c("partida", "pedido", "cod_color", "color", "peso", "tonalidad", "barca", "orden_registro")]
  partidas$tonalidad <- nombres_ton[partidas$tonalidad]
  partidas$estado <- unname(est$estado_partida[partidas$partida])
  componentes <- D$componentes[, c("partida", "cod_articulo", "articulo", "clave_articulo", "rol", "kg_requeridos")]
  proyeccion <- pr[, c("maquina_tejeduria", "pedido", "articulo", "clave_articulo", "fecha_tejido", "kg")]
  stock <- est$stock[est$stock > 1e-9]
  clave <- sub("^.*\\|", "", names(stock))
  stock_ini <- data.frame(clave_articulo = clave,
                          articulo = art_nombre$articulo[match(clave, art_nombre$clave_articulo)],
                          kg = round(as.numeric(stock), 3))

  wb <- createWorkbook()
  hojas <- list(Parametros = parametros, Maquinas = maquinas, Indisponibilidad = indisp, Pedidos = pedidos,
                Partidas = partidas, Componentes = componentes, Proyeccion = proyeccion, StockInicial = stock_ini)
  for (h in names(hojas)) {
    addWorksheet(wb, h)
    writeData(wb, h, hojas[[h]], headerStyle = createStyle(textDecoration = "bold"))
    setColWidths(wb, h, seq_along(hojas[[h]]), "auto")
  }
  saveWorkbook(wb, file.path(salida, paste0(n, ".xlsx")), overwrite = TRUE)
  cat(sprintf("%-7s partidas pendientes %3d | proyección %3d filas | stock %2d artículos\n",
              n, sum(partidas$estado == "Pendiente"), nrow(proyeccion), nrow(stock_ini)))
}
