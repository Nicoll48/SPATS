import { useState } from 'react'
import { CalendarOff, CircleHelp, Pencil, Plus, Search, Trash2 } from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { SoloLecturaAviso } from '@/components/common/SoloLecturaAviso'
import { AvisoError, Cargando } from '@/components/common/AvisoError'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { StatusPill } from '@/components/ui/StatusPill'
import { Drawer } from '@/components/ui/Drawer'
import { Field, inputClass } from '@/components/ui/FormField'
import { MENSAJE_SIN_PERMISO, useAuth } from '@/auth/auth-context'
import { useExperience } from '@/experience/experience-context'
import { maquinas as api, type GuardarMaquina } from '@/api/servicios'
import type { ErrorApi } from '@/api/cliente'
import type { EstadoMaquina, Indisponibilidad, Maquina, TipoIndisponibilidad } from '@/api/tipos'
import { intentar, useDatos } from '@/lib/useDatos'
import { CLASIFICACION_COLOR, ESTADO_MAQUINA, TONO_MAQUINA, kg } from '@/lib/etiquetas'
import { fechaHora } from '@/lib/fechas'
import { Paginado } from '@/components/ui/Paginacion'

const VACIA: GuardarMaquina = { codigo: '', capacidad: 0, clasificacionColor: 'C', objetivoSemanal: 16, estado: 'OPERATIVA' }

/** Registro, búsqueda, modificación y eliminación de máquinas (HU-07) e indisponibilidades (HU-08). */
export function MaquinasPage() {
  const { esPlanificador } = useAuth()
  const { isGuided } = useExperience()
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

  async function recargarTodo() {
    await Promise.all([lista.recargar(), intervalos.recargar()])
  }

  async function guardar() {
    if (!formulario) return
    setGuardando(true); setErrorForm(null)
    const cuerpo = formulario.datos
    const r = await intentar(() => formulario.id === null ? api.registrar(cuerpo) : api.modificar(formulario.id, cuerpo))
    setGuardando(false)
    if (!r.ok) { setErrorForm(r.error); return }
    setFormulario(null)
    await lista.recargar()
  }

  function editar(m: Maquina) {
    setErrorForm(null)
    setFormulario({ id: m.id, datos: { capacidad: m.capacidad, clasificacionColor: m.clasificacionColor, objetivoSemanal: m.objetivoSemanal, estado: m.estado } })
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
  const registradas = intervalos.datos ?? []

  return <>
    <PageHeader title="Máquinas" description="Máquinas de tintorería: cada partida solo va a una máquina de su misma barca."
      actions={<button type="button" disabled={!esPlanificador} title={esPlanificador ? 'Registrar una máquina' : MENSAJE_SIN_PERMISO}
        onClick={() => { setErrorForm(null); setFormulario({ id: null, datos: { ...VACIA } }) }}
        className="inline-flex items-center gap-1.5 rounded-lg bg-accent px-3 py-2 text-sm font-medium text-accent-ink disabled:opacity-40"><Plus className="h-4 w-4" />Registrar máquina</button>} />
    <SoloLecturaAviso />
    {isGuided && esPlanificador && <section className="mb-5 rounded-2xl border border-accent/25 bg-accent-soft/60 p-5"><div className="flex items-start gap-3"><CircleHelp className="mt-0.5 h-5 w-5 shrink-0 text-accent" /><div><p className="text-xs font-semibold uppercase tracking-wide text-accent">Configuración de apoyo</p><p className="mt-1 text-sm text-ink-soft">Revisa esta pantalla solo si cambió una máquina. El objetivo semanal baja 2 partidas por cada día con indisponibilidad registrada; un día tocado en parte cuenta como indisponible.</p></div></div></section>}

    <div className="mb-4 flex items-center gap-2 rounded-lg border border-line bg-panel px-3 py-2 sm:max-w-xs"><Search className="h-4 w-4 text-ink-faint" />
      <input value={busqueda} onChange={(e) => setBusqueda(e.target.value)} placeholder="Buscar por código o color (C/B)…" className="w-full bg-transparent text-sm text-ink focus:outline-none" /></div>

    <AvisoError error={error} onCerrar={() => setError(null)} />
    {lista.error && <AvisoError error={lista.error} />}
    {lista.cargando && !lista.datos ? <Cargando /> : <Paginado items={lista.datos ?? []} reinicio={busqueda}>{(pagina) => (
      <div className="overflow-hidden rounded-xl border border-line bg-panel"><div className="overflow-x-auto"><table className="w-full min-w-[980px] text-left text-sm">
        <thead><tr className="border-b border-line text-xs tracking-wide text-ink-faint uppercase"><th className="px-4 py-3">Código</th><th className="px-4 py-3">Barca</th><th className="px-4 py-3">Capacidad</th><th className="px-4 py-3">Clasificación de color</th><th className="px-4 py-3">Objetivo semanal</th><th className="px-4 py-3">Estado actual</th><th className="px-4 py-3" /></tr></thead>
        <tbody>{(lista.datos ?? []).length === 0 ? <tr><td colSpan={7} className="px-4 py-10 text-center text-ink-faint">No hay máquinas que coincidan.</td></tr> : pagina.map((m) => (
          <tr key={m.id} className="border-b border-line last:border-0">
            <td className="px-4 py-2.5 font-mono font-semibold">{m.codigo}</td>
            <td className="px-4 py-2.5 font-mono">{m.barca ?? <span className="text-danger">sin barca</span>}</td>
            <td className="px-4 py-2.5">{kg(m.capacidad)}</td><td className="px-4 py-2.5">{CLASIFICACION_COLOR[m.clasificacionColor]}</td>
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
      description="La barca sale de la capacidad y la clasificación de color."
      footer={<><button type="button" onClick={() => setFormulario(null)} className="rounded-lg border border-line px-3 py-2 text-sm">Cancelar</button><button type="button" onClick={guardar} disabled={guardando} className="rounded-lg bg-accent px-3 py-2 text-sm font-medium text-accent-ink disabled:opacity-50">Guardar</button></>}>
      {d && <div className="flex flex-col gap-4">
        <AvisoError error={errorForm} />
        {formulario?.id === null && <Field label="Código"><input className={inputClass} value={d.codigo ?? ''} onChange={(e) => set({ codigo: e.target.value })} /></Field>}
        <Field label="Capacidad (kg)"><input type="number" min="1" className={inputClass} value={d.capacidad || ''} onChange={(e) => set({ capacidad: Number(e.target.value) })} /></Field>
        <Field label="Clasificación de color"><select className={inputClass} value={d.clasificacionColor} onChange={(e) => set({ clasificacionColor: e.target.value })}><option value="C">Color (C)</option><option value="B">Blanco (B)</option></select></Field>
        <Field label="Barca" hint="Se completa sola."><input readOnly className={`${inputClass} font-mono opacity-70`} placeholder="Indica la capacidad" value={d.capacidad > 0 ? `${d.capacidad} ${d.clasificacionColor}` : ''} /></Field>
        <Field label="Objetivo semanal (partidas)" hint="Referencia: 16 partidas."><input type="number" min="1" className={inputClass} value={d.objetivoSemanal} onChange={(e) => set({ objetivoSemanal: Number(e.target.value) })} /></Field>
        <Field label="Estado registrado" hint="Los días sin partidas los definen los intervalos de indisponibilidad."><select className={inputClass} value={d.estado} onChange={(e) => set({ estado: e.target.value as EstadoMaquina })}>
          {(Object.keys(ESTADO_MAQUINA) as EstadoMaquina[]).map((e) => <option key={e} value={e}>{ESTADO_MAQUINA[e]}</option>)}</select></Field>
      </div>}
    </Drawer>

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
