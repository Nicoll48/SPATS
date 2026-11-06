import { CircleDot, PackageMinus } from 'lucide-react'
import { COLOR_SWATCH, type FilaColaMaquina, type OrigenCola } from '@/data/mockData'

const ORIGEN_ICON: Record<OrigenCola, typeof CircleDot> = {
  'En ejecución': CircleDot,
  'Sobrante prioritario': PackageMinus,
}

const ORIGEN_CLASS: Record<OrigenCola, string> = {
  'En ejecución': 'text-accent',
  'Sobrante prioritario': 'text-warning',
}

/**
 * Vista de solo lectura para cuando se navega a una semana que todavía no
 * tiene planificación propia: qué partidas ya están comprometidas por
 * máquina (en ejecución + sobrantes prioritarios que arrastran). No hay
 * nada que editar acá — es lectura de lo que ya existe, no un plan.
 */
export function QueueView({ filas }: { filas: FilaColaMaquina[] }) {
  if (filas.length === 0) {
    return (
      <div className="rounded-xl border border-line bg-panel p-8 text-center text-sm text-ink-soft">
        Ninguna máquina tiene partidas comprometidas todavía para esta semana.
      </div>
    )
  }

  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
      {filas.map((fila) => (
        <div key={fila.maquinaId} className="rounded-xl border border-line bg-panel p-4">
          <p className="mb-3 font-mono text-sm font-medium text-ink">{fila.codigo}</p>
          <ul className="flex flex-col gap-2.5">
            {fila.items.map((item) => {
              const Icon = ORIGEN_ICON[item.origen]
              const colorHex = COLOR_SWATCH[item.color] ?? '#8a8f98'
              return (
                <li key={item.id} className="flex items-start gap-2">
                  <span
                    className="mt-1 h-2 w-2 shrink-0 rounded-full ring-1 ring-black/10"
                    style={{ backgroundColor: colorHex }}
                    aria-hidden="true"
                  />
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-mono text-xs font-medium text-ink">
                      {item.codigo} <span className="text-ink-faint">· {item.kg} kg</span>
                    </p>
                    <p className={`mt-0.5 flex items-center gap-1 text-xs ${ORIGEN_CLASS[item.origen]}`}>
                      <Icon className="h-3 w-3 shrink-0" aria-hidden="true" />
                      {item.origen}
                    </p>
                  </div>
                </li>
              )
            })}
          </ul>
        </div>
      ))}
    </div>
  )
}
