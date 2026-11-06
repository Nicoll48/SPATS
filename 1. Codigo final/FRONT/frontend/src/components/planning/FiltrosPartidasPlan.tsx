import type { ReactNode } from 'react'
import { BarraFiltros, FiltroFechas, FiltroLista, FiltroTexto, enRango, sinRepetir, useFiltros } from '@/components/common/Filtros'
import { SobrantesTable, type FilaPartidaPlan } from '@/components/planning/SobrantesTable'
import { CLASIFICACION_COLOR, CLASIFICACION_RESULTADO, TONALIDAD } from '@/lib/etiquetas'
import { fechaCorta, nombreDia } from '@/lib/fechas'

/** Filtros de las partidas conformadas de una planificación: los usan la lista de partidas y el cierre de la semana. */
export function useFiltrosPartidasPlan(filas: FilaPartidaPlan[]) {
  const filtros = useFiltros({ texto: '', maquina: '', dia: '', clasificacionColor: '', tonalidad: '', clasificacion: '', lavado: '', entregaDesde: '', entregaHasta: '' })
  const v = filtros.valores
  const texto = v.texto.trim().toLowerCase()
  const visibles = filas.filter((p) => (!texto || [p.numeroPartida, p.numeroOp, p.color, p.codigoColor].some((c) => c.toLowerCase().includes(texto)))
    && (!v.maquina || p.maquina === v.maquina) && (!v.dia || p.dia === v.dia)
    && (!v.clasificacionColor || p.clasificacionColor === v.clasificacionColor) && (!v.tonalidad || p.tonalidad === v.tonalidad)
    && (!v.clasificacion || p.clasificacion === v.clasificacion) && (!v.lavado || (v.lavado === 'SI') === p.requiereLavado)
    && enRango(p.fechaEntrega, v.entregaDesde, v.entregaHasta))
  return { filtros, visibles }
}

interface ControlesProps {
  estado: ReturnType<typeof useFiltrosPartidasPlan>
  filas: FilaPartidaPlan[]
  /** Muestra el filtro Asignada/Sobrante cuando la lista tiene las dos clasificaciones. */
  conClasificacion?: boolean
  visibles?: number
  extra?: ReactNode
  extraActivo?: boolean
  onLimpiarExtra?: () => void
}

export function ControlesPartidasPlan({ estado, filas, conClasificacion = false, visibles, extra, extraActivo = false, onLimpiarExtra }: ControlesProps) {
  const { filtros } = estado
  const v = filtros.valores
  return (
    <BarraFiltros visibles={visibles ?? estado.visibles.length} total={filas.length} activos={filtros.activos || extraActivo}
      onLimpiar={() => { filtros.limpiar(); onLimpiarExtra?.() }}>
      <FiltroTexto etiqueta="Buscar" valor={v.texto} placeholder="Partida, pedido o color…" onChange={(texto) => filtros.poner({ texto })} />
      {extra}
      {conClasificacion && <FiltroLista etiqueta="Clasificación" valor={v.clasificacion} onChange={(clasificacion) => filtros.poner({ clasificacion })}
        opciones={(['ASIGNADA', 'SOBRANTE'] as const).map((c) => ({ valor: c, texto: CLASIFICACION_RESULTADO[c] }))} />}
      <FiltroLista etiqueta="Máquina" valor={v.maquina} onChange={(maquina) => filtros.poner({ maquina })}
        opciones={sinRepetir(filas.map((p) => p.maquina)).map((m) => ({ valor: m, texto: m }))} />
      <FiltroLista etiqueta="Día" valor={v.dia} onChange={(dia) => filtros.poner({ dia })}
        opciones={sinRepetir(filas.map((p) => p.dia)).map((d) => ({ valor: d, texto: `${nombreDia(d)} ${fechaCorta(d)}` }))} />
      <FiltroLista etiqueta="Tipo de color" valor={v.clasificacionColor} onChange={(clasificacionColor) => filtros.poner({ clasificacionColor })}
        opciones={Object.entries(CLASIFICACION_COLOR).map(([valor, texto]) => ({ valor, texto }))} />
      <FiltroLista etiqueta="Tonalidad" valor={v.tonalidad} onChange={(tonalidad) => filtros.poner({ tonalidad })}
        opciones={Object.entries(TONALIDAD).map(([valor, texto]) => ({ valor, texto }))} />
      <FiltroLista etiqueta="Lavado" valor={v.lavado} onChange={(lavado) => filtros.poner({ lavado })}
        opciones={[{ valor: 'SI', texto: 'Requiere lavado' }, { valor: 'NO', texto: 'Sin lavado' }]} />
      <FiltroFechas etiqueta="Fecha de entrega" desde={v.entregaDesde} hasta={v.entregaHasta}
        onDesde={(entregaDesde) => filtros.poner({ entregaDesde })} onHasta={(entregaHasta) => filtros.poner({ entregaHasta })} />
    </BarraFiltros>
  )
}

/** Lista de partidas conformadas con sus filtros. */
export function ListaPartidasPlan({ filas, vacio }: { filas: FilaPartidaPlan[]; vacio: string }) {
  const estado = useFiltrosPartidasPlan(filas)
  if (filas.length === 0) return <SobrantesTable filas={filas} vacio={vacio} />
  return <div>
    <ControlesPartidasPlan estado={estado} filas={filas} />
    <SobrantesTable filas={estado.visibles} reinicio={estado.filtros.valores} vacio="Ninguna partida coincide con los filtros." />
  </div>
}
