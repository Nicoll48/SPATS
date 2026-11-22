import { CheckCircle2, CircleAlert } from 'lucide-react'
import type { PlanificacionDetalle, Tonalidad } from '@/api/tipos'
import { MUESTRA_TONALIDAD, ORDEN_TONALIDAD, TONALIDAD } from '@/lib/etiquetas'
import { diaMes, nombreDia } from '@/lib/fechas'

/**
 * Partidas ubicadas (Asignadas y Sobrantes) por tonalidad, contadas desde el calendario guardado de la versión.
 * Se cuentan por tonalidad (Blanco, Claro, Medio, Oscuro) y no por clasificación Color/Blanco, a pedido de la autora.
 */
function distribucionTonalidad(plan: PlanificacionDetalle) {
  const cuenta = new Map<Tonalidad, { asignadas: number; sobrantes: number }>(ORDEN_TONALIDAD.map((t) => [t, { asignadas: 0, sobrantes: 0 }]))
  for (const m of plan.maquinas) for (const d of m.dias) for (const p of d.partidas) {
    const c = cuenta.get(p.tonalidad)!
    if (p.clasificacion === 'SOBRANTE') c.sobrantes++
    else c.asignadas++
  }
  return ORDEN_TONALIDAD.map((t) => ({ tonalidad: t, ...cuenta.get(t)! }))
}

/**
 * Barras horizontales, una por tonalidad en su orden natural (de la más clara a la más oscura), con el color de la
 * muestra de tonalidad del calendario. Se prefieren a una rueda porque con barras sobre una misma base se comparan
 * mejor cantidades parecidas; la cifra va al final de cada barra.
 */
function GraficoTonalidad({ plan }: { plan: PlanificacionDetalle }) {
  const filas = distribucionTonalidad(plan)
  const total = filas.reduce((s, f) => s + f.asignadas + f.sobrantes, 0)
  const maximo = Math.max(1, ...filas.map((f) => f.asignadas + f.sobrantes))
  return (
    <div className="mt-4 space-y-3" role="list" aria-label="Partidas por tonalidad">
      {filas.map((f) => {
        const n = f.asignadas + f.sobrantes
        const porcentaje = total > 0 ? Math.round((n / total) * 100) : 0
        const detalle = `${TONALIDAD[f.tonalidad]}: ${n} partidas (${porcentaje} %) · ${f.asignadas} asignadas · ${f.sobrantes} sobrantes`
        return (
          <div key={f.tonalidad} role="listitem" title={detalle} aria-label={detalle} className="grid grid-cols-[64px_1fr_auto] items-center gap-3">
            <span className="text-sm text-ink">{TONALIDAD[f.tonalidad]}</span>
            <span className="h-5 rounded-r-[4px] bg-panel-muted/60">
              {n > 0 && <span className="block h-full rounded-r-[4px] border border-black/15" style={{ width: `${(n / maximo) * 100}%`, background: MUESTRA_TONALIDAD[f.tonalidad] }} />}
            </span>
            <span className="min-w-20 text-right text-sm">
              <span className="font-mono font-semibold text-ink">{n}</span>
              <span className="ml-1 text-xs text-ink-soft">({porcentaje} %)</span>
              {f.sobrantes > 0 && <span className="block text-[11px] text-danger">{f.sobrantes} sobrantes</span>}
            </span>
          </div>
        )
      })}
      <p className="border-t border-line pt-2 text-right text-xs text-ink-soft">Total: <span className="font-mono font-semibold text-ink">{total}</span> partidas · {plan.resumen.asignadas} asignadas · {plan.resumen.sobrantes} sobrantes</p>
    </div>
  )
}

/**
 * Distribución de Asignadas y Sobrantes por tonalidad (RF40) y diferencia de cada máquina
 * con su objetivo semanal (RF29, HU-13 C4: cero cuando lo alcanza exactamente). Todo viene guardado.
 */
/**
 * Partidas Asignadas por día de la semana, sumando todas las máquinas (sin Sobrantes): muestra si la carga se reparte
 * parejo en la semana. Una sola serie, en el color de acento; la cifra va sobre cada columna.
 */
function GraficoPorDia({ plan }: { plan: PlanificacionDetalle }) {
  const filas = plan.dias.map((fecha) => ({
    fecha,
    n: plan.maquinas.reduce((s, m) => s + (m.dias.find((d) => d.fecha === fecha)?.partidas.filter((p) => p.clasificacion === 'ASIGNADA').length ?? 0), 0),
  }))
  const maximo = Math.max(1, ...filas.map((f) => f.n))
  const promedio = filas.reduce((s, f) => s + f.n, 0) / Math.max(1, filas.length)
  return (
    <div className="mt-6 border-t border-line pt-4">
      <h3 className="text-sm font-semibold text-ink">Asignadas por día</h3>
      <p className="mt-1 text-xs text-ink-soft">Partidas asignadas cada día, sumando todas las máquinas · promedio {promedio.toFixed(1)} por día.</p>
      <div className="mt-3 grid h-40 grid-cols-7 items-end gap-2" role="list" aria-label="Partidas asignadas por día">
        {filas.map((f) => {
          const detalle = `${nombreDia(f.fecha, true)} ${diaMes(f.fecha)}: ${f.n} partidas asignadas`
          return (
            <div key={f.fecha} role="listitem" title={detalle} aria-label={detalle} className="flex h-full flex-col items-center justify-end gap-1">
              <span className="font-mono text-xs font-semibold text-ink">{f.n}</span>
              <span className="w-full max-w-10 rounded-t-[4px] bg-accent" style={{ height: `calc((100% - 1.25rem) * ${f.n / maximo})`, minHeight: f.n > 0 ? 2 : 0 }} />
            </div>
          )
        })}
      </div>
      <div className="mt-1 grid grid-cols-7 gap-2 border-t border-line pt-1 text-center">
        {filas.map((f) => (
          <span key={f.fecha} className="text-[11px] leading-tight text-ink-soft"><span className="font-semibold text-ink">{nombreDia(f.fecha)}</span><br />{diaMes(f.fecha)}</span>
        ))}
      </div>
    </div>
  )
}

export function PartidasAsignadasDashboard({ plan }: { plan: PlanificacionDetalle }) {
  return (
    <div className="grid gap-4 lg:grid-cols-[1fr_1.4fr]">
      <section className="rounded-xl border border-line bg-panel p-5">
        <h3 className="text-sm font-semibold text-ink">Distribución por tonalidad</h3>
        <p className="mt-1 text-xs text-ink-soft">Partidas ubicadas (asignadas y sobrantes) de la versión {plan.numeroVersion}, de la más clara a la más oscura.</p>
        <GraficoTonalidad plan={plan} />
        <GraficoPorDia plan={plan} />
      </section>
      <section className="rounded-xl border border-line bg-panel p-5">
        <h3 className="text-sm font-semibold text-ink">Carga por máquina frente a su objetivo semanal</h3>
        <p className="mt-1 text-xs text-ink-faint">El objetivo baja 2 partidas por cada día indisponible de la máquina.</p>
        <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3">
          {plan.maquinas.map((m) => {
            const ok = m.diferencia === 0
            const clase = ok ? 'border-success/30 bg-success-soft' : m.diferencia < 0 ? 'border-warning/40 bg-warning-soft' : 'border-danger/40 bg-danger-soft'
            return (
              <div key={m.codigo} className={`rounded-lg border p-3 ${clase}`}>
                <div className="flex items-center justify-between"><span className="font-mono font-semibold text-ink">{m.codigo}</span>{ok ? <CheckCircle2 className="h-4 w-4 text-success" /> : <CircleAlert className="h-4 w-4 text-warning" />}</div>
                <p className="mt-2 font-mono text-xl font-semibold text-ink">{m.asignadas + m.sobrantes}/{m.objetivoAplicado}</p>
                <p className="text-[11px] text-ink-soft">{m.asignadas} asignadas · {m.sobrantes} sobrantes · {m.lavados} lavados</p>
                <p className="mt-1 text-xs font-medium text-ink">{ok ? 'Desviación 0' : m.diferencia < 0 ? `Faltan ${-m.diferencia}` : `Exceden ${m.diferencia}`}</p>
              </div>
            )
          })}
        </div>
      </section>
    </div>
  )
}
