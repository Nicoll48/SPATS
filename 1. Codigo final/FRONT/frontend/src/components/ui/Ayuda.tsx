import { useEffect, useId, useRef, useState } from 'react'
import { Info } from 'lucide-react'

/**
 * Información ampliada detrás de un ícono «i» (Carbon: toggletip). Se abre con clic o teclado,
 * a diferencia de un title, que solo funciona al pasar el mouse.
 */
export function Ayuda({ texto, etiqueta = 'Más información' }: { texto: string; etiqueta?: string }) {
  const [abierto, setAbierto] = useState(false)
  const caja = useRef<HTMLSpanElement>(null)
  const id = useId()

  useEffect(() => {
    if (!abierto) return
    const fuera = (e: MouseEvent) => { if (!caja.current?.contains(e.target as Node)) setAbierto(false) }
    const tecla = (e: KeyboardEvent) => { if (e.key === 'Escape') setAbierto(false) }
    document.addEventListener('click', fuera)
    window.addEventListener('keydown', tecla)
    return () => { document.removeEventListener('click', fuera); window.removeEventListener('keydown', tecla) }
  }, [abierto])

  return (
    <span ref={caja} className="relative inline-flex align-middle">
      <button type="button" aria-label={etiqueta} aria-expanded={abierto} aria-controls={id} onClick={() => setAbierto((v) => !v)}
        className="inline-flex h-5 w-5 items-center justify-center rounded-full text-ink-faint hover:text-ink">
        <Info className="h-4 w-4" aria-hidden="true" />
      </button>
      {abierto && (
        <span id={id} role="note" className="absolute top-full left-1/2 z-40 mt-2 w-72 -translate-x-1/2 rounded-lg border border-line bg-panel p-3 text-xs leading-5 font-normal text-ink shadow-lg">
          {texto}
        </span>
      )}
    </span>
  )
}
