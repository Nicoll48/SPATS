# =============================================================================
# 09_caracterizacion.R
# Dimensión del problema en una semana: bolsas donde las partidas compiten por
# la tela y tamaño aproximado del espacio de búsqueda.
# =============================================================================

caracterizar_instancia <- function(I) {
  total <- I$stock0 + rowSums(I$llegadas)
  bol <- lapply(seq_len(I$B), function(b) {
    rs <- which(I$req_bolsa == b)
    if (!length(rs)) return(NULL)
    pp <- I$req_partida[rs]
    data.frame(bolsa = I$bolsas[b], partidas = length(unique(pp)),
               pedidos = length(unique(I$partidas$pedido[pp])),
               kg_disponibles = total[b], kg_requeridos = sum(I$req_kg[rs]),
               detalle_barcas = paste(names(table(I$partidas$barca[pp])),
                                      table(I$partidas$barca[pp]), sep = ": ", collapse = "; "))
  })
  bol <- do.call(rbind, bol)
  bol$compiten <- bol$kg_requeridos > bol$kg_disponibles + 1e-9 & bol$partidas > 1
  # espacio de búsqueda (log10): formas de elegir qué partidas reciben la tela
  # en cada bolsa con competencia (combinaciones de n partidas en k, con k las
  # que alcanza la tela según el peso promedio), por máquinas de cada partida
  bol$alcanzan <- floor(bol$partidas * pmin(1, bol$kg_disponibles / bol$kg_requeridos))
  log_conf <- sum(lchoose(bol$partidas[bol$compiten], bol$alcanzan[bol$compiten])) / log(10)
  log_maq <- sum(log10(I$n_compat))
  list(bolsas = bol,
       resumen = data.frame(instancia = I$nombre, candidatas = I$P,
                            bolsas_con_partidas = nrow(bol),
                            bolsas_con_competencia = sum(bol$compiten),
                            log10_conformacion = log_conf, log10_maquinas = log_maq,
                            log10_total = log_conf + log_maq))
}
