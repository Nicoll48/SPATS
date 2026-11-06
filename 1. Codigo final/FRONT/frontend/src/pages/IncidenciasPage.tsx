import { useState } from 'react'
import { AlertTriangle, CheckCircle2, Plus } from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { SoloLecturaAviso } from '@/components/common/SoloLecturaAviso'
import { AvisoError, Cargando } from '@/components/common/AvisoError'
import { Drawer } from '@/components/ui/Drawer'
import { Field, inputClass } from '@/components/ui/FormField'
import { StatusPill } from '@/components/ui/StatusPill'
import { WeekSelector } from '@/components/planning/WeekSelector'
import { PanelReplanificacion } from '@/components/incidencias/PanelReplanificacion'
import { MENSAJE_SIN_PERMISO, useAuth } from '@/auth/auth-context'
import { useSemana } from '@/semana/semana-context'
import { incidencias as api, maquinas, planificacion, type RegistrarIncidencia } from '@/api/servicios'
import type { ErrorApi } from '@/api/cliente'
import type { TipoIncidencia } from '@/api/tipos'
import { intentar, useDatos } from '@/lib/useDatos'
import { TIPO_INCIDENCIA } from '@/lib/etiquetas'
import { ahoraLocal, etiquetaSemana, fechaCorta, fechaHora } from '@/lib/fechas'
import { Paginado } from '@/components/ui/Paginacion'

const TIPOS: TipoIncidencia[] = ['INCIDENCIA_MAQUINA', 'PROBLEMA_COMPLEMENTO', 'CAMBIO_PRIORIDAD']

function formularioVacio(): RegistrarIncidencia {
  return { tipo: 'INCIDENCIA_MAQUINA', fechaHoraOcurrencia: ahoraLocal(), codigoMaquina: '', numeroPartida: '', numeroOp: '', fechaHoraDisponibilidad: '', nuevaFechaEntrega: '', descripcion: '' }
}

/** Registro de incidencias sobre la planificación vigente (HU-17), que se acumulan (RF52), y re-planificación (HU-18). */
export function IncidenciasPage() {
  const { esPlanificador } = useAuth()
  const { lunes } = useSemana()
  const lista = useDatos(() => api.deLaSemana(lunes), [lunes])
  const estado = useDatos(() => planificacion.estadoSemana(lunes), [lunes])
  const parque = useDatos(() => maquinas.buscar(), [])
  const [form, setForm] = useState<RegistrarIncidencia | null>(null)
  const [error, setError] = useState<ErrorApi | null>(null)
  const [mensaje, setMensaje] = useState<string | null>(null)

  const vigente = estado.datos?.planificacionVigente
  const abierta = vigente?.estado === 'ABIERTA'

  async function registrar() {
    if (!form) return
    setError(null)
    const cuerpo: RegistrarIncidencia = {
      tipo: form.tipo, fechaHoraOcurrencia: form.fechaHoraOcurrencia, descripcion: form.descripcion || undefined,
      ...(form.tipo === 'INCIDENCIA_MAQUINA' && { codigoMaquina: form.codigoMaquina, fechaHoraDisponibilidad: form.fechaHoraDisponibilidad || undefined }),
      ...(form.tipo === 'PROBLEMA_COMPLEMENTO' && { numeroPartida: form.numeroPartida, fechaHoraDisponibilidad: form.fechaHoraDisponibilidad || undefined }),
      ...(form.tipo === 'CAMBIO_PRIORIDAD' && { numeroOp: form.numeroOp, nuevaFechaEntrega: form.nuevaFechaEntrega || undefined }),
    }
    const r = await intentar(() => api.registrar(lunes, cuerpo))
    if (!r.ok) { setError(r.error); return }
    setForm(null)
    setMensaje(r.valor.tipo === 'CAMBIO_PRIORIDAD'
      ? `Incidencia registrada. El pedido ${r.valor.referencia} conserva su fecha de entrega (${fechaCorta(r.valor.fechaEntregaActual)}); cambiará a ${fechaCorta(r.valor.nuevaFechaEntrega)} cuando confirmes la re-planificación que considere esta incidencia.`
      : 'Incidencia registrada.')
    await lista.recargar()
  }

  const motivoRegistrar = !esPlanificador ? MENSAJE_SIN_PERMISO : !vigente ? 'La semana no tiene planificación vigente' : !abierta ? 'La semana ya está cerrada' : null

  return <>
    <PageHeader title="Incidencias y replanificación" description="El planificador registra las incidencias que afectan la planificación vigente; el sistema no las detecta solo."
      actions={<button type="button" disabled={motivoRegistrar !== null} title={motivoRegistrar ?? 'Registrar una incidencia'} onClick={() => { setError(null); setForm(formularioVacio()) }}
        className="inline-flex items-center gap-1.5 rounded-lg bg-accent px-3 py-2 text-sm font-medium text-accent-ink disabled:opacity-40"><Plus className="h-4 w-4" />Registrar incidencia</button>} />
    <SoloLecturaAviso />
    <div className="mb-6 flex flex-wrap items-center gap-3"><WeekSelector />
      <span className="text-sm text-ink-soft">{vigente ? `Planificación vigente: versión ${vigente.numeroVersion}` : `Sin planificación vigente para la semana del ${etiquetaSemana(lunes)}`}</span></div>
    {mensaje && <p role="status" className="mb-5 flex items-center gap-2 rounded-xl border border-success/40 bg-success-soft p-3.5 text-sm text-success"><CheckCircle2 className="h-4 w-4" />{mensaje}</p>}

    <PanelReplanificacion lunes={lunes} incidencias={lista.datos ?? []} esPlanificador={esPlanificador}
      onCambio={async () => { await Promise.all([lista.recargar(), estado.recargar()]) }} />

    {lista.error && <AvisoError error={lista.error} />}
    {lista.cargando ? <Cargando /> : (lista.datos ?? []).length === 0
      ? <div className="rounded-xl border border-line bg-panel p-8 text-center text-sm text-ink-soft">No hay incidencias registradas en esta semana.</div>
      : <Paginado items={lista.datos ?? []} reinicio={lunes}>{(pagina) => <ul className="flex flex-col gap-3">{pagina.map((i) => (
          <li key={i.id} className="rounded-xl border border-line bg-panel p-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <p className="flex items-center gap-2 font-medium text-ink"><AlertTriangle className="h-4 w-4 text-warning" />{TIPO_INCIDENCIA[i.tipo].titulo} · {i.entidadAfectada} <span className="font-mono">{i.referencia}</span></p>
              <StatusPill tone={i.estado === 'PENDIENTE' ? 'warning' : 'success'} label={i.estado === 'PENDIENTE' ? 'Pendiente de re-planificación' : 'Considerada'} />
            </div>
            {i.descripcion && <p className="mt-2 text-sm text-ink-soft">{i.descripcion}</p>}
            <p className="mt-2 text-xs text-ink-faint">Ocurrió el {fechaHora(i.fechaHoraOcurrencia)}
              {i.fechaHoraDisponibilidad && ` · disponible desde ${fechaHora(i.fechaHoraDisponibilidad)}`}
              {i.nuevaFechaEntrega && ` · nueva fecha de entrega ${fechaCorta(i.nuevaFechaEntrega)}`}
              {` · registrada por ${i.registradaPor} sobre la versión ${i.versionPlanificacionOrigen}`}</p>
          </li>))}</ul>}</Paginado>}

    <Drawer open={form !== null} onClose={() => setForm(null)} title="Registrar incidencia" description={form ? TIPO_INCIDENCIA[form.tipo as TipoIncidencia].descripcion : undefined}
      footer={<><button type="button" onClick={() => setForm(null)} className="rounded-lg border border-line px-3 py-2 text-sm">Cancelar</button><button type="button" onClick={registrar} className="rounded-lg bg-accent px-3 py-2 text-sm font-medium text-accent-ink">Registrar</button></>}>
      {form && <div className="flex flex-col gap-4">
        <AvisoError error={error} />
        <Field label="Tipo de incidencia"><select className={inputClass} value={form.tipo} onChange={(e) => setForm({ ...form, tipo: e.target.value })}>
          {TIPOS.map((t) => <option key={t} value={t}>{TIPO_INCIDENCIA[t].titulo}</option>)}</select></Field>
        {form.tipo === 'INCIDENCIA_MAQUINA' && <Field label="Máquina afectada"><select className={inputClass} value={form.codigoMaquina} onChange={(e) => setForm({ ...form, codigoMaquina: e.target.value })}>
          <option value="">Selecciona una máquina…</option>{(parque.datos ?? []).map((m) => <option key={m.id} value={m.codigo}>{m.codigo} · {m.barca}</option>)}</select></Field>}
        {form.tipo === 'PROBLEMA_COMPLEMENTO' && <Field label="Partida afectada"><input className={inputClass} placeholder="Número de partida" value={form.numeroPartida} onChange={(e) => setForm({ ...form, numeroPartida: e.target.value })} /></Field>}
        {form.tipo === 'CAMBIO_PRIORIDAD' && <Field label="Pedido afectado (OP)"><input className={inputClass} placeholder="Número de OP" value={form.numeroOp} onChange={(e) => setForm({ ...form, numeroOp: e.target.value })} /></Field>}
        <Field label="Fecha y hora de ocurrencia"><input type="datetime-local" className={inputClass} value={form.fechaHoraOcurrencia} onChange={(e) => setForm({ ...form, fechaHoraOcurrencia: e.target.value })} /></Field>
        {form.tipo !== 'CAMBIO_PRIORIDAD'
          ? <Field label="Fecha y hora prevista de disponibilidad"><input type="datetime-local" className={inputClass} value={form.fechaHoraDisponibilidad} onChange={(e) => setForm({ ...form, fechaHoraDisponibilidad: e.target.value })} /></Field>
          : <Field label="Nueva fecha de entrega" hint="El pedido toma esta fecha cuando confirmes la re-planificación que considere la incidencia; la anterior queda en la trazabilidad."><input type="date" className={inputClass} value={form.nuevaFechaEntrega} onChange={(e) => setForm({ ...form, nuevaFechaEntrega: e.target.value })} /></Field>}
        <Field label="Descripción (opcional)"><textarea rows={3} maxLength={255} className={inputClass} value={form.descripcion} onChange={(e) => setForm({ ...form, descripcion: e.target.value })} /></Field>
      </div>}
    </Drawer>
  </>
}
