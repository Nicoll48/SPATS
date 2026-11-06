import { useState } from 'react'
import { ArrowRight, FileClock, GitCompare, History } from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { AvisoError, Cargando } from '@/components/common/AvisoError'
import { Tabs, type TabItem } from '@/components/ui/Tabs'
import { StatusPill } from '@/components/ui/StatusPill'
import { WeekSelector } from '@/components/planning/WeekSelector'
import { useSemana } from '@/semana/semana-context'
import { planificacion, trazabilidad } from '@/api/servicios'
import type { Ubicacion } from '@/api/tipos'
import { useDatos } from '@/lib/useDatos'
import { CLASIFICACION_RESULTADO, ESTADO_PLANIFICACION, TONO_PLANIFICACION } from '@/lib/etiquetas'
import { fechaCorta, fechaHora } from '@/lib/fechas'
import { Paginado, Paginador } from '@/components/ui/Paginacion'

const VISTAS: TabItem[] = [{ id: 'versiones', label: 'Versiones', icon: History }, { id: 'trazabilidad', label: 'Trazabilidad', icon: FileClock }]

/** Historial de versiones con comparación (HU-19) y trazabilidad de operaciones (HU-21). */
export function HistorialPage() {
  const [vista, setVista] = useState('versiones')
  return <>
    <PageHeader title="Historial" description="Versiones de la planificación de cada semana y registro de operaciones." />
    <Tabs tabs={VISTAS} activeId={vista} onChange={setVista} />
    {vista === 'versiones' ? <Versiones /> : <Trazabilidad />}
  </>
}

function Versiones() {
  const { lunes } = useSemana()
  const lista = useDatos(() => planificacion.versiones(lunes), [lunes])
  const [seleccion, setSeleccion] = useState<number[]>([])
  const comparacion = useDatos(() => (seleccion.length === 2 ? planificacion.comparar(seleccion[0], seleccion[1]) : Promise.resolve(null)), [seleccion.join(',')])

  function alternar(id: number) {
    setSeleccion((prev) => prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id].slice(-2))
  }

  return <>
    <div className="mb-5 flex flex-wrap items-center gap-3"><WeekSelector /><span className="text-sm text-ink-soft">Marca dos versiones para compararlas. Solo una es la vigente.</span></div>
    {lista.error && <AvisoError error={lista.error} />}
    {lista.cargando ? <Cargando /> : (lista.datos ?? []).length === 0
      ? <div className="rounded-xl border border-line bg-panel p-8 text-center text-sm text-ink-soft">La semana no tiene versiones de planificación.</div>
      : <Paginado items={lista.datos ?? []} reinicio={lunes}>{(pagina) => <div className="overflow-x-auto rounded-xl border border-line bg-panel"><table className="w-full min-w-[900px] text-left text-sm">
          <thead><tr className="border-b border-line text-xs uppercase text-ink-faint"><th className="px-4 py-3" /><th className="px-4 py-3">Versión</th><th className="px-4 py-3">Generada</th><th className="px-4 py-3">Estado</th><th className="px-4 py-3">Proyección</th><th className="px-4 py-3 text-right">Asignadas</th><th className="px-4 py-3 text-right">Sobrantes</th><th className="px-4 py-3 text-right">Bloqueadas</th><th className="px-4 py-3 text-right">Lavados</th></tr></thead>
          <tbody>{pagina.map((v) => (
            <tr key={v.id} className="border-b border-line last:border-0">
              <td className="px-4 py-2.5"><input type="checkbox" checked={seleccion.includes(v.id)} onChange={() => alternar(v.id)} aria-label={`Comparar versión ${v.numeroVersion}`} /></td>
              <td className="px-4 py-2.5 font-mono font-semibold">v{v.numeroVersion}</td><td className="px-4 py-2.5 text-ink-soft">{fechaHora(v.fechaGeneracion)}</td>
              <td className="px-4 py-2.5"><StatusPill tone={TONO_PLANIFICACION[v.estado]} label={ESTADO_PLANIFICACION[v.estado]} /></td>
              <td className="px-4 py-2.5 font-mono">v{v.versionProyeccion}</td>
              <td className="px-4 py-2.5 text-right font-mono">{v.asignadas}</td><td className="px-4 py-2.5 text-right font-mono">{v.sobrantes}</td>
              <td className="px-4 py-2.5 text-right font-mono">{v.bloqueadas}</td><td className="px-4 py-2.5 text-right font-mono">{v.indicadores.numeroLavados ?? '—'}</td>
            </tr>))}</tbody></table></div>}</Paginado>}

    {seleccion.length === 2 && (comparacion.cargando ? <div className="mt-6"><Cargando /></div> : comparacion.datos && (
      <section className="mt-6 space-y-4">
        <h3 className="flex items-center gap-2 text-sm font-semibold text-ink"><GitCompare className="h-4 w-4" />Versión {comparacion.datos.a.numeroVersion} frente a versión {comparacion.datos.b.numeroVersion}</h3>
        <Paginado items={comparacion.datos.maquinas}>{(pagina) => <div className="overflow-x-auto rounded-xl border border-line bg-panel"><table className="w-full min-w-[640px] text-left text-sm">
          <thead><tr className="border-b border-line text-xs uppercase text-ink-faint"><th className="px-4 py-3">Máquina</th><th className="px-4 py-3 text-right">Asig. A</th><th className="px-4 py-3 text-right">Sobr. A</th><th className="px-4 py-3 text-right">Dif. A</th><th className="px-4 py-3 text-right">Asig. B</th><th className="px-4 py-3 text-right">Sobr. B</th><th className="px-4 py-3 text-right">Dif. B</th></tr></thead>
          <tbody>{pagina.map((m) => <tr key={m.codigo} className="border-b border-line last:border-0"><td className="px-4 py-2 font-mono">{m.codigo}</td>
            {[m.asignadasA, m.sobrantesA, m.diferenciaA, m.asignadasB, m.sobrantesB, m.diferenciaB].map((x, i) => <td key={i} className="px-4 py-2 text-right font-mono">{x ?? '—'}</td>)}</tr>)}</tbody></table></div>}</Paginado>
        <p className="text-sm text-ink-soft">{comparacion.datos.cambios.length} partidas cambiaron de máquina, día o clasificación.</p>
        {comparacion.datos.cambios.length > 0 && <Paginado items={comparacion.datos.cambios}>{(pagina) => <div className="overflow-auto rounded-xl border border-line bg-panel"><table className="w-full text-left text-sm">
          <thead><tr className="border-b border-line text-xs uppercase text-ink-faint"><th className="px-4 py-3">Partida</th><th className="px-4 py-3">Versión A</th><th className="px-4 py-3" /><th className="px-4 py-3">Versión B</th></tr></thead>
          <tbody>{pagina.map((c) => <tr key={c.numeroPartida} className="border-b border-line last:border-0"><td className="px-4 py-2 font-mono">{c.numeroPartida}</td><td className="px-4 py-2">{ubicacion(c.a)}</td><td className="px-2 py-2"><ArrowRight className="h-3.5 w-3.5 text-ink-faint" /></td><td className="px-4 py-2">{ubicacion(c.b)}</td></tr>)}</tbody></table></div>}</Paginado>}
      </section>))}
  </>
}

function ubicacion(u: Ubicacion | null) {
  if (!u) return <span className="text-ink-faint">No figura</span>
  if (u.clasificacion === 'BLOQUEADA') return <span className="text-warning">Bloqueada</span>
  return <span>{CLASIFICACION_RESULTADO[u.clasificacion]} · {u.maquina} · {fechaCorta(u.dia)}</span>
}

function Trazabilidad() {
  const [pagina, setPagina] = useState(0)
  const { datos, error, cargando } = useDatos(() => trazabilidad.consultar(pagina), [pagina])
  const total = datos ? Math.max(1, Math.ceil(datos.total / datos.tamanio)) : 1
  return <>
    {error && <AvisoError error={error} />}
    {cargando && !datos ? <Cargando /> : <div className="overflow-x-auto rounded-xl border border-line bg-panel"><table className="w-full min-w-[900px] text-left text-sm">
      <thead><tr className="border-b border-line text-xs uppercase text-ink-faint"><th className="px-4 py-3">Cuándo</th><th className="px-4 py-3">Quién</th><th className="px-4 py-3">Operación</th><th className="px-4 py-3">Entidad</th><th className="px-4 py-3">Campo</th><th className="px-4 py-3">Valor anterior</th><th className="px-4 py-3">Valor nuevo</th></tr></thead>
      <tbody>{(datos?.contenido ?? []).length === 0 ? <tr><td colSpan={7} className="px-4 py-8 text-center text-ink-faint">Sin operaciones registradas.</td></tr>
        : (datos?.contenido ?? []).map((t) => <tr key={t.id} className="border-b border-line last:border-0"><td className="px-4 py-2 text-ink-soft">{fechaHora(t.fechaHora)}</td><td className="px-4 py-2">{t.usuario}</td>
          <td className="px-4 py-2 font-mono text-xs">{t.tipoOperacion}</td><td className="px-4 py-2 font-mono text-xs">{t.entidad}{t.idEntidad !== null ? ` #${t.idEntidad}` : ''}</td>
          <td className="px-4 py-2 font-mono text-xs">{t.campo ?? '—'}</td><td className="px-4 py-2 text-ink-soft">{t.valorAnterior ?? '—'}</td><td className="px-4 py-2">{t.valorNuevo?.replace(/, F = [\d.,]+$/, '') ?? '—'}</td></tr>)}</tbody></table></div>}
    {datos && datos.total > 0 && <Paginador pagina={pagina + 1} total={total} registros={datos.total} onCambiar={(p) => setPagina(p - 1)} />}
  </>
}
