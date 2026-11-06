import { useState } from 'react'
import { Beaker, Factory, FileDown } from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { Tabs, type TabItem } from '@/components/ui/Tabs'
import { StatusPill } from '@/components/ui/StatusPill'
import { DIAS_SEMANA, semanas } from '@/data/mockData'
import { descargarCSV } from '@/lib/exportCsv'

const VISTAS: TabItem[] = [{ id: 'laboratorio', label: 'Laboratorio', icon: Beaker }, { id: 'programacion', label: 'Programación', icon: Factory }]

export function ReportesPage() {
  const [vista, setVista] = useState('laboratorio')
  const semana = semanas.find((item) => item.vigente) ?? semanas[0]
  const filas = semana.filasCalendario.flatMap((maquina) => DIAS_SEMANA.flatMap((dia) => (maquina.bloquesPorDia[dia.key] ?? []).filter((bloque) => bloque.tipo === 'partida').map((bloque, indice) => bloque.tipo === 'partida' ? ({ maquina: maquina.codigo, dia: dia.largo, secuencia: indice + 1, ...bloque }) : null))).filter((fila): fila is NonNullable<typeof fila> => fila !== null)
  const exportar = () => descargarCSV(`reporte_${vista}.csv`, [['Máquina', 'Día', 'Partida', 'Color', 'Inicio', 'Fin'], ...filas.map((fila) => [fila.maquina, fila.dia, fila.codigo, fila.color, String(fila.horaInicio), String(fila.horaFin)])])
  return <>
    <PageHeader title="Reportes" description="Salidas para laboratorio y programación de tintorería." actions={<button type="button" onClick={exportar} className="inline-flex items-center gap-1.5 rounded-lg border border-line bg-panel px-3 py-2 text-sm"><FileDown className="h-4 w-4" />Descargar CSV</button>} />
    <Tabs tabs={VISTAS} activeId={vista} onChange={setVista} />
    <div className="overflow-hidden rounded-xl border border-line bg-panel"><div className="overflow-x-auto"><table className="w-full min-w-[680px] text-left text-sm"><thead><tr className="border-b border-line text-xs uppercase text-ink-faint"><th className="px-4 py-3">{vista === 'laboratorio' ? 'Día' : 'Máquina'}</th><th className="px-4 py-3">Partida</th><th className="px-4 py-3">Color</th><th className="px-4 py-3">{vista === 'laboratorio' ? 'Estado de receta' : 'Horario'}</th></tr></thead><tbody className="divide-y divide-line">{filas.map((fila) => <tr key={`${fila.maquina}-${fila.dia}-${fila.id}`}><td className="px-4 py-3">{vista === 'laboratorio' ? fila.dia : fila.maquina}</td><td className="px-4 py-3 font-mono">{fila.codigo}</td><td className="px-4 py-3">{fila.color}</td><td className="px-4 py-3">{vista === 'laboratorio' ? <StatusPill tone="warning" label="En preparación" /> : `${fila.horaInicio}:00–${fila.horaFin}:00`}</td></tr>)}</tbody></table></div></div>
  </>
}
