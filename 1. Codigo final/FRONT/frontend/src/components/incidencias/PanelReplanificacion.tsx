import { useEffect, useState } from 'react'
import { ArrowRight, CheckCircle2, Info, Loader2, RefreshCw, Trash2 } from 'lucide-react'
import { AvisoError, Cargando, ListaAdvertencias } from '@/components/common/AvisoError'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { PlanningGrid, PlanningGridLegend } from '@/components/planning/PlanningGrid'
import { MENSAJE_SIN_PERMISO } from '@/auth/auth-context'
import { planificacion, replanificacion } from '@/api/servicios'
import type { ErrorApi } from '@/api/cliente'
import type { IncidenciaRegistrada, ReplanificacionResponse, Ubicacion } from '@/api/tipos'
import { intentar, useDatos } from '@/lib/useDatos'
import { CLASIFICACION_RESULTADO, TIPO_INCIDENCIA } from '@/lib/etiquetas'
import { fechaCorta, nombreDia } from '@/lib/fechas'
import { Paginado } from '@/components/ui/Paginacion'

interface Props {
  lunes: string
  incidencias: IncidenciaRegistrada[]
  esPlanificador: boolean
  /** Se llama cuando cambian las incidencias o la vigente (confirmar o descartar). */
  onCambio: () => Promise<void> | void
}

/**
 * Re-planificación desde hoy (HU-18): el planificador elige las incidencias pendientes y marca las partidas de hoy
 * que ya entraron a máquina. Si el sistema propone una versión nueva, se revisa y se confirma o descarta el mismo día.
 */
export function PanelReplanificacion({ lunes, incidencias, esPlanificador, onCambio }: Props) {
  const contexto = useDatos(() => replanificacion.contexto(lunes), [lunes])
  const [seleccion, setSeleccion] = useState<Set<number>>(new Set())
  const [enMaquina, setEnMaquina] = useState<Set<string>>(new Set())
  const [ejecutando, setEjecutando] = useState(false)
  const [resultado, setResultado] = useState<ReplanificacionResponse | null>(null)
  const [error, setError] = useState<ErrorApi | null>(null)
  const [mensaje, setMensaje] = useState<string | null>(null)

  useEffect(() => { setSeleccion(new Set()); setEnMaquina(new Set()); setResultado(null); setError(null); setMensaje(null) }, [lunes])

  const ctx = contexto.datos
  const pendientes = incidencias.filter((i) => i.estado === 'PENDIENTE')
  const propuesta = ctx?.propuestaPendiente ?? null

  function alternar<T>(conjunto: Set<T>, valor: T): Set<T> {
    const nuevo = new Set(conjunto)
    if (nuevo.has(valor)) nuevo.delete(valor)
    else nuevo.add(valor)
    return nuevo
  }

  async function replanificar() {
    setEjecutando(true)
    setError(null)
    setMensaje(null)
    const r = await intentar(() => replanificacion.replanificar(lunes, [...seleccion], [...enMaquina]))
    setEjecutando(false)
    if (!r.ok) { setError(r.error); return }
    setResultado(r.valor)
    if (r.valor.resultado === 'PROPUESTA') { setSeleccion(new Set()); setEnMaquina(new Set()) }
    await contexto.recargar()
  }

  async function terminar(texto: string) {
    setResultado(null)
    setMensaje(texto)
    await Promise.all([contexto.recargar(), onCambio()])
  }

  const motivoBoton = !esPlanificador ? MENSAJE_SIN_PERMISO : seleccion.size === 0 ? 'Selecciona al menos una incidencia pendiente' : null

  return (
    <section className="mb-6 rounded-xl border border-line bg-panel-muted p-4">
      <div className="flex items-start gap-2.5"><Info className="mt-0.5 h-4 w-4 shrink-0 text-accent" /><div className="text-sm text-ink-soft">
        <p className="font-medium text-ink">Re-planificación desde hoy</p>
        <p className="mt-1">Lo ubicado antes de hoy no cambia. Desde hoy se vuelve a decidir con las incidencias seleccionadas, consideradas juntas. El resultado es una propuesta: solo pasa a ser la planificación vigente si la confirmas hoy.</p></div></div>

      <div className="mt-4">
        <AvisoError error={error ?? contexto.error} onCerrar={() => setError(null)} />
        {mensaje && <p role="status" className="mb-4 flex items-center gap-2 rounded-xl border border-success/40 bg-success-soft p-3.5 text-sm text-success"><CheckCircle2 className="h-4 w-4" />{mensaje}</p>}
        {contexto.cargando && !ctx ? <Cargando /> : !ctx ? null : propuesta && ctx.idVigente !== null
          ? <PropuestaPendiente idVigente={ctx.idVigente} idPropuesta={propuesta.id} numeroVersion={propuesta.numeroVersion}
              resultado={resultado?.idPropuesta === propuesta.id ? resultado : null} esPlanificador={esPlanificador}
              onTerminar={terminar} onError={setError} />
          : !ctx.permitida
            ? <p className="text-sm text-ink-soft">{ctx.motivo}</p>
            : <div className="grid gap-4 lg:grid-cols-2">
                <div>
                  <p className="mb-2 text-xs font-semibold uppercase text-ink-faint">Incidencias a considerar</p>
                  {pendientes.length === 0 ? <p className="text-sm text-ink-soft">No hay incidencias pendientes en esta semana.</p>
                    : <Paginado items={pendientes}>{(pagina) => <ul className="flex flex-col gap-1.5">{pagina.map((i) => (
                        <li key={i.id}><label className="flex items-center gap-2 text-sm">
                          <input type="checkbox" disabled={!esPlanificador} checked={seleccion.has(i.id)} onChange={() => setSeleccion((s) => alternar(s, i.id))} />
                          <span>{TIPO_INCIDENCIA[i.tipo].titulo} · {i.entidadAfectada} <span className="font-mono">{i.referencia}</span></span></label></li>))}</ul>}</Paginado>}
                </div>
                <div>
                  <p className="mb-2 text-xs font-semibold uppercase text-ink-faint">Partidas de hoy ({nombreDia(ctx.dia)} {fechaCorta(ctx.dia)}) que ya entraron a máquina</p>
                  {ctx.partidasDelDia.length === 0 ? <p className="text-sm text-ink-soft">La planificación vigente no tiene partidas ubicadas hoy.</p>
                    : <><p className="mb-2 text-xs text-ink-soft">Las que marques quedan como están; las demás de hoy se vuelven a decidir.</p>
                      <Paginado items={ctx.partidasDelDia}>{(pagina) => <ul className="flex flex-col gap-1.5">{pagina.map((p) => (
                        <li key={p.numeroPartida}><label className="flex items-center gap-2 text-sm">
                          <input type="checkbox" disabled={!esPlanificador} checked={enMaquina.has(p.numeroPartida)} onChange={() => setEnMaquina((s) => alternar(s, p.numeroPartida))} />
                          <span className="font-mono">{p.numeroPartida}</span><span className="text-ink-soft">· {p.maquina} · {CLASIFICACION_RESULTADO[p.clasificacion]}</span></label></li>))}</ul>}</Paginado></>}
                </div>
                <div className="lg:col-span-2">
                  <button type="button" onClick={replanificar} disabled={motivoBoton !== null || ejecutando} title={motivoBoton ?? 'Ejecuta el algoritmo desde hoy con las incidencias seleccionadas'}
                    className="inline-flex items-center gap-1.5 rounded-lg bg-accent px-3 py-2 text-sm font-medium text-accent-ink disabled:opacity-40">
                    {ejecutando ? <Loader2 className="h-4 w-4 animate-spin" /> : <RefreshCw className="h-4 w-4" />}{ejecutando ? 'Re-planificando…' : 'Re-planificar desde hoy'}</button>
                </div>
              </div>}
        {resultado?.resultado === 'SE_CONSERVA_VIGENTE' && (
          <div role="alert" className="mt-4 rounded-xl border border-warning/40 bg-warning-soft p-4 text-sm text-warning">{resultado.advertencias[0]}</div>)}
        {resultado && <div className="mt-4"><ListaAdvertencias titulo="Avisos de la re-planificación"
          avisos={resultado.resultado === 'SE_CONSERVA_VIGENTE' ? resultado.advertencias.slice(1) : resultado.advertencias} /></div>}
      </div>
    </section>
  )
}

function PropuestaPendiente({ idVigente, idPropuesta, numeroVersion, resultado, esPlanificador, onTerminar, onError }: {
  idVigente: number; idPropuesta: number; numeroVersion: number; resultado: ReplanificacionResponse | null; esPlanificador: boolean
  onTerminar: (mensaje: string) => Promise<void>; onError: (e: ErrorApi) => void
}) {
  const comparacion = useDatos(() => planificacion.comparar(idVigente, idPropuesta), [idVigente, idPropuesta])
  const detalle = useDatos(() => planificacion.detalle(idPropuesta), [idPropuesta])
  const [confirmando, setConfirmando] = useState<'confirmar' | 'descartar' | null>(null)

  async function confirmar() {
    setConfirmando(null)
    const r = await intentar(() => replanificacion.confirmar(idPropuesta))
    if (!r.ok) { onError(r.error); await onTerminar(''); return }
    await onTerminar(`Re-planificación confirmada: la versión ${r.valor.numeroVersion} es la planificación vigente. `
      + `${r.valor.incidenciasConsideradas} incidencias consideradas; ${r.valor.pedidosActualizados} pedidos con fecha de entrega nueva.`)
  }

  async function descartar() {
    setConfirmando(null)
    const r = await intentar(() => replanificacion.descartar(idPropuesta))
    if (!r.ok) { onError(r.error); return }
    await onTerminar('La propuesta se descartó. La planificación vigente no cambió y las incidencias siguen pendientes.')
  }

  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-warning/40 bg-warning-soft p-4 text-sm text-warning">
        <p className="font-medium">Propuesta: versión {numeroVersion}, sin confirmar</p>
        <p className="mt-1">Si la confirmas, pasa a ser la planificación vigente, las incidencias quedan consideradas y los pedidos con cambio de prioridad toman su nueva fecha de entrega. Solo puedes confirmarla hoy; si no, se descarta.</p>
      </div>
      {resultado && !resultado.vigenteFactible
        ? <p className="text-sm text-warning">La vigente ya no cumple las reglas con estas incidencias: {resultado.incumplimientosVigente.join(' · ')}</p>
        : <p className="text-sm text-ink-soft">Con estas incidencias, la propuesta reparte la carga mejor que la planificación vigente.</p>}
      {comparacion.error && <AvisoError error={comparacion.error} />}
      {comparacion.datos && <>
        <p className="text-sm text-ink-soft">{comparacion.datos.cambios.length} partidas cambian de máquina, día o clasificación respecto de la vigente.</p>
        {comparacion.datos.cambios.length > 0 && <Paginado items={comparacion.datos.cambios}>{(pagina) => <div className="overflow-auto rounded-xl border border-line bg-panel"><table className="w-full text-left text-sm">
          <thead><tr className="border-b border-line text-xs uppercase text-ink-faint"><th className="px-4 py-3">Partida</th><th className="px-4 py-3">Vigente</th><th className="px-4 py-3" /><th className="px-4 py-3">Propuesta</th></tr></thead>
          <tbody>{pagina.map((c) => <tr key={c.numeroPartida} className="border-b border-line last:border-0"><td className="px-4 py-2 font-mono">{c.numeroPartida}</td>
            <td className="px-4 py-2">{ubicacion(c.a)}</td><td className="px-2 py-2"><ArrowRight className="h-3.5 w-3.5 text-ink-faint" /></td><td className="px-4 py-2">{ubicacion(c.b)}</td></tr>)}</tbody></table></div>}</Paginado>}
      </>}
      {detalle.cargando ? <Cargando texto="Cargando el calendario de la propuesta…" /> : detalle.datos && <><PlanningGridLegend /><PlanningGrid dias={detalle.datos.dias} maquinas={detalle.datos.maquinas} /></>}
      <div className="flex flex-wrap gap-2">
        <button type="button" disabled={!esPlanificador} title={esPlanificador ? undefined : MENSAJE_SIN_PERMISO} onClick={() => setConfirmando('confirmar')}
          className="inline-flex items-center gap-1.5 rounded-lg bg-accent px-3 py-2 text-sm font-medium text-accent-ink disabled:opacity-40"><CheckCircle2 className="h-4 w-4" />Confirmar propuesta</button>
        <button type="button" disabled={!esPlanificador} title={esPlanificador ? undefined : MENSAJE_SIN_PERMISO} onClick={() => setConfirmando('descartar')}
          className="inline-flex items-center gap-1.5 rounded-lg border border-line bg-panel px-3 py-2 text-sm font-medium text-ink disabled:opacity-40"><Trash2 className="h-4 w-4" />Descartar</button>
      </div>
      <ConfirmDialog open={confirmando === 'confirmar'} title="Confirmar la re-planificación"
        description="La propuesta pasa a ser la planificación vigente y la actual queda en el historial como reemplazada. Las incidencias quedan consideradas y los pedidos con cambio de prioridad toman su nueva fecha de entrega."
        confirmLabel="Confirmar" onConfirm={confirmar} onCancel={() => setConfirmando(null)} />
      <ConfirmDialog open={confirmando === 'descartar'} title="Descartar la propuesta" tone="danger"
        description="La propuesta se elimina y no queda en el historial. La planificación vigente no cambia y las incidencias siguen pendientes."
        confirmLabel="Descartar" onConfirm={descartar} onCancel={() => setConfirmando(null)} />
    </div>
  )
}

function ubicacion(u: Ubicacion | null) {
  if (!u) return <span className="text-ink-faint">No figura</span>
  if (u.clasificacion === 'BLOQUEADA') return <span className="text-warning">Bloqueada</span>
  return <span>{CLASIFICACION_RESULTADO[u.clasificacion]} · {u.maquina} · {fechaCorta(u.dia)}</span>
}
