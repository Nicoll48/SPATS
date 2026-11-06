import { CheckCircle2, CircleAlert } from 'lucide-react'
import type { PlanificacionDetalle } from '@/api/tipos'
import { CLASIFICACION_COLOR } from '@/lib/etiquetas'

/**
 * Distribución de Asignadas y Sobrantes por clasificación de color (RF40) y diferencia de cada máquina
 * con su objetivo semanal (RF29, HU-13 C4: cero cuando lo alcanza exactamente). Todo viene guardado.
 */
export function PartidasAsignadasDashboard({ plan }: { plan: PlanificacionDetalle }) {
  const total = plan.distribucionColor.reduce((s, d) => s + d.asignadas + d.sobrantes, 0)
  return (
    <div className="grid gap-4 lg:grid-cols-[1fr_1.4fr]">
      <section className="rounded-xl border border-line bg-panel p-5">
        <h3 className="text-sm font-semibold text-ink">Distribución por clasificación de color</h3>
        <p className="mt-1 text-xs text-ink-faint">Asignadas y Sobrantes de la versión {plan.numeroVersion}.</p>
        <table className="mt-4 w-full text-sm">
          <thead><tr className="text-left text-xs uppercase text-ink-faint"><th className="py-2">Clasificación</th><th className="py-2 text-right">Asignadas</th><th className="py-2 text-right">Sobrantes</th><th className="py-2 text-right">Total</th></tr></thead>
          <tbody>
            {plan.distribucionColor.map((d) => (
              <tr key={d.clasificacionColor} className="border-t border-line">
                <td className="py-2 text-ink">{CLASIFICACION_COLOR[d.clasificacionColor]}</td>
                <td className="py-2 text-right font-mono text-ink">{d.asignadas}</td>
                <td className="py-2 text-right font-mono text-danger">{d.sobrantes}</td>
                <td className="py-2 text-right font-mono font-semibold text-ink">{d.asignadas + d.sobrantes}</td>
              </tr>
            ))}
            <tr className="border-t border-line"><td className="py-2 font-medium text-ink">Total</td><td className="py-2 text-right font-mono">{plan.resumen.asignadas}</td><td className="py-2 text-right font-mono text-danger">{plan.resumen.sobrantes}</td><td className="py-2 text-right font-mono font-semibold">{total}</td></tr>
          </tbody>
        </table>
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
