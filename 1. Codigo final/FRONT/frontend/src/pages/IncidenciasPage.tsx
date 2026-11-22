import { useState } from 'react'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { inputClass } from '@/components/ui/FormField'
import { useAvisos } from '@/avisos/avisos-context'
import type { ErrorApi } from '@/api/cliente'
import type { IncidenciaRegistrada } from '@/api/tipos'
import { Link } from 'react-router-dom'
import { AlertTriangle, CircleHelp, Play, Plus, RefreshCw } from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { SoloLecturaAviso } from '@/components/common/SoloLecturaAviso'
import { AvisoError, Cargando } from '@/components/common/AvisoError'
import { StatusPill } from '@/components/ui/StatusPill'
import { WeekSelector } from '@/components/planning/WeekSelector'
import { DialogoIncidencia, accionDeLaSemana } from '@/components/incidencias/DialogoIncidencia'
import { MENSAJE_SIN_PERMISO, useAuth } from '@/auth/auth-context'
import { useSemana } from '@/semana/semana-context'
import { useExperience } from '@/experience/experience-context'
import { incidencias as api, maquinas, planificacion, replanificacion } from '@/api/servicios'
import { intentar, useDatos } from '@/lib/useDatos'
import { TIPO_INCIDENCIA } from '@/lib/etiquetas'
import { etiquetaSemana, fechaCorta, fechaHora } from '@/lib/fechas'
import { Paginado } from '@/components/ui/Paginacion'

/**
 * Incidencias de la semana (HU-17) y su aplicación (HU-18). La pantalla solo lista las incidencias; registrar,
 * re-planificar desde hoy (semana en curso) o volver a generar (semana que aún no empieza) se hace en un mismo panel.
 * La propuesta de re-planificación se revisa en Planificación semanal, junto al calendario.
 */
export function IncidenciasPage() {
  const { esPlanificador } = useAuth()
  const { isGuided } = useExperience()
  const { lunes } = useSemana()
  const lista = useDatos(() => api.deLaSemana(lunes), [lunes])
  const estado = useDatos(() => planificacion.estadoSemana(lunes), [lunes])
  const contexto = useDatos(() => replanificacion.contexto(lunes), [lunes])
  const parque = useDatos(() => maquinas.buscar(), [])
  const [dialogo, setDialogo] = useState<'registrar' | 'aplicar' | null>(null)
  /** D-57: tras anular una incidencia considerada en la semana en curso, se re-planifica desde hoy sin ella. */
  const [replanPorAnulacion, setReplanPorAnulacion] = useState(false)
  const [porAnular, setPorAnular] = useState<IncidenciaRegistrada | null>(null)
  const [motivoAnulacion, setMotivoAnulacion] = useState('')
  const [anulando, setAnulando] = useState(false)
  const [porCerrar, setPorCerrar] = useState<IncidenciaRegistrada | null>(null)
  const [motivoCierre, setMotivoCierre] = useState('')
  const [errorCierre, setErrorCierre] = useState<ErrorApi | null>(null)
  const avisar = useAvisos()

  /** D-54: cerrar una incidencia pendiente sin re-planificar (la resolvió el planificador en planta). */
  async function cerrarSinReplanificar() {
    const i = porCerrar
    setPorCerrar(null)
    if (!i) return
    setErrorCierre(null)
    const r = await intentar(() => api.atender(lunes, i.id, motivoCierre.trim() || undefined))
    if (!r.ok) { setErrorCierre(r.error); return }
    avisar({ tipo: 'success', titulo: 'Incidencia cerrada sin re-planificar', detalle: 'Ya no se considerará al re-planificar ni al generar la semana. La planificación vigente no cambia.' })
    await Promise.all([lista.recargar(), contexto.recargar()])
  }

  /**
   * D-57: anula la incidencia. Si estaba considerada, su efecto se deshace volviendo a generar la semana (si aún no
   * empieza) o re-planificando desde hoy (si ya empezó), según lo que elija el planificador.
   */
  async function anular(deshacer: boolean) {
    const i = porAnular
    if (!i) return
    setAnulando(true)
    setErrorCierre(null)
    const r = await intentar(() => api.anular(lunes, i.id, motivoAnulacion.trim() || undefined))
    if (!r.ok) { setAnulando(false); setPorAnular(null); setErrorCierre(r.error); return }
    const considerada = i.estado === 'CONSIDERADA'
    if (deshacer && considerada && accion === 'regenerar') {
      const g = await intentar(() => planificacion.generar(lunes))
      setAnulando(false)
      setPorAnular(null)
      if (!g.ok) { setErrorCierre(g.error); await recargar(); return }
      avisar({ tipo: 'success', titulo: `Incidencia anulada y semana generada otra vez (versión ${g.valor.numeroVersion})`, detalle: 'La planificación vigente ya no la aplica.' })
      await recargar()
      return
    }
    setAnulando(false)
    setPorAnular(null)
    avisar({ tipo: 'success', titulo: 'Incidencia anulada', detalle: considerada
      ? (i.tipo === 'CAMBIO_PRIORIDAD' ? 'El pedido volvió a su fecha de entrega anterior. ' : '') + 'La planificación vigente todavía tiene su efecto hasta que vuelvas a generar o re-planificar.'
      : 'Ya no se considerará al re-planificar ni al generar la semana.' })
    await recargar()
    if (deshacer && considerada && accion === 'replanificar') { setReplanPorAnulacion(true); setDialogo('aplicar') }
  }

  async function recargar() { await Promise.all([lista.recargar(), estado.recargar(), contexto.recargar()]) }

  const vigente = estado.datos?.planificacionVigente
  const abierta = vigente?.estado === 'ABIERTA'
  const propuesta = estado.datos?.propuestaPendiente ?? null
  const pendientes = (lista.datos ?? []).filter((i) => i.estado === 'PENDIENTE')
  const accion = accionDeLaSemana(lunes, contexto.datos, abierta)

  const motivoRegistrar = !esPlanificador ? MENSAJE_SIN_PERMISO : !vigente ? 'La semana no tiene planificación vigente'
    : !abierta ? 'La semana ya está cerrada' : propuesta ? 'Hay una re-planificación propuesta sin confirmar: revísala en Planificación semanal' : null
  const motivoAplicar = motivoRegistrar ?? (pendientes.length === 0 ? 'No hay incidencias pendientes' : accion === null ? contexto.datos?.motivo ?? 'No se puede aplicar ahora' : null)

  return <>
    <PageHeader title="Incidencias y replanificación" description="Registra lo que pasó y que cambia la planificación vigente (el sistema no lo detecta solo). Desde el mismo panel puedes aplicarlo a la planificación."
      actions={<>
        {pendientes.length > 0 && accion !== null && (
          <button type="button" disabled={motivoAplicar !== null} title={motivoAplicar ?? undefined} onClick={() => setDialogo('aplicar')}
            className="inline-flex items-center gap-1.5 rounded-lg border border-accent bg-panel px-3 py-2 text-sm font-medium text-accent hover:bg-accent-soft disabled:opacity-40">
            {accion === 'replanificar' ? <RefreshCw className="h-4 w-4" /> : <Play className="h-4 w-4" />}
            {accion === 'replanificar' ? 'Re-planificar desde hoy' : 'Volver a generar con las incidencias'}</button>)}
        <button type="button" disabled={motivoRegistrar !== null} title={motivoRegistrar ?? 'Registrar una incidencia'} onClick={() => setDialogo('registrar')}
          className="inline-flex items-center gap-1.5 rounded-lg bg-accent px-3 py-2 text-sm font-medium text-accent-ink disabled:opacity-40"><Plus className="h-4 w-4" />Registrar incidencia</button>
      </>} />
    <SoloLecturaAviso />
    <div className="mb-5 flex flex-wrap items-center gap-3"><WeekSelector />
      <span className="text-sm text-ink-soft">{vigente ? `Planificación vigente: versión ${vigente.numeroVersion}` : `Sin planificación vigente para la semana del ${etiquetaSemana(lunes)}`}</span></div>

    {isGuided && esPlanificador && (
      <section className="mb-5 rounded-2xl border border-accent/25 bg-accent-soft/60 p-5">
        <div className="flex items-start gap-3"><CircleHelp className="mt-0.5 h-5 w-5 shrink-0 text-accent" aria-hidden="true" />
          <div className="text-sm text-ink-soft">
            <p className="text-xs font-semibold uppercase tracking-wide text-accent">Cómo funciona</p>
            <ol className="mt-2 list-decimal space-y-1 pl-5">
              <li><b className="text-ink">Registra la incidencia</b>: qué pasó (máquina, complemento o fecha de un pedido) y cuándo.</li>
              <li>Si es de la <b className="text-ink">semana en curso</b>, marca las partidas de hoy que ya entraron a máquina y <b className="text-ink">re-planifica desde hoy</b>: lo anterior no cambia.</li>
              <li>Si la semana <b className="text-ink">todavía no empieza</b>, se <b className="text-ink">vuelve a generar</b> completa con las incidencias.</li>
              <li>La propuesta de re-planificación se revisa y se confirma en <b className="text-ink">Planificación semanal</b>.</li>
            </ol>
          </div>
        </div>
      </section>
    )}

    {propuesta && (
      <div role="status" className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-warning/40 bg-warning-soft p-3.5 text-sm text-warning">
        <p className="flex items-center gap-2"><AlertTriangle className="h-4 w-4 shrink-0" aria-hidden="true" />Hay una re-planificación propuesta (versión {propuesta.numeroVersion}) sin confirmar. Revísala hoy, o se descartará.</p>
        <Link to="/" className="rounded-lg border border-line bg-panel px-3 py-1.5 text-sm font-medium text-ink hover:bg-panel-muted">Revisar en Planificación semanal</Link>
      </div>
    )}

    {(lista.error || estado.error) && <AvisoError error={lista.error ?? estado.error} />}
    <AvisoError error={errorCierre} onCerrar={() => setErrorCierre(null)} />
    {lista.cargando ? <Cargando /> : (lista.datos ?? []).length === 0
      ? <div className="rounded-xl border border-line bg-panel p-8 text-center text-sm text-ink-soft">No hay incidencias registradas en esta semana.</div>
      : <Paginado items={lista.datos ?? []} reinicio={lunes}>{(pagina) => <ul className="flex flex-col gap-3">{pagina.map((i) => (
          <li key={i.id} className="rounded-xl border border-line bg-panel p-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <p className="flex items-center gap-2 font-medium text-ink"><AlertTriangle className="h-4 w-4 text-warning" />{TIPO_INCIDENCIA[i.tipo].titulo} · {i.entidadAfectada} <span className="font-mono">{i.referencia}</span></p>
              <span className="flex items-center gap-2">
                <StatusPill tone={i.estado === 'PENDIENTE' ? 'warning' : i.estado === 'CONSIDERADA' ? 'success' : i.estado === 'ANULADA' ? 'danger' : 'neutral'}
                  label={i.estado === 'PENDIENTE' ? 'Pendiente' : i.estado === 'CONSIDERADA' ? 'Considerada' : i.estado === 'ANULADA' ? 'Anulada' : 'Cerrada sin re-planificar'} />
                {i.estado === 'PENDIENTE' && esPlanificador && (
                  <button type="button" disabled={!!propuesta} onClick={() => { setMotivoCierre(''); setPorCerrar(i) }}
                    title={propuesta ? 'Hay una re-planificación propuesta sin confirmar que ya la incluye' : 'La resolviste en planta o ya no aplica: deja de considerarse'}
                    className="rounded-lg border border-line bg-panel px-2.5 py-1 text-xs font-medium text-ink hover:bg-panel-muted disabled:opacity-40">Cerrar sin re-planificar</button>
                )}
                {i.estado !== 'ANULADA' && esPlanificador && abierta && (
                  <button type="button" disabled={!!propuesta} onClick={() => { setMotivoAnulacion(''); setPorAnular(i) }}
                    title={propuesta ? 'Hay una re-planificación propuesta sin confirmar: confírmala o descártala primero' : 'Se registró por error o al final no ocurrió'}
                    className="rounded-lg border border-danger/40 bg-panel px-2.5 py-1 text-xs font-medium text-danger hover:bg-danger-soft disabled:opacity-40">Anular</button>
                )}
              </span>
            </div>
            {i.descripcion && <p className="mt-2 text-sm text-ink-soft">{i.descripcion}</p>}
            <p className="mt-2 text-xs text-ink-soft">Ocurrió el {fechaHora(i.fechaHoraOcurrencia)}
              {i.fechaHoraDisponibilidad && ` · disponible desde ${fechaHora(i.fechaHoraDisponibilidad)}`}
              {i.nuevaFechaEntrega && ` · nueva fecha de entrega ${fechaCorta(i.nuevaFechaEntrega)}`}
              {` · registrada por ${i.registradaPor} sobre la versión ${i.versionPlanificacionOrigen}`}</p>
          </li>))}</ul>}</Paginado>}

    <ConfirmDialog open={porCerrar !== null} title="Cerrar la incidencia sin re-planificar" confirmLabel="Cerrar incidencia"
      description={<div className="space-y-3">
        <p>Úsalo si la resolviste en planta (por ejemplo, teñiste las partidas en otra máquina) o si ya no aplica. La planificación vigente no cambia y la incidencia deja de considerarse al re-planificar o generar. Las partidas que no se tiñan quedarán como arrastradas al cerrar la semana.</p>
        <label className="block text-sm"><span className="mb-1 block font-medium text-ink">Motivo (opcional)</span>
          <textarea rows={2} maxLength={200} className={inputClass} value={motivoCierre} onChange={(e) => setMotivoCierre(e.target.value)} placeholder="Ej.: se tiñeron en la M06" /></label>
      </div>}
      onConfirm={cerrarSinReplanificar} onCancel={() => setPorCerrar(null)} />

    {porAnular && (
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
        <button type="button" aria-label="Cerrar" onClick={() => !anulando && setPorAnular(null)} className="absolute inset-0 bg-black/50" />
        <div role="dialog" aria-modal="true" aria-labelledby="titulo-anular" className="relative w-full max-w-lg rounded-2xl border border-line bg-panel p-6 shadow-xl">
          <h2 id="titulo-anular" className="text-lg font-semibold text-ink">Anular la incidencia</h2>
          <p className="mt-1 text-sm text-ink-soft">{TIPO_INCIDENCIA[porAnular.tipo].titulo} · {porAnular.entidadAfectada} <span className="font-mono">{porAnular.referencia}</span></p>
          <div className="mt-4 space-y-3 text-sm text-ink-soft">
            <p>Úsalo si se registró por error o si al final no ocurrió. La incidencia no se borra: queda como <b className="text-ink">Anulada</b> y nunca más se aplica.</p>
            {porAnular.estado === 'CONSIDERADA' && <p className="rounded-lg border border-warning/40 bg-warning-soft px-3 py-2 text-warning">
              Ya está aplicada en la planificación vigente. Para deshacer su efecto hay que {accion === 'regenerar' ? 'volver a generar la semana (aún no empieza)'
                : accion === 'replanificar' ? 're-planificar desde hoy (lo anterior a hoy no cambia)' : 'volver a generar o re-planificar'}.
              {porAnular.tipo === 'CAMBIO_PRIORIDAD' && ' El pedido vuelve a su fecha de entrega anterior.'}</p>}
            <label className="block"><span className="mb-1 block font-medium text-ink">Motivo (opcional)</span>
              <textarea rows={2} maxLength={200} className={inputClass} value={motivoAnulacion} onChange={(e) => setMotivoAnulacion(e.target.value)} placeholder="Ej.: se registró por error" /></label>
          </div>
          <div className="mt-5 flex flex-wrap justify-end gap-2">
            <button type="button" onClick={() => setPorAnular(null)} disabled={anulando} className="rounded-lg border border-line px-3 py-2 text-sm text-ink hover:bg-panel-muted">Cancelar</button>
            <button type="button" onClick={() => anular(false)} disabled={anulando}
              className={`rounded-lg px-3 py-2 text-sm font-medium disabled:opacity-40 ${porAnular.estado === 'CONSIDERADA' && accion !== null ? 'border border-danger/40 text-danger hover:bg-danger-soft' : 'bg-danger text-white hover:opacity-90'}`}>
              {anulando ? 'Anulando…' : porAnular.estado === 'CONSIDERADA' && accion !== null ? 'Solo anular' : 'Anular'}</button>
            {porAnular.estado === 'CONSIDERADA' && accion !== null && (
              <button type="button" onClick={() => anular(true)} disabled={anulando} className="rounded-lg bg-danger px-3 py-2 text-sm font-medium text-white hover:opacity-90 disabled:opacity-40">
                {anulando ? 'Anulando…' : accion === 'regenerar' ? 'Anular y volver a generar la semana' : 'Anular y re-planificar desde hoy'}</button>
            )}
          </div>
        </div>
      </div>
    )}

    {dialogo && <DialogoIncidencia lunes={lunes} modo={dialogo} ctx={contexto.datos} vigenteAbierta={abierta} pendientes={pendientes}
      parque={parque.datos ?? []} guiado={isGuided} porAnulacion={replanPorAnulacion}
      onCerrar={() => { setDialogo(null); setReplanPorAnulacion(false); void recargar() }}
      onRegistrada={async () => { await lista.recargar() }} />}
  </>
}
