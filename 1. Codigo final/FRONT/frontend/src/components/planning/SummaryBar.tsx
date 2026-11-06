import type { LucideIcon } from 'lucide-react'

export interface SummaryStat {
  id: string
  label: string
  value: string
  icon: LucideIcon
  /** Color del valor. Omitilo si el número es informativo y no amerita juicio de bueno/malo. */
  tone?: 'success' | 'warning' | 'danger'
}

const TONE_CLASS: Record<NonNullable<SummaryStat['tone']>, string> = {
  success: 'text-success',
  warning: 'text-warning',
  danger: 'text-danger',
}

/**
 * Las 7 "cards compactas" del resumen superior de Planificación semanal.
 * Deliberadamente más chicas que KpiCard (panel-servidores): acá son un
 * vistazo de estado, no un dashboard con tendencias — por eso no llevan
 * flecha de variación, solo etiqueta + valor (+ color si el número
 * amerita un juicio, como cumplimiento de fechas o tiempo ocioso).
 */
export function SummaryBar({ stats }: { stats: SummaryStat[] }) {
  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 xl:grid-cols-5">
      {stats.map((stat) => {
        const Icon = stat.icon
        return (
          <div key={stat.id} className="rounded-xl border border-line bg-panel p-3">
            <div className="mb-2 flex items-center gap-1.5 text-ink-faint dark:text-white">
              <Icon className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
              <span className="truncate text-xs font-medium">{stat.label}</span>
            </div>
            <p
              className={`font-mono text-lg font-semibold tabular-nums sm:text-xl ${
                stat.tone ? TONE_CLASS[stat.tone] : 'text-ink'
              }`}
            >
              {stat.value}
            </p>
          </div>
        )
      })}
    </div>
  )
}
