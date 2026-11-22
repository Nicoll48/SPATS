import { useEffect, useLayoutEffect, useRef, useState, type CSSProperties } from 'react'
import { createPortal } from 'react-dom'
import { MoreVertical, type LucideIcon } from 'lucide-react'

export interface AccionMenu {
  texto: string
  icono?: LucideIcon
  onClick: () => void
  /** Acción destructiva: va al final, en rojo (regla de Carbon). */
  peligro?: boolean
  /** Si está deshabilitada, el motivo se muestra debajo del texto (no solo en un tooltip). */
  motivoDeshabilitada?: string
}

/**
 * Menú de acciones de una fila (Carbon: overflow menu «⋮»). El menú se dibuja en un portal con
 * posición fija para que no lo recorte el desplazamiento horizontal de las tablas.
 */
export function MenuAcciones({ acciones, etiqueta = 'Acciones' }: { acciones: AccionMenu[]; etiqueta?: string }) {
  const [abierto, setAbierto] = useState(false)
  const [posicion, setPosicion] = useState<CSSProperties | null>(null)
  const boton = useRef<HTMLButtonElement>(null)
  const menu = useRef<HTMLDivElement>(null)

  // Se mide oculto y con posición fija; recién ubicado recibe el foco (sin desplazar la página).
  useLayoutEffect(() => {
    if (!abierto || !boton.current) return
    const r = boton.current.getBoundingClientRect()
    const alto = menu.current?.offsetHeight ?? 0
    const arriba = r.bottom + alto + 8 > window.innerHeight
    setPosicion({ position: 'fixed', right: window.innerWidth - r.right, ...(arriba ? { bottom: window.innerHeight - r.top } : { top: r.bottom }) })
  }, [abierto])

  useEffect(() => {
    if (abierto && posicion) menu.current?.querySelector<HTMLButtonElement>('button:not(:disabled)')?.focus({ preventScroll: true })
  }, [abierto, posicion])

  useEffect(() => {
    if (!abierto) return
    function fuera(e: MouseEvent) {
      if (!menu.current?.contains(e.target as Node) && !boton.current?.contains(e.target as Node)) setAbierto(false)
    }
    function tecla(e: KeyboardEvent) {
      if (e.key === 'Escape') { setAbierto(false); boton.current?.focus() }
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        e.preventDefault()
        const items = [...(menu.current?.querySelectorAll<HTMLButtonElement>('button:not(:disabled)') ?? [])]
        const i = items.indexOf(document.activeElement as HTMLButtonElement)
        items[(i + (e.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length]?.focus()
      }
    }
    const cerrar = () => { setAbierto(false); setPosicion(null) }
    document.addEventListener('click', fuera)
    window.addEventListener('keydown', tecla)
    window.addEventListener('scroll', cerrar, true)
    window.addEventListener('resize', cerrar)
    return () => {
      document.removeEventListener('click', fuera)
      window.removeEventListener('keydown', tecla)
      window.removeEventListener('scroll', cerrar, true)
      window.removeEventListener('resize', cerrar)
    }
  }, [abierto])

  if (acciones.length === 0) return null
  const ordenadas = [...acciones.filter((a) => !a.peligro), ...acciones.filter((a) => a.peligro)]

  return (
    <>
      <button ref={boton} type="button" aria-label={etiqueta} aria-haspopup="menu" aria-expanded={abierto} onClick={() => { setPosicion(null); setAbierto((v) => !v) }}
        className={`inline-flex h-8 w-8 items-center justify-center rounded-md text-ink-soft hover:bg-panel-muted hover:text-ink ${abierto ? 'bg-panel-muted text-ink' : ''}`}>
        <MoreVertical className="h-4 w-4" aria-hidden="true" />
      </button>
      {abierto && createPortal(
        <div ref={menu} role="menu" style={posicion ?? { position: 'fixed', top: 0, left: 0, visibility: 'hidden' }} className="z-[60] min-w-48 max-w-72 overflow-hidden rounded-lg border border-line bg-panel py-1 shadow-lg">
          {ordenadas.map((a, i) => {
            const Icono = a.icono
            const deshabilitada = !!a.motivoDeshabilitada
            return (
              <button key={a.texto} type="button" role="menuitem" disabled={deshabilitada}
                onClick={() => { setAbierto(false); a.onClick() }}
                className={`flex w-full items-start gap-2 px-4 py-2.5 text-left text-sm ${a.peligro && i > 0 && !ordenadas[i - 1].peligro ? 'border-t border-line' : ''} ${deshabilitada ? 'cursor-not-allowed text-ink-faint' : a.peligro ? 'text-danger hover:bg-danger hover:text-white' : 'text-ink hover:bg-panel-muted'}`}>
                {Icono && <Icono className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />}
                <span className="min-w-0">{a.texto}{deshabilitada && <span className="mt-0.5 block text-xs">{a.motivoDeshabilitada}</span>}</span>
              </button>
            )
          })}
        </div>,
        document.body,
      )}
    </>
  )
}
