# =============================================================================
# 04_algoritmos.R
# Regla voraz (línea base), algoritmo genético y colonia de hormigas.
# Los tres usan el mismo modelo (03_modelo.R) y solo difieren en cómo eligen
# (1) qué partida recibe la tela entre las empatadas y (2) a qué máquina de su
# barca va cada partida conformada.
# =============================================================================

# Cronómetro y registro de convergencia comunes ------------------------------
nuevo_registro <- function(presupuesto) {
  e <- new.env()
  e$evals <- 0L; e$mejor <- Inf; e$sol <- NULL
  e$hist <- rep(NA_real_, presupuesto %/% CONFIG$paso_convergencia)
  e
}
anotar <- function(R, F, sol) {
  R$evals <- R$evals + 1L
  if (F < R$mejor) { R$mejor <- F; R$sol <- sol }
  if (R$evals %% CONFIG$paso_convergencia == 0L && R$evals <= length(R$hist) * CONFIG$paso_convergencia)
    R$hist[R$evals %/% CONFIG$paso_convergencia] <- R$mejor
}

# =============================================================================
# REGLA VORAZ: reproduce el criterio actual del planificador (RD-01, RD-07).
# La tela se entrega a las partidas en el orden en que fueron registradas y
# cada partida va a la máquina compatible con menor carga relativa.
# =============================================================================
regla_voraz <- function(I) {
  t0 <- proc.time()[["elapsed"]]
  regla <- list(prioridad = I$registro, maquina = "menor_carga")
  sol <- simular_semana(I, regla)
  ev <- evaluar(I, sol)
  list(F = ev$F, sol = sol, ev = ev, tiempo = proc.time()[["elapsed"]] - t0)
}

# =============================================================================
# ALGORITMO GENÉTICO (Campo et al., 2022)
#  - Cromosoma: cada gen guarda una partida y un número aleatorio entre 0 y 1.
#    El orden de los genes define qué partida recibe primero la tela cuando hay
#    empate; el número del gen elige la máquina entre las de su barca, todas con
#    la misma probabilidad (ceiling(r * número de máquinas compatibles)).
#  - Población inicial aleatoria, elitismo, selección aleatoria de dos padres,
#    cruce de un punto con reparación de partidas repetidas y mutación SWAP.
# =============================================================================
decodificar_ag <- function(I, perm, r) {
  pos <- integer(I$P); pos[perm] <- seq_len(I$P)
  # máquina de cada partida: el número del gen en su posición elige entre las
  # máquinas de su barca, todas con la misma probabilidad
  maq <- vapply(seq_len(I$P), function(p) {
    cm <- I$compat[[p]]
    cm[max(1L, ceiling(r[pos[p]] * length(cm)))]
  }, 1L)
  simular_semana(I, list(prioridad = pos, maquina = maq))
}

cruce_un_punto <- function(p1, p2, r1, r2) {
  n <- length(p1)
  c <- sample.int(n - 1L, 1L)
  cabeza <- p1[seq_len(c)]
  hijo <- c(cabeza, p2[!(p2 %in% cabeza)])          # reparación: sin repetidos
  list(perm = hijo, r = c(r1[seq_len(c)], r2[(c + 1L):n]))
}

mutacion_swap <- function(perm) {
  ij <- sample.int(length(perm), 2L)
  perm[ij] <- perm[rev(ij)]
  perm
}

algoritmo_genetico <- function(I, semilla, presupuesto = CONFIG$evaluaciones,
                               par = CONFIG$ag) {
  set.seed(semilla)
  t0 <- proc.time()[["elapsed"]]
  R <- nuevo_registro(presupuesto)
  n <- I$P; NP <- par$poblacion
  evaluar_ind <- function(perm, r) {
    sol <- decodificar_ag(I, perm, r)
    F <- evaluar(I, sol)$F
    anotar(R, F, list(perm = perm, r = r))
    F
  }
  pob <- lapply(seq_len(NP), function(i) list(perm = sample.int(n), r = runif(n)))
  fit <- vapply(pob, function(x) evaluar_ind(x$perm, x$r), 0)
  n_elite <- max(1L, round(par$elitismo * NP))
  while (R$evals < presupuesto) {
    o <- order(fit)
    nueva <- pob[o[seq_len(n_elite)]]; nfit <- fit[o[seq_len(n_elite)]]
    while (length(nueva) < NP && R$evals < presupuesto) {
      pa <- sample.int(NP, 2L)                       # selección aleatoria
      h <- pob[[pa[1]]]
      if (runif(1) < par$p_cruce)
        h <- cruce_un_punto(pob[[pa[1]]]$perm, pob[[pa[2]]]$perm,
                            pob[[pa[1]]]$r, pob[[pa[2]]]$r)
      if (runif(1) < par$p_mutacion) h$perm <- mutacion_swap(h$perm)
      nueva[[length(nueva) + 1L]] <- h
      nfit <- c(nfit, evaluar_ind(h$perm, h$r))
    }
    pob <- nueva; fit <- nfit
  }
  sol <- decodificar_ag(I, R$sol$perm, R$sol$r)
  ev <- evaluar(I, sol)
  list(F = ev$F, sol = sol, ev = ev, hist = R$hist, evals = R$evals,
       tiempo = proc.time()[["elapsed"]] - t0)
}

# =============================================================================
# COLONIA DE HORMIGAS (Fidanova et al., 2025)
#  - Cada nodo es el par partida-máquina, con feromona inicial tau0.
#  - Cada hormiga construye la semana día a día. Cuando varias partidas
#    empatadas pueden recibir la tela, elige un nodo partida-máquina con
#    probabilidad P(p,m) = tau(p,m)^a * eta(m)^b / suma, con eta(m) = 1/T_m y
#    T_m la carga relativa de la máquina al agregarle la partida (prefiere las
#    partidas cuya barca tiene máquinas menos cargadas).
#  - Al conformarse la partida, elige su máquina con la misma probabilidad.
#  - Al terminar cada iteración: tau <- rho*tau + delta, donde la mejor hormiga
#    de la iteración deposita delta = (1 - rho)/F en los nodos que utilizó.
# =============================================================================
colonia_hormigas <- function(I, semilla, presupuesto = CONFIG$evaluaciones,
                             par = CONFIG$aco) {
  set.seed(semilla)
  t0 <- proc.time()[["elapsed"]]
  R <- nuevo_registro(presupuesto)
  tau <- matrix(par$tau0, I$P, I$M)

  # W = tau^a solo en los nodos partida-máquina compatibles (barca)
  mascara <- matrix(0, I$P, I$M)
  mascara[cbind(rep(seq_len(I$P), I$n_compat), unlist(I$compat))] <- 1
  regla <- list(prioridad = NULL, maquina = "hormiga", tau = tau,
                W = tau^par$a * mascara, a = par$a, b = par$b)

  while (R$evals < presupuesto) {
    mejor_it <- NULL; F_it <- Inf
    for (h in seq_len(par$hormigas)) {
      if (R$evals >= presupuesto) break
      sol <- simular_semana(I, regla)
      F <- evaluar(I, sol)$F
      anotar(R, F, sol)
      if (F < F_it) { F_it <- F; mejor_it <- sol }
    }
    # evaporación y depósito de la mejor hormiga de la iteración
    tau <- par$rho * tau
    usados <- which(!is.na(mejor_it$maq))
    tau[cbind(usados, mejor_it$maq[usados])] <-
      tau[cbind(usados, mejor_it$maq[usados])] + (1 - par$rho) / (1 + F_it)
    regla$tau <- tau
    regla$W <- tau^par$a * mascara
  }
  sol <- R$sol
  ev <- evaluar(I, sol)
  list(F = ev$F, sol = sol, ev = ev, hist = R$hist, evals = R$evals,
       tiempo = proc.time()[["elapsed"]] - t0)
}
