import type { LucideIcon } from 'lucide-react'
import { CircleAlert, CircleCheck, CircleDashed, CircleX } from 'lucide-react'
import { StatusPill, type StatusTone } from '@/components/ui/StatusPill'
import { DIAS_SEMANA, type EquilibrioFila } from '@/data/mockData'

const TONE_LABEL: Record<StatusTone, string> = {
  success: 'Equilibrado',
  warning: 'Ajustado',
  danger: 'Desequilibrado',
  neutral: 'Sin partidas',
}

const TONE_ICON: Record<StatusTone, LucideIcon> = {
  success: CircleCheck,
  warning: CircleAlert,
  danger: CircleX,
  neutral: CircleDashed,
}

const TONE_TEXT_CLASS: Record<StatusTone, string> = {
  success: 'text-success',
  warning: 'text-warning',
  danger: 'text-danger',
  neutral: 'text-ink-faint',
}

/**
 * Fila compacta de 7 íconos, uno por día — el mismo set de íconos que
 * StatusPill (nunca un punto de color solo) para que el equilibrio diario
 * quede legible incluso sin distinguir colores. El detalle por día vive en
 * el `title` (tooltip nativo) y en un texto para lectores de pantalla.
 */
function EquilibrioDiario({ estadoDiario }: { estadoDiario: EquilibrioFila['estadoDiario'] }) {
  return (
    <div className="flex items-center gap-1.5">
      {DIAS_SEMANA.map((dia) => {
        const tone = estadoDiario[dia.key]
        if (!tone) {
          return (
            <span key={dia.key} className="text-[10px] text-ink-faint/50" title={`${dia.largo}: sin datos`}>
              ·
            </span>
          )
        }
        const Icon = TONE_ICON[tone]
        return (
          <span key={dia.key} title={`${dia.largo}: ${TONE_LABEL[tone]}`} className="inline-flex">
            <Icon className={`h-3.5 w-3.5 ${TONE_TEXT_CLASS[tone]}`} aria-hidden="true" />
            <span className="sr-only">{`${dia.largo}: ${TONE_LABEL[tone]}`}</span>
          </span>
        )
      })}
    </div>
  )
}

export function EquilibrioTable({ filas }: { filas: EquilibrioFila[] }) {
  return (
    <div className="overflow-hidden rounded-xl border border-line bg-panel">
      <div className="overflow-x-auto">
        <table className="w-full min-w-[760px] text-left text-sm">
          <thead>
            <tr className="border-b border-line text-xs tracking-wide text-ink-faint uppercase">
              <th className="px-4 py-3 font-medium">Máquina</th>
              <th className="px-4 py-3 font-medium">Objetivo</th>
              <th className="px-4 py-3 font-medium">Planificado</th>
              <th className="px-4 py-3 font-medium">Diferencia</th>
              <th className="px-4 py-3 font-medium">Equilibrio diario</th>
              <th className="px-4 py-3 font-medium">Equilibrio semanal</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {filas.map((fila) => (
              <tr key={fila.maquinaId} className="transition-colors hover:bg-panel-muted">
                <td className="px-4 py-3 font-mono font-medium text-ink">{fila.codigo}</td>
                <td className="px-4 py-3 font-mono tabular-nums text-ink-soft">{fila.objetivo}</td>
                <td className="px-4 py-3 font-mono tabular-nums text-ink-soft">{fila.planificado}</td>
                <td
                  className={`px-4 py-3 font-mono tabular-nums ${
                    fila.diferencia < 0 ? 'text-danger' : fila.diferencia > 0 ? 'text-warning' : 'text-ink-soft'
                  }`}
                >
                  {fila.diferencia > 0 ? `+${fila.diferencia}` : fila.diferencia}
                </td>
                <td className="px-4 py-3">
                  <EquilibrioDiario estadoDiario={fila.estadoDiario} />
                </td>
                <td className="px-4 py-3">
                  <StatusPill tone={fila.estadoSemanal} label={TONE_LABEL[fila.estadoSemanal]} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
