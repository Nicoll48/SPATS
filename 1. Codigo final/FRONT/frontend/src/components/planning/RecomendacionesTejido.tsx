import { useEffect, useState } from 'react'
import { AlertTriangle, CalendarRange, CheckCircle2, ChevronDown, ChevronRight, Download, EyeOff, HardDrive, Info, Scissors } from 'lucide-react'
import { AvisoError, Cargando } from '@/components/common/AvisoError'
import { CalendarioPlanificacion, EtiquetaVersion } from '@/components/planning/CalendarioPlanificacion'
import { RecomendacionesPorArticulo, estadoSeleccion, type FilaReco } from '@/components/planning/RecomendacionesPorArticulo'
import { planificacion } from '@/api/servicios'
import type { ErrorApi } from '@/api/cliente'
import type { MaquinaRecomendaciones, PlanificacionDetalle, RecomendacionTejido, RecomendacionesTejido as Datos } from '@/api/tipos'
import { intentar } from '@/lib/useDatos'
import { kg } from '@/lib/etiquetas'
import { diaMes, fechaCorta, nombreDia } from '@/lib/fechas'
import { colorVersion, compararVersiones, etiquetaVersion } from '@/lib/versiones'

/** Versión marcada en el panel: su número de proyección y el id de su planificación (null si no la tiene). */
export interface VersionRecomendaciones { version: number; idPlanificacion: number | null }

const dia = (iso: string) => `${nombreDia(iso)} ${diaMes(iso)}`

/** Todas las filas de una versión con la máquina en que llenan. */
const filasDe = (d: Datos): FilaReco[] => d.maquinas.flatMap((m) => m.recomendaciones.map((r) => ({ ...r, maquina: m.maquina })))

/** Día en que lo tejido llega a tintorería: lunes a viernes → al día siguiente; sábado → el lunes (D-56). */
function disponibleTrasTejer(tejido: string): string {
  const f = new Date(`${tejido}T00:00:00`)
  f.setDate(f.getDate() + (f.getDay() === 6 ? 2 : 1))
  return `${f.getFullYear()}-${String(f.getMonth() + 1).padStart(2, '0')}-${String(f.getDate()).padStart(2, '0')}`
}

/**
 * Vista previa (solo para ver, no se guarda): el calendario de la versión con las partidas recomendadas agregadas en su
 * máquina y su día, como Asignadas y marcadas «recomendada».
 */
function conRecomendadas(plan: PlanificacionDetalle, filas: RecomendacionTejido[], maquinaDe: Map<number, string>): PlanificacionDetalle {
  const porPartida = new Map<string, { f: RecomendacionTejido; kg: number; maquina: string }>()
  for (const f of filas) {
    const actual = porPartida.get(f.partida)
    if (actual) actual.kg += f.kg
    else porPartida.set(f.partida, { f, kg: f.kg, maquina: maquinaDe.get(f.id) ?? '' })
  }
  const maquinas = plan.maquinas.map((m) => ({
    ...m,
    dias: m.dias.map((d) => {
      const nuevas = [...porPartida.values()].filter((x) => x.maquina === m.codigo && x.f.diaLlena === d.fecha)
      if (nuevas.length === 0) return d
      const extra = nuevas.map((x, i) => ({
        numeroPartida: x.f.partida, numeroOp: x.f.numeroOp, fechaEntrega: x.f.fechaEntrega ?? '', codigoColor: x.f.codigoColor, color: x.f.color,
        clasificacionColor: x.f.clasificacionColor, tonalidad: x.f.tonalidad, kg: x.f.kgPartida, clasificacion: 'ASIGNADA' as const,
        requiereLavado: false, ordenSecuencia: d.partidas.length + i + 1, fechaDisponible: disponibleTrasTejer(x.f.diaTejido), estadoConfirmacionCierre: null,
        recomendada: { diaTejido: x.f.diaTejido, kgATejer: x.kg },
      }))
      return { ...d, regulares: d.regulares + extra.length, partidas: [...d.partidas, ...extra] }
    }),
  }))
  return { ...plan, maquinas }
}

/**
 * Pestaña «Recomendaciones» (RF30, HU-13, D-56): qué tejer DE MÁS en la misma semana para llenar el espacio libre del
 * calendario y acercar cada máquina a su objetivo. Por artículo (qué tejer; al desplegar, las partidas que abastece, su
 * máquina y su día) o por máquina. Se pueden seleccionar para verlas en el calendario (vista previa). Solo se muestran: no crean versiones de la proyección
 * (corrección de la autora, 13/11); si tejeduría teje de más, llega en la proyección que se sube.
 */
export function RecomendacionesTejido({ versiones, lunes, esPlanificador, hayVigente }: {
  versiones: VersionRecomendaciones[]; lunes: string; esPlanificador: boolean; hayVigente: boolean
}) {
  const [datos, setDatos] = useState<Record<number, Datos>>({})
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState<ErrorApi | null>(null)
  /** Recomendaciones seleccionadas por versión (ids), para la vista previa. */
  const [marcadas, setMarcadas] = useState<Record<number, Set<number>>>({})
  /** Vista previa en el calendario: versión que se está mostrando y su planificación. */
  const [vista, setVista] = useState<{ version: number; plan: PlanificacionDetalle } | null>(null)
  const [cargandoVista, setCargandoVista] = useState<number | null>(null)
  /** Presentación: por artículo (qué tejer, la principal) o por máquina. */
  const [modo, setModo] = useState<'articulo' | 'maquina'>('articulo')
  const clave = versiones.map((v) => `${v.version}:${v.idPlanificacion}`).join('|')

  useEffect(() => {
    let vigente = true
    setCargando(true)
    setError(null)
    setMarcadas({})
    void Promise.all(versiones.filter((v) => v.idPlanificacion !== null)
      .map(async (v) => [v.version, await intentar(() => planificacion.recomendaciones(v.idPlanificacion!))] as const))
      .then((rs) => {
        if (!vigente) return
        const nuevos: Record<number, Datos> = {}
        for (const [version, r] of rs) { if (r.ok) nuevos[version] = r.valor; else setError(r.error) }
        setDatos(nuevos)
        setCargando(false)
      })
    return () => { vigente = false }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [clave])

  if (cargando) return <Cargando texto="Calculando las recomendaciones…" />
  const dos = versiones.length > 1

  /** Marca o desmarca juntas todas las filas de una partida (principal y complemento). */
  function alternarPartida(version: number, ids: number[], marcar: boolean) {
    setMarcadas((m) => {
      const s = new Set(m[version] ?? [])
      ids.forEach((id) => (marcar ? s.add(id) : s.delete(id)))
      return { ...m, [version]: s }
    })
  }

  async function verCalendario(version: number, idPlanificacion: number) {
    if (vista?.version === version) { setVista(null); return }
    setCargandoVista(version)
    const r = await intentar(() => planificacion.detalle(idPlanificacion))
    setCargandoVista(null)
    if (!r.ok) { setError(r.error); return }
    setVista({ version, plan: r.valor })
  }

  async function exportar() {
    setError(null)
    const r = await intentar(() => planificacion.exportarRecomendaciones(lunes))
    if (!r.ok) setError(r.error)
  }

  return (
    <div className="space-y-6">
      <AvisoError error={error} onCerrar={() => setError(null)} />
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="flex items-center gap-2 text-base font-semibold text-ink"><Scissors className="h-5 w-5 text-accent" aria-hidden="true" />Tejido adicional recomendado para esta semana</h3>
          <p className="mt-1 max-w-3xl text-sm text-ink-soft">Qué artículos tejer de más para llenar el espacio libre del calendario y acercar cada máquina a su objetivo semanal. Despliega un artículo para ver las partidas que completa, en qué máquina y qué día.</p>
        </div>
        {esPlanificador && (
          <div className="flex flex-wrap gap-2">
            {hayVigente && (
              <button type="button" onClick={exportar} title="Excel de la planificación vigente, ordenado por día de tejido"
                className="inline-flex items-center gap-1.5 rounded-lg border border-line bg-panel px-3 py-2 text-sm font-medium text-ink hover:bg-panel-muted">
                <Download className="h-4 w-4" aria-hidden="true" />Exportar para tejeduría</button>
            )}
          </div>
        )}
      </div>

      {versiones.map((v) => {
        const d = datos[v.version]
        if (v.idPlanificacion === null)
          return <p key={v.version} className="rounded-xl border border-dashed border-line bg-panel p-4 text-sm text-ink-soft">
            Genera la planificación de la <EtiquetaVersion versiones={[v.version]} className="text-sm" /> para ver sus recomendaciones.</p>
        if (!d) return null
        return (
          <section key={v.version} aria-label={`Recomendaciones de la ${etiquetaVersion(v.version)}`} className="space-y-3"
            style={dos ? { borderLeft: `3px solid ${colorVersion(v.version)}`, paddingLeft: 12 } : undefined}>
            {dos && <EtiquetaVersion versiones={[v.version]} className="text-sm" />}
            {d.maquinas.length === 0
              ? <p className="rounded-xl border border-accent/20 bg-indicador px-4 py-3 text-sm"><span className="inline-flex items-center gap-1.5 text-success"><CheckCircle2 className="h-4 w-4" aria-hidden="true" />Todas las máquinas alcanzaron su objetivo semanal. No hay recomendaciones.</span></p>
              : (
                <div className="grid gap-3 sm:grid-cols-3">
                  {[[kg(d.totalKg), 'de tejido adicional'], [String(new Set(filasDe(d).map((f) => f.codigoArticulo)).size), 'artículos'],
                    [String(d.totalPartidas), d.totalPartidas === 1 ? 'partida que se completaría' : 'partidas que se completarían']].map(([n, t]) => (
                    <div key={t} className="rounded-xl border border-accent/20 bg-indicador px-4 py-3">
                      <p className="font-mono text-xl font-bold text-indicador-texto">{n}</p><p className="text-sm text-ink-soft">{t}</p></div>
                  ))}
                </div>
              )}
            {(marcadas[v.version]?.size ?? 0) > 0 && (() => {
              const todas = filasDe(d)
              const sel = marcadas[v.version]
              const { completas, incompletas, faltantes } = estadoSeleccion(todas, sel)
              const articulosSel = new Set(todas.filter((f) => sel.has(f.id)).map((f) => f.codigoArticulo)).size
              const faltan = [...new Set(faltantes.map((f) => f.codigoArticulo))]
              return (
                <div role="toolbar" aria-label="Acciones con las recomendaciones seleccionadas"
                  className="sticky top-2 z-20 flex flex-wrap items-center gap-x-4 gap-y-2 rounded-xl bg-accent px-4 py-2.5 text-sm text-accent-ink shadow-lg">
                  <span className="font-semibold">{articulosSel} {articulosSel === 1 ? 'artículo' : 'artículos'} · completan {completas.size} {completas.size === 1 ? 'partida' : 'partidas'}</span>
                  {incompletas > 0 && (
                    <span className="flex flex-wrap items-center gap-x-2 gap-y-1">
                      <span className="flex items-center gap-1"><AlertTriangle className="h-4 w-4" aria-hidden="true" />
                        {incompletas} {incompletas === 1 ? 'queda incompleta' : 'quedan incompletas'} sin {faltan.slice(0, 2).join(', ')}{faltan.length > 2 ? ` y ${faltan.length - 2} más` : ''}</span>
                      <button type="button" onClick={() => alternarPartida(v.version, faltantes.map((f) => f.id), true)} className="underline underline-offset-2 hover:opacity-80">
                        Agregar lo que falta</button>
                    </span>)}
                  <span className="ml-auto flex flex-wrap items-center gap-2">
                    {vista?.version === v.version
                      ? <span className="text-xs opacity-90">La vista previa muestra solo las seleccionadas</span>
                      : <button type="button" onClick={() => verCalendario(v.version, v.idPlanificacion!)} disabled={cargandoVista !== null}
                          className="inline-flex items-center gap-1.5 rounded-lg bg-panel px-3 py-1.5 font-medium text-accent hover:opacity-90 disabled:opacity-50">
                          <CalendarRange className="h-4 w-4" aria-hidden="true" />{cargandoVista === v.version ? 'Cargando…' : 'Ver en el calendario'}</button>}
                    <button type="button" onClick={() => setMarcadas((m) => ({ ...m, [v.version]: new Set() }))}
                      className="rounded-lg px-3 py-1.5 font-medium hover:bg-white/15">Cancelar</button>
                  </span>
                </div>
              )
            })()}
            {d.maquinas.length > 0 && (
              <button type="button" onClick={() => verCalendario(v.version, v.idPlanificacion!)} disabled={cargandoVista !== null}
                className="inline-flex items-center gap-1.5 rounded-lg border border-line bg-panel px-3 py-2 text-sm font-medium text-ink hover:bg-panel-muted disabled:opacity-40">
                {vista?.version === v.version ? <EyeOff className="h-4 w-4" aria-hidden="true" /> : <CalendarRange className="h-4 w-4" aria-hidden="true" />}
                {cargandoVista === v.version ? 'Cargando…' : vista?.version === v.version ? 'Ocultar calendario' : 'Ver en el calendario'}</button>
            )}
            {vista?.version === v.version && (() => {
              const marcadasVersion = marcadas[v.version] ?? new Set<number>()
              const todas = filasDe(d)
              const { completas } = estadoSeleccion(todas, marcadasVersion)
              // Solo entran las partidas con todas sus telas seleccionadas: con una sola no se llenan
              const filas = marcadasVersion.size > 0 ? todas.filter((f) => completas.has(f.partida)) : todas
              const maquinaDe = new Map<number, string>(todas.map((f) => [f.id, f.maquina]))
              const partidas = new Set(filas.map((f) => f.partida)).size
              return (
                <div className="space-y-2 rounded-xl border border-accent/30 bg-panel p-3">
                  <p className="text-sm text-ink-soft">
                    <b className="text-ink">Vista previa (estimación).</b> {marcadasVersion.size > 0 ? `Las ${partidas} partidas que completa lo seleccionado` : `Las ${partidas} partidas recomendadas`} aparecen
                    con <span className="rounded border-2 border-dotted border-accent bg-accent-soft/60 px-1 text-ink">borde punteado</span> en el día que llenan; pasa el puntero
                    sobre una para ver qué tejer. Es solo para ver cómo quedaría: no cambia la planificación ni la proyección. Si tejeduría teje de más, se sube la proyección nueva y se genera esa versión.
                  </p>
                  <CalendarioPlanificacion calendario={compararVersiones([{ version: v.version, plan: conRecomendadas(vista.plan, filas, maquinaDe) }])} />
                </div>
              )
            })()}
            {d.maquinas.length > 0 && (
              <div role="tablist" aria-label="Presentación de las recomendaciones" className="inline-flex rounded-lg border border-line bg-panel p-0.5 text-sm">
                {([['articulo', 'Por artículo'], ['maquina', 'Por máquina']] as const).map(([k, t]) => (
                  <button key={k} type="button" role="tab" aria-selected={modo === k} onClick={() => setModo(k)}
                    className={`rounded-md px-3 py-1.5 font-medium ${modo === k ? 'bg-accent text-accent-ink' : 'text-ink-soft hover:bg-panel-muted'}`}>{t}</button>
                ))}
              </div>
            )}
            {d.maquinas.length > 0 && modo === 'articulo' && (
              <RecomendacionesPorArticulo filas={filasDe(d)} marcadas={marcadas[v.version] ?? new Set()}
                onAlternar={(ids, marcar) => alternarPartida(v.version, ids, marcar)} />
            )}
            {modo === 'maquina' && d.maquinas.map((m) => (
              <Maquina key={m.maquina} m={m} editable marcadas={marcadas[v.version] ?? new Set()}
                onAlternar={(ids, marcar) => alternarPartida(v.version, ids, marcar)} />
            ))}
          </section>
        )
      })}
    </div>
  )
}

/** Resumen desplegable de una máquina y sus recomendaciones. */
function Maquina({ m, editable, marcadas, onAlternar }: {
  m: MaquinaRecomendaciones; editable: boolean; marcadas: Set<number>; onAlternar: (ids: number[], marcar: boolean) => void
}) {
  const [abierta, setAbierta] = useState(false)
  const porPartida = new Map<string, number[]>()
  m.recomendaciones.forEach((r) => porPartida.set(r.partida, [...(porPartida.get(r.partida) ?? []), r.id]))
  const ids = m.recomendaciones.map((r) => r.id)
  const marcadasAqui = ids.filter((id) => marcadas.has(id)).length
  const todas = ids.length > 0 && marcadasAqui === ids.length
  return (
    <div className="overflow-hidden rounded-xl border border-line bg-panel">
      <button type="button" onClick={() => setAbierta((a) => !a)} aria-expanded={abierta}
        className="flex w-full flex-wrap items-center gap-x-3 gap-y-1 px-4 py-3 text-left hover:bg-panel-muted/60">
        {abierta ? <ChevronDown className="h-4 w-4 text-ink-soft" /> : <ChevronRight className="h-4 w-4 text-ink-soft" />}
        <span className="flex items-center gap-1.5 font-mono text-sm font-bold text-ink"><HardDrive className="h-4 w-4 text-ink-faint" aria-hidden="true" />{m.maquina}</span>
        <span className="text-sm text-ink-soft">faltan <b className="text-ink">{m.faltan}</b> · se cubren <b className="text-ink">{m.seCubren}</b> · <b className="text-ink">{kg(m.kg)}</b></span>
        {m.mensaje && <span className="flex items-center gap-1 text-xs text-warning"><Info className="h-3.5 w-3.5" aria-hidden="true" />{m.mensaje}</span>}
        {editable && marcadasAqui > 0 && <span className="ml-auto text-xs font-medium text-accent">{new Set(m.recomendaciones.filter((r) => marcadas.has(r.id)).map((r) => r.partida)).size} seleccionadas</span>}
      </button>
      {abierta && m.recomendaciones.length > 0 && (
        <div className="overflow-x-auto border-t border-line">
          <table className="w-full min-w-[860px] text-left text-sm">
            <thead><tr className="border-b border-line bg-panel-muted/60 text-xs text-ink-soft">
              {editable && <th className="w-10 px-3 py-2">
                <input type="checkbox" className="h-4 w-4" checked={todas} aria-label={`Seleccionar todas las recomendaciones de ${m.maquina}`}
                  ref={(el) => { if (el) el.indeterminate = marcadasAqui > 0 && !todas }}
                  onChange={() => onAlternar(ids, !todas)} /></th>}
              <th className="px-3 py-2 font-semibold">Día que llena</th><th className="px-3 py-2 font-semibold">Partida</th>
              <th className="px-3 py-2 font-semibold">Pedido (OP)</th><th className="px-3 py-2 font-semibold">Artículo</th>
              <th className="px-3 py-2 font-semibold">Componente</th><th className="px-3 py-2 text-right font-semibold">Kg a tejer</th>
              <th className="px-3 py-2 font-semibold">Tejer a más tardar el</th><th className="px-3 py-2 font-semibold">Entrega</th>
            </tr></thead>
            <tbody>{m.recomendaciones.map((r) => {
              const ids = porPartida.get(r.partida) ?? [r.id]
              const marcada = ids.every((id) => marcadas.has(id))
              return (
                <tr key={r.id} className={`border-b border-line last:border-0 ${marcada ? 'bg-accent-soft/50' : ''}`}>
                  {editable && <td className="px-3 py-2"><input type="checkbox" className="h-4 w-4" checked={marcada}
                    aria-label={`Marcar la partida ${r.partida}`} onChange={() => onAlternar(ids, !marcada)} /></td>}
                  <td className="px-3 py-2 text-ink">{dia(r.diaLlena)}</td>
                  <td className="px-3 py-2 font-mono font-semibold text-ink">{r.partida}</td>
                  <td className="px-3 py-2 font-mono text-ink-soft">{r.numeroOp}</td>
                  <td className="px-3 py-2"><span className="font-mono text-ink">{r.codigoArticulo}</span><span className="block text-xs text-ink-soft">{r.nombreArticulo}</span></td>
                  <td className="px-3 py-2 text-ink-soft">{r.componente === 'PRINCIPAL' ? 'Principal' : 'Complemento'}</td>
                  <td className="px-3 py-2 text-right font-mono font-semibold text-ink">{kg(r.kg)}</td>
                  <td className="px-3 py-2 text-ink">{dia(r.diaTejido)}</td>
                  <td className="px-3 py-2 text-ink-soft">{r.fechaEntrega ? fechaCorta(r.fechaEntrega) : '—'}</td>
                </tr>
              )
            })}</tbody>
          </table>
        </div>
      )}
    </div>
  )
}
