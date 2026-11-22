import { useState, type ReactNode } from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'

/** Registros por página en todos los listados. */
export const REGISTROS_POR_PAGINA = 15

const boton = 'inline-flex h-8 w-8 items-center justify-center rounded-lg border border-line bg-panel text-ink hover:bg-panel-muted disabled:pointer-events-none disabled:text-ink-faint'

/** Paginación de Carbon: rango visible a la izquierda; página actual y flechas a la derecha. */
export function Paginador({ pagina, total, registros, onCambiar }: { pagina: number; total: number; registros: number; onCambiar: (pagina: number) => void }) {
  const desde = (pagina - 1) * REGISTROS_POR_PAGINA + 1
  const hasta = Math.min(pagina * REGISTROS_POR_PAGINA, registros)
  return (
    <nav aria-label="Paginación" className="mt-3 flex flex-wrap items-center justify-between gap-2 text-sm">
      <span className="text-ink-soft">Mostrando {desde}–{hasta} de {registros} {registros === 1 ? 'registro' : 'registros'}</span>
      <div className="flex items-center gap-2">
        <span className="text-ink-soft">Página {pagina} de {total}</span>
        <button type="button" className={boton} disabled={pagina <= 1} onClick={() => onCambiar(pagina - 1)} aria-label="Página anterior"><ChevronLeft className="h-4 w-4" /></button>
        <button type="button" className={boton} disabled={pagina >= total} onClick={() => onCambiar(pagina + 1)} aria-label="Página siguiente"><ChevronRight className="h-4 w-4" /></button>
      </div>
    </nav>
  )
}

/**
 * Muestra los registros de 15 en 15 con su paginador. Vuelve a la primera página cuando cambia
 * {@code reinicio} (por ejemplo, los filtros) y se ajusta si la lista se acorta.
 */
export function Paginado<T>({ items, reinicio, children }: { items: T[]; reinicio?: unknown; children: (pagina: T[]) => ReactNode }) {
  const clave = JSON.stringify(reinicio ?? null)
  const [estado, setEstado] = useState({ pagina: 1, clave })
  if (estado.clave !== clave) setEstado({ pagina: 1, clave })
  const total = Math.max(1, Math.ceil(items.length / REGISTROS_POR_PAGINA))
  const pagina = Math.min(estado.clave === clave ? estado.pagina : 1, total)
  const visibles = items.slice((pagina - 1) * REGISTROS_POR_PAGINA, pagina * REGISTROS_POR_PAGINA)
  return <>
    {children(visibles)}
    {items.length > 0 && <Paginador pagina={pagina} total={total} registros={items.length} onCambiar={(p) => setEstado({ pagina: p, clave })} />}
  </>
}
