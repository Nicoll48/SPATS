import { useMemo, useState, type ReactNode } from 'react'
import { SemanaContext } from './semana-context'
import { lunesDe, sumarDias } from '@/lib/fechas'

const CLAVE = 'spats:semana'

function semanaInicial(): string {
  try {
    const guardada = window.sessionStorage.getItem(CLAVE)
    if (guardada && /^\d{4}-\d{2}-\d{2}$/.test(guardada)) return lunesDe(new Date(`${guardada}T00:00:00`))
  } catch { /* sin almacenamiento: semana actual */ }
  return lunesDe(new Date())
}

export function SemanaProvider({ children }: { children: ReactNode }) {
  const [lunes, actualizar] = useState(semanaInicial)

  const valor = useMemo(() => {
    const setLunes = (nuevo: string) => {
      const normalizado = lunesDe(new Date(`${nuevo}T00:00:00`))
      try { window.sessionStorage.setItem(CLAVE, normalizado) } catch { /* conveniencia, no estado crítico */ }
      actualizar(normalizado)
    }
    return { lunes, setLunes, anterior: () => setLunes(sumarDias(lunes, -7)), siguiente: () => setLunes(sumarDias(lunes, 7)) }
  }, [lunes])

  return <SemanaContext.Provider value={valor}>{children}</SemanaContext.Provider>
}
