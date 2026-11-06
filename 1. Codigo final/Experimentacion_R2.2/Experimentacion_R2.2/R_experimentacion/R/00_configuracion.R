# =============================================================================
# 00_configuracion.R
# Parámetros de la experimentación numérica del R2.2.
# Todos los valores son configurables: el sistema los recibirá del planificador.
# =============================================================================

CONFIG <- list(
  semilla_datos = 2026L,          # semilla para completar los datos del planificador

  # --- Calendario de tintorería ------------------------------------------------
  dias_semana = 7L,               # lunes a domingo
  nombres_dias = c("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"),

  # --- Función objetivo: F = alfa*E_S + beta*E_D + gamma*Lambda -----------------
  alfa  = 1,      # desequilibrio semanal  (RD-01, RF27, RF29, RF36)
  beta  = 1,      # desequilibrio diario   (RD-08, RF27)
  gamma = 0.25,   # lavados por paso de tonalidad oscura a clara (RD-06, RF26)

  # --- Prioridad de conformación (RD-07, RF23) --------------------------------
  # La tela se acumula por artículo: un mismo artículo puede abastecer partidas
  # de distintos pedidos, y entre ellas manda la fecha de entrega más próxima.
  # ("pedido_articulo" restringiría la tela al pedido para el que se tejió.)
  agrupar_tela_por = "articulo",

  # --- Presupuesto común para ambos algoritmos --------------------------------
  evaluaciones = 6000L,           # evaluaciones de la función objetivo por corrida
  corridas     = 20L,             # corridas independientes por instancia
  paso_convergencia = 100L,       # cada cuántas evaluaciones se registra el mejor F

  # --- Algoritmo genético (Campo et al., 2022) --------------------------------
  # Valores elegidos con la calibración 2^3 (07_calibracion.R); ejecutar_todo.R
  # los vuelve a tomar de resultados/calibracion.rds si existe.
  ag = list(poblacion = 50L, elitismo = 0.10, p_cruce = 0.90, p_mutacion = 0.30),

  # --- Colonia de hormigas (Fidanova et al., 2025) ----------------------------
  # rho = fracción de feromona que se conserva en cada iteración
  aco = list(hormigas = 20L, rho = 0.90, tau0 = 0.50, a = 1, b = 2),

  # --- RNF03 -------------------------------------------------------------------
  tiempo_maximo_segundos = 60
)

# -----------------------------------------------------------------------------
# Parque de máquinas de tintorería (barcas) registrado por el planificador:
# dos de 600 kg, dos de 400 kg, dos de 300 kg y tres de 200 kg. Todas tiñen
# color (tipo "C"); el tipo "B" (blanco) queda disponible como parámetro.
# Objetivo semanal: 16 partidas (2.2 partidas por día x 7 días, redondeado).
# -----------------------------------------------------------------------------
MAQUINAS_BASE <- data.frame(
  maquina   = c("M600-1", "M600-2", "M400-1", "M400-2", "M300-1", "M300-2",
                "M200-1", "M200-2", "M200-3"),
  capacidad = c(600, 600, 400, 400, 300, 300, 200, 200, 200),
  tipo      = "C",
  objetivo  = 16L,
  stringsAsFactors = FALSE
)
MAQUINAS_BASE$barca <- paste(MAQUINAS_BASE$capacidad, MAQUINAS_BASE$tipo)

# Objetivo semanal cuando la máquina pierde un día (16 -> 14)
OBJETIVO_CON_UN_DIA_INDISPONIBLE <- 14L

# Grupos de capacidad disponibles (menor capacidad que admite el peso: RD-02, RD-11)
GRUPOS_CAPACIDAD <- c(200, 300, 400, 600)

# Tonalidades ordenadas de la más clara a la más oscura. Pasar de una tonalidad a
# otra más clara exige lavar la máquina (RD-06).
TONALIDADES <- c("Blanco", "Claro", "Medio", "Oscuro")
