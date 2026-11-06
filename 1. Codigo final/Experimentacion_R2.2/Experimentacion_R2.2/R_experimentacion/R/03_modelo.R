# =============================================================================
# 03_modelo.R
# Modelo común con el que ambos algoritmos construyen y evalúan una semana.
#
# DOS DECISIONES QUE SE EVALÚAN JUNTAS
#   1) Conformación: cuando la tela acumulada de un artículo alcanza para
#      completar a más de una partida (del mismo o de distintos pedidos), ¿cuál
#      la recibe? Primero manda la fecha de entrega más próxima; entre las que
#      tienen la misma fecha decide el algoritmo (cada elección y cada orden
#      abren un escenario distinto).
#   2) Máquina: cada partida conformada va a una de las máquinas de su barca
#      (por ejemplo, "400 C": 400 kg y color); decide el algoritmo.
#
# La semana se simula día a día. Cada día llega a cada artículo (bolsa) la tela
# que tejeduría dejó disponible. Una partida NUNCA se llena a medias: recibe
# tela solo cuando la tela acumulada cubre todo lo que necesita (tela principal
# y complemento). Los kilogramos que no alcanzan para completar una partida
# quedan guardados en el artículo (memoria) y se suman a la tela de los días
# siguientes y de la semana siguiente.
#
# Cada algoritmo entrega una "regla" con sus dos elecciones:
#   regla$prioridad : número por partida (menor = recibe primero la tela entre
#                     las empatadas); si es NULL elige la hormiga (feromona y carga)
#   regla$maquina   : vector con la máquina de cada partida, o bien
#                     "menor_carga" (regla voraz) o "hormiga" (colonia)
# =============================================================================

# Ruleta: elige un elemento con probabilidad proporcional a su peso
ruleta <- function(w) {
  cw <- cumsum(w)
  i <- which(cw >= runif(1L) * cw[length(cw)])[1L]
  if (is.na(i)) length(w) else i
}

elegir_maquina_dinamica <- function(I, regla, p, carga) {
  cm <- I$compat[[p]]
  if (length(cm) == 1L) return(cm)
  if (regla$voraz)                        # regla voraz: menor carga relativa
    return(cm[which.min(carga[cm] / I$O[cm])])
  # colonia de hormigas: P(p,m) proporcional a tau^a * (1/T)^b
  T <- (carga[cm] + 1) / I$O[cm]
  w <- regla$tau[p, cm]^regla$a * (1 / T)^regla$b
  cm[ruleta(w)]
}

# Partidas pendientes cuya necesidad completa cubre hoy la tela acumulada
cubribles <- function(I, stock, pendiente) {
  falta <- I$req_kg > stock[I$req_bolsa] + 1e-9
  pendiente & tabulate(I$req_partida[falta], I$P) == 0L
}

simular_semana <- function(I, regla) {
  stock <- I$stock0
  pendiente <- rep(TRUE, I$P)
  dia_conf <- rep(NA_integer_, I$P)
  maq   <- rep(NA_integer_, I$P)
  orden <- rep(NA_integer_, I$P)
  carga <- integer(I$M)
  k <- 0L
  fecha <- I$fecha
  prio <- regla$prioridad
  fija <- is.numeric(regla$maquina)
  regla$voraz <- identical(regla$maquina, "menor_carga")

  for (d in seq_len(I$ND)) {
    stock <- stock + I$llegadas[, d]
    repeat {
      cand <- which(cubribles(I, stock, pendiente))
      if (length(cand) == 0L) break      # la tela queda guardada para mañana
      # (1) fecha de entrega más próxima
      f <- fecha[cand]
      cand <- cand[f == min(f)]
      # (2) entre las empatadas decide el algoritmo
      p <- if (length(cand) == 1L) cand
           else if (!is.null(prio)) cand[which.min(prio[cand])]
           else {
             # hormiga: atractivo de los nodos partida-máquina de cada partida,
             # feromona por heurística 1/T de cada máquina de su barca
             eta <- (I$O / (carga + 1))^regla$b
             cand[ruleta(as.vector(regla$W[cand, , drop = FALSE] %*% eta))]
           }
      # se compromete toda la tela que necesita la partida
      r <- I$reqs_de_partida[[p]]
      stock[I$req_bolsa[r]] <- stock[I$req_bolsa[r]] - I$req_kg[r]
      pendiente[p] <- FALSE
      k <- k + 1L; dia_conf[p] <- d; orden[p] <- k
      m <- if (fija) regla$maquina[p] else elegir_maquina_dinamica(I, regla, p, carga)
      maq[p] <- m; carga[m] <- carga[m] + 1L
    }
  }
  list(dia_conf = dia_conf, maq = maq, orden = orden, stock_final = stock)
}

# -----------------------------------------------------------------------------
# Calendario de cada máquina (lunes a domingo)
# Regla común: cada día se ubican las partidas que corresponden (2 o 3, en
# partidas completas, hasta llegar al objetivo semanal) y el resto pasa al día
# siguiente más cercano. Ante más partidas disponibles que espacio, primero va
# la de fecha de entrega más próxima y luego la que llegó antes. Las que exceden
# el objetivo semanal, o que llegan al final de la semana cuando los días
# restantes ya tienen su máximo, se muestran como Sobrante en su primer día
# disponible.
# -----------------------------------------------------------------------------
construir_calendario <- function(I, sol) {
  dia <- rep(NA_integer_, I$P)
  sobrante <- rep(FALSE, I$P)
  n_dia <- matrix(0L, I$M, I$ND)          # partidas regulares por máquina y día
  conf <- which(!is.na(sol$maq))
  if (length(conf) == 0L) return(list(dia = dia, sobrante = sobrante, n_dia = n_dia))
  # orden de atención: máquina, fecha de entrega, día disponible, orden de llegada
  clave <- ((sol$maq[conf] * 2000 + (I$fecha[conf] - I$fecha_min)) * 10 +
              sol$dia_conf[conf]) * 10000 + sol$orden[conf]
  conf <- conf[order(clave, method = "radix")]
  grupos <- split(conf, sol$maq[conf])
  for (g in names(grupos)) {
    m <- as.integer(g)
    idx <- grupos[[g]]
    hab <- I$dias_habiles_lista[[m]]
    disp <- I$sig_habil[m, sol$dia_conf[idx]]   # primer día hábil disponible
    libre <- !is.na(disp)
    R <- I$O[m]; L <- I$L[m]; U <- I$U[m]
    nk <- length(hab)
    for (j in seq_len(nk)) {
      if (R <= 0L) break
      d <- hab[j]
      cuota <- if (R - U >= L * (nk - j)) U else L
      if (cuota > R) cuota <- R
      cola <- which(libre & disp <= d)
      if (length(cola) == 0L) next
      if (length(cola) > cuota) cola <- cola[seq_len(cuota)]
      libre[cola] <- FALSE
      dia[idx[cola]] <- d
      n_dia[m, d] <- length(cola)
      R <- R - length(cola)
    }
    resto <- which(is.na(dia[idx]))
    if (length(resto)) {
      sobrante[idx[resto]] <- TRUE
      dr <- disp[resto]
      dr[is.na(dr)] <- sol$dia_conf[idx[resto]][is.na(dr)]
      dia[idx[resto]] <- dr
    }
  }
  list(dia = dia, sobrante = sobrante, n_dia = n_dia)
}

# -----------------------------------------------------------------------------
# Función objetivo  F = alfa*E_S + beta*E_D + gamma*Lambda  (menor es mejor)
#   E_S : suma, por máquina, del cuadrado de la diferencia entre sus partidas
#         (asignadas y sobrantes) y su objetivo semanal
#   E_D : suma, por máquina y día hábil, del cuadrado de lo que falta para el
#         mínimo diario (2) o excede el máximo diario (3); sin Sobrantes
#   Lambda: pasos de una tonalidad a otra más clara entre partidas consecutivas
#         de una misma máquina (requieren lavado)
# -----------------------------------------------------------------------------
evaluar <- function(I, sol, cal = construir_calendario(I, sol)) {
  conf <- which(!is.na(sol$maq))
  N <- tabulate(sol$maq[conf], I$M)
  ES <- sum((N - I$O)^2)
  nd <- cal$n_dia
  deficit <- pmax(0L, I$L - nd); exceso <- pmax(0L, nd - I$U)
  ED <- sum(((deficit^2 + exceso^2) * I$habil))
  # secuencia de cada máquina: día, regulares antes que Sobrantes, llegada
  LAV <- 0L
  if (length(conf) > 1L) {
    clave <- ((sol$maq[conf] * 10 + cal$dia[conf]) * 2 + cal$sobrante[conf]) * 10000 +
      sol$orden[conf]
    sec <- conf[order(clave, method = "radix")]
    mm <- sol$maq[sec]
    misma <- mm[-1L] == mm[-length(mm)]
    t <- I$tono[sec]
    LAV <- sum(misma & (t[-1L] < t[-length(t)]))
  }
  F <- CONFIG$alfa * ES + CONFIG$beta * ED + CONFIG$gamma * LAV
  nsob <- sum(cal$sobrante)
  list(F = F, ES = ES, ED = ED, lavados = LAV, N = N,
       asignadas = length(conf) - nsob, sobrantes = nsob,
       conformadas = length(conf), calendario = cal)
}

# -----------------------------------------------------------------------------
# Validador independiente: rehace el consumo de tela en el orden de llegada de
# las partidas y revisa todas las reglas. Devuelve los incumplimientos (vacío =
# factible).
# -----------------------------------------------------------------------------
validar <- function(I, sol, ev) {
  err <- character(0)
  cal <- ev$calendario
  conf <- which(!is.na(sol$dia_conf))
  # 1) asignación única y compatible con la barca
  for (p in conf)
    if (is.na(sol$maq[p]) || !(sol$maq[p] %in% I$compat[[p]]))
      err <- c(err, sprintf("partida %s en máquina incompatible", I$partidas$partida[p]))
  if (any(!is.na(sol$maq[-conf]))) err <- c(err, "partida no conformada con máquina")
  # 2) tela: cada partida se completa de una vez, sin exceder la tela acumulada,
  #    respetando la fecha de entrega; al cierre de cada día no queda ninguna
  #    partida que la tela permita completar (todas se incluyen: RF32)
  stock <- I$stock0; pendiente <- rep(TRUE, I$P)
  secuencia <- conf[order(sol$orden[conf])]
  j <- 1L
  for (d in seq_len(I$ND)) {
    stock <- stock + I$llegadas[, d]
    while (j <= length(secuencia) && sol$dia_conf[secuencia[j]] == d) {
      p <- secuencia[j]
      cub <- which(cubribles(I, stock, pendiente))
      if (!(p %in% cub)) err <- c(err, sprintf("partida %s sin tela suficiente", I$partidas$partida[p]))
      else if (I$fecha[p] > min(I$fecha[cub])) err <- c(err, "prioridad por fecha de entrega incumplida")
      r <- I$reqs_de_partida[[p]]
      stock[I$req_bolsa[r]] <- stock[I$req_bolsa[r]] - I$req_kg[r]
      pendiente[p] <- FALSE
      j <- j + 1L
    }
    if (any(stock < -1e-6)) err <- c(err, "consumo mayor que la tela disponible")
    if (any(cubribles(I, stock, pendiente))) err <- c(err, sprintf("día %d: partida completable no incluida", d))
  }
  if (j <= length(secuencia)) err <- c(err, "orden de conformación inconsistente con los días")
  # 3) disponibilidad temporal y días hábiles
  for (p in conf) {
    if (is.na(cal$dia[p])) { err <- c(err, "partida conformada fuera del calendario"); next }
    if (cal$dia[p] < sol$dia_conf[p]) err <- c(err, "partida ubicada antes de estar disponible")
    if (!cal$sobrante[p] && !I$habil[sol$maq[p], cal$dia[p]])
      err <- c(err, "partida ubicada en día indisponible")
  }
  # 4) Sobrantes solo cuando se excede el objetivo; máximo diario respetado
  for (m in seq_len(I$M)) {
    reg <- conf[!cal$sobrante[conf] & sol$maq[conf] == m]
    if (length(reg) > I$O[m]) err <- c(err, "asignadas por encima del objetivo")
    # Sobrante: excede el objetivo semanal o ya no cabe en ningún día hábil
    # desde que está disponible (todos llegaron a su máximo diario)
    sob <- conf[cal$sobrante[conf] & sol$maq[conf] == m]
    if (length(sob) && length(reg) < I$O[m]) {
      nd <- tabulate(cal$dia[reg], I$ND)
      for (p in sob) {
        dias <- which(I$habil[m, ] & seq_len(I$ND) >= sol$dia_conf[p])
        if (any(nd[dias] < I$U[m])) err <- c(err, "Sobrante con espacio disponible en la semana")
      }
    }
    if (any(tabulate(cal$dia[reg], I$ND) > I$U[m])) err <- c(err, "más partidas que el máximo diario")
  }
  unique(err)
}

# Tabla legible del calendario (máquina x día)
tabla_calendario <- function(I, sol, ev) {
  cal <- ev$calendario
  conf <- which(!is.na(sol$dia_conf))
  t <- matrix("", I$M, I$ND, dimnames = list(I$maquinas$maquina, CONFIG$nombres_dias))
  for (m in seq_len(I$M)) for (d in seq_len(I$ND)) {
    idx <- conf[sol$maq[conf] == m & cal$dia[conf] == d]
    if (!I$habil[m, d]) { t[m, d] <- "X"; next }
    r <- sum(!cal$sobrante[idx]); s <- sum(cal$sobrante[idx])
    t[m, d] <- if (s > 0) paste0(r, "+", s, "S") else as.character(r)
  }
  data.frame(t, Total = paste0(ev$N, "/", I$O), check.names = FALSE)
}

detalle_solucion <- function(I, sol, ev) {
  cal <- ev$calendario
  conf <- which(!is.na(sol$dia_conf))
  out <- data.frame(
    partida = I$partidas$partida, pedido = I$partidas$pedido,
    fecha_entrega = I$partidas$fecha_entrega, barca = I$partidas$barca,
    peso = I$partidas$peso, tonalidad = TONALIDADES[I$tono],
    dia_disponible = ifelse(is.na(sol$dia_conf), NA, CONFIG$nombres_dias[sol$dia_conf]),
    maquina = ifelse(is.na(sol$maq), NA, I$maquinas$maquina[sol$maq]),
    dia_asignado = ifelse(is.na(cal$dia), NA, CONFIG$nombres_dias[cal$dia]),
    clasificacion = ifelse(is.na(sol$maq), "Bloqueada",
                           ifelse(cal$sobrante, "Sobrante", "Asignada")),
    orden_llegada = sol$orden)
  if (nrow(I$bloqueadas)) {
    b <- I$bloqueadas
    out <- rbind(out, data.frame(partida = b$partida, pedido = b$pedido,
      fecha_entrega = b$fecha_entrega, barca = b$barca, peso = b$peso,
      tonalidad = TONALIDADES[b$tonalidad], dia_disponible = NA, maquina = NA,
      dia_asignado = NA, clasificacion = "Bloqueada", orden_llegada = NA))
  }
  out
}
