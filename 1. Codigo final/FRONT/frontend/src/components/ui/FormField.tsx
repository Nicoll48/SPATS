import type { ReactNode } from 'react'

/** Clase compartida para <input>/<select>/<textarea> — un solo lugar para el look de todos los campos. */
export const inputClass =
  'w-full rounded-lg border border-line bg-panel px-3 py-2 text-sm text-ink placeholder:text-ink-faint focus:outline-none focus:ring-2 focus:ring-accent/40'

interface FieldProps {
  label: string
  hint?: string
  children: ReactNode
}

/** Envoltorio etiqueta + control + ayuda opcional, usado en Drawer (edición de máquina) e Incidencias. */
export function Field({ label, hint, children }: FieldProps) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-sm font-medium text-ink">{label}</span>
      {children}
      {hint && <span className="mt-1 block text-xs text-ink-soft">{hint}</span>}
    </label>
  )
}
