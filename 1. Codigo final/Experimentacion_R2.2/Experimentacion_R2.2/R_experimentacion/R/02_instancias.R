# =============================================================================
# 02_instancias.R
# Construye la instancia de una semana de tintorería (lunes a domingo) a partir
# de los datos del planificador y del ESTADO que dejó la semana anterior
# (memoria): tela no utilizada de cada artículo y partidas ya planificadas.
#
# La tela se agrupa en "bolsas": cada bolsa reúne los kilogramos de un mismo
# artículo, sin importar el pedido para el que se tejió. La bolsa recibe la tela
# proyectada día a día; los kilogramos que no alcanzan para completar una
# partida quedan guardados en la bolsa hasta que lleguen más.
# =============================================================================

clave_bolsa <- function(pedido, clave_art) {
  if (identical(CONFIG$agrupar_tela_por, "articulo")) clave_art
  else paste(pedido, clave_art, sep = "|")
}

# Estado inicial del mes: toda partida pendiente, sin kilogramos comprometidos y
# con la tela tejida antes del primer lunes evaluado en almacén.
estado_inicial <- function(D, primer_lunes) {
  pr <- D$proyeccion[D$proyeccion$fecha_disponible < primer_lunes, ]
  stock <- tapply(pr$kg, clave_bolsa(pr$pedido, pr$clave_articulo), sum)
  list(stock = stock,
       comprometidos = D$componentes$kg_comprometidos,
       estado_partida = setNames(D$partidas$estado, D$partidas$partida),
       semana_previa = NULL)
}

# Retraso de tejeduría: la tela de las bolsas afectadas se teje un día después
# (si cae en domingo pasa al lunes) y se recalcula su fecha de disponibilidad.
aplicar_retraso_tejeduria <- function(pr, bolsas_afectadas, desde) {
  # solo se retrasa la tela que aún no llegaba al iniciar la semana
  afect <- clave_bolsa(pr$pedido, pr$clave_articulo) %in% bolsas_afectadas &
    pr$fecha_disponible >= desde
  nueva <- pr$fecha_tejido[afect] + 1
  nueva <- nueva + ifelse(as.POSIXlt(nueva)$wday == 0, 1, 0)
  pr$fecha_tejido[afect] <- nueva
  pr$fecha_disponible[afect] <- fecha_disponible_tintoreria(nueva)
  pr
}

# -----------------------------------------------------------------------------
# construir_instancia
#   D        : datos del planificador (01_preparar_datos.R)
#   estado   : memoria de la semana anterior
#   lunes    : fecha del lunes de la semana de tintorería
#   indisponibilidad : data.frame(maquina, dia) con los días sin carga
#   retraso  : proporción de bolsas cuya tela se retrasa un día (0 = ninguna)
# -----------------------------------------------------------------------------
construir_instancia <- function(D, estado, lunes, nombre,
                                indisponibilidad = NULL, retraso = 0,
                                semilla = 1L) {
  ND <- CONFIG$dias_semana
  dias <- lunes + 0:(ND - 1)

  # ---- Máquinas: días hábiles y objetivo efectivo ---------------------------
  maq <- D$maquinas
  habil <- matrix(TRUE, nrow(maq), ND, dimnames = list(maq$maquina, CONFIG$nombres_dias))
  if (!is.null(indisponibilidad) && nrow(indisponibilidad) > 0) {
    for (i in seq_len(nrow(indisponibilidad)))
      habil[indisponibilidad$maquina[i], indisponibilidad$dia[i]] <- FALSE
  }
  dias_perdidos <- ND - rowSums(habil)
  maq$objetivo <- ifelse(dias_perdidos == 0, maq$objetivo,
                         ifelse(dias_perdidos == 1, OBJETIVO_CON_UN_DIA_INDISPONIBLE,
                                round(maq$objetivo * rowSums(habil) / ND)))
  maq$dias_habiles <- rowSums(habil)
  maq$minimo_diario <- floor(maq$objetivo / maq$dias_habiles)    # 2 partidas
  maq$maximo_diario <- ceiling(maq$objetivo / maq$dias_habiles)  # 3 partidas

  # ---- Proyección de la semana y bolsas de tela -----------------------------
  pr <- D$proyeccion
  pr$bolsa <- clave_bolsa(pr$pedido, pr$clave_articulo)
  if (retraso > 0) {
    set.seed(semilla)
    activas <- unique(pr$bolsa[pr$fecha_disponible >= lunes &
                               pr$fecha_disponible <= dias[ND]])
    afectadas <- sample(activas, round(retraso * length(activas)))
    pr <- aplicar_retraso_tejeduria(pr, afectadas, lunes)
  }
  pr <- pr[pr$fecha_disponible >= lunes & pr$fecha_disponible <= dias[ND], ]

  comp <- D$componentes
  comp$bolsa <- clave_bolsa(comp$pedido, comp$clave_articulo)
  comp$kg_comprometidos <- estado$comprometidos

  bolsas <- sort(unique(c(names(estado$stock)[estado$stock > 0], pr$bolsa)))
  llegadas <- matrix(0, length(bolsas), ND, dimnames = list(bolsas, CONFIG$nombres_dias))
  if (nrow(pr) > 0) {
    ag <- aggregate(kg ~ bolsa + fecha_disponible, data = pr, FUN = sum)
    llegadas[cbind(match(ag$bolsa, bolsas),
                   as.integer(ag$fecha_disponible - lunes) + 1L)] <- ag$kg
  }
  stock0 <- setNames(rep(0, length(bolsas)), bolsas)
  st <- estado$stock[names(estado$stock) %in% bolsas]
  stock0[names(st)] <- st
  total_bolsa <- stock0 + rowSums(llegadas)

  # ---- Partidas candidatas (RF21) y bloqueadas (RF34) -----------------------
  pend <- names(estado$estado_partida)[estado$estado_partida == "Pendiente"]
  partidas <- D$partidas[D$partidas$partida %in% pend, ]
  partidas <- merge(partidas, D$pedidos[, c("pedido", "fecha_entrega")],
                    by = "pedido", all.x = TRUE, sort = FALSE)
  partidas$fecha_entrega[is.na(partidas$fecha_entrega)] <- max(D$pedidos$fecha_entrega)
  partidas <- partidas[order(partidas$orden_registro), ]

  cp <- comp[comp$partida %in% partidas$partida, ]
  cp$faltante <- pmax(0, cp$kg_requeridos - cp$kg_comprometidos)
  # requerimiento de cada partida por bolsa (dos componentes del mismo artículo
  # se suman, porque se cubren con la misma tela)
  rq <- aggregate(faltante ~ partida + bolsa, data = cp, FUN = sum)
  rq <- rq[rq$faltante > 1e-9, ]
  rq$disponible <- ifelse(rq$bolsa %in% bolsas, total_bolsa[rq$bolsa], 0)
  rq$cubrible <- rq$faltante <= rq$disponible + 1e-9
  ok <- tapply(rq$cubrible, factor(rq$partida, levels = partidas$partida), all)
  ok[is.na(ok)] <- TRUE
  # compatibilidad con al menos una máquina de su barca
  compat_ok <- partidas$barca %in% maq$barca
  candidatas <- partidas[ok[partidas$partida] & compat_ok, ]
  bloqueadas <- partidas[!(ok[partidas$partida] & compat_ok), ]

  cc <- cp[cp$partida %in% candidatas$partida, ]
  cc <- cc[order(match(cc$partida, candidatas$partida)), ]
  rq <- rq[rq$partida %in% candidatas$partida, ]
  rq <- rq[order(match(rq$partida, candidatas$partida)), ]

  P <- nrow(candidatas); M <- nrow(maq)
  compat <- lapply(candidatas$barca, function(b) which(maq$barca == b))

  inst <- list(
    nombre = nombre, lunes = lunes, dias = dias, ND = ND,
    maquinas = maq, habil = habil, M = M,
    O = maq$objetivo, L = maq$minimo_diario, U = maq$maximo_diario,
    partidas = candidatas, bloqueadas = bloqueadas, P = P,
    fecha = as.integer(candidatas$fecha_entrega),   # prioridad: menor = más urgente
    tono = candidatas$tonalidad, registro = candidatas$orden_registro,
    compat = compat, n_compat = lengths(compat),
    bolsas = bolsas, B = length(bolsas), stock0 = unname(stock0),
    llegadas = unname(llegadas),
    comp = cc,
    # requerimientos (partida, bolsa, kg): la partida se conforma solo cuando
    # todas sus bolsas cubren a la vez lo que necesita
    req_partida = match(rq$partida, candidatas$partida),
    req_bolsa = match(rq$bolsa, bolsas),
    req_kg = rq$faltante,
    indisponibilidad = indisponibilidad, retraso = retraso,
    estado_entrada = estado
  )
  inst$reqs_de_partida <- split(seq_len(nrow(rq)), factor(inst$req_partida, levels = seq_len(P)))
  inst$partidas_de_bolsa <- lapply(split(inst$req_partida, factor(inst$req_bolsa, levels = seq_len(inst$B))), unique)
  inst$objetivo_total <- sum(inst$O)
  inst$fecha_min <- if (P > 0) min(inst$fecha) else 0L
  inst$dias_habiles_lista <- lapply(seq_len(M), function(m) which(habil[m, ]))
  # primer día hábil de cada máquina a partir de cada día (NA si no queda)
  inst$sig_habil <- t(apply(habil, 1, function(h) {
    vapply(seq_len(ND), function(a) { x <- which(h)[which(h) >= a]; if (length(x)) x[1] else NA_integer_ }, 1L)
  }))
  inst
}

# -----------------------------------------------------------------------------
# Memoria: estado que deja una semana al aplicarse una solución.
#   - las partidas conformadas pasan a planificadas (no vuelven a evaluarse)
#   - la tela no utilizada queda guardada en su artículo para la semana siguiente
#   (una partida nunca se llena a medias, por lo que no hay kg comprometidos
#    de partidas incompletas)
# -----------------------------------------------------------------------------
estado_siguiente <- function(D, inst, sol) {
  est <- inst$estado_entrada
  stock <- est$stock
  stock[names(stock)] <- 0
  fin <- setNames(sol$stock_final, inst$bolsas)
  stock[names(fin)] <- fin
  stock <- stock[stock > 1e-9]
  ep <- est$estado_partida
  conf <- inst$partidas$partida[!is.na(sol$dia_conf)]
  ep[conf] <- "Planificada"
  list(stock = stock, comprometidos = est$comprometidos, estado_partida = ep,
       semana_previa = inst$nombre)
}

resumen_instancia <- function(inst) {
  kg_semana <- sum(inst$stock0) + sum(inst$llegadas)
  data.frame(
    instancia = inst$nombre,
    partidas_pendientes = inst$P + nrow(inst$bloqueadas),
    candidatas = inst$P,
    bolsas = inst$B,
    kg_almacen = round(sum(inst$stock0)),
    kg_proyectados = round(sum(inst$llegadas)),
    objetivo_total = inst$objetivo_total,
    maquina_indisponible = !is.null(inst$indisponibilidad),
    retraso_tejeduria = inst$retraso > 0
  )
}
