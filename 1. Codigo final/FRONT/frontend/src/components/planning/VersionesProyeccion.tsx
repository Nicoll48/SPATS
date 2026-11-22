import { useState } from 'react'
import { AlertTriangle, Ban, CheckCircle2, Droplets, Layers, Loader2, Package, Play, RefreshCw, Star, type LucideIcon } from 'lucide-react'
import type { ContextoCambioVigente, ReplanificacionResponse, VersionProyeccionPlan } from '@/api/tipos'
import { colorVersion, etiquetaVersion, fondoVersion, type VersionMarcada } from '@/lib/versiones'
import { numero } from '@/lib/etiquetas'
import { fechaCorta, nombreDia } from '@/lib/fechas'
import { EtiquetaVersion } from '@/components/planning/CalendarioPlanificacion'

/** Se pueden marcar como máximo dos versiones a la vez. */
export const MAXIMO_MARCADAS = 2

/** «11/11 09:30» a partir de la fecha y hora de carga. */
function cargaCorta(iso: string): string {
  const [dia, hora = ''] = iso.split('T')
  const [, mes, d] = dia.split('-')
  return `${d}/${mes} ${hora.slice(0, 5)}`
}

interface PanelProps {
  versiones: VersionProyeccionPlan[]
  marcadas: number[]
  onAlternar: (version: number) => void
  onGenerar: (v: VersionProyeccionPlan) => void
  onUsarComoVigente: (v: VersionProyeccionPlan) => void
  /** Versión que se está generando, si hay una. */
  generando: number | null
  bloqueado: string | null
}

/**
 * Panel de versiones de la proyección (D-52), como los calendarios de Google Calendar: cada versión es una casilla
 * con su color y solo su nombre («V1»). Va en una fila debajo de «Visualización». Solo aparece con dos o más versiones y la semana abierta.
 */
export function PanelVersiones({ versiones, marcadas, onAlternar, onGenerar, onUsarComoVigente, generando, bloqueado }: PanelProps) {
  const lleno = marcadas.length >= MAXIMO_MARCADAS
  return (
    <div role="group" aria-label="Versiones de la proyección" className="flex flex-wrap items-center gap-1.5">
      <span className="text-sm font-semibold text-ink">Versiones:</span>
      {versiones.map((v) => {
        const plan = v.planificacion
        const marcada = marcadas.includes(v.version)
        const sinPlan = plan === null
        const deshabilitada = sinPlan || (!marcada && lleno)
        const cargada = `Proyección cargada el ${cargaCorta(v.fechaCarga)}`
        const titulo = sinPlan ? `${cargada}. Genera la planificación para poder verla en el calendario`
          : !marcada && lleno ? `Puedes comparar como máximo ${MAXIMO_MARCADAS} versiones a la vez: desmarca una`
          : `${cargada}. ${marcada ? 'Ocultar' : 'Mostrar'} la ${etiquetaVersion(v.version)} en el calendario`
        return (
          <span key={v.idProyeccion} className={`inline-flex items-center gap-1.5 rounded-lg border px-2 py-1 text-sm ${marcada ? 'border-line bg-panel' : 'border-line/70 bg-panel-muted/40'}`}>
            <label title={titulo} className={`flex items-center gap-1.5 ${deshabilitada ? 'cursor-not-allowed text-ink-faint' : 'cursor-pointer text-ink'}`}>
              <input type="checkbox" checked={marcada} disabled={deshabilitada} onChange={() => onAlternar(v.version)}
                className="h-4 w-4 disabled:opacity-60" style={{ accentColor: sinPlan ? 'var(--color-ink-faint)' : colorVersion(v.version) }} />
              <span aria-hidden="true" className="h-3.5 w-1 rounded-sm" style={{ background: sinPlan ? 'var(--color-ink-faint)' : colorVersion(v.version) }} />
              <span className="font-semibold" style={{ color: sinPlan ? undefined : colorVersion(v.version) }}>{etiquetaVersion(v.version)}</span>
            </label>
            {plan?.esVigente && <span className="inline-flex items-center gap-0.5 text-xs font-semibold text-success"><Star className="h-3.5 w-3.5 fill-current" aria-hidden="true" />Vigente</span>}
            {sinPlan && <>
              <span className="text-xs text-ink-faint">· Sin planificación</span>
              <button type="button" onClick={() => onGenerar(v)} disabled={generando !== null || bloqueado !== null}
                title={bloqueado ?? `Genera la planificación de la ${etiquetaVersion(v.version)} sin cambiar la vigente`}
                className="inline-flex items-center gap-1 rounded-md bg-accent px-2 py-0.5 text-xs font-medium text-accent-ink hover:opacity-90 disabled:opacity-40">
                {generando === v.version ? <Loader2 className="h-3 w-3 animate-spin" /> : <Play className="h-3 w-3" />}Generar
              </button>
            </>}
            {plan && !plan.esVigente && <>
              <span className="text-xs text-ink-soft">· Generada{plan.stockDesactualizado ? ' (stock desactualizado)' : ''}</span>
              <button type="button" onClick={() => onGenerar(v)} disabled={generando !== null || bloqueado !== null} aria-label={`Volver a generar la ${etiquetaVersion(v.version)}`}
                title={bloqueado ?? `Vuelve a generar la planificación de la ${etiquetaVersion(v.version)} (la vigente no cambia)`}
                className="rounded p-0.5 text-ink-soft hover:bg-panel-muted disabled:opacity-40">
                {generando === v.version ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <RefreshCw className="h-3.5 w-3.5" />}
              </button>
              <button type="button" onClick={() => onUsarComoVigente(v)} disabled={bloqueado !== null || generando !== null}
                title={bloqueado ?? `La ${etiquetaVersion(v.version)} pasa a ser la planificación vigente (pide confirmación)`}
                className="rounded-md border border-line bg-panel px-2 py-0.5 text-xs font-medium text-ink hover:bg-panel-muted disabled:opacity-40">
                Usar como vigente
              </button>
            </>}
          </span>
        )
      })}
    </div>
  )
}

const INDICADORES_VERSION: { icono: LucideIcon; etiqueta: string; valor: (r: VersionMarcada['plan']['resumen']) => number; clase: string }[] = [
  { icono: Layers, etiqueta: 'Asignadas', valor: (r) => r.asignadas, clase: 'text-indicador-texto' },
  { icono: Package, etiqueta: 'Sobrantes', valor: (r) => r.sobrantes, clase: 'text-indicador-texto' },
  { icono: Ban, etiqueta: 'Bloqueadas', valor: (r) => r.bloqueadas, clase: 'text-indicador-texto' },
  { icono: Droplets, etiqueta: 'Lavados', valor: (r) => r.lavados, clase: 'text-indicador-texto' },
]

/**
 * Indicadores de las versiones marcadas (D-52): una tarjeta por versión, con su color, y dentro sus partidas
 * asignadas, sobrantes, bloqueadas y lavados.
 */
export function BarraIndicadoresVersiones({ marcadas }: { marcadas: VersionMarcada[] }) {
  return (
    <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
      {marcadas.map(({ version, plan }) => (
        <section key={version} aria-label={`Indicadores de la ${etiquetaVersion(version)}`} className="rounded-2xl border-2 px-4 py-3"
          style={{ borderColor: colorVersion(version), background: fondoVersion(version) }}>
          <div className="mb-2 flex items-center gap-2">
            <EtiquetaVersion versiones={[version]} className="text-base" />
            <span className="text-xs text-ink-soft">{plan.esVigente ? 'Vigente' : 'No vigente'} · versión {plan.numeroVersion} de la planificación</span>
          </div>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
            {INDICADORES_VERSION.map(({ icono: Icono, etiqueta, valor, clase }) => (
              <div key={etiqueta} className="flex items-center gap-2.5 rounded-xl bg-panel/80 px-3 py-2">
                <Icono className="h-6 w-6 shrink-0" style={{ color: colorVersion(version) }} strokeWidth={1.6} aria-hidden="true" />
                <div className="min-w-0">
                  <p className="truncate text-xs font-medium text-indicador-texto">{etiqueta}</p>
                  <p className={`font-mono text-xl font-bold tabular-nums ${clase}`}>{valor(plan.resumen)}</p>
                </div>
              </div>
            ))}
          </div>
        </section>
      ))}
    </div>
  )
}

/** Confirmación de «Usar como vigente» con los avisos de la vigente actual (RF59). */
export function DialogoCambioVigente({ contexto, enviando, onConfirmar, onCancelar }: {
  contexto: ContextoCambioVigente
  enviando: boolean
  onConfirmar: (partidasEnMaquina: string[]) => void
  onCancelar: () => void
}) {
  const [enMaquina, setEnMaquina] = useState<Set<string>>(new Set())
  const c = contexto
  const nueva = etiquetaVersion(c.versionProyeccion)
  const actual = c.versionProyeccionVigente === null ? null : etiquetaVersion(c.versionProyeccionVigente)
  const avisos: string[] = []
  if (c.replanificaciones > 0) avisos.push(`La semana tiene ${c.replanificaciones} ${c.replanificaciones === 1 ? 're-planificación confirmada' : 're-planificaciones confirmadas'}.`)
  if (c.incidenciasConsideradas > 0) avisos.push(`${c.incidenciasConsideradas} ${c.incidenciasConsideradas === 1 ? 'incidencia ya considerada' : 'incidencias ya consideradas'}: las de máquina y complemento que siguen vigentes desde hoy se vuelven a aplicar.`)
  if (c.incidenciasPendientes > 0) avisos.push(`${c.incidenciasPendientes} ${c.incidenciasPendientes === 1 ? 'incidencia pendiente no se aplica' : 'incidencias pendientes no se aplican'}: siguen pendientes para una re-planificación.`)
  if (c.stockDesactualizado) avisos.push(`La planificación de la ${nueva} tiene el stock inicial desactualizado (cambió la semana anterior). Conviene volver a generarla antes.`)
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <button type="button" aria-label="Cancelar" onClick={onCancelar} className="fixed inset-0 bg-black/50" />
      <div role="alertdialog" aria-modal="true" aria-labelledby="cambio-title" className="relative flex max-h-[92vh] w-full max-w-lg flex-col rounded-2xl border border-line bg-panel p-5 shadow-xl">
        <h2 id="cambio-title" className="text-base font-semibold text-ink">Usar la {nueva} como vigente</h2>
        <div className="mt-2 space-y-2 overflow-y-auto text-sm text-ink-soft">
          {!c.permitido && <p className="rounded-lg bg-danger-soft px-3 py-2 text-danger">{c.motivo}</p>}
          {c.permitido && !c.semanaIniciada && <p>La planificación de la {nueva} (versión {c.numeroVersion}) pasa a ser la vigente.{actual && <> La actual ({actual}, versión {c.numeroVersionVigente}) queda guardada y puedes volver a ella.</>} Exportar y el usuario Consulta verán la {nueva}.</p>}
          {c.permitido && c.semanaIniciada && <p>La semana ya empezó (hoy es {nombreDia(c.dia, true).toLowerCase()} {fechaCorta(c.dia)}). Se re-planifica <strong>desde hoy</strong> con la proyección {nueva}: lo anterior a hoy se conserva de la vigente{actual ? ` (${actual})` : ''}. Verás el resultado y decidirás si lo confirmas; hasta entonces la vigente no cambia.</p>}
          {avisos.length > 0 && (
            <ul className="space-y-1 rounded-lg border border-warning/40 bg-warning-soft px-3 py-2 text-warning">
              {avisos.map((a) => <li key={a} className="flex gap-1.5"><AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0" />{a}</li>)}
            </ul>
          )}
          {c.permitido && c.semanaIniciada && c.partidasDelDia.length > 0 && (
            <fieldset className="rounded-lg border border-line p-2.5">
              <legend className="px-1 text-xs font-semibold text-ink">Partidas de hoy que ya entraron a máquina (quedan fijas)</legend>
              <div className="max-h-40 space-y-1 overflow-y-auto">
                {c.partidasDelDia.map((p) => (
                  <label key={p.numeroPartida} className="flex cursor-pointer items-center gap-2 text-xs text-ink">
                    <input type="checkbox" checked={enMaquina.has(p.numeroPartida)} onChange={(e) => setEnMaquina((prev) => { const n = new Set(prev); if (e.target.checked) n.add(p.numeroPartida); else n.delete(p.numeroPartida); return n })} />
                    <span className="font-mono font-semibold">{p.numeroPartida}</span><span className="text-ink-soft">{p.maquina}{p.ordenSecuencia ? ` · orden ${p.ordenSecuencia}` : ''}</span>
                  </label>
                ))}
              </div>
            </fieldset>
          )}
        </div>
        <div className="mt-5 flex items-center justify-end gap-2">
          <button type="button" autoFocus onClick={onCancelar} className="rounded-lg border border-line bg-panel px-3 py-2 text-sm font-medium text-ink hover:bg-panel-muted">Cancelar</button>
          <button type="button" onClick={() => onConfirmar([...enMaquina])} disabled={!c.permitido || enviando}
            className="inline-flex items-center gap-1.5 rounded-lg bg-accent px-3 py-2 text-sm font-medium text-accent-ink hover:opacity-90 disabled:opacity-40">
            {enviando && <Loader2 className="h-4 w-4 animate-spin" />}{c.semanaIniciada ? 'Re-planificar desde hoy' : 'Usar como vigente'}
          </button>
        </div>
      </div>
    </div>
  )
}

/** Resultado de la re-planificación desde hoy con otra versión: el planificador la confirma o la descarta. */
export function DialogoPropuestaVersion({ version, resultado, enviando, onConfirmar, onDescartar, onDespues }: {
  version: number
  resultado: ReplanificacionResponse
  enviando: boolean
  onConfirmar: () => void
  onDescartar: () => void
  onDespues: () => void
}) {
  const mejor = resultado.fNueva <= resultado.fVigente || !resultado.vigenteFactible
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <button type="button" aria-label="Decidir después" onClick={onDespues} className="fixed inset-0 bg-black/50" />
      <div role="alertdialog" aria-modal="true" aria-labelledby="propuesta-title" className="relative flex max-h-[92vh] w-full max-w-lg flex-col rounded-2xl border border-line bg-panel p-5 shadow-xl">
        <h2 id="propuesta-title" className="flex items-center gap-2 text-base font-semibold text-ink"><CheckCircle2 className="h-5 w-5 text-success" />Re-planificación con la {etiquetaVersion(version)} lista</h2>
        <div className="mt-2 space-y-2 overflow-y-auto text-sm text-ink-soft">
          <p>Se guardó como propuesta (versión {resultado.numeroVersion}), desde el {fechaCorta(resultado.dia)}. Lo anterior se conserva. La vigente no cambia hasta que la confirmes; si no la confirmas hoy, se descarta.</p>
          <dl className="grid grid-cols-2 gap-2 rounded-lg border border-line p-3 text-center">
            <div><dt className="text-xs">F de la propuesta</dt><dd className="font-mono text-lg font-bold text-ink">{numero(resultado.fNueva, 2)}</dd></div>
            <div><dt className="text-xs">F de la vigente con la {etiquetaVersion(version)}</dt><dd className="font-mono text-lg font-bold text-ink">{resultado.vigenteFactible ? numero(resultado.fVigente, 2) : 'No factible'}</dd></div>
          </dl>
          {!mejor && <p className="rounded-lg border border-warning/40 bg-warning-soft px-3 py-2 text-warning">La vigente, evaluada con la proyección {etiquetaVersion(version)}, queda con menor desviación. Puedes confirmar igual, porque la proyección cambió.</p>}
          {resultado.incumplimientosVigente.length > 0 && <p className="text-xs">La vigente ya no se puede cumplir con la {etiquetaVersion(version)}: {resultado.incumplimientosVigente.slice(0, 3).join('; ')}{resultado.incumplimientosVigente.length > 3 ? '…' : ''}</p>}
          {resultado.advertencias.length > 0 && <ul className="list-disc space-y-0.5 pl-5 text-xs">{resultado.advertencias.slice(0, 6).map((a) => <li key={a}>{a}</li>)}</ul>}
        </div>
        <div className="mt-5 flex flex-wrap items-center justify-end gap-2">
          <button type="button" autoFocus onClick={onDespues} className="rounded-lg px-3 py-2 text-sm font-medium text-accent hover:bg-panel-muted">Decidir después</button>
          <button type="button" onClick={onDescartar} disabled={enviando} className="rounded-lg border border-line bg-panel px-3 py-2 text-sm font-medium text-ink hover:bg-panel-muted disabled:opacity-40">Descartar</button>
          <button type="button" onClick={onConfirmar} disabled={enviando}
            className="inline-flex items-center gap-1.5 rounded-lg bg-accent px-3 py-2 text-sm font-medium text-accent-ink hover:opacity-90 disabled:opacity-40">
            {enviando && <Loader2 className="h-4 w-4 animate-spin" />}Confirmar como vigente
          </button>
        </div>
      </div>
    </div>
  )
}
