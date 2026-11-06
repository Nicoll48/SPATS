import { CalendarClock } from 'lucide-react'

/** Contenido de las pestañas cuando la semana no tiene planificación vigente. */
export function EmptyPlanState({ texto }: { texto?: string }) {
  return (
    <div className="flex flex-col items-center gap-2 rounded-xl border border-line bg-panel px-6 py-12 text-center">
      <CalendarClock className="h-6 w-6 text-ink-faint" aria-hidden="true" />
      <p className="text-sm font-medium text-ink">Esta semana no tiene una planificación vigente</p>
      <p className="max-w-md text-xs text-ink-soft">
        {texto ?? 'El Planificador la genera con el botón «Generar planificación» cuando la proyección de tejeduría de la semana está cargada.'}
      </p>
    </div>
  )
}
