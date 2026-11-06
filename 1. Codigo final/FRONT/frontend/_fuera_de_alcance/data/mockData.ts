import type { LucideIcon } from 'lucide-react'
import { Ban, Droplets, Gauge, ListChecks, PackageMinus } from 'lucide-react'
import type { StatusTone } from '@/components/ui/StatusPill'
import type { PerfilUsuario, Usuario } from '@/auth/auth-context'

/**
 * Datos de ejemplo para toda la plantilla, organizados en el mismo orden
 * que las 8 pantallas del documento de referencia. Todo este archivo es
 * reemplazable: es el único lugar a tocar (o borrar) cuando el sistema
 * tenga un backend real — ver "Cambiar los datos" en SKILL.md.
 */

// ---------------------------------------------------------------------------
// Compartido: días de la semana
// ---------------------------------------------------------------------------

export type DiaKey = 'lun' | 'mar' | 'mie' | 'jue' | 'vie' | 'sab' | 'dom'

export const DIAS_SEMANA: { key: DiaKey; corto: string; largo: string }[] = [
  { key: 'lun', corto: 'Lun', largo: 'Lunes' },
  { key: 'mar', corto: 'Mar', largo: 'Martes' },
  { key: 'mie', corto: 'Mié', largo: 'Miércoles' },
  { key: 'jue', corto: 'Jue', largo: 'Jueves' },
  { key: 'vie', corto: 'Vie', largo: 'Viernes' },
  { key: 'sab', corto: 'Sáb', largo: 'Sábado' },
  { key: 'dom', corto: 'Dom', largo: 'Domingo' },
]

export function formatHora(hora: number): string {
  const h = Math.floor(hora)
  const m = Math.round((hora - h) * 60)
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`
}

// ---------------------------------------------------------------------------
// 0. Cuentas y sesión
// ---------------------------------------------------------------------------

/**
 * Cuentas de ejemplo mientras no hay backend (RF01). En el sistema real la
 * validación ocurre en la API — acá solo sirven para poder recorrer el panel
 * con cada uno de los dos perfiles.
 *
 * El perfil Planificador es una sola persona en la práctica; el de Consulta
 * admite tantas cuentas como haga falta, todas con la misma visibilidad (RF04).
 */
export interface Cuenta extends Usuario {
  contrasena: string
}

export const cuentas: Cuenta[] = [
  { id: 'u-1', usuario: 'carlos', contrasena: 'planificar', nombre: 'Carlos T.', perfil: 'Planificador' },
  { id: 'u-2', usuario: 'consulta', contrasena: 'consulta', nombre: 'Gerencia', perfil: 'Consulta' },
  { id: 'u-3', usuario: 'laboratorio', contrasena: 'consulta', nombre: 'Laboratorio', perfil: 'Consulta' },
]

/** Etiqueta de ayuda del login: qué puede hacer cada perfil. */
export const RESUMEN_PERFILES: Record<PerfilUsuario, string> = {
  Planificador: 'Carga datos, configura, planifica, replanifica y aplica escenarios.',
  Consulta: 'Solo visualización de todas las pantallas.',
}

// ---------------------------------------------------------------------------
// 4. Máquinas y parámetros
// ---------------------------------------------------------------------------

export type EstadoMaquina = 'Operativa' | 'Indisponible' | 'Mantenimiento'
export type ClasificacionColor = 'Color' | 'Blanco'
export type RestriccionTonalidad = 'Sin restricción' | 'Solo claros' | 'Solo oscuros'

export interface Maquina {
  id: string
  codigo: string
  capacidadKg: number
  objetivoSemanalPartidas: number
  clasificacionColor: ClasificacionColor
  /** TODO: confirmar con Carlos si sigue vigente junto a clasificacionColor (R3.2). */
  restriccionTonalidad?: RestriccionTonalidad
  tiempoProcesamientoHoras: number
  estado: EstadoMaquina
}

export const maquinas: Maquina[] = [
  { id: 'M1', codigo: 'M1', capacidadKg: 600, objetivoSemanalPartidas: 16, clasificacionColor: 'Color', tiempoProcesamientoHoras: 10.5, estado: 'Operativa' },
  { id: 'M2', codigo: 'M2', capacidadKg: 600, objetivoSemanalPartidas: 16, clasificacionColor: 'Color', tiempoProcesamientoHoras: 11, estado: 'Operativa' },
  { id: 'M3', codigo: 'M3', capacidadKg: 400, objetivoSemanalPartidas: 16, clasificacionColor: 'Color', tiempoProcesamientoHoras: 9.5, estado: 'Operativa' },
  { id: 'M4', codigo: 'M4', capacidadKg: 400, objetivoSemanalPartidas: 16, clasificacionColor: 'Color', tiempoProcesamientoHoras: 10, estado: 'Mantenimiento' },
  { id: 'M5', codigo: 'M5', capacidadKg: 300, objetivoSemanalPartidas: 16, clasificacionColor: 'Blanco', tiempoProcesamientoHoras: 8.5, estado: 'Operativa' },
  { id: 'M6', codigo: 'M6', capacidadKg: 300, objetivoSemanalPartidas: 16, clasificacionColor: 'Color', tiempoProcesamientoHoras: 9, estado: 'Operativa' },
  { id: 'M7', codigo: 'M7', capacidadKg: 200, objetivoSemanalPartidas: 16, clasificacionColor: 'Color', tiempoProcesamientoHoras: 8, estado: 'Operativa' },
  { id: 'M8', codigo: 'M8', capacidadKg: 200, objetivoSemanalPartidas: 16, clasificacionColor: 'Color', tiempoProcesamientoHoras: 8.5, estado: 'Operativa' },
  { id: 'M9', codigo: 'M9', capacidadKg: 200, objetivoSemanalPartidas: 16, clasificacionColor: 'Blanco', tiempoProcesamientoHoras: 8, estado: 'Indisponible' },
]

export interface IndisponibilidadMaquina {
  id: string
  maquinaId: string
  desde: string
  hasta: string
  motivo: string
}

export const indisponibilidades: IndisponibilidadMaquina[] = [
  { id: 'ind-1', maquinaId: 'M4', desde: '9 jun 2026, 06:00', hasta: '11 jun 2026, 18:00', motivo: 'Mantenimiento preventivo programado' },
  { id: 'ind-2', maquinaId: 'M9', desde: '8 jun 2026, 00:00', hasta: '14 jun 2026, 23:59', motivo: 'Falla en sistema de calentamiento — a la espera de repuesto' },
]

export interface ParametrosOperativos {
  tiempoLavadoHoras: number
  horizonteCongeladoHoras: number
}

export const parametrosOperativos: ParametrosOperativos = {
  tiempoLavadoHoras: 2.5,
  horizonteCongeladoHoras: 2,
}

// ---------------------------------------------------------------------------
// Colores de tintura (compartido por calendario, proyección y reportes)
// ---------------------------------------------------------------------------

export const COLOR_SWATCH: Record<string, string> = {
  'Azul Marino': '#1e3a5f',
  Negro: '#14151a',
  'Blanco Óptico': '#e8e6df',
  'Rojo Carmín': '#a3123a',
  Beige: '#d8c4a0',
  'Verde Botella': '#1f4d3d',
  'Gris Perla': '#9aa0a6',
  'Amarillo Mostaza': '#c9962c',
  Turquesa: '#1b8a94',
  Vino: '#5c1a2e',
}

// ---------------------------------------------------------------------------
// 1. Planificación semanal
// ---------------------------------------------------------------------------

export type TipoBloque = 'partida' | 'lavado' | 'indisponible'

interface BloqueBase {
  id: string
  horaInicio: number
  horaFin: number
}

export interface BloquePartida extends BloqueBase {
  tipo: 'partida'
  codigo: string
  color: string
  kg: number
  arrastrada?: boolean
}

export interface BloqueLavado extends BloqueBase {
  tipo: 'lavado'
}

export interface BloqueIndisponible extends BloqueBase {
  tipo: 'indisponible'
  motivo: string
}

export type BloqueCalendario = BloquePartida | BloqueLavado | BloqueIndisponible

export interface FilaMaquinaCalendario {
  maquinaId: string
  codigo: string
  capacidadKg: number
  bloquesPorDia: Partial<Record<DiaKey, BloqueCalendario[]>>
}

export interface SummaryStat {
  id: string
  label: string
  value: string
  icon: LucideIcon
  tone?: 'success' | 'warning' | 'danger'
}

export interface EquilibrioFila {
  maquinaId: string
  codigo: string
  objetivo: number
  planificado: number
  diferencia: number
  estadoSemanal: StatusTone
  estadoDiario: Partial<Record<DiaKey, StatusTone>>
}

export interface PartidaSobrante {
  id: string
  codigo: string
  color: string
  clasificacion: 'Color' | 'Blanco' | 'Medio'
  kg: number
  maquinaCompatible: string
  disponibleDesde: DiaKey
  motivo: string
}

export type MotivoBloqueo = 'Composición incompleta' | 'Sin proyección en el horizonte' | 'Sin máquina compatible disponible'
// TODO: verificar estos tres motivos contra R1.1 antes de modificar el catálogo.

export interface PartidaBloqueada {
  id: string
  codigo: string
  color: string
  kg: number
  motivo: MotivoBloqueo
  telaPrincipal: string
  complemento: string
  cobertura: string
}

export type TipoAlertaPlanificacion = 'Sobrecarga' | 'Baja carga' | 'Falta de abastecimiento' | 'Concentración de carga'

export interface AlertaPlanificacion {
  id: string
  tipo: TipoAlertaPlanificacion
  severidad: StatusTone
  maquina?: string
  referencia: string
  causaRaiz: string
  desviacion: string
}

export type OrigenCola = 'En ejecución' | 'Sobrante prioritario'

export interface ItemCola {
  id: string
  codigo: string
  color: string
  kg: number
  origen: OrigenCola
}

export interface FilaColaMaquina {
  maquinaId: string
  codigo: string
  items: ItemCola[]
}

export interface DatosSemana {
  id: string
  etiqueta: string
  tienePlanPropio: boolean
  vigente: boolean
  resumen: SummaryStat[]
  filasCalendario: FilaMaquinaCalendario[]
  equilibrio: EquilibrioFila[]
  sobrantes: PartidaSobrante[]
  bloqueadas: PartidaBloqueada[]
  alertas: AlertaPlanificacion[]
  cola: FilaColaMaquina[]
}

const resumenSemanaConPlan: SummaryStat[] = [
  { id: 'planificadas', label: 'Partidas asignadas', value: '149', icon: ListChecks },
  { id: 'sobrantes', label: 'Partidas sobrantes', value: '5', icon: PackageMinus, tone: 'warning' },
  { id: 'bloqueadas', label: 'Partidas bloqueadas', value: '2', icon: Ban, tone: 'danger' },
  { id: 'equilibradas', label: 'Máquinas equilibradas', value: '9/9', icon: Gauge, tone: 'success' },
  { id: 'lavados', label: 'Lavados', value: '14', icon: Droplets },
]

const resumenSemanaSinPlan: SummaryStat[] = [
  { id: 'planificadas', label: 'Partidas asignadas', value: '—', icon: ListChecks },
  { id: 'sobrantes', label: 'Partidas sobrantes', value: '—', icon: PackageMinus },
  { id: 'bloqueadas', label: 'Partidas bloqueadas', value: '—', icon: Ban },
  { id: 'equilibradas', label: 'Máquinas equilibradas', value: '—', icon: Gauge },
  { id: 'lavados', label: 'Lavados', value: '—', icon: Droplets },
]

/** Grilla de calendario de ejemplo, reutilizada por la semana anterior y la semana actual (ambas con plan propio). */
const filasCalendarioEjemplo: FilaMaquinaCalendario[] = [
  {
    maquinaId: 'M1',
    codigo: 'M1',
    capacidadKg: 600,
    bloquesPorDia: {
      lun: [{ id: 'b1', tipo: 'partida', horaInicio: 6, horaFin: 14, codigo: 'P-0341', color: 'Azul Marino', kg: 580 }],
      mar: [{ id: 'b2', tipo: 'partida', horaInicio: 6, horaFin: 13.5, codigo: 'P-0342', color: 'Azul Marino', kg: 560, arrastrada: true }],
      mie: [
        { id: 'b3', tipo: 'lavado', horaInicio: 6, horaFin: 8.5 },
        { id: 'b4', tipo: 'partida', horaInicio: 8.5, horaFin: 17, codigo: 'P-0355', color: 'Blanco Óptico', kg: 590 },
      ],
      jue: [{ id: 'b5', tipo: 'partida', horaInicio: 6, horaFin: 15, codigo: 'P-0368', color: 'Gris Perla', kg: 600 }],
      vie: [{ id: 'b6', tipo: 'partida', horaInicio: 6, horaFin: 14.5, codigo: 'P-0379', color: 'Beige', kg: 575 }],
    },
  },
  {
    maquinaId: 'M2',
    codigo: 'M2',
    capacidadKg: 600,
    bloquesPorDia: {
      lun: [{ id: 'b7', tipo: 'partida', horaInicio: 6, horaFin: 15, codigo: 'P-0344', color: 'Negro', kg: 600 }],
      mar: [{ id: 'b8', tipo: 'partida', horaInicio: 6, horaFin: 14, codigo: 'P-0345', color: 'Negro', kg: 590 }],
      mie: [{ id: 'b9', tipo: 'partida', horaInicio: 6, horaFin: 13, codigo: 'P-0350', color: 'Vino', kg: 540 }],
      jue: [
        { id: 'b10', tipo: 'lavado', horaInicio: 6, horaFin: 8.5 },
        { id: 'b11', tipo: 'partida', horaInicio: 8.5, horaFin: 16, codigo: 'P-0362', color: 'Verde Botella', kg: 555 },
      ],
      vie: [{ id: 'b12', tipo: 'indisponible', horaInicio: 6, horaFin: 16, motivo: 'Ajuste mecánico no programado' }],
    },
  },
  {
    maquinaId: 'M3',
    codigo: 'M3',
    capacidadKg: 400,
    bloquesPorDia: {
      lun: [{ id: 'b13', tipo: 'partida', horaInicio: 6, horaFin: 12, codigo: 'P-0347', color: 'Turquesa', kg: 380 }],
      mar: [{ id: 'b14', tipo: 'partida', horaInicio: 6, horaFin: 11.5, codigo: 'P-0348', color: 'Turquesa', kg: 360, arrastrada: true }],
      mie: [{ id: 'b15', tipo: 'partida', horaInicio: 6, horaFin: 12.5, codigo: 'P-0356', color: 'Amarillo Mostaza', kg: 395 }],
      jue: [{ id: 'b16', tipo: 'partida', horaInicio: 6, horaFin: 12, codigo: 'P-0369', color: 'Amarillo Mostaza', kg: 370 }],
      vie: [{ id: 'b17', tipo: 'partida', horaInicio: 6, horaFin: 11, codigo: 'P-0380', color: 'Rojo Carmín', kg: 350 }],
    },
  },
  {
    maquinaId: 'M4',
    codigo: 'M4',
    capacidadKg: 400,
    bloquesPorDia: {
      lun: [{ id: 'b18', tipo: 'indisponible', horaInicio: 0, horaFin: 24, motivo: 'Mantenimiento preventivo programado' }],
      mar: [{ id: 'b19', tipo: 'indisponible', horaInicio: 0, horaFin: 24, motivo: 'Mantenimiento preventivo programado' }],
      mie: [{ id: 'b20', tipo: 'partida', horaInicio: 8.5, horaFin: 14, codigo: 'P-0357', color: 'Beige', kg: 340 }],
      jue: [{ id: 'b21', tipo: 'partida', horaInicio: 6, horaFin: 11.5, codigo: 'P-0370', color: 'Beige', kg: 355 }],
      vie: [{ id: 'b22', tipo: 'partida', horaInicio: 6, horaFin: 11, codigo: 'P-0381', color: 'Gris Perla', kg: 330 }],
    },
  },
  {
    maquinaId: 'M5',
    codigo: 'M5',
    capacidadKg: 300,
    bloquesPorDia: {
      lun: [{ id: 'b23', tipo: 'partida', horaInicio: 6, horaFin: 10.5, codigo: 'P-0349', color: 'Blanco Óptico', kg: 280 }],
      mar: [{ id: 'b24', tipo: 'partida', horaInicio: 6, horaFin: 10, codigo: 'P-0351', color: 'Beige', kg: 265 }],
      mie: [{ id: 'b25', tipo: 'partida', horaInicio: 6, horaFin: 10.5, codigo: 'P-0358', color: 'Blanco Óptico', kg: 290 }],
      jue: [{ id: 'b26', tipo: 'partida', horaInicio: 6, horaFin: 10, codigo: 'P-0371', color: 'Amarillo Mostaza', kg: 275 }],
      vie: [{ id: 'b27', tipo: 'partida', horaInicio: 6, horaFin: 9.5, codigo: 'P-0382', color: 'Beige', kg: 260 }],
    },
  },
  {
    maquinaId: 'M6',
    codigo: 'M6',
    capacidadKg: 300,
    bloquesPorDia: {
      lun: [{ id: 'b28', tipo: 'partida', horaInicio: 6, horaFin: 10.5, codigo: 'P-0352', color: 'Rojo Carmín', kg: 290 }],
      mar: [
        { id: 'b29', tipo: 'lavado', horaInicio: 6, horaFin: 8.5 },
        { id: 'b30', tipo: 'partida', horaInicio: 8.5, horaFin: 13, codigo: 'P-0359', color: 'Negro', kg: 295 },
      ],
      mie: [{ id: 'b31', tipo: 'partida', horaInicio: 6, horaFin: 10, codigo: 'P-0360', color: 'Negro', kg: 285 }],
      jue: [{ id: 'b32', tipo: 'partida', horaInicio: 6, horaFin: 10.5, codigo: 'P-0372', color: 'Vino', kg: 300 }],
      vie: [],
    },
  },
  {
    maquinaId: 'M7',
    codigo: 'M7',
    capacidadKg: 200,
    bloquesPorDia: {
      lun: [{ id: 'b33', tipo: 'partida', horaInicio: 6, horaFin: 9, codigo: 'P-0353', color: 'Turquesa', kg: 190 }],
      mar: [{ id: 'b34', tipo: 'partida', horaInicio: 6, horaFin: 9, codigo: 'P-0354', color: 'Turquesa', kg: 185 }],
      mie: [{ id: 'b35', tipo: 'partida', horaInicio: 6, horaFin: 8.5, codigo: 'P-0361', color: 'Verde Botella', kg: 175 }],
      jue: [{ id: 'b36', tipo: 'partida', horaInicio: 6, horaFin: 9, codigo: 'P-0373', color: 'Verde Botella', kg: 195 }],
      vie: [{ id: 'b37', tipo: 'partida', horaInicio: 6, horaFin: 8.5, codigo: 'P-0383', color: 'Gris Perla', kg: 180 }],
    },
  },
  {
    maquinaId: 'M8',
    codigo: 'M8',
    capacidadKg: 200,
    bloquesPorDia: {
      lun: [{ id: 'b38', tipo: 'partida', horaInicio: 6, horaFin: 9, codigo: 'P-0363', color: 'Negro', kg: 198 }],
      mar: [{ id: 'b39', tipo: 'partida', horaInicio: 6, horaFin: 8.5, codigo: 'P-0364', color: 'Vino', kg: 180 }],
      mie: [{ id: 'b40', tipo: 'partida', horaInicio: 6, horaFin: 9, codigo: 'P-0365', color: 'Negro', kg: 195, arrastrada: true }],
      jue: [],
      vie: [{ id: 'b41', tipo: 'partida', horaInicio: 6, horaFin: 8.5, codigo: 'P-0384', color: 'Vino', kg: 185 }],
    },
  },
  {
    maquinaId: 'M9',
    codigo: 'M9',
    capacidadKg: 200,
    bloquesPorDia: {
      lun: [{ id: 'b42', tipo: 'indisponible', horaInicio: 0, horaFin: 24, motivo: 'Falla en sistema de calentamiento' }],
      mar: [{ id: 'b43', tipo: 'indisponible', horaInicio: 0, horaFin: 24, motivo: 'Falla en sistema de calentamiento' }],
      mie: [{ id: 'b44', tipo: 'indisponible', horaInicio: 0, horaFin: 24, motivo: 'Falla en sistema de calentamiento' }],
      jue: [{ id: 'b45', tipo: 'indisponible', horaInicio: 0, horaFin: 24, motivo: 'Falla en sistema de calentamiento' }],
      vie: [{ id: 'b46', tipo: 'indisponible', horaInicio: 0, horaFin: 24, motivo: 'Falla en sistema de calentamiento' }],
    },
  },
]

const equilibrioEjemplo: EquilibrioFila[] = [
  { maquinaId: 'M1', codigo: 'M1', objetivo: 12, planificado: 12, diferencia: 0, estadoSemanal: 'success', estadoDiario: { lun: 'success', mar: 'success', mie: 'success', jue: 'success', vie: 'success' } },
  { maquinaId: 'M2', codigo: 'M2', objetivo: 12, planificado: 9, diferencia: -3, estadoSemanal: 'warning', estadoDiario: { lun: 'success', mar: 'success', mie: 'success', jue: 'success', vie: 'danger' } },
  { maquinaId: 'M3', codigo: 'M3', objetivo: 15, planificado: 15, diferencia: 0, estadoSemanal: 'success', estadoDiario: { lun: 'success', mar: 'success', mie: 'success', jue: 'success', vie: 'success' } },
  { maquinaId: 'M4', codigo: 'M4', objetivo: 15, planificado: 6, diferencia: -9, estadoSemanal: 'danger', estadoDiario: { lun: 'neutral', mar: 'neutral', mie: 'warning', jue: 'warning', vie: 'warning' } },
  { maquinaId: 'M5', codigo: 'M5', objetivo: 18, planificado: 17, diferencia: -1, estadoSemanal: 'success', estadoDiario: { lun: 'success', mar: 'success', mie: 'success', jue: 'success', vie: 'success' } },
  { maquinaId: 'M6', codigo: 'M6', objetivo: 18, planificado: 16, diferencia: -2, estadoSemanal: 'success', estadoDiario: { lun: 'success', mar: 'success', mie: 'success', jue: 'success', vie: 'warning' } },
  { maquinaId: 'M7', codigo: 'M7', objetivo: 20, planificado: 21, diferencia: 1, estadoSemanal: 'success', estadoDiario: { lun: 'success', mar: 'success', mie: 'success', jue: 'success', vie: 'success' } },
  { maquinaId: 'M8', codigo: 'M8', objetivo: 20, planificado: 24, diferencia: 4, estadoSemanal: 'warning', estadoDiario: { lun: 'warning', mar: 'success', mie: 'warning', jue: 'success', vie: 'warning' } },
  { maquinaId: 'M9', codigo: 'M9', objetivo: 20, planificado: 0, diferencia: -20, estadoSemanal: 'neutral', estadoDiario: { lun: 'neutral', mar: 'neutral', mie: 'neutral', jue: 'neutral', vie: 'neutral' } },
]

const sobrantesEjemplo: PartidaSobrante[] = [
  { id: 'sob-1', codigo: 'P-0390', color: 'Negro', clasificacion: 'Color', kg: 210, maquinaCompatible: 'M8', disponibleDesde: 'vie', motivo: 'Reserva disponible si la máquina consume antes su cola principal' },
  { id: 'sob-2', codigo: 'P-0391', color: 'Azul Marino', clasificacion: 'Color', kg: 540, maquinaCompatible: 'M1', disponibleDesde: 'sab', motivo: 'Reserva disponible si la máquina consume antes su cola principal' },
  { id: 'sob-3', codigo: 'P-0392', color: 'Turquesa', clasificacion: 'Medio', kg: 175, maquinaCompatible: 'M7', disponibleDesde: 'jue', motivo: 'Reserva compatible para mantener abastecida la máquina' },
  { id: 'sob-4', codigo: 'P-0393', color: 'Beige', clasificacion: 'Medio', kg: 320, maquinaCompatible: 'M4', disponibleDesde: 'sab', motivo: 'Reserva disponible cuando la máquina vuelva a estar operativa' },
  { id: 'sob-5', codigo: 'P-0394', color: 'Blanco Óptico', clasificacion: 'Blanco', kg: 188, maquinaCompatible: 'M7', disponibleDesde: 'dom', motivo: 'Reserva compatible para mantener abastecida la máquina' },
]

const bloqueadasEjemplo: PartidaBloqueada[] = [
  { id: 'blq-1', codigo: 'P-0401', color: 'Rojo Carmín', kg: 265, motivo: 'Composición incompleta', telaPrincipal: 'Jersey 30/1 · 240 kg', complemento: 'Rib 1x1 · 25 kg', cobertura: 'Principal lista; complemento proyectado solo al 40% (faltan 15 kg)' },
  { id: 'blq-2', codigo: 'P-0402', color: 'Amarillo Mostaza', kg: 410, motivo: 'Sin máquina compatible disponible', telaPrincipal: 'French terry · 370 kg', complemento: 'Rib 2x1 · 40 kg', cobertura: 'Composición completa; ninguna máquina compatible tiene capacidad' },
]

const alertasEjemplo: AlertaPlanificacion[] = [
  { id: 'alr-1', tipo: 'Baja carga', severidad: 'warning', maquina: 'M4', referencia: 'M4 al 40% de su objetivo semanal', causaRaiz: 'Mantenimiento preventivo programado reduce su disponibilidad a 3 de 5 días', desviacion: 'Déficit de 9 partidas (60% por debajo del objetivo)' },
  { id: 'alr-2', tipo: 'Sobrecarga', severidad: 'warning', maquina: 'M8', referencia: 'M8 al 120% de su objetivo semanal', causaRaiz: 'Concentración de partidas de tonalidad oscura sin alternativa compatible esta semana', desviacion: 'Sobregiro de 4 partidas (20% sobre el objetivo)' },
  { id: 'alr-3', tipo: 'Falta de abastecimiento', severidad: 'danger', maquina: 'M2', referencia: 'Viernes sin partidas asignadas', causaRaiz: 'Proyección de tejeduría no cubre color compatible con la restricción de M2 (solo oscuros) para ese día', desviacion: 'Faltan 3 partidas para llegar al objetivo semanal' },
  { id: 'alr-4', tipo: 'Concentración de carga', severidad: 'warning', referencia: 'Lunes y miércoles concentran 38% de las partidas de la semana', causaRaiz: 'Mayor volumen de proyección de tejeduría recibido a inicio de semana', desviacion: 'Exceso de 8 puntos porcentuales frente al máximo recomendado de 30%' },
]

const colaEjemplo: FilaColaMaquina[] = [
  { maquinaId: 'M1', codigo: 'M1', items: [
    { id: 'col-1', codigo: 'P-0379', color: 'Beige', kg: 575, origen: 'En ejecución' },
    { id: 'col-2', codigo: 'P-0405', color: 'Azul Marino', kg: 560, origen: 'Sobrante prioritario' },
  ] },
  { maquinaId: 'M2', codigo: 'M2', items: [{ id: 'col-3', codigo: 'P-0362', color: 'Verde Botella', kg: 555, origen: 'En ejecución' }] },
  { maquinaId: 'M3', codigo: 'M3', items: [
    { id: 'col-4', codigo: 'P-0380', color: 'Rojo Carmín', kg: 350, origen: 'En ejecución' },
    { id: 'col-5', codigo: 'P-0392', color: 'Turquesa', kg: 175, origen: 'Sobrante prioritario' },
  ] },
  { maquinaId: 'M5', codigo: 'M5', items: [{ id: 'col-6', codigo: 'P-0382', color: 'Beige', kg: 260, origen: 'En ejecución' }] },
  { maquinaId: 'M6', codigo: 'M6', items: [{ id: 'col-7', codigo: 'P-0372', color: 'Vino', kg: 300, origen: 'En ejecución' }] },
  { maquinaId: 'M7', codigo: 'M7', items: [
    { id: 'col-8', codigo: 'P-0383', color: 'Gris Perla', kg: 180, origen: 'En ejecución' },
    { id: 'col-9', codigo: 'P-0394', color: 'Verde Botella', kg: 188, origen: 'Sobrante prioritario' },
  ] },
]

/** Demostración balanceada: 16 partidas por máquina, distribuidas hasta el domingo. */
const cantidadesPorDia: Record<DiaKey, number> = { lun: 3, mar: 3, mie: 2, jue: 2, vie: 2, sab: 2, dom: 2 }
const coloresBalanceados = ['Azul Marino', 'Blanco Óptico', 'Gris Perla', 'Negro', 'Beige', 'Rojo Carmín', 'Turquesa', 'Verde Botella']
const offsetDatosReferencia = filasCalendarioEjemplo.length + equilibrioEjemplo.length
const filasCalendarioBalanceado: FilaMaquinaCalendario[] = maquinas.map((maquina, indiceMaquina) => {
  let correlativo = indiceMaquina * 16 + 1
  const bloquesPorDia: Partial<Record<DiaKey, BloqueCalendario[]>> = {}
  for (const dia of DIAS_SEMANA) {
    const cantidad = cantidadesPorDia[dia.key]
    bloquesPorDia[dia.key] = Array.from({ length: cantidad }, (_, indice) => {
      const duracion = 24 / cantidad
      const numero = correlativo++
      return { id: `bal-${maquina.id}-${dia.key}-${indice}`, tipo: 'partida' as const, horaInicio: indice * duracion, horaFin: (indice + 1) * duracion, codigo: `P-${String(5000 + offsetDatosReferencia + numero).padStart(4, '0')}`, color: coloresBalanceados[(numero + indiceMaquina) % coloresBalanceados.length], kg: Math.round(maquina.capacidadKg * (0.88 + (numero % 3) * 0.04)) }
    })
  }
  return { maquinaId: maquina.id, codigo: maquina.codigo, capacidadKg: maquina.capacidadKg, bloquesPorDia }
})

const equilibrioBalanceado: EquilibrioFila[] = maquinas.map((maquina) => ({
  maquinaId: maquina.id,
  codigo: maquina.codigo,
  objetivo: 16,
  planificado: 16,
  diferencia: 0,
  estadoSemanal: 'success',
  estadoDiario: Object.fromEntries(DIAS_SEMANA.map((dia) => [dia.key, 'success'])) as Partial<Record<DiaKey, StatusTone>>,
}))

export const semanas: DatosSemana[] = [
  {
    id: 'sem-1-jun',
    etiqueta: '1–7 jun 2026',
    tienePlanPropio: true,
    vigente: false,
    resumen: resumenSemanaConPlan,
    filasCalendario: filasCalendarioBalanceado,
    equilibrio: equilibrioBalanceado,
    sobrantes: sobrantesEjemplo,
    bloqueadas: bloqueadasEjemplo,
    alertas: alertasEjemplo,
    cola: [],
  },
  {
    id: 'sem-8-jun',
    etiqueta: '8–14 jun 2026',
    tienePlanPropio: true,
    vigente: true,
    resumen: resumenSemanaConPlan,
    filasCalendario: filasCalendarioBalanceado,
    equilibrio: equilibrioBalanceado,
    sobrantes: sobrantesEjemplo,
    bloqueadas: bloqueadasEjemplo,
    alertas: alertasEjemplo,
    cola: [],
  },
  {
    id: 'sem-15-jun',
    etiqueta: '15–21 jun 2026',
    tienePlanPropio: false,
    vigente: false,
    resumen: resumenSemanaSinPlan,
    filasCalendario: [],
    equilibrio: [],
    sobrantes: [],
    bloqueadas: [],
    alertas: [],
    cola: colaEjemplo,
  },
]

// ---------------------------------------------------------------------------
// 2. Datos semanales
// ---------------------------------------------------------------------------

export type EstadoCarga = 'Cargado' | 'Pendiente'

export interface TarjetaCarga {
  id: string
  titulo: string
  archivo?: string
  fechaCarga?: string
  registros?: number
  estado: EstadoCarga
}

export const tarjetasCarga: TarjetaCarga[] = [
  { id: 'carga-pedidos', titulo: 'Pedidos y artículos', archivo: 'pedidos_articulos_sem24.xlsx', fechaCarga: '13 jun 2026, 07:52', registros: 214, estado: 'Cargado' },
  { id: 'carga-partidas', titulo: 'Partidas', archivo: 'partidas_sem24.csv', fechaCarga: '13 jun 2026, 07:55', registros: 96, estado: 'Cargado' },
  { id: 'carga-proyeccion', titulo: 'Proyección de tejeduría', archivo: undefined, fechaCarga: undefined, registros: undefined, estado: 'Pendiente' },
]

export type EstadoRegistro = 'Pendiente' | 'Arrastrada' | 'Completada'

export interface PedidoRegistrado {
  id: string
  codigo: string
  cliente: string
  fechaEntrega: string
  estado: EstadoRegistro
  fechaCarga: string
}

export interface ArticuloRegistrado {
  id: string
  codigo: string
  pedidoId: string
  descripcion: string
  color: string
  kg: number
  fechaCarga: string
}

export interface PartidaRegistrada {
  id: string
  codigo: string
  pedidoId: string
  articuloId: string
  kg: number
  maquinaCompatible: string
  estado: EstadoRegistro
  fechaCarga: string
}

export interface ProyeccionRegistrada {
  id: string
  semana: string
  articulo: string
  color: string
  kgDisponibles: number
  fechaDisponibilidad: string
  fechaCarga: string
}

export const pedidosRegistradosIniciales: PedidoRegistrado[] = [
  { id: 'ped-24031', codigo: 'PED-24031', cliente: 'Confecciones Andinas', fechaEntrega: '2026-06-30', estado: 'Pendiente', fechaCarga: '2026-06-13' },
  { id: 'ped-24032', codigo: 'PED-24032', cliente: 'Textiles del Pacífico', fechaEntrega: '2026-07-04', estado: 'Pendiente', fechaCarga: '2026-06-13' },
  { id: 'ped-24018', codigo: 'PED-24018', cliente: 'Moda Sur', fechaEntrega: '2026-06-12', estado: 'Completada', fechaCarga: '2026-06-06' },
]

export const articulosRegistradosIniciales: ArticuloRegistrado[] = [
  { id: 'art-101', codigo: 'ART-101', pedidoId: 'ped-24031', descripcion: 'Jersey algodón 30/1', color: 'Azul Marino', kg: 1140, fechaCarga: '2026-06-13' },
  { id: 'art-102', codigo: 'ART-102', pedidoId: 'ped-24031', descripcion: 'Rib algodón 1x1', color: 'Azul Marino', kg: 95, fechaCarga: '2026-06-13' },
  { id: 'art-205', codigo: 'ART-205', pedidoId: 'ped-24032', descripcion: 'French terry 24/1', color: 'Negro', kg: 1200, fechaCarga: '2026-06-13' },
  { id: 'art-087', codigo: 'ART-087', pedidoId: 'ped-24018', descripcion: 'Piqué algodón', color: 'Blanco Óptico', kg: 560, fechaCarga: '2026-06-06' },
]

export const partidasRegistradasIniciales: PartidaRegistrada[] = [
  { id: 'par-341', codigo: 'P-0341', pedidoId: 'ped-24031', articuloId: 'art-101', kg: 580, maquinaCompatible: 'M1', estado: 'Pendiente', fechaCarga: '2026-06-13' },
  { id: 'par-342', codigo: 'P-0342', pedidoId: 'ped-24031', articuloId: 'art-101', kg: 560, maquinaCompatible: 'M1', estado: 'Pendiente', fechaCarga: '2026-06-13' },
  { id: 'par-344', codigo: 'P-0344', pedidoId: 'ped-24032', articuloId: 'art-205', kg: 600, maquinaCompatible: 'M2', estado: 'Pendiente', fechaCarga: '2026-06-13' },
  { id: 'par-345', codigo: 'P-0345', pedidoId: 'ped-24032', articuloId: 'art-205', kg: 600, maquinaCompatible: 'M2', estado: 'Pendiente', fechaCarga: '2026-06-13' },
  { id: 'par-319', codigo: 'P-0319', pedidoId: 'ped-24018', articuloId: 'art-087', kg: 560, maquinaCompatible: 'M1', estado: 'Completada', fechaCarga: '2026-06-06' },
]

export const proyeccionesRegistradasIniciales: ProyeccionRegistrada[] = [
  { id: 'proy-24-101', semana: 'Semana 24', articulo: 'ART-101', color: 'Azul Marino', kgDisponibles: 1140, fechaDisponibilidad: '2026-06-11', fechaCarga: '2026-06-13' },
  { id: 'proy-24-205', semana: 'Semana 24', articulo: 'ART-205', color: 'Negro', kgDisponibles: 1200, fechaDisponibilidad: '2026-06-12', fechaCarga: '2026-06-13' },
]

// ---------------------------------------------------------------------------
// 1b. Sugerencias de tejeduría para completar máquinas bajo objetivo (RF35)
// ---------------------------------------------------------------------------

/**
 * Qué convendría tejer para que una máquina que quedó bajo su objetivo semanal
 * pueda completarse. Es una salida del backend, no un cálculo del frontend:
 * cruza los pedidos y artículos ya registrados contra el déficit de cada
 * máquina. Se muestra dentro de la pestaña Equilibrio, junto a la máquina.
 */
export interface SugerenciaTejido {
  id: string
  maquinaId: string
  maquina: string
  partidasFaltantes: number
  pedido: string
  cliente: string
  articulo: string
  color: string
  kg: number
  /** Cuándo tendría que estar tejido para llegar a tiempo a esta semana. */
  tejerAntesDe: string
}

export const sugerenciasTejido: SugerenciaTejido[] = [
  {
    id: 'sug-1',
    maquinaId: 'M4',
    maquina: 'M4',
    partidasFaltantes: 9,
    pedido: 'PED-2291',
    cliente: 'Textiles Rivas',
    articulo: 'JER-30/1 Algodón peinado',
    color: 'Azul Marino',
    kg: 380,
    tejerAntesDe: 'Mar 9 jun',
  },
  {
    id: 'sug-2',
    maquinaId: 'M4',
    maquina: 'M4',
    partidasFaltantes: 9,
    pedido: 'PED-2304',
    cliente: 'Confecciones Vega',
    articulo: 'PIQ-24/1 Piqué',
    color: 'Gris Perla',
    kg: 360,
    tejerAntesDe: 'Mié 10 jun',
  },
  {
    id: 'sug-3',
    maquinaId: 'M2',
    maquina: 'M2',
    partidasFaltantes: 3,
    pedido: 'PED-2288',
    cliente: 'Textiles Rivas',
    articulo: 'RIB-20/1 Rib 1x1',
    color: 'Negro',
    kg: 560,
    tejerAntesDe: 'Lun 8 jun',
  },
  {
    id: 'sug-4',
    maquinaId: 'M9',
    maquina: 'M9',
    partidasFaltantes: 20,
    pedido: 'PED-2312',
    cliente: 'Andina Knits',
    articulo: 'JER-24/1 Algodón/Poliéster',
    color: 'Turquesa',
    kg: 190,
    tejerAntesDe: 'Lun 8 jun',
  },
]

// ---------------------------------------------------------------------------
// 5. Incidencias y replanificación
// ---------------------------------------------------------------------------

export type TipoIncidencia =
  | 'Retraso de tejeduría'
  | 'Incidencia de máquina'
  | 'Falta o demora de receta'
  | 'Problema de complemento'
  | 'Cambio de prioridad'

export interface CampoIncidencia {
  id: string
  etiqueta: string
  tipo: 'texto' | 'select' | 'fecha-hora' | 'textarea'
  opciones?: string[]
  requerido?: boolean
}

export interface TipoIncidenciaConfig {
  tipo: TipoIncidencia
  descripcion: string
  camposAdicionales: CampoIncidencia[]
}

const codigosMaquina = maquinas.map((m) => m.codigo)

export const TIPOS_INCIDENCIA: TipoIncidenciaConfig[] = [
  {
    tipo: 'Retraso de tejeduría',
    descripcion: 'La proyección de tejeduría llega después de lo previsto para una o más partidas.',
    camposAdicionales: [
      { id: 'partida', etiqueta: 'Partida afectada', tipo: 'texto', requerido: true },
      { id: 'nuevaFecha', etiqueta: 'Nueva fecha estimada de disponibilidad', tipo: 'fecha-hora', requerido: true },
    ],
  },
  {
    tipo: 'Incidencia de máquina',
    descripcion: 'Una máquina deja de estar disponible durante un intervalo (falla, parada no programada).',
    camposAdicionales: [
      { id: 'maquina', etiqueta: 'Máquina afectada', tipo: 'select', opciones: codigosMaquina, requerido: true },
      { id: 'desde', etiqueta: 'No disponible desde', tipo: 'fecha-hora', requerido: true },
      { id: 'hasta', etiqueta: 'No disponible hasta', tipo: 'fecha-hora', requerido: true },
    ],
  },
  {
    tipo: 'Falta o demora de receta',
    descripcion: 'Laboratorio no entrega a tiempo la receta necesaria para iniciar una partida.',
    camposAdicionales: [
      { id: 'partida', etiqueta: 'Partida afectada', tipo: 'texto', requerido: true },
      { id: 'nuevaFecha', etiqueta: 'Nueva fecha estimada de receta', tipo: 'fecha-hora', requerido: true },
    ],
  },
  {
    tipo: 'Problema de complemento',
    descripcion: 'Falta material complementario (habilitación, insumo) para completar una partida.',
    camposAdicionales: [
      { id: 'partida', etiqueta: 'Partida afectada', tipo: 'texto', requerido: true },
      { id: 'detalleComplemento', etiqueta: 'Complemento faltante', tipo: 'texto', requerido: true },
    ],
  },
  {
    tipo: 'Cambio de prioridad',
    descripcion: 'Una partida debe adelantarse o postergarse por un cambio comercial o del cliente.',
    camposAdicionales: [
      { id: 'partida', etiqueta: 'Partida afectada', tipo: 'texto', requerido: true },
      { id: 'nuevaPrioridad', etiqueta: 'Nueva prioridad', tipo: 'select', opciones: ['Adelantar', 'Postergar', 'Urgente'], requerido: true },
    ],
  },
]
// R3.2 redujo el catálogo: "Problema de rollo" y "Reproceso" existían en la versión anterior y quedan pendientes de confirmación.

export interface Incidencia {
  id: string
  tipo: TipoIncidencia
  detalle: string
  referencia?: string
  partidaDisponibleDesde: string
  registradaEl: string
  registradaPor: string
}

export const incidenciasPendientes: Incidencia[] = [
  { id: 'inc-1', tipo: 'Incidencia de máquina', detalle: 'Falla en sistema de calentamiento, a la espera de repuesto importado', referencia: 'M9', partidaDisponibleDesde: '10 jun 2026, 14:00', registradaEl: '8 jun 2026, 08:10', registradaPor: 'Carlos T.' },
  { id: 'inc-2', tipo: 'Cambio de prioridad', detalle: 'Cliente Rivas solicita adelantar entrega una semana', referencia: 'P-0391', partidaDisponibleDesde: '12 jun 2026, 12:00', registradaEl: '12 jun 2026, 11:30', registradaPor: 'Carlos T.' },
]

// ---------------------------------------------------------------------------
// 6. Escenarios
// ---------------------------------------------------------------------------

export interface ComparacionEscenarioFila {
  metrica: string
  vigente: string
  escenario: string
  mejora: boolean
}

export const comparacionEscenario: ComparacionEscenarioFila[] = [
  { metrica: 'Partidas planificadas', vigente: '87', escenario: '89', mejora: true },
  { metrica: 'Sobrantes', vigente: '5', escenario: '3', mejora: true },
  { metrica: 'Bloqueadas', vigente: '2', escenario: '2', mejora: false },
  { metrica: 'Máquinas equilibradas', vigente: '7/9', escenario: '8/9', mejora: true },
  { metrica: 'Lavados', vigente: '14', escenario: '16', mejora: false },
  { metrica: 'Cumplimiento de fechas', vigente: '94%', escenario: '98%', mejora: true },
]

// ---------------------------------------------------------------------------
// 7. Historial
// ---------------------------------------------------------------------------

export type OrigenVersion = 'Plan inicial' | 'Replanificación' | 'Escenario aplicado'

export interface VersionHistorial {
  id: string
  semana: string
  origen: OrigenVersion
  detalleOrigen?: string
  fecha: string
  autor: string
  vigente: boolean
}

export const historialVersiones: VersionHistorial[] = [
  { id: 'v-1', semana: '1–7 jun 2026', origen: 'Plan inicial', fecha: '30 may 2026, 09:05', autor: 'Carlos T.', vigente: true },
  { id: 'v-2', semana: '8–14 jun 2026', origen: 'Plan inicial', fecha: '6 jun 2026, 09:14', autor: 'Carlos T.', vigente: false },
  { id: 'v-3', semana: '8–14 jun 2026', origen: 'Replanificación', detalleOrigen: 'Incidencia de máquina — M9', fecha: '8 jun 2026, 08:22', autor: 'Carlos T.', vigente: true },
  { id: 'v-4', semana: '15–21 jun 2026', origen: 'Plan inicial', fecha: '13 jun 2026, 08:02', autor: 'Carlos T.', vigente: false },
  { id: 'v-5', semana: '15–21 jun 2026', origen: 'Escenario aplicado', detalleOrigen: 'Escenario «Adelanto pedido Cliente Rivas»', fecha: '14 jun 2026, 11:20', autor: 'Carlos T.', vigente: true },
]

export interface EntradaTrazabilidad {
  id: string
  quien: string
  cuando: string
  que: string
  valorAnterior: string
  valorNuevo: string
}

export const trazabilidad: EntradaTrazabilidad[] = [
  { id: 't-1', quien: 'Carlos T.', cuando: '8 jun 2026, 08:22', que: 'Máquina asignada — Partida P-0405', valorAnterior: 'M9', valorNuevo: 'M1' },
  { id: 't-2', quien: 'Carlos T.', cuando: '8 jun 2026, 08:22', que: 'Estado — Partida P-0406', valorAnterior: 'Planificada', valorNuevo: 'Sobrante' },
  { id: 't-3', quien: 'Carlos T.', cuando: '14 jun 2026, 11:20', que: 'Fecha de entrega — Partida P-0391', valorAnterior: '28 jun 2026', valorNuevo: '21 jun 2026' },
]

// ---------------------------------------------------------------------------
// Alertas para la campana del Topbar (todas las alertas abiertas, cualquier semana)
// ---------------------------------------------------------------------------

export const alertas: AlertaPlanificacion[] = alertasEjemplo
