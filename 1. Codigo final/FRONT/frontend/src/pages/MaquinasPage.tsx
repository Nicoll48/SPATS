import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AlertTriangle, CalendarOff, CircleHelp, Pencil, Plus, Search, Trash2 } from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { SoloLecturaAviso } from '@/components/common/SoloLecturaAviso'
import { AvisoError, Cargando } from '@/components/common/AvisoError'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { StatusPill } from '@/components/ui/StatusPill'
import { Drawer } from '@/components/ui/Drawer'
import { Field, inputClass } from '@/components/ui/FormField'
import { MENSAJE_SIN_PERMISO, useAuth } from '@/auth/auth-context'
import { useExperience } from '@/experience/experience-context'
import { useSemana } from '@/semana/semana-context'
import { maquinas as api, planificacion, type GuardarMaquina } from '@/api/servicios'
import { useAvisos } from '@/avisos/avisos-context'
import type { ErrorApi } from '@/api/cliente'
import type { EstadoMaquina, Indisponibilidad, Maquina, TipoIndisponibilidad, Tonalidad } from '@/api/tipos'
import { intentar, useDatos } from '@/lib/useDatos'
import { CLASIFICACION_COLOR, ESTADO_MAQUINA, ORDEN_TONALIDAD, TONALIDAD, TONO_MAQUINA, kg, textoAcepta } from '@/lib/etiquetas'
import { ahoraLocal, etiquetaSemana, fechaCorta, fechaHora } from '@/lib/fechas'
import { Paginado } from '@/components/ui/Paginacion'

/** D-51: al registrar, la máquina acepta las cuatro tonalidades. */
const VACIA: GuardarMaquina = { codigo: '', capacidad: 0, clasificacionColor: 'C', objetivoSemanal: 16, estado: 'OPERATIVA', tonalidadesAceptadas: [...ORDEN_TONALIDAD] }

const SIN_TONALIDADES = 'Marca al menos una tonalidad. Si la máquina acepta todos los colores, deja todas marcadas'

/** Aviso del backend antes de guardar un cambio de tonalidades (D-51). */
interface AvisoCambio {
  codigo: 'CONFIRMACION_REQUERIDA' | 'CONFIRMACION_PLAN_VIGENTE'
  mensaje: string
  sinMaquinaCompatible: string[]
  enPlanificacionVigente: { partida: string; tonalidad: Tonalidad; semana: string; dia: string }[]
}

function avisoDe(e: ErrorApi): AvisoCambio | null {
  if (e.codigo !== 'CONFIRMACION_REQUERIDA' && e.codigo !== 'CONFIRMACION_PLAN_VIGENTE') return null
  return {
    codigo: e.codigo, mensaje: e.message,
    sinMaquinaCompatible: (e.datos.sinMaquinaCompatible as string[] | undefined) ?? [],
    enPlanificacionVigente: (e.datos.enPlanificacionVigente as AvisoCambio['enPlanificacionVigente'] | undefined) ?? [],
  }
}

/** Registro, búsqueda, modificación y eliminación de máquinas (HU-07) e indisponibilidades (HU-08). */
export function MaquinasPage() {
  const { esPlanificador } = useAuth()
  const { isGuided } = useExperience()
  const { setLunes } = useSemana()
  const navigate = useNavigate()
  const avisar = useAvisos()
  const [busqueda, setBusqueda] = useState('')
  const lista = useDatos(() => api.buscar(busqueda), [busqueda])
  const intervalos = useDatos(() => api.todasLasIndisponibilidades(), [])
  const [formulario, setFormulario] = useState<{ id: number | null; datos: GuardarMaquina } | null>(null)
  const [paraIndisponibilidad, setParaIndisponibilidad] = useState<Maquina | null>(null)
  const [maquinaPorEliminar, setMaquinaPorEliminar] = useState<Maquina | null>(null)
  const [intervaloPorEliminar, setIntervaloPorEliminar] = useState<Indisponibilidad | null>(null)
  const [error, setError] = useState<ErrorApi | null>(null)
  const [errorForm, setErrorForm] = useState<ErrorApi | null>(null)
  const [guardando, setGuardando] = useState(false)
  const [aviso, setAviso] = useState<AvisoCambio | null>(null)

  async function recargarTodo() {
    await Promise.all([lista.recargar(), intervalos.recargar()])
  }

  /**
   * @param confirmado el planificador ya vio el aviso
   * @param regenerarSemana lunes de una semana que aún no empieza y se vuelve a generar después de guardar, para
   *                        aplicar las tonalidades nuevas (D-51)
   */
  async function guardar(confirmado = false, regenerarSemana: string | null = null) {
    if (!formulario) return
    setGuardando(true); setErrorForm(null); setAviso(null)
    const cuerpo = formulario.datos
    const r = await intentar(() => formulario.id === null ? api.registrar(cuerpo) : api.modificar(formulario.id, cuerpo, confirmado))
    setGuardando(false)
    if (!r.ok) {
      const a = avisoDe(r.error)
      if (a) setAviso(a)
      else setErrorForm(r.error)
      return
    }
    setFormulario(null)
    if (regenerarSemana) {
      setGuardando(true)
      const g = await intentar(() => planificacion.generar(regenerarSemana))
      setGuardando(false)
      if (!g.ok) { setErrorForm(g.error); await lista.recargar(); return }
      avisar({ tipo: 'success', titulo: `Máquina guardada y semana del ${etiquetaSemana(regenerarSemana)} generada otra vez`,
        detalle: `La versión ${g.valor.numeroVersion} ya usa las tonalidades nuevas.` })
      if (g.valor.advertencias.length > 0) avisar({ tipo: 'warning', titulo: 'Avisos de la generación', lista: g.valor.advertencias })
      setLunes(regenerarSemana)
      navigate('/')
      return
    }
    await lista.recargar()
  }

  function editar(m: Maquina) {
    setErrorForm(null)
    setFormulario({ id: m.id, datos: { capacidad: m.capacidad, clasificacionColor: m.clasificacionColor, objetivoSemanal: m.objetivoSemanal, estado: m.estado,
      tonalidadesAceptadas: [...(m.tonalidadesAceptadas ?? ORDEN_TONALIDAD)] } })
  }

  async function eliminarMaquina() {
    const m = maquinaPorEliminar
    setMaquinaPorEliminar(null)
    if (!m) return
    setError(null)
    const r = await intentar(() => api.eliminar(m.id))
    if (!r.ok) { setError(r.error); return }
    await recargarTodo()
  }

  async function eliminarIntervalo() {
    const i = intervaloPorEliminar
    setIntervaloPorEliminar(null)
    if (!i) return
    setError(null)
    const r = await intentar(() => api.eliminarIndisponibilidad(i.id))
    if (!r.ok) { setError(r.error); return }
    await recargarTodo()
  }

  const d = formulario?.datos
  const set = (cambio: Partial<GuardarMaquina>) => formulario && setFormulario({ ...formulario, datos: { ...formulario.datos, ...cambio } })
  const alternarTonalidad = (t: Tonalidad) => d && set({ tonalidadesAceptadas: d.tonalidadesAceptadas.includes(t)
    ? d.tonalidadesAceptadas.filter((x) => x !== t) : ORDEN_TONALIDAD.filter((x) => x === t || d.tonalidadesAceptadas.includes(x)) })
  const registradas = intervalos.datos ?? []
  const sinTonalidades = !!d && d.tonalidadesAceptadas.length === 0

  return <>
    <PageHeader title="Máquinas" description="Máquinas de tintorería: cada partida va a una máquina de su misma capacidad que acepte su tonalidad."
      actions={<button type="button" disabled={!esPlanificador} title={esPlanificador ? 'Registrar una máquina' : MENSAJE_SIN_PERMISO}
        onClick={() => { setErrorForm(null); setFormulario({ id: null, datos: { ...VACIA, tonalidadesAceptadas: [...ORDEN_TONALIDAD] } }) }}
        className="inline-flex items-center gap-1.5 rounded-lg bg-accent px-3 py-2 text-sm font-medium text-accent-ink disabled:opacity-40"><Plus className="h-4 w-4" />Registrar máquina</button>} />
    <SoloLecturaAviso />
    {isGuided && esPlanificador && <section className="mb-5 rounded-2xl border border-accent/25 bg-accent-soft/60 p-5"><div className="flex items-start gap-3"><CircleHelp className="mt-0.5 h-5 w-5 shrink-0 text-accent" /><div><p className="text-xs font-semibold uppercase tracking-wide text-accent">Configuración de apoyo</p><p className="mt-1 text-sm text-ink-soft">Revisa esta pantalla solo si cambió una máquina. El objetivo semanal baja 2 partidas por cada día con indisponibilidad registrada; un día tocado en parte cuenta como indisponible.</p></div></div></section>}

    <div className="mb-4 flex items-center gap-2 rounded-lg border border-line bg-panel px-3 py-2 sm:max-w-xs"><Search className="h-4 w-4 text-ink-faint" />
      <input value={busqueda} onChange={(e) => setBusqueda(e.target.value)} placeholder="Buscar por código, color (C/B) o tonalidad (Oscuro)…" className="w-full bg-transparent text-sm text-ink focus:outline-none" /></div>

    <AvisoError error={error} onCerrar={() => setError(null)} />
    {lista.error && <AvisoError error={lista.error} />}
    {lista.cargando && !lista.datos ? <Cargando /> : <Paginado items={lista.datos ?? []} reinicio={busqueda}>{(pagina) => (
      <div className="overflow-hidden rounded-xl border border-line bg-panel"><div className="overflow-x-auto"><table className="w-full min-w-[1120px] text-left text-sm">
        <thead><tr className="border-b border-line text-xs tracking-wide text-ink-faint uppercase"><th className="px-4 py-3">Código</th><th className="px-4 py-3">Barca</th><th className="px-4 py-3">Capacidad</th><th className="px-4 py-3">Clasificación de color</th><th className="px-4 py-3">Acepta</th><th className="px-4 py-3">Objetivo semanal</th><th className="px-4 py-3">Estado actual</th><th className="px-4 py-3" /></tr></thead>
        <tbody>{(lista.datos ?? []).length === 0 ? <tr><td colSpan={8} className="px-4 py-10 text-center text-ink-faint">No hay máquinas que coincidan.</td></tr> : pagina.map((m) => (
          <tr key={m.id} className="border-b border-line last:border-0">
            <td className="px-4 py-2.5 font-mono font-semibold">{m.codigo}</td>
            <td className="px-4 py-2.5 font-mono">{m.barca ?? <span className="text-danger">sin barca</span>}</td>
            <td className="px-4 py-2.5">{kg(m.capacidad)}</td><td className="px-4 py-2.5">{CLASIFICACION_COLOR[m.clasificacionColor]}</td>
            <td className="px-4 py-2.5">{textoAcepta(m.tonalidadesAceptadas)}</td>
            <td className="px-4 py-2.5 font-mono">{m.objetivoSemanal}</td>
            <td className="px-4 py-2.5"><StatusPill tone={TONO_MAQUINA[m.estadoActual]} label={ESTADO_MAQUINA[m.estadoActual]} /></td>
            <td className="px-4 py-2.5 text-right whitespace-nowrap">
              <button type="button" disabled={!esPlanificador} onClick={() => setParaIndisponibilidad(m)} title={esPlanificador ? 'Registrar una indisponibilidad' : MENSAJE_SIN_PERMISO} className="mr-4 inline-flex items-center gap-1 text-ink-soft hover:text-ink disabled:opacity-30"><CalendarOff className="h-4 w-4" />Indisponibilidad</button>
              <button type="button" disabled={!esPlanificador} onClick={() => editar(m)} title={esPlanificador ? 'Modificar' : MENSAJE_SIN_PERMISO} className="mr-4 inline-flex items-center gap-1 text-accent disabled:opacity-30"><Pencil className="h-4 w-4" />Modificar</button>
              <button type="button" disabled={!esPlanificador} onClick={() => setMaquinaPorEliminar(m)} title={esPlanificador ? 'Eliminar' : MENSAJE_SIN_PERMISO} className="inline-flex items-center gap-1 text-danger disabled:opacity-30"><Trash2 className="h-4 w-4" />Eliminar</button>
            </td>
          </tr>))}</tbody></table></div></div>
    )}</Paginado>}

    {registradas.length > 0 && <section className="mt-8">
      <h3 className="mb-3 text-sm font-semibold">Indisponibilidades registradas</h3>
      <Paginado items={registradas}>{(pagina) => <div className="overflow-x-auto rounded-xl border border-line bg-panel"><table className="w-full min-w-[800px] text-left text-sm">
        <thead><tr className="border-b border-line text-xs uppercase text-ink-faint"><th className="px-4 py-3">Máquina</th><th className="px-4 py-3">Desde</th><th className="px-4 py-3">Hasta</th><th className="px-4 py-3">Tipo</th><th className="px-4 py-3">Motivo</th><th className="px-4 py-3" /></tr></thead>
        <tbody>{pagina.map((i) => <tr key={i.id} className="border-b border-line last:border-0"><td className="px-4 py-2.5 font-mono font-semibold">{i.codigoMaquina}</td><td className="px-4 py-2.5">{fechaHora(i.fechaHoraInicio)}</td><td className="px-4 py-2.5">{fechaHora(i.fechaHoraFin)}</td>
          <td className="px-4 py-2.5"><StatusPill tone={TONO_MAQUINA[i.estadoResultante]} label={ESTADO_MAQUINA[i.estadoResultante]} /></td><td className="px-4 py-2.5">{i.motivo}</td>
          <td className="px-4 py-2.5 text-right"><button type="button" disabled={!esPlanificador} onClick={() => setIntervaloPorEliminar(i)} title={esPlanificador ? 'Eliminar' : MENSAJE_SIN_PERMISO} className="inline-flex items-center gap-1 text-danger disabled:opacity-30"><Trash2 className="h-4 w-4" />Eliminar</button></td></tr>)}</tbody></table></div>}</Paginado>
    </section>}

    <RegistrarIndisponibilidad maquina={paraIndisponibilidad} onCerrar={() => setParaIndisponibilidad(null)} onRegistrada={recargarTodo} />

    <Drawer open={formulario !== null} onClose={() => setFormulario(null)} title={formulario?.id === null ? 'Registrar máquina' : 'Modificar máquina'}
      description="La barca sale de la capacidad y la clasificación de color. La compatibilidad usa la capacidad y las tonalidades que acepta la máquina."
      footer={<><button type="button" onClick={() => setFormulario(null)} className="rounded-lg border border-line px-3 py-2 text-sm">Cancelar</button><button type="button" onClick={() => guardar()} disabled={guardando || sinTonalidades} title={sinTonalidades ? SIN_TONALIDADES : undefined} className="rounded-lg bg-accent px-3 py-2 text-sm font-medium text-accent-ink disabled:opacity-50">Guardar</button></>}>
      {d && <div className="flex flex-col gap-4">
        <AvisoError error={errorForm} />
        {formulario?.id === null && <Field label="Código"><input className={inputClass} value={d.codigo ?? ''} onChange={(e) => set({ codigo: e.target.value })} /></Field>}
        <Field label="Capacidad (kg)"><input type="number" min="1" className={inputClass} value={d.capacidad || ''} onChange={(e) => set({ capacidad: Number(e.target.value) })} /></Field>
        <Field label="Clasificación de color" hint="Informativa: la compatibilidad no usa la letra de la barca."><select className={inputClass} value={d.clasificacionColor} onChange={(e) => set({ clasificacionColor: e.target.value })}><option value="C">Color (C)</option><option value="B">Blanco (B)</option></select></Field>
        <fieldset>
          <legend className="mb-1.5 block text-sm font-medium text-ink">Tonalidades que acepta</legend>
          <div className="grid grid-cols-2 gap-1.5">
            {ORDEN_TONALIDAD.map((t) => (
              <label key={t} className="flex cursor-pointer items-center gap-2 rounded-lg border border-line bg-panel px-3 py-2 text-sm text-ink has-[:checked]:border-accent has-[:checked]:bg-accent-soft/60">
                <input type="checkbox" checked={d.tonalidadesAceptadas.includes(t)} onChange={() => alternarTonalidad(t)} className="accent-accent" />
                {TONALIDAD[t]}
              </label>
            ))}
          </div>
          {isGuided && <span className="mt-1 block text-xs text-ink-soft">Desmarca las tonalidades que esta máquina no debe teñir. Por defecto acepta todas.</span>}
          {sinTonalidades && <span role="alert" className="mt-1 block text-xs text-danger">{SIN_TONALIDADES}.</span>}
        </fieldset>
        <Field label="Barca" hint="Se completa sola."><input readOnly className={`${inputClass} font-mono opacity-70`} placeholder="Indica la capacidad" value={d.capacidad > 0 ? `${d.capacidad} ${d.clasificacionColor}` : ''} /></Field>
        <Field label="Objetivo semanal (partidas)" hint="Referencia: 16 partidas."><input type="number" min="1" className={inputClass} value={d.objetivoSemanal} onChange={(e) => set({ objetivoSemanal: Number(e.target.value) })} /></Field>
        <Field label="Estado registrado" hint="Los días sin partidas los definen los intervalos de indisponibilidad."><select className={inputClass} value={d.estado} onChange={(e) => set({ estado: e.target.value as EstadoMaquina })}>
          {(Object.keys(ESTADO_MAQUINA) as EstadoMaquina[]).map((e) => <option key={e} value={e}>{ESTADO_MAQUINA[e]}</option>)}</select></Field>
      </div>}
    </Drawer>

    <AvisoCambioTonalidades aviso={aviso} guardando={guardando} onCancelar={() => setAviso(null)}
      onGuardar={() => guardar(true)} onRegenerar={(lunes) => guardar(true, lunes)} />
    <ConfirmDialog open={maquinaPorEliminar !== null} title={`Eliminar la máquina ${maquinaPorEliminar?.codigo ?? ''}`}
      description="Se eliminarán la máquina y sus indisponibilidades. Si ya aparece en una planificación o incidencia, no se podrá eliminar."
      confirmLabel="Eliminar" tone="danger" onConfirm={eliminarMaquina} onCancel={() => setMaquinaPorEliminar(null)} />
    <ConfirmDialog open={intervaloPorEliminar !== null} title={`Eliminar la indisponibilidad de ${intervaloPorEliminar?.codigoMaquina ?? ''}`}
      description="La máquina volverá a contar como disponible en esos días en la próxima planificación que generes. La planificación ya generada no cambia."
      confirmLabel="Eliminar" tone="danger" onConfirm={eliminarIntervalo} onCancel={() => setIntervaloPorEliminar(null)} />
  </>
}

const NUEVA = { fechaHoraInicio: '', fechaHoraFin: '', tipo: 'INDISPONIBLE' as TipoIndisponibilidad, motivo: '' }

function RegistrarIndisponibilidad({ maquina, onCerrar, onRegistrada }: { maquina: Maquina | null; onCerrar: () => void; onRegistrada: () => Promise<void> }) {
  const [nueva, setNueva] = useState(NUEVA)
  const [error, setError] = useState<ErrorApi | null>(null)

  function cerrar() {
    setNueva(NUEVA)
    setError(null)
    onCerrar()
  }

  async function registrar() {
    if (!maquina) return
    setError(null)
    const r = await intentar(() => api.registrarIndisponibilidad(maquina.id, nueva))
    if (!r.ok) { setError(r.error); return }
    cerrar()
    await onRegistrada()
  }

  return (
    <Drawer open={maquina !== null} onClose={cerrar} title={`Registrar indisponibilidad · ${maquina?.codigo ?? ''}`}
      description="La máquina no recibe partidas regulares en los días que toque este intervalo. Su estado cambia mientras el intervalo está en curso."
      footer={<><button type="button" onClick={cerrar} className="rounded-lg border border-line px-3 py-2 text-sm">Cancelar</button><button type="button" onClick={registrar} className="rounded-lg bg-accent px-3 py-2 text-sm text-accent-ink">Registrar</button></>}>
      <div className="flex flex-col gap-4"><AvisoError error={error} />
        <Field label="Desde"><input type="datetime-local" className={inputClass} value={nueva.fechaHoraInicio} onChange={(e) => setNueva({ ...nueva, fechaHoraInicio: e.target.value })} /></Field>
        <Field label="Hasta"><input type="datetime-local" className={inputClass} value={nueva.fechaHoraFin} onChange={(e) => setNueva({ ...nueva, fechaHoraFin: e.target.value })} /></Field>
        <Field label="Tipo de indisponibilidad" hint="Define el estado que toma la máquina durante el intervalo."><select className={inputClass} value={nueva.tipo} onChange={(e) => setNueva({ ...nueva, tipo: e.target.value as TipoIndisponibilidad })}><option value="INDISPONIBLE">Indisponible</option><option value="MANTENIMIENTO">Mantenimiento</option></select></Field>
        <Field label="Motivo"><textarea rows={3} className={inputClass} value={nueva.motivo} onChange={(e) => setNueva({ ...nueva, motivo: e.target.value })} /></Field>
      </div>
    </Drawer>
  )
}

/**
 * Aviso antes de guardar un cambio de tonalidades (D-51): partidas pendientes que se quedan sin máquina compatible
 * y partidas de la planificación vigente que la máquina deja de aceptar. La vigente nunca se modifica sola.
 */
function AvisoCambioTonalidades({ aviso, guardando, onCancelar, onGuardar, onRegenerar }: {
  aviso: AvisoCambio | null; guardando: boolean; onCancelar: () => void; onGuardar: () => void; onRegenerar: (lunes: string) => void
}) {
  if (!aviso) return null
  const conVigente = aviso.enPlanificacionVigente.length > 0
  // Una semana que aún no empieza se puede volver a generar completa; una ya empezada toma el cambio en la próxima
  const hoy = ahoraLocal().slice(0, 10)
  const lunes = [...new Set(aviso.enPlanificacionVigente.map((f) => f.semana))].filter((x) => x > hoy).sort()[0] ?? null
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/40" onClick={onCancelar} aria-hidden="true" />
      <div role="alertdialog" aria-modal="true" aria-labelledby="aviso-tonalidades" className="relative max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-2xl border border-line bg-panel p-5 shadow-xl">
        <div className="flex items-start gap-3">
          <span className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-warning-soft"><AlertTriangle className="h-[18px] w-[18px] text-warning" aria-hidden="true" /></span>
          <div className="min-w-0 text-sm text-ink-soft">
            <h2 id="aviso-tonalidades" className="text-base font-semibold text-ink">El cambio de tonalidades afecta partidas</h2>
            {conVigente && <>
              <p className="mt-2 font-medium text-ink">En la planificación vigente, esta máquina dejaría de aceptar:</p>
              <ul className="mt-1 max-h-40 list-disc overflow-y-auto pl-5">{aviso.enPlanificacionVigente.map((f) => (
                <li key={f.partida + f.semana}><span className="font-mono">{f.partida}</span> · {TONALIDAD[f.tonalidad]}{f.dia ? ` · ${fechaCorta(f.dia)}` : ''}</li>))}</ul>
              <p className="mt-2">La planificación vigente no cambia sola. {lunes
                ? `La semana del ${etiquetaSemana(lunes)} todavía no empieza: puedes volver a generarla ahora con las tonalidades nuevas.`
                : 'La semana ya empezó: el cambio se aplica desde la próxima planificación que generes.'}</p>
            </>}
            {aviso.sinMaquinaCompatible.length > 0 && <>
              <p className="mt-2 font-medium text-ink">Partidas pendientes que se quedarían sin ninguna máquina compatible:</p>
              <p className="mt-1 max-h-32 overflow-y-auto font-mono text-xs">{aviso.sinMaquinaCompatible.join(', ')}</p>
              <p className="mt-1">Al generar quedarán Bloqueadas con el motivo «Ninguna máquina de su capacidad acepta su tonalidad».</p>
            </>}
          </div>
        </div>
        <div className="mt-5 flex flex-wrap items-center justify-end gap-2">
          <button type="button" autoFocus onClick={onCancelar} className="rounded-lg border border-line bg-panel px-3 py-2 text-sm font-medium text-ink hover:bg-panel-muted">Cancelar</button>
          {conVigente ? <>
            <button type="button" disabled={guardando} onClick={onGuardar} className={lunes
              ? 'rounded-lg border border-accent px-3 py-2 text-sm font-medium text-accent disabled:opacity-50'
              : 'rounded-lg bg-accent px-3 py-2 text-sm font-medium text-accent-ink disabled:opacity-50'}>Guardar y aplicar desde la próxima planificación</button>
            {lunes && <button type="button" disabled={guardando} onClick={() => onRegenerar(lunes)}
              className="rounded-lg bg-accent px-3 py-2 text-sm font-medium text-accent-ink disabled:opacity-50">{guardando ? 'Generando…' : 'Guardar y volver a generar la semana'}</button>}
          </> : <button type="button" disabled={guardando} onClick={onGuardar} className="rounded-lg bg-accent px-3 py-2 text-sm font-medium text-accent-ink disabled:opacity-50">Guardar de todas formas</button>}
        </div>
      </div>
    </div>
  )
}
