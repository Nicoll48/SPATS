import { createContext, useContext, type ReactNode } from 'react'

export type TipoAviso = 'success' | 'warning' | 'info'

/** Resultado de una acción que se muestra como aviso flotante abajo a la derecha y se cierra solo. */
export interface Aviso {
  tipo: TipoAviso
  titulo: string
  detalle?: ReactNode
  /** Lista de puntos (por ejemplo, los avisos de una generación); se desplaza si es larga. */
  lista?: string[]
  /** Segundos antes de cerrarse; por defecto 6 (éxito) o 10 (advertencia, con más para leer). */
  segundos?: number
}

export const AvisosContext = createContext<((aviso: Aviso) => void) | null>(null)

/** Muestra un aviso flotante: `const avisar = useAvisos(); avisar({ tipo: 'success', titulo: '…' })`. */
export function useAvisos() {
  const ctx = useContext(AvisosContext)
  if (!ctx) throw new Error('useAvisos debe usarse dentro de <AvisosProvider>')
  return ctx
}
