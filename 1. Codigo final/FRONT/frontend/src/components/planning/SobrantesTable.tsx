import type { MaquinaPlan, PartidaCalendario } from '@/api/tipos'
import { CLASIFICACION_COLOR, CLASIFICACION_RESULTADO, TONALIDAD, kg } from '@/lib/etiquetas'
import { fechaCorta, nombreDia } from '@/lib/fechas'
import { Paginado } from '@/components/ui/Paginacion'

export interface FilaPartidaPlan extends PartidaCalendario {
  maquina: string
  dia: string
}

/** Partidas conformadas de la planificación, con su máquina y día, para consultarlas por clasificación (RF39). */
export function partidasDelPlan(maquinas: MaquinaPlan[]): FilaPartidaPlan[] {
  return maquinas.flatMap((m) => m.dias.flatMap((d) => d.partidas.map((p) => ({ ...p, maquina: m.codigo, dia: d.fecha }))))
}

/** Tabla de partidas conformadas (Asignadas o Sobrantes) con su ubicación en el calendario. */
export function SobrantesTable({ filas, vacio, reinicio }: { filas: FilaPartidaPlan[]; vacio: string; reinicio?: unknown }) {
  if (filas.length === 0)
    return <div className="rounded-xl border border-line bg-panel p-8 text-center text-sm text-ink-soft">{vacio}</div>
  return <Paginado items={filas} reinicio={reinicio}>{(pagina) => (
    <div className="overflow-hidden rounded-xl border border-line bg-panel">
      <div className="overflow-x-auto">
        <table className="w-full min-w-[900px] text-left text-sm">
          <thead>
            <tr className="border-b border-line text-xs tracking-wide text-ink-faint uppercase">
              <th className="px-4 py-3">Partida</th><th className="px-4 py-3">Pedido</th><th className="px-4 py-3">Entrega</th>
              <th className="px-4 py-3">Color</th><th className="px-4 py-3">Kg</th><th className="px-4 py-3">Máquina</th>
              <th className="px-4 py-3">Día</th><th className="px-4 py-3">Orden</th><th className="px-4 py-3">Clasificación</th>
            </tr>
          </thead>
          <tbody>
            {pagina.map((f) => (
              <tr key={f.numeroPartida} className="border-b border-line last:border-0">
                <td className="px-4 py-2.5 font-mono font-semibold text-ink">{f.numeroPartida}</td>
                <td className="px-4 py-2.5 font-mono text-ink-soft">{f.numeroOp}</td>
                <td className="px-4 py-2.5 text-ink-soft">{fechaCorta(f.fechaEntrega)}</td>
                <td className="px-4 py-2.5 text-ink">{f.color} <span className="text-xs text-ink-faint">· {TONALIDAD[f.tonalidad]} · {CLASIFICACION_COLOR[f.clasificacionColor]}</span></td>
                <td className="px-4 py-2.5 font-mono text-ink">{kg(f.kg)}</td>
                <td className="px-4 py-2.5 font-mono text-ink">{f.maquina}</td>
                <td className="px-4 py-2.5 text-ink-soft">{nombreDia(f.dia)} {fechaCorta(f.dia)}</td>
                <td className="px-4 py-2.5 font-mono text-ink-soft">{f.ordenSecuencia}{f.requiereLavado ? ' · lavado' : ''}</td>
                <td className={`px-4 py-2.5 font-medium ${f.clasificacion === 'SOBRANTE' ? 'text-danger' : 'text-ink'}`}>{CLASIFICACION_RESULTADO[f.clasificacion]}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )}</Paginado>
}
