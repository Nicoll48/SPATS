import { useState, type ReactNode } from 'react'
import { AlertTriangle, ArrowRight, CalendarRange, Gauge, LogIn, LogOut, Shuffle } from 'lucide-react'
import { Tabs, type TabItem } from '@/components/ui/Tabs'
import type { Comparacion, IncidenciaRegistrada, PlanificacionDetalle, Tonalidad, Ubicacion } from '@/api/tipos'
import { MUESTRA_TONALIDAD, TONALIDAD, TIPO_INCIDENCIA } from '@/lib/etiquetas'
import { diaMes, fechaHora, nombreDia } from '@/lib/fechas'

type Grupo = 'afectada' | 'entro' | 'salio' | 'movida'
interface Fila { partida: string; a: Ubicacion | null; b: Ubicacion | null; grupo: Grupo; motivo?: string }
interface DatosPartida { color: string; tonalidad: Tonalidad; numeroOp: string; motivo: string | null }

const ubicada = (u: Ubicacion | null) => !!u && u.clasificacion !== 'BLOQUEADA' && !!u.maquina

/** Datos de cada partida (color, tonalidad, pedido y motivo de bloqueo) tomados de las dos versiones. */
function datosPartidas(planes: (PlanificacionDetalle | null)[]) {
  const m = new Map<string, DatosPartida>()
  for (const plan of planes) {
    if (!plan) continue
    for (const maq of plan.maquinas) for (const d of maq.dias) for (const p of d.partidas)
      if (!m.has(p.numeroPartida)) m.set(p.numeroPartida, { color: p.color, tonalidad: p.tonalidad, numeroOp: p.numeroOp, motivo: null })
    for (const b of plan.bloqueadas)
      m.set(b.numeroPartida, { color: b.color, tonalidad: b.tonalidad, numeroOp: b.numeroOp, motivo: b.motivo ?? m.get(b.numeroPartida)?.motivo ?? null })
  }
  return m
}

/** La partida estaba (en la vigente) en algo que una incidencia afecta: máquina caída ese día, su complemento o su pedido. */
function afectadaPor(fila: { partida: string; a: Ubicacion | null }, numeroOp: string | undefined, incidencias: IncidenciaRegistrada[]) {
  return incidencias.find((i) => {
    if (i.tipo === 'PROBLEMA_COMPLEMENTO') return i.referencia === fila.partida
    if (i.tipo === 'CAMBIO_PRIORIDAD') return !!numeroOp && i.referencia === numeroOp
    if (!fila.a?.maquina || !fila.a.dia || fila.a.maquina !== i.referencia) return false
    const inicioDia = `${fila.a.dia}T00:00`
    const finDia = `${fila.a.dia}T23:59:59`
    return i.fechaHoraOcurrencia <= finDia && (!i.fechaHoraDisponibilidad || i.fechaHoraDisponibilidad > inicioDia)
  })
}

const TITULO: Record<Grupo, { titulo: string; texto: string; icono: typeof AlertTriangle; tono: string }> = {
  afectada: { titulo: 'Afectadas por la incidencia', texto: 'Estaban en la máquina, los días, la partida o el pedido que la incidencia afecta. Así quedan con la propuesta.', icono: AlertTriangle, tono: 'text-warning' },
  entro: { titulo: 'Ahora sí entran', texto: 'En la vigente estaban Bloqueadas (sin tela o sin lugar). Con la propuesta tienen máquina y día.', icono: LogIn, tono: 'text-success' },
  salio: { titulo: 'Ahora quedan bloqueadas', texto: 'En la vigente tenían máquina y día. Con la propuesta su tela o su lugar pasó a otras partidas. Pasa el puntero sobre «Bloqueada» para ver el motivo.', icono: LogOut, tono: 'text-danger' },
  movida: { titulo: 'Reubicadas por equilibrio', texto: 'La incidencia no las afectaba; el algoritmo las cambió de máquina o de día para equilibrar la carga de la semana.', icono: Shuffle, tono: 'text-accent' },
}

/**
 * Propuesta de re-planificación frente a la vigente, en pestañas como las de Planificación semanal: las partidas
 * afectadas por la incidencia, las que ahora entran, las que ahora quedan bloqueadas, las reubicadas por equilibrio,
 * la carga por máquina y el calendario de la propuesta. Cada partida muestra «en la vigente → con la propuesta».
 */
export function ResumenCambios({ comparacion, vigente, propuesta, incidencias, calendario }: {
  comparacion: Comparacion; vigente: PlanificacionDetalle | null; propuesta: PlanificacionDetalle | null; incidencias: IncidenciaRegistrada[]
  /** Calendario de la propuesta, en su propia pestaña. */
  calendario: ReactNode
}) {
  const datos = datosPartidas([propuesta, vigente])
  const filas: Fila[] = comparacion.cambios.map((c) => {
    const d = datos.get(c.numeroPartida)
    const inc = afectadaPor({ partida: c.numeroPartida, a: c.a }, d?.numeroOp, incidencias)
    const grupo: Grupo = inc ? 'afectada' : !ubicada(c.a) && ubicada(c.b) ? 'entro' : ubicada(c.a) && !ubicada(c.b) ? 'salio' : 'movida'
    return { partida: c.numeroPartida, a: c.a, b: c.b, grupo, motivo: inc ? `${TIPO_INCIDENCIA[inc.tipo].titulo} · ${inc.referencia}` : undefined }
  })
  const grupos = (['afectada', 'entro', 'salio', 'movida'] as Grupo[]).map((g) => ({ g, filas: filas.filter((f) => f.grupo === g) })).filter((x) => x.filas.length > 0)
  const maquinas = comparacion.maquinas.filter((m) => m.asignadasA !== m.asignadasB)
  const incMaquina = incidencias.filter((i) => i.tipo === 'INCIDENCIA_MAQUINA')

  const conteo = (g: Grupo) => filas.filter((f) => f.grupo === g).length
  const pestanas: TabItem[] = [
    ...(['afectada', 'entro', 'salio', 'movida'] as Grupo[]).map((g) => ({ id: g, label: TITULO[g].titulo, icon: TITULO[g].icono, count: conteo(g) })),
    { id: 'carga', label: 'Carga por máquina', icon: Gauge, count: maquinas.length },
    { id: 'calendario', label: 'Calendario de la propuesta', icon: CalendarRange },
  ]
  const [tab, setTab] = useState<string>(() => grupos[0]?.g ?? 'calendario')
  const grupoActivo = (['afectada', 'entro', 'salio', 'movida'] as string[]).includes(tab) ? tab as Grupo : null
  const filasActivas = grupoActivo ? filas.filter((f) => f.grupo === grupoActivo) : []

  return (
    <div>
      {incMaquina.length > 0 && (
        <p className="mb-3 text-sm text-ink-soft">{incMaquina.map((i) => `${i.referencia} fuera de servicio desde ${fechaHora(i.fechaHoraOcurrencia)}${i.fechaHoraDisponibilidad ? ` hasta ${fechaHora(i.fechaHoraDisponibilidad)}` : ' hasta fin de semana'}`).join(' · ')}.</p>
      )}
      <Tabs tabs={pestanas} activeId={tab} onChange={setTab} wrap />

      {tab === 'calendario' && calendario}

      {tab === 'carga' && (maquinas.length === 0
        ? <p className="text-sm text-ink-soft">Ninguna máquina cambia su cantidad de partidas asignadas.</p>
        : <>
          <p className="mb-3 text-sm text-ink-soft">Partidas asignadas en la semana a cada máquina, en la vigente y con la propuesta, y cuántas le faltan para su objetivo semanal.</p>
          <div className="overflow-auto rounded-xl border border-line bg-panel">
            <table className="w-full text-left text-sm">
              <thead><tr className="border-b border-line bg-panel-muted/60 text-xs text-ink-soft"><th className="px-4 py-2.5 font-semibold">Máquina</th><th className="px-4 py-2.5 font-semibold">En la vigente</th><th className="px-4 py-2.5 font-semibold">Con la propuesta</th><th className="px-4 py-2.5 font-semibold">Le faltan para su objetivo</th></tr></thead>
              <tbody>{maquinas.map((m) => (
                <tr key={m.codigo} className="border-b border-line last:border-0">
                  <td className="px-4 py-2 font-mono font-semibold text-ink">{m.codigo}</td>
                  <td className="px-4 py-2 font-mono text-ink-soft">{m.asignadasA ?? 0}</td>
                  <td className="px-4 py-2 font-mono font-semibold text-ink">{m.asignadasB ?? 0}</td>
                  <td className="px-4 py-2 text-ink-soft">{m.diferenciaB === null ? '—' : m.diferenciaB < 0 ? -m.diferenciaB : 'Ninguna'}</td>
                </tr>))}</tbody>
            </table>
          </div>
        </>)}

      {grupoActivo && <>
        <p className="mb-3 text-sm text-ink-soft">{TITULO[grupoActivo].texto}</p>
        {filasActivas.length === 0 ? <p className="rounded-xl border border-line bg-panel p-6 text-center text-sm text-ink-soft">No hay partidas en este grupo.</p> : (
          <div className="overflow-auto rounded-xl border border-line bg-panel">
            <table className="w-full min-w-[640px] text-left text-sm">
              <thead><tr className="border-b border-line bg-panel-muted/60 text-xs text-ink-soft">
                <th className="px-4 py-2.5 font-semibold">Partida</th><th className="px-4 py-2.5 font-semibold">Color</th><th className="px-4 py-2.5 font-semibold">Pedido</th>
                <th className="px-4 py-2.5 font-semibold">En la vigente</th><th className="px-1 py-2.5" /><th className="px-4 py-2.5 font-semibold">Con la propuesta</th>
                {grupoActivo === 'afectada' && <th className="px-4 py-2.5 font-semibold">Por</th>}
              </tr></thead>
              <tbody>{filasActivas.map((f) => {
                const d = datos.get(f.partida)
                return (
                  <tr key={f.partida} className="border-b border-line last:border-0">
                    <td className="px-4 py-2"><span className="flex items-center gap-1.5">
                      {d && <span className="h-2.5 w-2.5 shrink-0 rounded-full border border-black/20" style={{ background: MUESTRA_TONALIDAD[d.tonalidad] }} title={TONALIDAD[d.tonalidad]} aria-hidden="true" />}
                      <span className="font-mono font-bold text-ink">{f.partida}</span></span></td>
                    <td className="px-4 py-2 text-ink-soft">{d?.color ?? '—'}{d && <span className="block text-xs">{TONALIDAD[d.tonalidad]}</span>}</td>
                    <td className="px-4 py-2 font-mono text-ink-soft">{d?.numeroOp ?? '—'}</td>
                    <td className="px-4 py-2"><Lugar u={f.a} /></td>
                    <td className="px-1 py-2"><ArrowRight className="h-4 w-4 text-ink-faint" aria-hidden="true" /></td>
                    <td className="px-4 py-2"><Lugar u={f.b} destino motivo={!ubicada(f.b) ? d?.motivo ?? undefined : undefined} /></td>
                    {grupoActivo === 'afectada' && <td className="px-4 py-2 text-xs text-ink-soft">{f.motivo}</td>}
                  </tr>
                )
              })}</tbody>
            </table>
          </div>
        )}
      </>}
    </div>
  )
}

/** Caja de un lugar: «M05 · Jue 12 nov», o «Bloqueada» / «No figura». */
function Lugar({ u, destino = false, motivo }: { u: Ubicacion | null; destino?: boolean; motivo?: string }): ReactNode {
  if (!u || !u.maquina || u.clasificacion === 'BLOQUEADA') {
    return <span title={motivo} className={`rounded-md border px-2 py-0.5 text-xs font-medium ${destino ? 'border-warning/50 bg-warning-soft text-warning' : 'border-dashed border-line text-ink-soft'}`}>
      {u?.clasificacion === 'BLOQUEADA' ? 'Bloqueada' : 'Sin lugar'}</span>
  }
  const sobrante = u.clasificacion === 'SOBRANTE'
  return (
    <span className={`rounded-md border px-2 py-0.5 text-xs ${destino ? (sobrante ? 'border-danger/40 bg-danger-soft text-danger' : 'border-accent/30 bg-accent-soft text-ink') : 'border-line bg-panel-muted/60 text-ink-soft'}`}>
      <b className="font-mono">{u.maquina}</b> · {nombreDia(u.dia!)} {diaMes(u.dia!)}{sobrante ? ' · Sobrante' : ''}
    </span>
  )
}
