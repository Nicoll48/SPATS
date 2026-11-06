# =============================================================================
# 08_exportar_java.R
# Exporta cada instancia en archivos CSV para la implementación en Java
# (carpeta java/), de modo que ambas implementaciones lean exactamente los
# mismos datos. Índices en base 1, como en R.
# =============================================================================

exportar_instancia_csv <- function(I, dir) {
  dir.create(dir, showWarnings = FALSE, recursive = TRUE)
  h <- I$habil * 1L; colnames(h) <- paste0("h", seq_len(I$ND))
  write.csv(data.frame(maquina = I$maquinas$maquina, barca = I$maquinas$barca,
                       objetivo = I$O, minimo = I$L, maximo = I$U, h),
            file.path(dir, "maquinas.csv"), row.names = FALSE, quote = FALSE)
  write.csv(data.frame(partida = I$partidas$partida, fecha = I$fecha, tono = I$tono,
                       registro = I$registro,
                       compat = vapply(I$compat, paste, "", collapse = ";")),
            file.path(dir, "partidas.csv"), row.names = FALSE, quote = FALSE)
  write.csv(data.frame(partida = I$req_partida, bolsa = I$req_bolsa, kg = I$req_kg),
            file.path(dir, "requerimientos.csv"), row.names = FALSE, quote = FALSE)
  ll <- I$llegadas; colnames(ll) <- paste0("d", seq_len(I$ND))
  write.csv(data.frame(bolsa = gsub(",", " ", I$bolsas), stock0 = I$stock0, ll),
            file.path(dir, "bolsas.csv"), row.names = FALSE, quote = FALSE)
}

exportar_todas_csv <- function(inst, dir = "instancias/csv") {
  for (n in names(inst)) exportar_instancia_csv(inst[[n]], file.path(dir, n))
  writeLines(names(inst), file.path(dir, "lista.txt"))
}
