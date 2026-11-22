/**
 * Versiones de la proyección en el calendario (D-52): colores, etiquetas y la comparación partida por partida entre
 * las planificaciones de hasta dos versiones marcadas. Es lógica pura (sin React ni la API) para poder probarla con
 * `node --test`; solo arma lo que ya está guardado y no recalcula indicadores.
 *
 * Casos al comparar dos versiones (A y B, en el orden en que se marcaron):
 * - igual (a):   misma máquina, mismo día y misma clasificación → una sola tarjeta con los dos colores.
 * - estado (b):  misma máquina y día, distinta clasificación (Asignada en una, Sobrante en otra) → una sola tarjeta.
 * - movida (c):  ubicada en las dos, en otra máquina o día → una tarjeta en cada lugar, con borde punteado.
 * - solo (d):    ubicada solo en una → la tarjeta de esa versión; el detalle dice qué pasó en la otra.
 */
import type {
  ClasificacionColor, ClasificacionResultado, MaquinaPlan, PartidaBloqueada, PartidaCalendario, PlanificacionDetalle,
} from '../api/tipos'
import { desdeIso } from './fechas.ts'

/** Cantidad de colores distintos; desde la V6 se repiten. V1 azul #234F79 y V2 oliva #5B663A (fijos, imagenes/colores.png). */
export const COLORES_VERSION = 5

/** Máximo de versiones no eliminadas por semana: una por color (el backend rechaza la sexta). */
export const MAXIMO_VERSIONES = COLORES_VERSION

/**
 * Color asignado a cada versión: por su lugar entre las versiones no eliminadas de la semana (la más antigua, el
 * primer color), no por su número. Así, aunque al eliminar y volver a cargar los números sigan subiendo (V6, V7…),
 * nunca dos versiones de la semana comparten color. Sin asignar, se usa el número.
 */
let ranuras = new Map<number, number>()

export function asignarColoresVersiones(versiones: number[]) {
  ranuras = new Map([...versiones].sort((a, b) => a - b).slice(0, COLORES_VERSION).map((v, i) => [v, i + 1]))
}

function variableVersion(version: number, sufijo = ''): string {
  const ranura = ranuras.get(version) ?? ((Math.max(1, version) - 1) % COLORES_VERSION) + 1
  return `var(--color-version-${ranura}${sufijo})`
}

/** Color de la versión (etiqueta «V1» y texto), como variable CSS definida en index.css para los dos temas. */
export function colorVersion(version: number): string {
  return variableVersion(version)
}

/** Fondo de la caja de la versión (V1 #E4EDF6, V2 #E9EEDC). */
export function fondoVersion(version: number): string {
  return variableVersion(version, '-soft')
}

/** Borde claro de la caja de la versión (V1 #7DA0C1, V2 #A0AE84). */
export function bordeVersion(version: number): string {
  return variableVersion(version, '-borde')
}

/** Cabecera sólida de la caja, con texto blanco (V1 #234F79, V2 #5B663A en los dos temas). */
export function cabeceraVersion(version: number): string {
  return variableVersion(version, '-cabecera')
}

export function etiquetaVersion(version: number): string {
  return `V${version}`
}

/** Versión marcada en el panel: número de la versión de la proyección y su planificación ya cargada. */
export interface VersionMarcada {
  version: number
  plan: PlanificacionDetalle
}

export type CasoComparacion = 'igual' | 'estado' | 'movida' | 'solo'

export interface PresenciaVersion {
  version: number
  clasificacion: ClasificacionResultado
  requiereLavado: boolean
  ordenSecuencia: number
}

/** Una tarjeta del calendario: una partida en una celda, con las versiones en que está ahí. */
export interface TarjetaPartida {
  clave: string
  partida: PartidaCalendario
  versiones: PresenciaVersion[]
  /** null con una sola versión marcada. */
  caso: CasoComparacion | null
  /** Qué pasa con la partida en la otra versión (casos c y d). */
  enLaOtra: string | null
}

export interface ContadorVersion {
  version: number
  regulares: number
  minimoDiario: number
  maximoDiario: number
}

export interface CeldaComparada {
  fecha: string
  indisponible: boolean
  motivoIndisponibilidad: string | null
  contadores: ContadorVersion[]
  tarjetas: TarjetaPartida[]
}

export interface ResumenMaquinaVersion {
  version: number
  asignadas: number
  sobrantes: number
  objetivoAplicado: number
  lavados: number
  minimoDiario: number
  maximoDiario: number
}

export interface MaquinaComparada {
  codigo: string
  barca: string
  capacidad: number
  clasificacionColor: ClasificacionColor
  porVersion: ResumenMaquinaVersion[]
  celdas: CeldaComparada[]
}

export interface CalendarioComparado {
  dias: string[]
  versiones: number[]
  maquinas: MaquinaComparada[]
  /** Partidas por caso (cada partida cuenta una vez). Vacío con una sola versión. */
  conteo: Record<CasoComparacion, number>
}

interface Ubicacion {
  maquina: string
  dia: string
  partida: PartidaCalendario
}

const DIAS = ['dom', 'lun', 'mar', 'mié', 'jue', 'vie', 'sáb']

/** «mar 17/11». */
export function diaCorto(iso: string): string {
  const fecha = desdeIso(iso)
  return `${DIAS[fecha.getDay()]} ${String(fecha.getDate()).padStart(2, '0')}/${String(fecha.getMonth() + 1).padStart(2, '0')}`
}

function ubicaciones(plan: PlanificacionDetalle): Map<string, Ubicacion> {
  const r = new Map<string, Ubicacion>()
  for (const m of plan.maquinas)
    for (const d of m.dias)
      for (const p of d.partidas) r.set(p.numeroPartida, { maquina: m.codigo, dia: d.fecha, partida: p })
  return r
}

function presencia(version: number, p: PartidaCalendario): PresenciaVersion {
  return { version, clasificacion: p.clasificacion, requiereLavado: p.requiereLavado, ordenSecuencia: p.ordenSecuencia }
}

/** Texto de lo que pasó con una partida en la otra versión (caso d): bloqueada con su motivo o sigue pendiente. */
function fueraDeLaOtra(version: number, bloqueada: PartidaBloqueada | undefined): string {
  if (bloqueada) return `En ${etiquetaVersion(version)}: Bloqueada — ${bloqueada.motivo ?? 'la tela no alcanzó para completarla'}`
  return `En ${etiquetaVersion(version)}: sigue Pendiente`
}

/** Une las máquinas de las versiones sin repetir, en el orden de la primera. */
function maquinasUnidas(versiones: VersionMarcada[]): MaquinaPlan[] {
  const vistas = new Map<string, MaquinaPlan>()
  for (const v of versiones) for (const m of v.plan.maquinas) if (!vistas.has(m.codigo)) vistas.set(m.codigo, m)
  return [...vistas.values()]
}

/**
 * Calendario de una o dos versiones marcadas. Con una sola, cada partida es una tarjeta de esa versión. Con dos,
 * cada partida se compara entre ambas y cae en uno de los cuatro casos.
 */
export function compararVersiones(marcadas: VersionMarcada[]): CalendarioComparado {
  const versiones = marcadas.slice(0, 2)
  const conteo: Record<CasoComparacion, number> = { igual: 0, estado: 0, movida: 0, solo: 0 }
  if (versiones.length === 0) return { dias: [], versiones: [], maquinas: [], conteo }
  const dias = versiones[0].plan.dias
  const ubic = versiones.map((v) => ubicaciones(v.plan))
  const bloqueadas = versiones.map((v) => new Map(v.plan.bloqueadas.map((b) => [b.numeroPartida, b])))
  const dosVersiones = versiones.length === 2

  // Tarjetas por celda (máquina|día)
  const porCelda = new Map<string, TarjetaPartida[]>()
  const agregar = (maquina: string, dia: string, t: TarjetaPartida) => {
    const k = `${maquina}|${dia}`
    porCelda.set(k, [...(porCelda.get(k) ?? []), t])
  }
  versiones.forEach((v, i) => {
    const otra = dosVersiones ? 1 - i : -1
    for (const [numero, u] of ubic[i]) {
      if (!dosVersiones) {
        agregar(u.maquina, u.dia, { clave: `${numero}@${v.version}`, partida: u.partida, versiones: [presencia(v.version, u.partida)], caso: null, enLaOtra: null })
        continue
      }
      const vo = versiones[otra]
      const uo = ubic[otra].get(numero)
      if (uo && uo.maquina === u.maquina && uo.dia === u.dia) {
        if (i === 1) continue   // ya se agregó desde la primera versión
        const caso: CasoComparacion = uo.partida.clasificacion === u.partida.clasificacion ? 'igual' : 'estado'
        conteo[caso]++
        agregar(u.maquina, u.dia, {
          clave: `${numero}@${v.version}·${vo.version}`, partida: u.partida,
          versiones: [presencia(v.version, u.partida), presencia(vo.version, uo.partida)], caso, enLaOtra: null,
        })
      } else if (uo) {
        if (i === 0) conteo.movida++
        const primera = i === 0 ? u : uo
        const segunda = i === 0 ? uo : u
        agregar(u.maquina, u.dia, {
          clave: `${numero}@${v.version}`, partida: u.partida, versiones: [presencia(v.version, u.partida)], caso: 'movida',
          enLaOtra: `En ${etiquetaVersion(versiones[0].version)}: ${primera.maquina} ${diaCorto(primera.dia)} · `
            + `En ${etiquetaVersion(versiones[1].version)}: ${segunda.maquina} ${diaCorto(segunda.dia)}`,
        })
      } else {
        conteo.solo++
        agregar(u.maquina, u.dia, {
          clave: `${numero}@${v.version}`, partida: u.partida, versiones: [presencia(v.version, u.partida)], caso: 'solo',
          enLaOtra: fueraDeLaOtra(vo.version, bloqueadas[otra].get(numero)),
        })
      }
    }
  })

  const maquinas: MaquinaComparada[] = maquinasUnidas(versiones).map((base) => {
    const suyas = versiones.map((v) => ({ version: v.version, m: v.plan.maquinas.find((x) => x.codigo === base.codigo) }))
    return {
      codigo: base.codigo, barca: base.barca, capacidad: base.capacidad, clasificacionColor: base.clasificacionColor,
      porVersion: suyas.filter((s) => s.m).map(({ version, m }) => ({
        version, asignadas: m!.asignadas, sobrantes: m!.sobrantes, objetivoAplicado: m!.objetivoAplicado, lavados: m!.lavados,
        minimoDiario: m!.minimoDiario, maximoDiario: m!.maximoDiario,
      })),
      celdas: dias.map((fecha) => {
        const delDia = suyas.map(({ version, m }) => ({ version, m, d: m?.dias.find((x) => x.fecha === fecha) }))
        const indisponibles = delDia.filter((x) => x.d?.indisponible)
        const tarjetas = [...(porCelda.get(`${base.codigo}|${fecha}`) ?? [])]
          .sort((a, b) => a.versiones[0].ordenSecuencia - b.versiones[0].ordenSecuencia
            || versiones.findIndex((v) => v.version === a.versiones[0].version) - versiones.findIndex((v) => v.version === b.versiones[0].version))
        return {
          fecha,
          indisponible: indisponibles.length > 0,
          motivoIndisponibilidad: indisponibles[0]?.d?.motivoIndisponibilidad ?? null,
          contadores: delDia.filter((x) => x.m && x.d).map(({ version, m, d }) => ({
            version, regulares: d!.regulares, minimoDiario: m!.minimoDiario, maximoDiario: m!.maximoDiario,
          })),
          tarjetas,
        }
      }),
    }
  })
  return { dias, versiones: versiones.map((v) => v.version), maquinas, conteo }
}

/** Partidas de una celda que se pintan juntas con el color de su versión. */
export interface GrupoTarjetas {
  /** Una versión, o las dos para las partidas iguales en ambas (casos a y b). */
  versiones: number[]
  tarjetas: TarjetaPartida[]
}

/**
 * Agrupa las tarjetas de una celda por versión, en orden: primero todas las de la primera versión marcada, luego
 * las de la segunda y al final las que están en ambas. Con una sola versión hay un solo grupo, sin color.
 */
export function agruparPorVersion(tarjetas: TarjetaPartida[], versiones: number[]): GrupoTarjetas[] {
  if (versiones.length < 2) return tarjetas.length > 0 ? [{ versiones: versiones.slice(0, 1), tarjetas }] : []
  const grupos: GrupoTarjetas[] = versiones.map((v) => ({
    versiones: [v], tarjetas: tarjetas.filter((t) => t.versiones.length === 1 && t.versiones[0].version === v),
  }))
  grupos.push({ versiones: [...versiones], tarjetas: tarjetas.filter((t) => t.versiones.length > 1) })
  return grupos.filter((g) => g.tarjetas.length > 0)
}

/** Color del contador «2/3»: ámbar bajo el mínimo del día, verde si llegó al máximo, gris en otro caso. */
export type EstadoCarga = 'baja' | 'completa' | 'normal'

export function estadoCarga(c: { regulares: number; minimoDiario: number; maximoDiario: number }): EstadoCarga {
  if (c.maximoDiario > 0 && c.regulares >= c.maximoDiario) return 'completa'
  if (c.regulares < c.minimoDiario) return 'baja'
  return 'normal'
}

/** Partida con las versiones en que aparece igual (listas de Sobrantes y Bloqueadas con dos versiones). */
export interface FilaConVersiones<T> {
  clave: string
  fila: T
  versiones: number[]
}

/** Une filas de varias versiones: las que tienen la misma clave (por ejemplo, partida, máquina y día) van juntas. */
export function unirPorVersion<T>(listas: { version: number; filas: T[] }[], clave: (fila: T) => string): FilaConVersiones<T>[] {
  const r = new Map<string, FilaConVersiones<T>>()
  for (const { version, filas } of listas)
    for (const fila of filas) {
      const k = clave(fila)
      const ya = r.get(k)
      if (ya) ya.versiones.push(version)
      else r.set(k, { clave: k, fila, versiones: [version] })
    }
  return [...r.values()]
}
