import { useEffect } from 'react'
import type { ReactNode } from 'react'
import { AlertTriangle } from 'lucide-react'

export type ConfirmTone = 'accent' | 'danger' | 'scenario'

interface ConfirmDialogProps {
  open: boolean
  title: string
  /** Qué va a pasar exactamente si confirma — no un "¿estás seguro?" genérico. */
  description: ReactNode
  confirmLabel: string
  cancelLabel?: string
  tone?: ConfirmTone
  onConfirm: () => void
  onCancel: () => void
}

const TONE_BUTTON: Record<ConfirmTone, string> = {
  accent: 'bg-accent text-accent-ink',
  danger: 'bg-danger text-white',
  scenario: 'bg-scenario text-scenario-ink',
}

/**
 * Confirmación previa a toda acción que altera la planificación vigente
 * (RF50): Planificar, Replanificar, Marcar como vigente, Aplicar como plan
 * real y Descartar escenario. Deliberadamente modal y con foco inicial en
 * Cancelar — la acción destructiva nunca se dispara con un Enter distraído.
 */
export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel,
  cancelLabel = 'Cancelar',
  tone = 'accent',
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  useEffect(() => {
    if (!open) return
    function handleKey(event: KeyboardEvent) {
      if (event.key === 'Escape') onCancel()
    }
    window.addEventListener('keydown', handleKey)
    return () => window.removeEventListener('keydown', handleKey)
  }, [open, onCancel])

  if (!open) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <button type="button" aria-label="Cancelar" onClick={onCancel} className="fixed inset-0 bg-black/50" />

      <div
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="confirm-title"
        className="relative w-full max-w-md rounded-2xl border border-line bg-panel p-5 shadow-xl"
      >
        <div className="flex items-start gap-3">
          <span className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-warning-soft">
            <AlertTriangle className="h-[18px] w-[18px] text-warning" aria-hidden="true" />
          </span>
          <div className="min-w-0">
            <h2 id="confirm-title" className="text-base font-semibold text-ink">
              {title}
            </h2>
            <div className="mt-1 text-sm text-ink-soft">{description}</div>
          </div>
        </div>

        <div className="mt-5 flex items-center justify-end gap-2">
          <button
            type="button"
            autoFocus
            onClick={onCancel}
            className="rounded-lg border border-line bg-panel px-3 py-2 text-sm font-medium text-ink transition-colors hover:bg-panel-muted"
          >
            {cancelLabel}
          </button>
          <button
            type="button"
            onClick={onConfirm}
            className={`rounded-lg px-3 py-2 text-sm font-medium transition-opacity hover:opacity-90 ${TONE_BUTTON[tone]}`}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
