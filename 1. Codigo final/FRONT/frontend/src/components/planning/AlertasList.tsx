import { AlertTriangle, ArrowDown, ArrowUp, CalendarRange } from 'lucide-react'
import type { Alerta, TipoAlerta } from '@/api/tipos'
import { Paginado } from '@/components/ui/Paginacion'

const PRESENTACION: Record<TipoAlerta, { titulo: string; icono: typeof AlertTriangle; clase: string }> = {
  FALTANTE: { titulo: 'Bajo el objetivo semanal', icono: ArrowDown, clase: 'border-warning/40 bg-warning-soft text-warning' },
  EXCEDENTE: { titulo: 'Sobre el objetivo semanal', icono: ArrowUp, clase: 'border-danger/40 bg-danger-soft text-danger' },
  DESBALANCE_DIARIO: { titulo: 'Fuera del reparto diario', icono: CalendarRange, clase: 'border-line bg-panel-muted text-ink-soft' },
}

/**
 * Desviaciones de la planificación (HU-13 C1): partidas faltantes o excedentes respecto del objetivo
 * semanal, y días fuera del reparto diario (RN-09). Las arrastradas no participan (RF48).
 */
export function AlertasList({ alertas }: { alertas: Alerta[] }) {
  if (alertas.length === 0)
    return <div className="rounded-xl border border-success/40 bg-success-soft p-8 text-center text-sm text-success">Todas las máquinas cumplen su objetivo semanal y su reparto diario.</div>
  return <Paginado items={alertas}>{(pagina) => (
    <ul className="flex flex-col gap-2">
      {pagina.map((a, i) => {
        const p = PRESENTACION[a.tipo]
        const Icono = p.icono
        return (
          <li key={i} className={`flex items-start gap-3 rounded-xl border p-3.5 text-sm ${p.clase}`}>
            <Icono className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
            <div><p className="font-medium">{p.titulo}</p><p className="mt-0.5 text-ink-soft">{a.mensaje}</p></div>
          </li>
        )
      })}
    </ul>
  )}</Paginado>
}
