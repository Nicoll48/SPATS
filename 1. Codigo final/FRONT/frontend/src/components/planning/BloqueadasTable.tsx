import type { PartidaBloqueada } from '@/api/tipos'
import { TONALIDAD, kg } from '@/lib/etiquetas'
import { fechaCorta } from '@/lib/fechas'
import { Paginado } from '@/components/ui/Paginacion'
import { EtiquetaVersion, FranjaVersion } from '@/components/planning/CalendarioPlanificacion'

/** Bloqueada con las versiones de la proyección en que lo está (D-52); solo al comparar versiones. */
export type BloqueadaConVersion = PartidaBloqueada & { versiones?: number[] }

/**
 * Partidas Bloqueadas (sección 6.2): la bolsa de al menos uno de sus artículos tuvo kilos en la semana,
 * pero no alcanzaron para completarla, o ninguna máquina de su capacidad acepta su tonalidad (D-51).
 * No tienen máquina ni día y no consumen tela; siguen pendientes.
 */
export function BloqueadasTable({ filas }: { filas: BloqueadaConVersion[] }) {
  const conVersion = filas.some((f) => f.versiones && f.versiones.length > 0)
  if (filas.length === 0)
    return <div className="rounded-xl border border-line bg-panel p-8 text-center text-sm text-ink-soft">No hay partidas bloqueadas en esta versión.</div>
  return <Paginado items={filas}>{(pagina) => (
    <div className="overflow-hidden rounded-xl border border-line bg-panel">
      <div className="overflow-x-auto">
        <table className="w-full min-w-[1080px] text-left text-sm">
          <thead>
            <tr className="border-b border-line text-xs tracking-wide text-ink-faint uppercase">
              {conVersion && <th className="px-4 py-3">Versión</th>}
              <th className="px-4 py-3">Partida</th><th className="px-4 py-3">Pedido</th><th className="px-4 py-3">Entrega</th>
              <th className="px-4 py-3">Color</th><th className="px-4 py-3">Barca</th><th className="px-4 py-3">Kg</th>
              <th className="px-4 py-3">Motivo</th>
              <th className="px-4 py-3">Tela requerida y saldo de la bolsa al domingo</th>
            </tr>
          </thead>
          <tbody>
            {pagina.map((f) => (
              <tr key={`${f.numeroPartida}|${f.versiones?.join() ?? ''}|${f.motivo ?? ''}`} className="border-b border-line align-top last:border-0">
                {conVersion && <td className="relative py-3 pr-4 pl-5">{f.versiones && <><FranjaVersion versiones={f.versiones} className="w-1" /><EtiquetaVersion versiones={f.versiones} className="text-xs" /></>}</td>}
                <td className="px-4 py-3 font-mono font-semibold text-ink">{f.numeroPartida}</td>
                <td className="px-4 py-3 font-mono text-ink-soft">{f.numeroOp}</td>
                <td className="px-4 py-3 text-ink-soft">{fechaCorta(f.fechaEntrega)}</td>
                <td className="px-4 py-3 text-ink">{f.color} <span className="text-xs text-ink-faint">· {TONALIDAD[f.tonalidad]}</span></td>
                <td className="px-4 py-3 font-mono text-ink-soft">{f.barca}</td>
                <td className="px-4 py-3 font-mono text-ink">{kg(f.kg)}</td>
                <td className="px-4 py-3 text-xs text-ink-soft">{f.motivo ?? '—'}</td>
                <td className="px-4 py-3">
                  <ul className="space-y-1 text-xs">
                    {f.componentes.map((c) => (
                      <li key={c.codigoArticulo + c.tipoComponente} className="text-ink-soft">
                        <span className="font-medium text-ink">{c.tipoComponente === 'PRINCIPAL' ? 'Principal' : 'Complemento'}</span>{' '}
                        <span className="font-mono">{c.codigoArticulo}</span>: requiere {kg(c.kgRequeridos)} · saldo {kg(c.kgSaldoFinalArticulo)}
                      </li>
                    ))}
                  </ul>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )}</Paginado>
}
