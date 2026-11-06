import { useMemo, type ReactNode } from 'react'
import { ExperienceContext, type ExperienceMode } from './experience-context'
import { useAuth } from '@/auth/auth-context'
import { autenticacion } from '@/api/servicios'

/**
 * Modo Guiado / Experto (RF07, HU-03): solo cambia la presentación, nunca las funciones ni los permisos.
 * Se guarda en la cuenta del usuario ({@code usuario.modo_interaccion}); solo el Planificador lo alterna.
 */
export function ExperienceProvider({ children }: { children: ReactNode }) {
  const { usuario, esPlanificador, actualizarModo } = useAuth()
  const mode: ExperienceMode = usuario?.modoInteraccion === 'GUIADO' ? 'guided' : 'expert'

  const value = useMemo(() => ({
    mode,
    isGuided: mode === 'guided',
    setMode: (next: ExperienceMode) => {
      if (!esPlanificador || next === mode) return
      const modo = next === 'guided' ? 'GUIADO' : 'EXPERTO'
      actualizarModo(modo)
      autenticacion.cambiarModo(modo).catch(() => actualizarModo(mode === 'guided' ? 'GUIADO' : 'EXPERTO'))
    },
  }), [mode, esPlanificador, actualizarModo])

  return <ExperienceContext.Provider value={value}>{children}</ExperienceContext.Provider>
}
