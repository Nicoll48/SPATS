import { useState, type ReactNode } from 'react'
import { Search, X } from 'lucide-react'

export function enRango(fecha: string | null | undefined, desde: string, hasta: string) {
  const dia = (fecha ?? '').slice(0, 10)
  return (!desde || dia >= desde) && (!hasta || dia <= hasta)
}

/** true si la fecha cae en el mes indicado (aaaa-mm); un mes vacío no filtra. */
export function enMes(fecha: string | null | undefined, mes: string) {
  return !mes || (fecha ?? '').slice(0, 7) === mes
}

const NOMBRE_MES = new Intl.DateTimeFormat('es-PE', { month: 'long', year: 'numeric' })

/** Lista de los meses en que caen las fechas, del más reciente al más antiguo: «noviembre de 2026». */
export function FiltroMes({ etiqueta, valor, fechas, onChange }: { etiqueta: string; valor: string; fechas: (string | null | undefined)[]; onChange: (v: string) => void }) {
  const meses = [...new Set(fechas.filter((f): f is string => !!f).map((f) => f.slice(0, 7)))].sort().reverse()
  const opciones = meses.map((m) => {
    const texto = NOMBRE_MES.format(new Date(Number(m.slice(0, 4)), Number(m.slice(5, 7)) - 1, 1))
    return { valor: m, texto: texto.charAt(0).toUpperCase() + texto.slice(1) }
  })
  return <FiltroLista etiqueta={etiqueta} valor={valor} opciones={opciones} onChange={onChange} />
}

export function sinRepetir(valores: (string | null | undefined)[]) {
  return [...new Set(valores.filter((v): v is string => !!v))].sort((a, b) => a.localeCompare(b))
}

/** Valores de los filtros de una sección; '' significa que ese filtro no aplica. */
export function useFiltros<T extends Record<string, string>>(inicial: T) {
  const [valores, setValores] = useState(inicial)
  return {
    valores,
    poner: (cambio: Partial<T>) => setValores((v) => ({ ...v, ...cambio })),
    limpiar: () => setValores(inicial),
    activos: Object.values(valores).some((v) => v !== ''),
  }
}

const claseFiltro = 'h-10 rounded-lg border border-line bg-panel px-3 text-sm text-ink'

export function FiltroTexto({ etiqueta, valor, placeholder, onChange }: { etiqueta: string; valor: string; placeholder?: string; onChange: (v: string) => void }) {
  return <label className="flex flex-col gap-1 text-xs font-medium text-ink-soft">{etiqueta}
    <input className={claseFiltro} value={valor} placeholder={placeholder} onChange={(e) => onChange(e.target.value)} /></label>
}

export function FiltroLista({ etiqueta, valor, opciones, onChange }: { etiqueta: string; valor: string; opciones: { valor: string; texto: string }[]; onChange: (v: string) => void }) {
  return <label className="flex flex-col gap-1 text-xs font-medium text-ink-soft">{etiqueta}
    <select className={claseFiltro} value={valor} onChange={(e) => onChange(e.target.value)}>
      <option value="">Todos</option>
      {opciones.map((o) => <option key={o.valor} value={o.valor}>{o.texto}</option>)}
    </select></label>
}

export function FiltroFechas({ etiqueta, desde, hasta, onDesde, onHasta }: { etiqueta: string; desde: string; hasta: string; onDesde: (v: string) => void; onHasta: (v: string) => void }) {
  return <div className="flex flex-col gap-1 text-xs font-medium text-ink-soft">{etiqueta}
    <div className="flex items-center gap-1">
      <input type="date" aria-label={`${etiqueta}: desde`} className={claseFiltro} value={desde} onChange={(e) => onDesde(e.target.value)} />
      <span>a</span>
      <input type="date" aria-label={`${etiqueta}: hasta`} className={claseFiltro} value={hasta} onChange={(e) => onHasta(e.target.value)} />
    </div></div>
}

/** Filtro aplicado que se muestra como etiqueta removible (Carbon: filter tag). */
export interface FiltroAplicado { etiqueta: string; texto: string; quitar: () => void }

/** Arma la etiqueta de un filtro de lista si tiene valor; si no, no devuelve nada. */
export function aplicado(etiqueta: string, valor: string, opciones: { valor: string; texto: string }[], quitar: () => void): FiltroAplicado[] {
  if (!valor) return []
  return [{ etiqueta, texto: opciones.find((o) => o.valor === valor)?.texto ?? valor, quitar }]
}

/** Búsqueda de la barra de herramientas de una tabla (Carbon: search en el toolbar). */
export function BusquedaTabla({ valor, onChange, placeholder }: { valor: string; onChange: (v: string) => void; placeholder: string }) {
  return (
    <label className="flex h-10 min-w-56 flex-1 items-center gap-2 rounded-lg border border-line bg-panel px-3 focus-within:outline focus-within:outline-2 focus-within:outline-accent sm:max-w-sm">
      <Search className="h-4 w-4 shrink-0 text-ink-soft" aria-hidden="true" />
      <span className="sr-only">Buscar</span>
      <input type="search" value={valor} onChange={(e) => onChange(e.target.value)} placeholder={placeholder}
        className="w-full bg-transparent text-sm text-ink placeholder:text-ink-faint focus:outline-none" />
      {valor && <button type="button" onClick={() => onChange('')} aria-label="Borrar búsqueda" className="text-ink-soft hover:text-ink"><X className="h-4 w-4" aria-hidden="true" /></button>}
    </label>
  )
}

export function BarraFiltros({ visibles, total, activos, onLimpiar, busqueda, aplicados, children }: {
  visibles: number
  total: number
  activos: boolean
  onLimpiar: () => void
  /** Búsqueda a la izquierda de los filtros (opcional). */
  busqueda?: ReactNode
  /** Filtros aplicados como etiquetas removibles debajo de la barra (opcional). */
  aplicados?: FiltroAplicado[]
  children?: ReactNode
}) {
  return <div className="mb-4">
    <div className="flex flex-wrap items-end gap-3">
      {busqueda}
      {children}
      {activos && !aplicados?.length && <button type="button" onClick={onLimpiar} className="h-10 px-2 text-sm font-medium text-accent hover:underline">Limpiar filtros</button>}
      <span className="ml-auto py-2 text-sm text-ink-soft">{visibles} de {total}</span>
    </div>
    {aplicados && aplicados.length > 0 && (
      <div className="mt-3 flex flex-wrap items-center gap-2" aria-label="Filtros aplicados">
        {aplicados.map((a) => (
          <span key={a.etiqueta} className="inline-flex h-7 items-center gap-1 rounded-full bg-accent-soft pr-1 pl-3 text-xs text-ink">
            <span className="text-ink-soft">{a.etiqueta}:</span><span className="font-medium">{a.texto}</span>
            <button type="button" onClick={a.quitar} aria-label={`Quitar filtro ${a.etiqueta}`} className="inline-flex h-5 w-5 items-center justify-center rounded-full hover:bg-panel"><X className="h-3 w-3" aria-hidden="true" /></button>
          </span>
        ))}
        <button type="button" onClick={onLimpiar} className="px-2 text-sm font-medium text-accent hover:underline">Limpiar filtros</button>
      </div>
    )}
  </div>
}
