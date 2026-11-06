import { Ban, Droplets, X } from 'lucide-react'
import type { MaquinaPlan, PartidaCalendario } from '@/api/tipos'
import { CLASIFICACION_COLOR, MUESTRA_TONALIDAD, TONALIDAD, kg } from '@/lib/etiquetas'
import { diaMes, fechaCorta, nombreDia } from '@/lib/fechas'

/** Una partida en la cola del día. Las Sobrantes se distinguen en rojo (RF33). */
function BloquePartida({ partida }: { partida: PartidaCalendario }) {
  const sobrante = partida.clasificacion === 'SOBRANTE'
  const detalle = `${partida.numeroPartida} · pedido ${partida.numeroOp} · ${partida.color} (${TONALIDAD[partida.tonalidad]}, ${CLASIFICACION_COLOR[partida.clasificacionColor]}) · ${kg(partida.kg)}`
    + `\nEntrega: ${fechaCorta(partida.fechaEntrega)} · disponible desde ${fechaCorta(partida.fechaDisponible)}`
    + (sobrante ? '\nSobrante: excede el objetivo semanal o ya no había espacio en los días restantes' : '')
  return (
    <div title={detalle}
      className={`flex min-h-7 min-w-0 items-center gap-1 rounded border px-1.5 py-1 ${sobrante ? 'border-2 border-danger bg-danger-soft text-danger' : 'border-line bg-panel-muted/60'}`}>
      {sobrante
        ? <X className="h-3.5 w-3.5 shrink-0 stroke-[3]" aria-label="Sobrante" />
        : <span className="h-2.5 w-2.5 shrink-0 rounded-full border border-black/20" style={{ backgroundColor: MUESTRA_TONALIDAD[partida.tonalidad] }} aria-hidden="true" />}
      <span className={`truncate font-mono text-xs font-bold ${sobrante ? 'underline decoration-danger decoration-2' : 'text-ink'}`}>{partida.numeroPartida}</span>
    </div>
  )
}

/** Indicador de lavado en la transición hacia una tonalidad más clara en la misma máquina (RF26, HU-12 C5). */
function MarcaLavado() {
  return (
    <div title="Lavado previo: la partida anterior de esta máquina tiene una tonalidad más oscura"
      className="flex items-center justify-center gap-1 rounded border border-dashed border-ink-faint/60 py-0.5 text-[10px] text-ink-faint">
      <Droplets className="h-3 w-3" aria-hidden="true" />Lavado
    </div>
  )
}

/**
 * Calendario de 7 días por máquina (RF31, RF38). Cada celda es la cola de partidas del día en el orden
 * de secuencia guardado (RN-15): no hay horas de inicio ni de fin. Es de solo lectura: el calendario solo
 * cambia al generar una versión nueva.
 */
export function PlanningGrid({ dias, maquinas }: { dias: string[]; maquinas: MaquinaPlan[] }) {
  return (
    <div className="w-full overflow-x-auto rounded-xl border border-black bg-panel dark:border-white/60">
      <div className="min-w-[900px]">
        <div className="grid grid-cols-[104px_repeat(7,minmax(0,1fr))] border-b border-black bg-panel-muted/40 dark:border-white/60">
          <div className="px-2 py-3 text-xs font-semibold uppercase text-ink">Máquina</div>
          {dias.map((dia) => (
            <div key={dia} className="border-l border-black px-1 py-2 text-center dark:border-white/60">
              <p className="text-sm font-bold text-ink">{nombreDia(dia, true)}</p>
              <p className="text-[11px] text-ink-soft">{diaMes(dia)}</p>
            </div>
          ))}
        </div>
        {maquinas.map((m) => (
          <div key={m.codigo} className="grid grid-cols-[104px_repeat(7,minmax(0,1fr))] border-b border-black last:border-b-0 dark:border-white/60">
            <div className="flex flex-col justify-center gap-0.5 px-2 py-2">
              <span className="font-mono text-sm font-bold text-ink">{m.codigo}</span>
              <span className="text-xs text-ink-soft">Barca {m.barca}</span>
              <span className={`text-xs font-medium ${m.diferencia === 0 ? 'text-success' : m.diferencia < 0 ? 'text-warning' : 'text-danger'}`}
                title="Partidas de la semana (Asignadas + Sobrantes) / objetivo aplicado">
                {m.asignadas + m.sobrantes}/{m.objetivoAplicado}
              </span>
            </div>
            {m.dias.map((d) => {
              const fueraDeRango = !d.indisponible && m.objetivoAplicado > 0 && (d.regulares < m.minimoDiario || d.regulares > m.maximoDiario)
              return (
                <div key={d.fecha} className={`min-h-24 min-w-0 border-l border-black p-1 dark:border-white/60 ${d.indisponible ? 'bg-[repeating-linear-gradient(135deg,transparent,transparent_6px,var(--color-panel-muted)_6px,var(--color-panel-muted)_12px)]' : ''}`}>
                  <div className="mb-1 flex items-center justify-between px-0.5 text-[10px]">
                    {d.indisponible
                      ? <span className="flex items-center gap-1 font-medium text-danger" title={d.motivoIndisponibilidad ?? 'Máquina indisponible'}><Ban className="h-3 w-3" />No disponible</span>
                      : <span />}
                    {!d.indisponible && m.objetivoAplicado > 0 && (
                      <span className={fueraDeRango ? 'font-semibold text-warning' : 'text-ink-faint'}
                        title={`Asignadas del día (reparto diario de ${m.minimoDiario} a ${m.maximoDiario})`}>
                        {d.regulares}/{m.minimoDiario}–{m.maximoDiario}
                      </span>
                    )}
                  </div>
                  <div className="flex flex-col gap-1">
                    {d.partidas.map((p) => (
                      <div key={p.numeroPartida} className="flex flex-col gap-1">
                        {p.requiereLavado && <MarcaLavado />}
                        <BloquePartida partida={p} />
                      </div>
                    ))}
                  </div>
                </div>
              )
            })}
          </div>
        ))}
      </div>
    </div>
  )
}

export function PlanningGridLegend() {
  return (
    <div className="mb-3 flex flex-wrap items-center gap-x-5 gap-y-2 text-xs text-ink-soft">
      {(Object.keys(MUESTRA_TONALIDAD) as (keyof typeof MUESTRA_TONALIDAD)[]).map((t) => (
        <span key={t} className="flex items-center gap-1.5"><span className="h-2.5 w-2.5 rounded-full border border-black/20" style={{ backgroundColor: MUESTRA_TONALIDAD[t] }} />{TONALIDAD[t]}</span>
      ))}
      <span className="flex items-center gap-1.5"><Droplets className="h-3.5 w-3.5" />Lavado previo</span>
      <span className="flex items-center gap-1.5 font-medium text-danger"><X className="h-3.5 w-3.5 stroke-[3]" />Sobrante</span>
      <span className="flex items-center gap-1.5"><Ban className="h-3.5 w-3.5 text-danger" />Día indisponible</span>
      <span>«2/2–3»: asignadas del día / reparto diario</span>
    </div>
  )
}
