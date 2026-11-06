import { useEffect } from 'react'
import type { ReactNode } from 'react'
import { X } from 'lucide-react'

interface DrawerProps {
  open: boolean
  onClose: () => void
  title: string
  description?: string
  children: ReactNode
  /** Botones de acción, alineados a la derecha del pie del panel. */
  footer?: ReactNode
}

/**
 * Panel lateral deslizante para edición puntual (una máquina, una
 * incidencia) sin abandonar la pantalla de fondo. Mismo patrón de overlay
 * que el menú mobile del Sidebar, para no introducir un segundo lenguaje
 * de "capa flotante" en el panel.
 */
export function Drawer({ open, onClose, title, description, children, footer }: DrawerProps) {
  useEffect(() => {
    if (!open) return
    function handleKey(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', handleKey)
    return () => window.removeEventListener('keydown', handleKey)
  }, [open, onClose])

  if (!open) return null

  return (
    <div className="fixed inset-0 z-50 flex justify-end">
      <button type="button" aria-label="Cerrar panel" onClick={onClose} className="fixed inset-0 bg-black/50" />

      <aside
        role="dialog"
        aria-modal="true"
        aria-labelledby="drawer-title"
        className="relative flex h-dvh w-full max-w-md flex-col border-l border-line bg-panel shadow-xl"
      >
        <div className="flex shrink-0 items-start justify-between gap-3 border-b border-line px-5 py-4">
          <div className="min-w-0">
            <h2 id="drawer-title" className="text-base font-semibold text-ink">
              {title}
            </h2>
            {description && <p className="mt-0.5 text-xs text-ink-soft">{description}</p>}
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Cerrar"
            className="shrink-0 text-ink-soft hover:text-ink"
          >
            <X className="h-5 w-5" aria-hidden="true" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto px-5 py-4">{children}</div>

        {footer && <div className="flex shrink-0 items-center justify-end gap-2 border-t border-line px-5 py-4">{footer}</div>}
      </aside>
    </div>
  )
}
