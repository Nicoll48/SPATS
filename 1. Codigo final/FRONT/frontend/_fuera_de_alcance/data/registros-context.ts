import { createContext, useContext } from 'react'
import type { ArticuloRegistrado, PartidaRegistrada, PedidoRegistrado, ProyeccionRegistrada } from '@/data/mockData'

export interface RegistrosContextValue {
  arrastradas: { codigo: string; color: string; kg: number; origen: string; semanaOrigen: number; maquina: string }[]
  pedidos: PedidoRegistrado[]
  articulos: ArticuloRegistrado[]
  partidas: PartidaRegistrada[]
  proyecciones: ProyeccionRegistrada[]
  guardarPedido: (registro: PedidoRegistrado) => void
  guardarArticulo: (registro: ArticuloRegistrado) => void
  guardarPartida: (registro: PartidaRegistrada) => void
  guardarProyeccion: (registro: ProyeccionRegistrada) => void
  eliminarPedido: (id: string) => void
  eliminarArticulo: (id: string) => void
  eliminarPartida: (id: string) => void
  eliminarProyeccion: (id: string) => void
  completarPartidas: (codigos: string[]) => void
  cerrarSemana: (completadas: string[], arrastradas: RegistrosContextValue['arrastradas']) => void
}

export const RegistrosContext = createContext<RegistrosContextValue | null>(null)

export function useRegistros() {
  const context = useContext(RegistrosContext)
  if (!context) throw new Error('useRegistros debe usarse dentro de <RegistrosProvider>')
  return context
}
