function escaparCampo(valor: string): string {
  if (/[",\n]/.test(valor)) {
    return `"${valor.replace(/"/g, '""')}"`
  }
  return valor
}

/**
 * Arma un CSV a partir de filas de texto y dispara la descarga en el
 * navegador. Es una operación puramente de cliente — sirve para el botón
 * Exportar la planificación semanal mientras no haya un
 * backend real generando el archivo del lado del servidor.
 */
export function descargarCSV(nombreArchivo: string, filas: string[][]) {
  const contenido = filas.map((fila) => fila.map(escaparCampo).join(',')).join('\n')
  const blob = new Blob([`\ufeff${contenido}`], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const enlace = document.createElement('a')
  enlace.href = url
  enlace.download = nombreArchivo
  document.body.appendChild(enlace)
  enlace.click()
  document.body.removeChild(enlace)
  URL.revokeObjectURL(url)
}
