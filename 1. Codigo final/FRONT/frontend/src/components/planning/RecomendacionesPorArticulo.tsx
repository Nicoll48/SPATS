import { Fragment, useState } from 'react'
import { ChevronDown, ChevronRight, Link2 } from 'lucide-react'
import type { RecomendacionTejido } from '@/api/tipos'
import { kg } from '@/lib/etiquetas'
import { diaMes, fechaCorta, nombreDia } from '@/lib/fechas'

/** Una fila de recomendación (un componente de una partida) con la máquina en que llena. */
export type FilaReco = RecomendacionTejido & { maquina: string }

const dia = (iso: string) => `${nombreDia(iso)} ${diaMes(iso)}`

/** Ids de las filas de cada partida (principal y complemento van juntos). */
export function filasPorPartida(filas: FilaReco[]): Map<string, FilaReco[]> {
  const m = new Map<string, FilaReco[]>()
  for (const f of filas) m.set(f.partida, [...(m.get(f.partida) ?? []), f])
  return m
}

/**
 * Qué partidas quedan completas con lo seleccionado (todas sus telas marcadas) y cuáles a medias, con lo que les falta.
 * Una partida necesita su principal y su complemento: con una sola tela no se llena.
 */
export function estadoSeleccion(filas: FilaReco[], sel: Set<number>) {
  const completas = new Set<string>()
  const faltantes: FilaReco[] = []
  let incompletas = 0
  for (const [partida, fs] of filasPorPartida(filas)) {
    const marcadas = fs.filter((f) => sel.has(f.id)).length
    if (marcadas === fs.length) completas.add(partida)
    else if (marcadas > 0) { incompletas++; faltantes.push(...fs.filter((f) => !sel.has(f.id))) }
  }
  return { completas, incompletas, faltantes }
}

interface Articulo {
  codigo: string
  nombre: string
  componentes: Set<RecomendacionTejido['componente']>
  filas: FilaReco[]
  kg: number
  partidas: number
  pedidos: number
  /** Primer día de tejido: lo que se necesita antes. */
  desde: string
  porDia: [string, number][]
}

function agrupar(filas: FilaReco[]): Articulo[] {
  const m = new Map<string, FilaReco[]>()
  for (const f of filas) m.set(f.codigoArticulo, [...(m.get(f.codigoArticulo) ?? []), f])
  return [...m.entries()].map(([codigo, fs]) => {
    const porDia = new Map<string, number>()
    fs.forEach((f) => porDia.set(f.diaTejido, (porDia.get(f.diaTejido) ?? 0) + f.kg))
    const dias = [...porDia.entries()].sort(([a], [b]) => a.localeCompare(b))
    return {
      codigo, nombre: fs[0].nombreArticulo, componentes: new Set(fs.map((f) => f.componente)),
      filas: [...fs].sort((a, b) => a.diaLlena.localeCompare(b.diaLlena) || a.maquina.localeCompare(b.maquina)),
      kg: fs.reduce((s, f) => s + f.kg, 0), partidas: new Set(fs.map((f) => f.partida)).size,
      pedidos: new Set(fs.map((f) => f.numeroOp)).size, desde: dias[0][0], porDia: dias,
    }
  }).sort((a, b) => b.partidas - a.partidas || b.kg - a.kg || a.codigo.localeCompare(b.codigo))
}

const tipo = (c: Set<RecomendacionTejido['componente']>) =>
  c.size > 1 ? 'Principal y complemento' : c.has('PRINCIPAL') ? 'Principal' : 'Complemento'

/**
 * Recomendaciones por artículo (qué tejer): una fila por artículo con sus kg, cuántas partidas completa y desde qué día
 * se necesita. Al desplegarla, las partidas que abastece, en qué máquina y qué día llenan, y qué otra tela necesitan.
 */
export function RecomendacionesPorArticulo({ filas, marcadas, onAlternar }: {
  filas: FilaReco[]; marcadas: Set<number>; onAlternar: (ids: number[], marcar: boolean) => void
}) {
  const [abiertos, setAbiertos] = useState<Set<string>>(new Set())
  const articulos = agrupar(filas)
  const deLaPartida = filasPorPartida(filas)
  const todosIds = filas.map((f) => f.id)
  const todos = todosIds.length > 0 && todosIds.every((id) => marcadas.has(id))
  const alguno = todosIds.some((id) => marcadas.has(id))
  const alternarAbierto = (c: string) => setAbiertos((s) => { const n = new Set(s); if (n.has(c)) n.delete(c); else n.add(c); return n })

  return (
    <div className="overflow-x-auto rounded-xl border border-line bg-panel">
      <table className="w-full min-w-[760px] text-left text-sm">
        <thead><tr className="border-b border-line bg-panel-muted/60 text-xs text-ink-soft">
          <th className="w-10 px-3 py-2">
            <input type="checkbox" className="h-4 w-4" checked={todos} aria-label="Seleccionar todos los artículos"
              ref={(el) => { if (el) el.indeterminate = alguno && !todos }} onChange={() => onAlternar(todosIds, !todos)} /></th>
          <th className="w-8 px-1 py-2"><span className="sr-only">Desplegar</span></th>
          <th className="px-3 py-2 font-semibold">Artículo</th>
          <th className="px-3 py-2 font-semibold">Tipo</th>
          <th className="px-3 py-2 text-right font-semibold">Kg a tejer</th>
          <th className="px-3 py-2 text-right font-semibold">Partidas que completa</th>
          <th className="px-3 py-2 font-semibold" title="Primer día de tejido: lo que llena el día más temprano tiene que estar tejido a más tardar ese día">Se necesita desde</th>
        </tr></thead>
        <tbody>{articulos.map((a) => {
          const ids = a.filas.map((f) => f.id)
          const n = ids.filter((id) => marcadas.has(id)).length
          const abierto = abiertos.has(a.codigo)
          return (
            <Fragment key={a.codigo}>
              <tr className={`border-b border-line ${n === ids.length ? 'bg-accent-soft/50' : ''}`}>
                <td className="px-3 py-2.5">
                  <input type="checkbox" className="h-4 w-4" checked={n === ids.length} aria-label={`Seleccionar el artículo ${a.codigo}`}
                    ref={(el) => { if (el) el.indeterminate = n > 0 && n < ids.length }} onChange={() => onAlternar(ids, n < ids.length)} /></td>
                <td className="px-1 py-2.5">
                  <button type="button" onClick={() => alternarAbierto(a.codigo)} aria-expanded={abierto}
                    aria-label={`${abierto ? 'Ocultar' : 'Ver'} las partidas de ${a.codigo}`} className="rounded p-0.5 text-ink-soft hover:bg-panel-muted">
                    {abierto ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}</button></td>
                <td className="cursor-pointer px-3 py-2.5" onClick={() => alternarAbierto(a.codigo)}>
                  <span className="font-mono font-semibold text-ink">{a.codigo}</span>
                  <span className="block text-xs text-ink-soft">{a.nombre}{a.pedidos > 1 ? ` · ${a.pedidos} pedidos` : ''}</span></td>
                <td className="px-3 py-2.5 text-ink-soft">{tipo(a.componentes)}</td>
                <td className="px-3 py-2.5 text-right font-mono font-semibold text-ink">{kg(a.kg)}</td>
                <td className="px-3 py-2.5 text-right font-mono text-ink">{a.partidas}</td>
                <td className="px-3 py-2.5 text-ink">{dia(a.desde)}</td>
              </tr>
              {abierto && (
                <tr className="border-b border-line bg-panel-muted/30">
                  <td colSpan={7} className="px-4 py-3">
                    <p className="mb-2 text-xs text-ink-soft">
                      <b className="text-ink">Tejer a más tardar:</b>{' '}
                      {a.porDia.map(([d, k], i) => <span key={d}>{i > 0 && ' · '}{dia(d)} <b className="font-mono text-ink">{kg(k)}</b></span>)}
                    </p>
                    <table className="w-full text-left text-sm">
                      <thead><tr className="text-xs text-ink-soft">
                        <th className="w-8 py-1.5"><span className="sr-only">Seleccionar</span></th>
                        <th className="px-2 py-1.5 font-semibold">Partida</th><th className="px-2 py-1.5 font-semibold">Pedido</th>
                        <th className="px-2 py-1.5 font-semibold">Color</th><th className="px-2 py-1.5 font-semibold">Máquina</th>
                        <th className="px-2 py-1.5 font-semibold">Día que llena</th><th className="px-2 py-1.5 text-right font-semibold">Kg de este artículo</th>
                        <th className="px-2 py-1.5 font-semibold">También necesita</th><th className="px-2 py-1.5 font-semibold">Entrega</th>
                      </tr></thead>
                      <tbody>{a.filas.map((f) => {
                        const dePartida = deLaPartida.get(f.partida) ?? [f]
                        const otras = dePartida.filter((o) => o.id !== f.id)
                        const idsPartida = dePartida.map((o) => o.id)
                        const marcada = idsPartida.every((id) => marcadas.has(id))
                        return (
                          <tr key={f.id} className="border-t border-line">
                            <td className="py-1.5"><input type="checkbox" className="h-4 w-4" checked={marcada}
                              aria-label={`Seleccionar la partida ${f.partida} con todas sus telas`} onChange={() => onAlternar(idsPartida, !marcada)} /></td>
                            <td className="px-2 py-1.5 font-mono font-semibold text-ink">{f.partida}</td>
                            <td className="px-2 py-1.5 font-mono text-ink-soft">{f.numeroOp}</td>
                            <td className="px-2 py-1.5 text-ink-soft">{f.color}</td>
                            <td className="px-2 py-1.5 font-mono text-ink">{f.maquina}</td>
                            <td className="px-2 py-1.5 text-ink">{dia(f.diaLlena)}</td>
                            <td className="px-2 py-1.5 text-right font-mono text-ink">{kg(f.kg)}</td>
                            <td className="px-2 py-1.5 text-xs text-ink-soft">{otras.length === 0 ? '—' : otras.map((o) => (
                              <span key={o.id} className="flex items-center gap-1"><Link2 className="h-3 w-3 shrink-0" aria-hidden="true" />
                                <span className="font-mono">{o.codigoArticulo}</span> · {kg(o.kg)}</span>))}</td>
                            <td className="px-2 py-1.5 text-ink-soft">{f.fechaEntrega ? fechaCorta(f.fechaEntrega) : '—'}</td>
                          </tr>
                        )
                      })}</tbody>
                    </table>
                  </td>
                </tr>
              )}
            </Fragment>
          )
        })}</tbody>
      </table>
    </div>
  )
}
