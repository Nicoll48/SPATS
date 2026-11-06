import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import {
  articulosRegistradosIniciales,
  partidasRegistradasIniciales,
  pedidosRegistradosIniciales,
  proyeccionesRegistradasIniciales,
  type ArticuloRegistrado,
  type PartidaRegistrada,
  type PedidoRegistrado,
  type ProyeccionRegistrada,
} from '@/data/mockData'
import { RegistrosContext } from '@/data/registros-context'

const STORAGE_KEY = 'planificacion-textil-registros-v2'
const partidasArrastradasDemo = [
  { codigo: 'P-0341', color: 'Azul Marino', kg: 580, origen: 'Asignada', semanaOrigen: 0, maquina: 'M1' },
  { codigo: 'P-0342', color: 'Azul Marino', kg: 560, origen: 'Sobrante', semanaOrigen: 0, maquina: 'M1' },
]

function incluirDemo<T extends { partidas: PartidaRegistrada[]; arrastradas: typeof partidasArrastradasDemo; demoArrastradasCargada?: boolean }>(datos: T): T {
  if (datos.demoArrastradasCargada) return datos
  const existentes = new Set(datos.arrastradas.map((item) => item.codigo))
  const nuevas = partidasArrastradasDemo.filter((item) => !existentes.has(item.codigo) && datos.partidas.find((partida) => partida.codigo === item.codigo)?.estado !== 'Completada')
  const codigos = new Set(nuevas.map((item) => item.codigo))
  return { ...datos, demoArrastradasCargada: true, arrastradas: [...datos.arrastradas, ...nuevas], partidas: datos.partidas.map((item) => codigos.has(item.codigo) ? { ...item, estado: 'Arrastrada' as const } : item) }
}

function cargarIniciales() {
  try {
    const guardado = localStorage.getItem(STORAGE_KEY)
    if (guardado) {
      const datos = JSON.parse(guardado) as { pedidos: PedidoRegistrado[]; articulos: ArticuloRegistrado[]; partidas: PartidaRegistrada[]; proyecciones: ProyeccionRegistrada[]; arrastradas?: { codigo: string; color: string; kg: number; origen: string; semanaOrigen: number; maquina: string }[] }
      return incluirDemo({ ...datos, arrastradas: datos.arrastradas ?? [] })
    }
  } catch { /* usa los datos de demostración si el almacenamiento no está disponible */ }
  return incluirDemo({ pedidos: pedidosRegistradosIniciales, articulos: articulosRegistradosIniciales, partidas: partidasRegistradasIniciales, proyecciones: proyeccionesRegistradasIniciales, arrastradas: [] as typeof partidasArrastradasDemo })
}

export function RegistrosProvider({ children }: { children: ReactNode }) {
  const [datos, setDatos] = useState(cargarIniciales)

  useEffect(() => localStorage.setItem(STORAGE_KEY, JSON.stringify(datos)), [datos])

  const guardarPedido = useCallback((registro: PedidoRegistrado) => setDatos((prev) => ({ ...prev, pedidos: [...prev.pedidos.filter((item) => item.id !== registro.id), registro] })), [])
  const guardarArticulo = useCallback((registro: ArticuloRegistrado) => setDatos((prev) => ({ ...prev, articulos: [...prev.articulos.filter((item) => item.id !== registro.id), registro] })), [])
  const guardarPartida = useCallback((registro: PartidaRegistrada) => setDatos((prev) => ({ ...prev, partidas: [...prev.partidas.filter((item) => item.id !== registro.id), registro] })), [])
  const guardarProyeccion = useCallback((registro: ProyeccionRegistrada) => setDatos((prev) => ({ ...prev, proyecciones: [...prev.proyecciones.filter((item) => item.id !== registro.id), registro] })), [])
  const eliminarPedido = useCallback((id: string) => setDatos((prev) => ({ ...prev, pedidos: prev.pedidos.filter((item) => item.id !== id), articulos: prev.articulos.filter((item) => item.pedidoId !== id), partidas: prev.partidas.filter((item) => item.pedidoId !== id) })), [])
  const eliminarArticulo = useCallback((id: string) => setDatos((prev) => ({ ...prev, articulos: prev.articulos.filter((item) => item.id !== id), partidas: prev.partidas.filter((item) => item.articuloId !== id) })), [])
  const eliminarPartida = useCallback((id: string) => setDatos((prev) => ({ ...prev, partidas: prev.partidas.filter((item) => item.id !== id) })), [])
  const eliminarProyeccion = useCallback((id: string) => setDatos((prev) => ({ ...prev, proyecciones: prev.proyecciones.filter((item) => item.id !== id) })), [])
  const completarPartidas = useCallback((codigos: string[]) => setDatos((prev) => {
    const completadas = new Set(codigos)
    const partidas = prev.partidas.map((item) => completadas.has(item.codigo) ? { ...item, estado: 'Completada' as const } : item)
    const pedidos = prev.pedidos.map((pedido) => {
      const asociadas = partidas.filter((partida) => partida.pedidoId === pedido.id)
      return asociadas.length > 0 && asociadas.every((partida) => partida.estado === 'Completada') ? { ...pedido, estado: 'Completada' as const } : pedido
    })
    return { ...prev, partidas, pedidos, arrastradas: prev.arrastradas.filter((item) => !completadas.has(item.codigo)) }
  }), [])

  const cerrarSemana = useCallback((codigosCompletados: string[], nuevasArrastradas: typeof datos.arrastradas) => setDatos((prev) => {
    const completadas = new Set(codigosCompletados)
    const arrastradas = new Set(nuevasArrastradas.map((item) => item.codigo))
    const partidas = prev.partidas.map((item) => completadas.has(item.codigo) ? { ...item, estado: 'Completada' as const } : arrastradas.has(item.codigo) ? { ...item, estado: 'Arrastrada' as const } : item)
    const pedidos = prev.pedidos.map((pedido) => {
      const asociadas = partidas.filter((partida) => partida.pedidoId === pedido.id)
      return asociadas.length > 0 && asociadas.every((partida) => partida.estado === 'Completada') ? { ...pedido, estado: 'Completada' as const } : pedido
    })
    return { ...prev, partidas, pedidos, arrastradas: [...prev.arrastradas.filter((item) => !completadas.has(item.codigo) && !arrastradas.has(item.codigo)), ...nuevasArrastradas] }
  }), [])

  const value = useMemo(() => ({ ...datos, guardarPedido, guardarArticulo, guardarPartida, guardarProyeccion, eliminarPedido, eliminarArticulo, eliminarPartida, eliminarProyeccion, completarPartidas, cerrarSemana }), [datos, guardarPedido, guardarArticulo, guardarPartida, guardarProyeccion, eliminarPedido, eliminarArticulo, eliminarPartida, eliminarProyeccion, completarPartidas, cerrarSemana])
  return <RegistrosContext.Provider value={value}>{children}</RegistrosContext.Provider>
}
