import type {
  ClasificacionColor, ClasificacionResultado, EstadoMaquina, EstadoPartida, EstadoPlanificacion, PerfilUsuario, TipoDato,
  TipoIncidencia, Tonalidad,
} from '@/api/tipos'
import type { StatusTone } from '@/components/ui/StatusPill'

/** Textos visibles de los códigos que guarda la base (la base guarda el código; la interfaz muestra la etiqueta). */

export const PERFIL: Record<PerfilUsuario, string> = { PLANIFICADOR: 'Planificador', CONSULTA: 'Consulta' }

export const CLASIFICACION_COLOR: Record<ClasificacionColor, string> = { C: 'Color', B: 'Blanco' }

export const TONALIDAD: Record<Tonalidad, string> = { BLANCO: 'Blanco', CLARO: 'Claro', MEDIO: 'Medio', OSCURO: 'Oscuro' }

/** Orden de las tonalidades, de la más clara a la más oscura. */
export const ORDEN_TONALIDAD: Tonalidad[] = ['BLANCO', 'CLARO', 'MEDIO', 'OSCURO']

/** D-51: tonalidades que acepta una máquina, en texto: «Todas» o «Blanco, Claro». */
export function textoAcepta(tonalidades: Tonalidad[] | null | undefined): string {
  if (!tonalidades || tonalidades.length === 0) return '—'
  if (tonalidades.length === ORDEN_TONALIDAD.length) return 'Todas'
  return ORDEN_TONALIDAD.filter((t) => tonalidades.includes(t)).map((t) => TONALIDAD[t]).join(', ')
}

/** Muestra de la tonalidad: de más clara a más oscura (el lavado se da al pasar a una más clara). */
export const MUESTRA_TONALIDAD: Record<Tonalidad, string> = {
  BLANCO: '#ecebe4',
  CLARO: '#e9c46a',
  MEDIO: '#c2703d',
  OSCURO: '#2b2d42',
}

export const CLASIFICACION_RESULTADO: Record<ClasificacionResultado, string> = {
  ASIGNADA: 'Asignada',
  SOBRANTE: 'Sobrante',
  BLOQUEADA: 'Bloqueada',
}

export const ESTADO_PARTIDA: Record<EstadoPartida, string> = { PENDIENTE: 'Pendiente', ARRASTRADA: 'Arrastrada', COMPLETADA: 'Completada' }
export const TONO_PARTIDA: Record<EstadoPartida, StatusTone> = { PENDIENTE: 'neutral', ARRASTRADA: 'warning', COMPLETADA: 'success' }

export const ESTADO_MAQUINA: Record<EstadoMaquina, string> = {
  OPERATIVA: 'Operativa',
  INDISPONIBLE: 'Indisponible',
  EN_MANTENIMIENTO: 'En mantenimiento',
}
export const TONO_MAQUINA: Record<EstadoMaquina, StatusTone> = { OPERATIVA: 'success', EN_MANTENIMIENTO: 'warning', INDISPONIBLE: 'danger' }

export const ESTADO_PLANIFICACION: Record<EstadoPlanificacion, string> = {
  ABIERTA: 'Vigente',
  CERRADA: 'Vigente · cerrada',
  REEMPLAZADA: 'Reemplazada',
  INVALIDADA: 'Sin vigencia (cambiaron sus datos)',
  PROPUESTA: 'Propuesta sin confirmar',
  CANDIDATA: 'Otra versión (no vigente)',
}
export const TONO_PLANIFICACION: Record<EstadoPlanificacion, StatusTone> = {
  ABIERTA: 'success',
  CERRADA: 'neutral',
  REEMPLAZADA: 'neutral',
  INVALIDADA: 'warning',
  PROPUESTA: 'warning',
  CANDIDATA: 'neutral',
}

export const TIPO_DATO: Record<TipoDato, string> = {
  PEDIDOS: 'Pedidos y artículos',
  PARTIDAS: 'Partidas',
  PROYECCION_TEJEDURIA: 'Proyección de tejeduría',
}

export const TIPO_INCIDENCIA: Record<TipoIncidencia, { titulo: string; entidad: string; descripcion: string }> = {
  INCIDENCIA_MAQUINA: {
    titulo: 'Incidencia de máquina',
    entidad: 'Máquina',
    descripcion: 'Una máquina deja de estar disponible. Se indica cuándo estará disponible de nuevo.',
  },
  PROBLEMA_COMPLEMENTO: {
    titulo: 'Problema de complemento',
    entidad: 'Partida',
    descripcion: 'Falta el complemento de una partida. Se indica desde cuándo estará disponible.',
  },
  CAMBIO_PRIORIDAD: {
    titulo: 'Cambio de prioridad',
    entidad: 'Pedido',
    descripcion: 'Cambia la fecha de entrega de un pedido. Se actualiza la fecha del pedido.',
  },
}

export function kg(valor: number | null | undefined): string {
  if (valor === null || valor === undefined) return '—'
  return `${valor.toLocaleString('es-PE', { maximumFractionDigits: 3 })} kg`
}

export function numero(valor: number | null | undefined, decimales = 2): string {
  if (valor === null || valor === undefined) return '—'
  return valor.toLocaleString('es-PE', { maximumFractionDigits: decimales })
}
