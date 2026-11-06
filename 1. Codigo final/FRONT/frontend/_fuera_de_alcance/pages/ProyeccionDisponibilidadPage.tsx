import { useState } from 'react'
import { LayoutGrid, Rows3 } from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { Tabs, type TabItem } from '@/components/ui/Tabs'
import { StatusPill } from '@/components/ui/StatusPill'
import { maquinas, semanas } from '@/data/mockData'

const VISTAS: TabItem[] = [
  { id: 'dia', label: 'Por día', icon: Rows3 },
  { id: 'maquina', label: 'Por máquina', icon: LayoutGrid },
]

export function ProyeccionDisponibilidadPage() {
  const [vista, setVista] = useState('dia')
  const semana = semanas.find((item) => item.vigente) ?? semanas[0]
  return <>
    <PageHeader title="Proyección de disponibilidad" description="Partidas disponibles y compatibilidad prevista para la semana vigente." />
    <Tabs tabs={VISTAS} activeId={vista} onChange={setVista} />
    {vista === 'dia' ? (
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">{semana.filasCalendario.slice(0, 6).map((fila) => <section key={fila.maquinaId} className="rounded-xl border border-line bg-panel p-4"><h2 className="font-mono text-sm font-semibold text-ink">{fila.codigo}</h2><ul className="mt-3 space-y-2">{Object.entries(fila.bloquesPorDia).flatMap(([dia, bloques]) => bloques.filter((bloque) => bloque.tipo === 'partida').map((bloque) => bloque.tipo === 'partida' && <li key={bloque.id} className="flex items-center justify-between gap-2 text-xs"><span className="text-ink-soft">{dia.toUpperCase()} · {bloque.codigo} · {bloque.color}</span><StatusPill tone="success" label="Completa" /></li>))}</ul></section>)}</div>
    ) : (
      <div className="overflow-hidden rounded-xl border border-line bg-panel"><table className="w-full text-left text-sm"><thead><tr className="border-b border-line text-xs uppercase text-ink-faint"><th className="px-4 py-3">Máquina</th><th className="px-4 py-3">Clasificación</th><th className="px-4 py-3">Disponibilidad</th></tr></thead><tbody className="divide-y divide-line">{maquinas.map((maquina) => <tr key={maquina.id}><td className="px-4 py-3 font-mono">{maquina.codigo}</td><td className="px-4 py-3">{maquina.clasificacionColor}</td><td className="px-4 py-3"><StatusPill tone={maquina.estado === 'Operativa' ? 'success' : 'warning'} label={maquina.estado === 'Operativa' ? 'Proyectada' : 'Pendiente de complemento'} /></td></tr>)}</tbody></table></div>
    )}
  </>
}
