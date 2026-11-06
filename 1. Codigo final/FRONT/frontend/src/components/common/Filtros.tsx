import { useState, type ReactNode } from 'react'

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

const claseFiltro = 'rounded-lg border border-line bg-panel px-2 py-1.5 text-sm text-ink'

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

export function BarraFiltros({ visibles, total, activos, onLimpiar, children }: { visibles: number; total: number; activos: boolean; onLimpiar: () => void; children?: ReactNode }) {
  return <div className="mb-4 flex flex-wrap items-end gap-3">
    {children}
    {activos && <button type="button" onClick={onLimpiar} className="py-1.5 text-sm font-medium text-accent hover:underline">Limpiar filtros</button>}
    <span className="ml-auto py-1.5 text-xs text-ink-faint">{visibles} de {total}</span>
  </div>
}
