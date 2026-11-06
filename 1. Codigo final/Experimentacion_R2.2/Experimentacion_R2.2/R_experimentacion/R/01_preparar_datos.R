# =============================================================================
# 01_preparar_datos.R
# Lee las hojas de cálculo de planeamiento y construye las tablas que usa el
# algoritmo, siguiendo el catálogo de variables del R2.1:
#   - pedidos     : pedido, cliente, fecha de pedido, fecha de entrega
#   - partidas    : partida, pedido, color, tonalidad, peso total, barca
#   - componentes : partida, artículo, rol, kg requeridos, kg comprometidos
#   - proyeccion  : pedido, artículo, fecha de tejido, fecha en que la tela queda
#                   disponible para tintorería, kg proyectados
# =============================================================================

suppressMessages(library(readxl))

limpiar <- function(x) {
  x <- gsub(intToUtf8(160), " ", enc2utf8(as.character(x)), fixed = TRUE)
  trimws(gsub("\\s+", " ", x))
}
# Clave de artículo sin espacios, puntos ni guiones: los tres archivos escriben
# el mismo artículo con pequeñas diferencias ("PEI.-02" y "PEI. - 02").
clave_articulo <- function(x) gsub("[[:space:].-]", "", toupper(limpiar(x)))
op_texto <- function(x) sub("\\.0+$", "", limpiar(x))

# -----------------------------------------------------------------------------
# Tonalidad a partir del código de color: el segundo carácter indica la
# intensidad (0 blanco, 1 claro, 2 medio, 3 oscuro).
# -----------------------------------------------------------------------------
tonalidad_por_codigo <- function(codigo) {
  d <- substr(limpiar(codigo), 2, 2)
  t <- match(d, c("0", "1", "2", "3"))
  t[is.na(t)] <- 4L
  t                      # 1 = Blanco, 2 = Claro, 3 = Medio, 4 = Oscuro
}

# Barca de la partida: menor grupo de capacidad que admite su peso (RD-02 y
# RD-11) y tipo de color de la máquina ("C" color, "B" blanco).
barca_por_peso <- function(peso, tipo = "C") {
  g <- vapply(peso, function(p) GRUPOS_CAPACIDAD[which(GRUPOS_CAPACIDAD >= p)[1]],
              numeric(1))
  paste(g, tipo)
}

# Disponibilidad para tintorería: lo tejido de lunes a viernes queda disponible
# al día siguiente; lo tejido el sábado (o domingo), el lunes.
fecha_disponible_tintoreria <- function(fecha_tejido) {
  wd <- as.POSIXlt(fecha_tejido)$wday     # 0 = domingo, 6 = sábado
  fecha_tejido + ifelse(wd == 6, 2, 1)
}

leer_datos_planeamiento <- function(dir_datos = "datos") {
  set.seed(CONFIG$semilla_datos)

  # ---------------------------------------------------------------- Pedidos --
  s <- read_excel(file.path(dir_datos, "PROG_SETIEMBRE.xlsx"), skip = 3,
                  col_types = c("text", "date", rep("text", 4), rep("numeric", 9)),
                  .name_repair = "minimal")
  names(s) <- c("aff", "fecha", "cliente", "pedido", "articulo", "cod_articulo",
                "kg_solicitados", "diametro", "galga", "malla", "kg_tejidos",
                "mes_prev", "mes_act", "mes_post", "kg_por_tejer")
  s <- s[!is.na(s$pedido) & grepl("^[0-9]", s$pedido), ]
  s$pedido <- op_texto(s$pedido)
  s$fecha  <- as.Date(s$fecha)

  pedidos <- do.call(rbind, lapply(split(s, s$pedido), function(d) {
    data.frame(pedido = d$pedido[1], cliente = limpiar(d$cliente[1]),
               fecha_pedido = suppressWarnings(min(d$fecha, na.rm = TRUE)))
  }))
  # Fecha de entrega comprometida: entre 30 y 45 días después del pedido (R1.2)
  sin_fecha <- !is.finite(pedidos$fecha_pedido)
  pedidos$fecha_pedido[sin_fecha] <- as.Date("2026-09-01")
  pedidos$fecha_entrega <- pedidos$fecha_pedido +
    sample(30:45, nrow(pedidos), replace = TRUE)
  rownames(pedidos) <- NULL

  # --------------------------------------------------- Partidas y componentes --
  p <- read_excel(file.path(dir_datos, "PARTIDAS.xlsx"), col_types = "text")
  p <- p[, 1:10]
  names(p) <- c("partida", "cliente", "pedido", "cod_color", "color",
                "cod_articulo", "articulo", "kg", "tipo_ta", "ruta")
  p <- p[!is.na(p$partida), ]
  for (v in names(p)) p[[v]] <- limpiar(p[[v]])
  p$pedido <- op_texto(p$pedido)
  p$kg <- as.numeric(p$kg)
  p$clave_articulo <- clave_articulo(p$articulo)

  # Rol del componente: la tela principal es la de mayor peso; el resto,
  # complementos (en el sistema lo registra el planificador).
  p <- p[order(match(p$partida, unique(p$partida)), -p$kg), ]
  p$rol <- ifelse(!duplicated(p$partida), "Principal", "Complemento")

  componentes <- data.frame(
    partida = p$partida, pedido = p$pedido, cod_articulo = p$cod_articulo,
    articulo = p$articulo, clave_articulo = p$clave_articulo, rol = p$rol,
    tipo_ta = p$tipo_ta, kg_requeridos = p$kg, kg_comprometidos = 0,
    stringsAsFactors = FALSE)
  componentes$id_comp <- seq_len(nrow(componentes))

  partidas <- do.call(rbind, lapply(split(p, factor(p$partida, unique(p$partida))),
    function(d) data.frame(partida = d$partida[1], pedido = d$pedido[1],
                           cliente = d$cliente[1], cod_color = d$cod_color[1],
                           color = d$color[1], peso = sum(d$kg))))
  partidas$tonalidad <- tonalidad_por_codigo(partidas$cod_color)
  partidas$barca <- barca_por_peso(partidas$peso)
  partidas$orden_registro <- seq_len(nrow(partidas))   # orden en que se registraron
  partidas$estado <- "Pendiente"
  rownames(partidas) <- NULL

  # ---------------------------------------------------- Proyección de tejeduría --
  t <- read_excel(file.path(dir_datos, "PROG_TEJIDO.xlsx"), col_names = FALSE,
                  .name_repair = "minimal")
  t <- as.data.frame(t)
  fila_fechas <- which(apply(t, 1, function(r) sum(grepl("^4[0-9]{4}$", r)) >= 5))[1]
  cols_fecha <- which(grepl("^4[0-9]{4}$", t[fila_fechas, ]))
  fechas <- as.Date(as.numeric(t[fila_fechas, cols_fecha]), origin = "1899-12-30")
  datos <- t[(fila_fechas + 1):nrow(t), ]
  datos <- datos[!is.na(datos[[3]]), ]
  proyeccion <- do.call(rbind, lapply(seq_along(cols_fecha), function(j) {
    kg <- suppressWarnings(as.numeric(datos[[cols_fecha[j]]]))
    data.frame(maquina_tejeduria = limpiar(datos[[1]]), pedido = op_texto(datos[[3]]),
               articulo = limpiar(datos[[4]]), clave_articulo = clave_articulo(datos[[4]]),
               fecha_tejido = fechas[j], kg = ifelse(is.na(kg), 0, kg))
  }))
  proyeccion <- proyeccion[proyeccion$kg > 0, ]
  proyeccion$fecha_disponible <- fecha_disponible_tintoreria(proyeccion$fecha_tejido)
  rownames(proyeccion) <- NULL

  list(pedidos = pedidos, partidas = partidas, componentes = componentes,
       proyeccion = proyeccion, maquinas = MAQUINAS_BASE)
}

# Guarda las tablas en CSV para revisarlas o cargarlas en otra herramienta
guardar_datos <- function(D, dir_salida = "instancias") {
  dir.create(dir_salida, showWarnings = FALSE, recursive = TRUE)
  for (n in c("pedidos", "partidas", "componentes", "proyeccion", "maquinas")) {
    write.csv(D[[n]], file.path(dir_salida, paste0("datos_", n, ".csv")),
              row.names = FALSE, fileEncoding = "UTF-8")
  }
}
