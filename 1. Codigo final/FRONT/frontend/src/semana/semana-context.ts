import { createContext, useContext } from 'react'

/** Semana de planificación seleccionada (lunes en ISO), compartida por todas las pantallas. */
export interface SemanaContextValue {
  lunes: string
  setLunes: (lunes: string) => void
  anterior: () => void
  siguiente: () => void
}

export const SemanaContext = createContext<SemanaContextValue | null>(null)

export function useSemana() {
  const ctx = useContext(SemanaContext)
  if (!ctx) throw new Error('useSemana debe usarse dentro de <SemanaProvider>')
  return ctx
}
