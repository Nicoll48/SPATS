import { CalendarDays, ChevronLeft, ChevronRight } from 'lucide-react'
import { useSemana } from '@/semana/semana-context'
import { etiquetaSemana } from '@/lib/fechas'

/**
 * Semana de planificación activa (lunes a domingo), compartida por todas las pantallas. Las flechas
 * avanzan de a una semana; el calendario permite saltar a cualquier fecha (se toma su lunes).
 */
export function WeekSelector() {
  const { lunes, setLunes, anterior, siguiente } = useSemana()
  return (
    <div className="inline-flex items-center gap-1 rounded-lg border border-line bg-panel p-1">
      <button type="button" onClick={anterior} aria-label="Semana anterior"
        className="rounded-md p-1.5 text-ink-soft transition-colors hover:bg-panel-muted hover:text-ink">
        <ChevronLeft className="h-4 w-4" aria-hidden="true" />
      </button>
      <label className="relative flex cursor-pointer items-center gap-1.5 px-1.5 text-sm font-medium whitespace-nowrap text-ink" title="Elegir otra semana">
        <CalendarDays className="h-4 w-4 shrink-0 text-ink-faint" aria-hidden="true" />
        Semana del {etiquetaSemana(lunes)}
        <input type="date" value={lunes} onChange={(e) => e.target.value && setLunes(e.target.value)}
          className="absolute inset-0 cursor-pointer opacity-0" aria-label="Elegir semana" />
      </label>
      <button type="button" onClick={siguiente} aria-label="Semana siguiente"
        className="rounded-md p-1.5 text-ink-soft transition-colors hover:bg-panel-muted hover:text-ink">
        <ChevronRight className="h-4 w-4" aria-hidden="true" />
      </button>
    </div>
  )
}
