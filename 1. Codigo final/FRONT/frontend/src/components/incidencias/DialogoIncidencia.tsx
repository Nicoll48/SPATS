import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowLeft, ArrowRight, CircleHelp, HardDrive, Loader2, Play, RefreshCw, X } from 'lucide-react'
import { AvisoError } from '@/components/common/AvisoError'
import { Field, inputClass } from '@/components/ui/FormField'
import { incidencias as api, planificacion, replanificacion, type RegistrarIncidencia } from '@/api/servicios'
import type { ErrorApi } from '@/api/cliente'
import type { ContextoReplanificacion, IncidenciaRegistrada, TipoIncidencia } from '@/api/tipos'
import { intentar } from '@/lib/useDatos'
import { CLASIFICACION_RESULTADO, TIPO_INCIDENCIA } from '@/lib/etiquetas'
import { ahoraLocal, etiquetaSemana, fechaCorta, nombreDia } from '@/lib/fechas'
import { useAvisos } from '@/avisos/avisos-context'
import { useSemana } from '@/semana/semana-context'

const TIPOS: TipoIncidencia[] = ['INCIDENCIA_MAQUINA', 'PROBLEMA_COMPLEMENTO', 'CAMBIO_PRIORIDAD']

function formularioVacio(): RegistrarIncidencia {
  return { tipo: 'INCIDENCIA_MAQUINA', fechaHoraOcurrencia: ahoraLocal(), codigoMaquina: '', numeroPartida: '', numeroOp: '', fechaHoraDisponibilidad: '', nuevaFechaEntrega: '', descripcion: '' }
}

/** Qué se puede hacer con las incidencias de la semana elegida. */
export type AccionIncidencias = 'replanificar' | 'regenerar' | null

/**
 * Semana en curso con vigente abierta: se re-planifica desde hoy. Semana que aún no empieza con vigente abierta: se
 * vuelve a generar completa aplicando las incidencias pendientes. En otro caso solo se registran.
 */
export function accionDeLaSemana(lunes: string, ctx: ContextoReplanificacion | null, vigenteAbierta: boolean): AccionIncidencias {
  if (!ctx || !vigenteAbierta) return null
  if (ctx.permitida) return 'replanificar'
  return ctx.dia < lunes ? 'regenerar' : null
}

interface Props {
  lunes: string
  /** «registrar»: una incidencia nueva; «aplicar»: solo aplicar las pendientes. */
  modo: 'registrar' | 'aplicar'
  ctx: ContextoReplanificacion | null
  vigenteAbierta: boolean
  pendientes: IncidenciaRegistrada[]
  parque: { id: number; codigo: string; barca: string | null }[]
  guiado: boolean
  onCerrar: () => void
  /** Se llama después de registrar, para recargar la lista. */
  onRegistrada: () => Promise<void> | void
  /** D-57: re-planificar desde hoy para deshacer una incidencia anulada (no exige incidencias pendientes). */
  porAnulacion?: boolean
}

/**
 * Registrar una incidencia y, en el mismo panel, re-planificar desde hoy (semana en curso) o volver a generar la
 * semana (si aún no empieza). Pasos: 1) qué pasó, 2) partidas de hoy que ya entraron a máquina, 3) qué hacer. En el
 * modo guiado se muestra un paso a la vez, con más explicación; en el experto, todo junto.
 */
export function DialogoIncidencia({ lunes, modo: modoInicial, ctx, vigenteAbierta, pendientes, parque, guiado, onCerrar, onRegistrada, porAnulacion = false }: Props) {
  const navegar = useNavigate()
  const { setLunes } = useSemana()
  const avisar = useAvisos()
  const [modo, setModo] = useState(modoInicial)
  const [form, setForm] = useState<RegistrarIncidencia>(formularioVacio)
  const [enMaquina, setEnMaquina] = useState<Set<string>>(new Set())
  const [registrada, setRegistrada] = useState<IncidenciaRegistrada | null>(null)
  const [enviando, setEnviando] = useState<'solo' | 'aplicar' | null>(null)
  const [error, setError] = useState<ErrorApi | null>(null)
  const [paso, setPaso] = useState(0)

  useEffect(() => {
    function tecla(e: KeyboardEvent) { if (e.key === 'Escape' && !enviando) onCerrar() }
    window.addEventListener('keydown', tecla)
    return () => window.removeEventListener('keydown', tecla)
  }, [enviando, onCerrar])

  const accion = accionDeLaSemana(lunes, ctx, vigenteAbierta)
  const partidasHoy = accion === 'replanificar' ? ctx?.partidasDelDia ?? [] : []
  const porMaquina = useMemo(() => {
    const m = new Map<string, typeof partidasHoy>()
    for (const p of [...partidasHoy].sort((a, b) => a.maquina.localeCompare(b.maquina) || (a.ordenSecuencia ?? 0) - (b.ordenSecuencia ?? 0)))
      m.set(p.maquina, [...(m.get(p.maquina) ?? []), p])
    return [...m.entries()]
  }, [partidasHoy])

  const pasos: { id: 'que' | 'hoy' | 'aplicar'; titulo: string }[] = [
    ...(modo === 'registrar' ? [{ id: 'que' as const, titulo: '¿Qué pasó?' }] : []),
    ...(porMaquina.length > 0 ? [{ id: 'hoy' as const, titulo: 'Partidas de hoy que ya entraron a máquina' }] : []),
    { id: 'aplicar', titulo: accion === null ? 'Registrar' : '¿Qué hacemos ahora?' },
  ]
  const pasoActual = pasos[Math.min(paso, pasos.length - 1)]

  const faltaDato = modo !== 'registrar' ? null
    : form.tipo === 'INCIDENCIA_MAQUINA' && !form.codigoMaquina ? 'Elige la máquina afectada.'
    : form.tipo === 'PROBLEMA_COMPLEMENTO' && !form.numeroPartida?.trim() ? 'Escribe el número de la partida afectada.'
    : form.tipo === 'CAMBIO_PRIORIDAD' && (!form.numeroOp?.trim() || !form.nuevaFechaEntrega) ? 'Escribe el pedido y su nueva fecha de entrega.'
    : !form.fechaHoraOcurrencia ? 'Indica cuándo ocurrió.' : null

  const consideradas = [...pendientes.map((i) => `${TIPO_INCIDENCIA[i.tipo].titulo} · ${i.referencia}`),
    ...(modo === 'registrar' ? [`${TIPO_INCIDENCIA[form.tipo as TipoIncidencia].titulo} (la que estás registrando)`] : [])]

  async function registrar(): Promise<IncidenciaRegistrada | null> {
    const cuerpo: RegistrarIncidencia = {
      tipo: form.tipo, fechaHoraOcurrencia: form.fechaHoraOcurrencia, descripcion: form.descripcion || undefined,
      ...(form.tipo === 'INCIDENCIA_MAQUINA' && { codigoMaquina: form.codigoMaquina, fechaHoraDisponibilidad: form.fechaHoraDisponibilidad || undefined }),
      ...(form.tipo === 'PROBLEMA_COMPLEMENTO' && { numeroPartida: form.numeroPartida, fechaHoraDisponibilidad: form.fechaHoraDisponibilidad || undefined }),
      ...(form.tipo === 'CAMBIO_PRIORIDAD' && { numeroOp: form.numeroOp, nuevaFechaEntrega: form.nuevaFechaEntrega || undefined }),
    }
    const r = await intentar(() => api.registrar(lunes, cuerpo))
    if (!r.ok) { setError(r.error); return null }
    setRegistrada(r.valor)
    // Si después falla la re-planificación, el reintento no vuelve a registrarla
    setModo('aplicar')
    await onRegistrada()
    return r.valor
  }

  function irAPlanificacion() {
    setLunes(lunes)
    navegar('/')
    onCerrar()
  }

  async function ejecutar(aplicar: boolean) {
    setEnviando(aplicar ? 'aplicar' : 'solo')
    setError(null)
    try {
      let nueva = registrada
      if (modo === 'registrar') {
        nueva = await registrar()
        if (!nueva) return
      }
      if (!aplicar || accion === null) {
        avisar({
          tipo: 'success', titulo: 'Incidencia registrada',
          detalle: nueva?.tipo === 'CAMBIO_PRIORIDAD'
            ? `El pedido ${nueva.referencia} conserva su fecha de entrega (${fechaCorta(nueva.fechaEntregaActual)}) hasta que la incidencia se aplique.`
            : 'Queda pendiente hasta que re-planifiques o vuelvas a generar la semana.',
        })
        onCerrar()
        return
      }
      if (accion === 'replanificar') {
        const ids = [...new Set([...pendientes.map((i) => i.id), ...(nueva ? [nueva.id] : [])])]
        const r = await intentar(() => replanificacion.replanificar(lunes, ids, [...enMaquina], porAnulacion))
        if (!r.ok) { setError(r.error); return }
        if (r.valor.resultado === 'PROPUESTA') {
          avisar({ tipo: 'success', titulo: `Propuesta de re-planificación lista (versión ${r.valor.numeroVersion})`,
            detalle: 'Revísala junto al calendario y confírmala o descártala hoy.' })
          if (r.valor.advertencias.length > 0) avisar({ tipo: 'warning', titulo: 'Avisos de la re-planificación', lista: r.valor.advertencias })
          irAPlanificacion()
        } else {
          avisar({ tipo: 'warning', titulo: 'Se conserva la planificación vigente', detalle: r.valor.advertencias[0],
            lista: r.valor.advertencias.length > 1 ? r.valor.advertencias.slice(1) : undefined, segundos: 14 })
          onCerrar()
        }
        return
      }
      const r = await intentar(() => planificacion.generar(lunes))
      if (!r.ok) { setError(r.error); return }
      avisar({ tipo: 'success', titulo: `Versión ${r.valor.numeroVersion} generada con las incidencias de la semana`,
        detalle: 'Las incidencias aplicadas quedan como consideradas.' })
      if (r.valor.advertencias.length > 0) avisar({ tipo: 'warning', titulo: 'Avisos de la generación', lista: r.valor.advertencias })
      irAPlanificacion()
    } finally {
      setEnviando(null)
    }
  }

  // ------------------------------------------------------------------ secciones
  const seccionQue = (
    <div className="space-y-4">
      {guiado && <Explicacion>Elige qué ocurrió y completa sus datos. Una incidencia es algo que pasó sin que lo decidieras: una máquina se malogró, falta el complemento de una partida o el cliente cambió la fecha de un pedido.</Explicacion>}
      <div className="grid gap-2 sm:grid-cols-3" role="radiogroup" aria-label="Tipo de incidencia">
        {TIPOS.map((t) => (
          <button key={t} type="button" role="radio" aria-checked={form.tipo === t} onClick={() => setForm({ ...form, tipo: t })}
            className={`rounded-xl border p-3 text-left text-sm ${form.tipo === t ? 'border-accent bg-accent-soft ring-1 ring-accent' : 'border-line bg-panel hover:bg-panel-muted'}`}>
            <span className="block font-semibold text-ink">{TIPO_INCIDENCIA[t].titulo}</span>
            <span className="mt-0.5 block text-xs text-ink-soft">{TIPO_INCIDENCIA[t].descripcion}</span>
          </button>
        ))}
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        {form.tipo === 'INCIDENCIA_MAQUINA' && <Field label="Máquina afectada"><select className={inputClass} value={form.codigoMaquina} onChange={(e) => setForm({ ...form, codigoMaquina: e.target.value })}>
          <option value="">Selecciona una máquina…</option>{parque.map((m) => <option key={m.id} value={m.codigo}>{m.codigo} · {m.barca}</option>)}</select></Field>}
        {form.tipo === 'PROBLEMA_COMPLEMENTO' && <Field label="Partida afectada"><input className={inputClass} placeholder="Número de partida" value={form.numeroPartida} onChange={(e) => setForm({ ...form, numeroPartida: e.target.value })} /></Field>}
        {form.tipo === 'CAMBIO_PRIORIDAD' && <Field label="Pedido afectado (OP)"><input className={inputClass} placeholder="Número de OP" value={form.numeroOp} onChange={(e) => setForm({ ...form, numeroOp: e.target.value })} /></Field>}
        <Field label="Fecha y hora de ocurrencia"><input type="datetime-local" className={inputClass} value={form.fechaHoraOcurrencia} onChange={(e) => setForm({ ...form, fechaHoraOcurrencia: e.target.value })} /></Field>
        {form.tipo !== 'CAMBIO_PRIORIDAD'
          ? <Field label="Fecha y hora prevista de disponibilidad" hint={form.tipo === 'INCIDENCIA_MAQUINA' ? 'Desde cuándo la máquina vuelve a recibir partidas. Vacío: el resto de la semana.' : 'Desde cuándo llega el complemento. Vacío: no llega esta semana.'}>
              <input type="datetime-local" className={inputClass} value={form.fechaHoraDisponibilidad} onChange={(e) => setForm({ ...form, fechaHoraDisponibilidad: e.target.value })} /></Field>
          : <Field label="Nueva fecha de entrega" hint="El pedido toma esta fecha cuando la incidencia se aplique; la anterior queda en la trazabilidad.">
              <input type="date" className={inputClass} value={form.nuevaFechaEntrega} onChange={(e) => setForm({ ...form, nuevaFechaEntrega: e.target.value })} /></Field>}
      </div>
      <Field label="Descripción (opcional)"><textarea rows={2} maxLength={255} className={inputClass} value={form.descripcion} onChange={(e) => setForm({ ...form, descripcion: e.target.value })} /></Field>
    </div>
  )

  const seccionHoy = ctx && (
    <div className="space-y-3">
      <Explicacion>
        Estas son las partidas planificadas para hoy, {nombreDia(ctx.dia, true)} {fechaCorta(ctx.dia)}, en cada máquina. <b>Marca las que ya entraron a máquina</b>:
        esas se quedan donde están. Las que no marques todavía pueden cambiar de máquina, de orden o de día al re-planificar.
        Si no marcas ninguna, se asume que hoy todavía no empezó ninguna.
      </Explicacion>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {porMaquina.map(([maquina, filas]) => {
          const todas = filas.every((p) => enMaquina.has(p.numeroPartida))
          return (
            <div key={maquina} className="rounded-xl border border-line bg-panel p-3">
              <div className="mb-2 flex items-center justify-between gap-2">
                <span className="flex items-center gap-1.5 font-mono text-sm font-bold text-ink"><HardDrive className="h-4 w-4 text-ink-faint" aria-hidden="true" />{maquina}</span>
                <button type="button" onClick={() => setEnMaquina((s) => { const n = new Set(s); filas.forEach((p) => (todas ? n.delete(p.numeroPartida) : n.add(p.numeroPartida))); return n })}
                  className="text-xs font-medium text-accent hover:underline">{todas ? 'Desmarcar todas' : 'Marcar todas'}</button>
              </div>
              <ul className="space-y-1.5">
                {filas.map((p) => (
                  <li key={p.numeroPartida}><label className="flex cursor-pointer items-center gap-2 text-sm">
                    <input type="checkbox" className="h-4 w-4" checked={enMaquina.has(p.numeroPartida)}
                      onChange={() => setEnMaquina((s) => { const n = new Set(s); if (n.has(p.numeroPartida)) n.delete(p.numeroPartida); else n.add(p.numeroPartida); return n })} />
                    <span className="font-mono font-semibold text-ink">{p.numeroPartida}</span>
                    <span className="text-xs text-ink-soft">{CLASIFICACION_RESULTADO[p.clasificacion]}</span>
                  </label></li>
                ))}
              </ul>
            </div>
          )
        })}
      </div>
      <p className="text-xs text-ink-soft">{enMaquina.size} de {partidasHoy.length} partidas de hoy marcadas como ya en máquina.</p>
    </div>
  )

  const seccionAplicar = (
    <div className="space-y-3 text-sm">
      {accion === 'replanificar' && porAnulacion && <Explicacion>
        Se <b>re-planifica desde hoy sin la incidencia anulada</b>: lo de días anteriores no cambia y desde hoy se vuelve a decidir como si no
        hubiera ocurrido. Las demás incidencias ya consideradas que siguen teniendo efecto se vuelven a aplicar. El resultado es una
        <b> propuesta</b> que revisas en Planificación semanal.
      </Explicacion>}
      {accion === 'replanificar' && !porAnulacion && <Explicacion>
        Se <b>re-planifica desde hoy</b> considerando juntas las incidencias pendientes. Lo de días anteriores no cambia; desde hoy el algoritmo vuelve a
        decidir máquina, día y orden. El resultado es una <b>propuesta</b>: la revisas en Planificación semanal junto al calendario y solo pasa a ser la
        vigente si la confirmas hoy.
      </Explicacion>}
      {accion === 'regenerar' && <Explicacion>
        La semana del {etiquetaSemana(lunes)} todavía no empieza, así que se <b>vuelve a generar completa</b> aplicando las incidencias pendientes. La versión
        nueva queda vigente, la anterior queda en el historial y las incidencias pasan a consideradas.
      </Explicacion>}
      {accion === null && <Explicacion>
        {ctx && !vigenteAbierta ? 'La semana no tiene una planificación vigente abierta. ' : ctx?.motivo ? `${ctx.motivo} ` : ''}
        La incidencia queda registrada como pendiente.
      </Explicacion>}
      {accion !== null && consideradas.length > 0 && !porAnulacion && <div>
        <p className="mb-1 font-medium text-ink">Se considerarán {consideradas.length === 1 ? 'esta incidencia' : `estas ${consideradas.length} incidencias`}:</p>
        <ul className="list-disc space-y-0.5 pl-5 text-ink-soft">{consideradas.map((c) => <li key={c}>{c}</li>)}</ul>
      </div>}
      {accion === 'replanificar' && porMaquina.length > 0 && <p className="text-ink-soft">Partidas de hoy que se quedan donde están: <b className="text-ink">{enMaquina.size}</b>.</p>}
    </div>
  )

  const contenido: Record<'que' | 'hoy' | 'aplicar', ReactNode> = { que: seccionQue, hoy: seccionHoy, aplicar: seccionAplicar }
  const esUltimo = paso >= pasos.length - 1
  const puedeSeguir = !(pasoActual.id === 'que' && faltaDato)

  const botonesFinales = <>
    {modo === 'registrar' && accion !== null && (
      <button type="button" disabled={enviando !== null || faltaDato !== null} onClick={() => ejecutar(false)} title={faltaDato ?? 'Guarda la incidencia como pendiente, sin cambiar la planificación'}
        className="rounded-lg border border-line bg-panel px-3 py-2 text-sm font-medium text-ink hover:bg-panel-muted disabled:opacity-40">
        {enviando === 'solo' ? 'Registrando…' : 'Solo registrar'}</button>)}
    <button type="button" disabled={enviando !== null || faltaDato !== null} onClick={() => ejecutar(true)} title={faltaDato ?? undefined}
      className="inline-flex items-center gap-1.5 rounded-lg bg-accent px-3 py-2 text-sm font-medium text-accent-ink hover:opacity-90 disabled:opacity-40">
      {enviando === 'aplicar' ? <Loader2 className="h-4 w-4 animate-spin" /> : accion === 'replanificar' ? <RefreshCw className="h-4 w-4" /> : accion === 'regenerar' ? <Play className="h-4 w-4" /> : null}
      {accion === 'replanificar' ? (modo === 'registrar' ? 'Registrar y re-planificar desde hoy' : 'Re-planificar desde hoy')
        : accion === 'regenerar' ? (modo === 'registrar' ? 'Registrar y volver a generar la semana' : 'Volver a generar la semana')
        : 'Registrar incidencia'}
    </button>
  </>

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <button type="button" aria-label="Cerrar" onClick={() => !enviando && onCerrar()} className="absolute inset-0 bg-black/50" />
      <div role="dialog" aria-modal="true" aria-labelledby="dialogo-incidencia" className="relative flex max-h-[92vh] w-full max-w-4xl flex-col rounded-2xl border border-line bg-panel shadow-xl">
        <div className="flex items-start justify-between gap-3 border-b border-line px-6 py-4">
          <div>
            <h2 id="dialogo-incidencia" className="text-lg font-semibold text-ink">{modo === 'registrar' && !registrada ? 'Registrar incidencia' : accion === 'regenerar' ? 'Volver a generar con las incidencias' : 'Re-planificar desde hoy'}</h2>
            <p className="text-sm text-ink-soft">Semana del {etiquetaSemana(lunes)}</p>
          </div>
          <button type="button" onClick={onCerrar} disabled={enviando !== null} aria-label="Cerrar" className="rounded-md p-1 text-ink-soft hover:bg-panel-muted"><X className="h-5 w-5" /></button>
        </div>

        {guiado && pasos.length > 1 && (
          <ol className="flex gap-2 border-b border-line px-6 py-3 text-xs" aria-label="Pasos">
            {pasos.map((p, i) => (
              <li key={p.id} aria-current={i === paso ? 'step' : undefined}
                className={`flex flex-1 items-center gap-2 rounded-lg px-2 py-1.5 ${i === paso ? 'bg-accent-soft font-semibold text-accent' : i < paso ? 'text-success' : 'text-ink-soft'}`}>
                <span className={`flex h-5 w-5 shrink-0 items-center justify-center rounded-full border text-[11px] ${i === paso ? 'border-accent bg-accent text-accent-ink' : 'border-line'}`}>{i + 1}</span>
                <span className="truncate">{p.titulo}</span>
              </li>
            ))}
          </ol>
        )}

        <div className="flex-1 space-y-6 overflow-y-auto px-6 py-5">
          <AvisoError error={error} onCerrar={() => setError(null)} />
          {registrada && <p className="rounded-lg border border-success/40 bg-success-soft px-3 py-2 text-sm text-success">La incidencia ya quedó registrada. Puedes volver a intentar o cerrar: seguirá pendiente.</p>}
          {guiado
            ? <section aria-label={pasoActual.titulo}><h3 className="mb-3 text-base font-semibold text-ink">Paso {paso + 1}. {pasoActual.titulo}</h3>{contenido[pasoActual.id]}</section>
            : pasos.map((p, i) => (
                <section key={p.id} aria-label={p.titulo}>
                  <h3 className="mb-3 flex items-center gap-2 text-sm font-semibold uppercase tracking-wide text-ink-soft">
                    <span className="flex h-5 w-5 items-center justify-center rounded-full bg-accent text-[11px] text-accent-ink">{i + 1}</span>{p.titulo}</h3>
                  {contenido[p.id]}
                </section>
              ))}
        </div>

        <div className="flex flex-wrap items-center justify-end gap-2 border-t border-line px-6 py-3">
          {faltaDato && (!guiado || pasoActual.id === 'que') && <span className="mr-auto text-xs text-warning">{faltaDato}</span>}
          <button type="button" onClick={onCerrar} disabled={enviando !== null} className="rounded-lg border border-line px-3 py-2 text-sm text-ink hover:bg-panel-muted">Cancelar</button>
          {guiado && paso > 0 && <button type="button" onClick={() => setPaso((p) => p - 1)} disabled={enviando !== null}
            className="inline-flex items-center gap-1 rounded-lg border border-line px-3 py-2 text-sm text-ink hover:bg-panel-muted"><ArrowLeft className="h-4 w-4" />Atrás</button>}
          {guiado && !esUltimo
            ? <button type="button" onClick={() => setPaso((p) => p + 1)} disabled={!puedeSeguir}
                className="inline-flex items-center gap-1 rounded-lg bg-accent px-3 py-2 text-sm font-medium text-accent-ink disabled:opacity-40">Siguiente<ArrowRight className="h-4 w-4" /></button>
            : botonesFinales}
        </div>
      </div>
    </div>
  )
}

function Explicacion({ children }: { children: ReactNode }) {
  return (
    <p className="flex items-start gap-2 rounded-lg bg-accent-soft/60 px-3 py-2.5 text-sm text-ink-soft">
      <CircleHelp className="mt-0.5 h-4 w-4 shrink-0 text-accent" aria-hidden="true" /><span>{children}</span>
    </p>
  )
}
