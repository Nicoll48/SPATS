import type { LucideIcon } from 'lucide-react'
import { Ban, Droplets, HardDrive, Layers, Package } from 'lucide-react'
import type { PlanificacionDetalle } from '@/api/tipos'

/** Todas las tarjetas usan el mismo color, el de «Partidas asignadas», en un tono muy claro (pedido de la autora). */
const TARJETA = { tarjeta: 'border-accent/20 bg-indicador', icono: 'text-accent', valor: 'text-indicador-texto' }

function Indicador({ icono: Icono, etiqueta, valor, total, nota }: {
  icono: LucideIcon; etiqueta: string; valor: number; total?: number; nota?: string
}) {
  const t = TARJETA
  return (
    <div className={`flex items-center gap-3.5 rounded-2xl border px-4 py-3.5 ${t.tarjeta}`}>
      <Icono className={`h-8 w-8 shrink-0 ${t.icono}`} strokeWidth={1.6} aria-hidden="true" />
      <div className="min-w-0 flex-1">
        <p className="text-sm leading-tight font-medium text-indicador-texto">{etiqueta}</p>
        <div className="mt-0.5 flex items-end justify-between gap-2">
          <p className={`font-mono text-2xl font-bold tabular-nums ${t.valor}`}>
            {valor}{total !== undefined && <span className="text-base font-semibold text-indicador-texto"> de {total}</span>}
          </p>
          {nota && <span className={`mb-1 rounded-md bg-panel/70 px-1.5 py-0.5 text-[11px] font-semibold ${t.icono}`}>{nota}</span>}
        </div>
      </div>
    </div>
  )
}

/**
 * Indicadores de la versión: asignadas, sobrantes, bloqueadas, máquinas que llegan a su objetivo semanal
 * (carga completa) y lavados. Todo sale de lo guardado; no se recalcula nada.
 */
export function IndicadoresPlan({ plan }: { plan: PlanificacionDetalle }) {
  const r = plan.resumen
  const objetivo = plan.maquinas.reduce((s, m) => s + m.objetivoAplicado, 0)
  const avance = objetivo > 0 ? Math.round((r.asignadas / objetivo) * 100) : null
  return (
    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-5">
      <Indicador icono={Layers} etiqueta="Partidas asignadas" valor={r.asignadas} nota={avance === null ? undefined : `${avance}% del objetivo`} />
      <Indicador icono={Package} etiqueta="Partidas sobrantes" valor={r.sobrantes} />
      <Indicador icono={Ban} etiqueta="Partidas bloqueadas" valor={r.bloqueadas} />
      <Indicador icono={HardDrive} etiqueta="Máquinas con carga completa" valor={r.maquinasEnObjetivo} total={r.totalMaquinas} />
      <Indicador icono={Droplets} etiqueta="Lavados" valor={r.lavados} />
    </div>
  )
}
