import { createContext, useContext } from 'react'

export type ExperienceMode = 'guided' | 'expert'

export interface ExperienceContextValue {
  mode: ExperienceMode
  setMode: (mode: ExperienceMode) => void
  isGuided: boolean
}

export const ExperienceContext = createContext<ExperienceContextValue | null>(null)

export function useExperience() {
  const context = useContext(ExperienceContext)
  if (!context) throw new Error('useExperience debe usarse dentro de <ExperienceProvider>')
  return context
}
