/**
 * Fechas del negocio en hora local. Los días viajan como texto ISO (YYYY-MM-DD) y se interpretan sin
 * pasar por UTC, para que un lunes nunca se convierta en domingo por la zona horaria.
 */

const LOCAL = 'es-PE'

export function desdeIso(iso: string): Date {
  const [anio, mes, dia] = iso.slice(0, 10).split('-').map(Number)
  return new Date(anio, mes - 1, dia)
}

export function aIso(fecha: Date): string {
  const mes = String(fecha.getMonth() + 1).padStart(2, '0')
  const dia = String(fecha.getDate()).padStart(2, '0')
  return `${fecha.getFullYear()}-${mes}-${dia}`
}

export function sumarDias(iso: string, dias: number): string {
  const fecha = desdeIso(iso)
  fecha.setDate(fecha.getDate() + dias)
  return aIso(fecha)
}

/** Lunes de la semana de planificación (lunes a domingo) que contiene la fecha. */
export function lunesDe(fecha: Date): string {
  const copia = new Date(fecha.getFullYear(), fecha.getMonth(), fecha.getDate())
  const desdeLunes = (copia.getDay() + 6) % 7
  copia.setDate(copia.getDate() - desdeLunes)
  return aIso(copia)
}

export function etiquetaSemana(lunes: string): string {
  const inicio = desdeIso(lunes)
  const fin = desdeIso(sumarDias(lunes, 6))
  const mismoMes = inicio.getMonth() === fin.getMonth()
  const diaInicio = inicio.toLocaleDateString(LOCAL, mismoMes ? { day: 'numeric' } : { day: 'numeric', month: 'short' })
  const diaFin = fin.toLocaleDateString(LOCAL, { day: 'numeric', month: 'short', year: 'numeric' })
  return `${diaInicio}–${diaFin}`.replace(/\./g, '')
}

export function nombreDia(iso: string, largo = false): string {
  const texto = desdeIso(iso).toLocaleDateString(LOCAL, { weekday: largo ? 'long' : 'short' }).replace('.', '')
  return texto.charAt(0).toUpperCase() + texto.slice(1)
}

export function fechaCorta(iso: string | null | undefined): string {
  if (!iso) return '—'
  return desdeIso(iso).toLocaleDateString(LOCAL, { day: 'numeric', month: 'short', year: 'numeric' }).replace(/\./g, '')
}

export function diaMes(iso: string): string {
  return desdeIso(iso).toLocaleDateString(LOCAL, { day: 'numeric', month: 'short' }).replace(/\./g, '')
}

/** Fecha y hora ISO local (YYYY-MM-DDTHH:mm[:ss]) para mostrar. */
export function fechaHora(iso: string | null | undefined): string {
  if (!iso) return '—'
  const [dia, hora = ''] = iso.split('T')
  return `${fechaCorta(dia)}, ${hora.slice(0, 5)}`
}

/** Valor para un <input type="datetime-local"> con la hora actual. */
export function ahoraLocal(): string {
  const ahora = new Date()
  return `${aIso(ahora)}T${String(ahora.getHours()).padStart(2, '0')}:${String(ahora.getMinutes()).padStart(2, '0')}`
}
