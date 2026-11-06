import { Lightbulb } from 'lucide-react'
import { COLOR_SWATCH, type SugerenciaTejido } from '@/data/mockData'

interface SugerenciasTejidoProps {
  sugerencias: SugerenciaTejido[]
}

/**
 * RF35 — qué convendría tejer para completar las máquinas que quedaron bajo
 * objetivo. Va debajo de la tabla de equilibrio porque solo tiene sentido
 * leerla después de ver qué máquina tiene déficit: agrupa las sugerencias por
 * máquina, con el déficit exacto como encabezado.
 *
 * Es una sugerencia hacia tejeduría, no una acción del planificador: acá no
 * hay ningún botón que modifique el plan — el calendario no se edita a mano.
 */
export function SugerenciasTejido({ sugerencias }: SugerenciasTejidoProps) {
  if (sugerencias.length === 0) return <section className="mt-8 rounded-xl border border-line bg-panel p-4 text-sm text-ink-soft"><span className="font-semibold text-ink">Recomendación de tejido:</span> 0 · la carga quedó equilibrada.</section>

  const maquinas = Array.from(new Set(sugerencias.map((s) => s.maquina)))

  return (
    <section className="mt-8">
      <div className="mb-1 flex items-center gap-2">
        <Lightbulb className="h-4 w-4 shrink-0 text-warning" aria-hidden="true" />
        <h3 className="text-sm font-semibold text-ink">Recomendación de tejido</h3>
      </div>
      <div className="mt-3 flex flex-col gap-4">
        {maquinas.map((maquina) => {
          const filas = sugerencias.filter((s) => s.maquina === maquina)
          return (
            <div key={maquina} className="overflow-hidden rounded-xl border border-line bg-panel">
              <div className="flex flex-wrap items-center gap-2 border-b border-line px-4 py-3">
                <span className="font-mono text-sm font-semibold text-ink">{maquina}</span>
                <span className="text-xs text-ink-soft">
                  le faltan {filas[0].partidasFaltantes} partidas para su objetivo semanal
                </span>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full min-w-[720px] text-left text-sm">
                  <thead>
                    <tr className="border-b border-line text-xs tracking-wide text-ink-faint uppercase">
                      <th className="px-4 py-3 font-medium">Pedido</th>
                      <th className="px-4 py-3 font-medium">Cliente</th>
                      <th className="px-4 py-3 font-medium">Artículo</th>
                      <th className="px-4 py-3 font-medium">Color</th>
                      <th className="px-4 py-3 font-medium">Kg</th>
                      <th className="px-4 py-3 font-medium">Tejer antes de</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-line">
                    {filas.map((fila) => (
                      <tr key={fila.id} className="transition-colors hover:bg-panel-muted">
                        <td className="px-4 py-3 font-mono font-medium text-ink">{fila.pedido}</td>
                        <td className="px-4 py-3 text-ink-soft">{fila.cliente}</td>
                        <td className="px-4 py-3 text-ink-soft">{fila.articulo}</td>
                        <td className="px-4 py-3">
                          <span className="inline-flex items-center gap-1.5 text-ink-soft">
                            <span
                              className="h-2.5 w-2.5 shrink-0 rounded-full ring-1 ring-black/10"
                              style={{ backgroundColor: COLOR_SWATCH[fila.color] ?? '#8a8f98' }}
                              aria-hidden="true"
                            />
                            {fila.color}
                          </span>
                        </td>
                        <td className="px-4 py-3 font-mono tabular-nums text-ink-soft">{fila.kg}</td>
                        <td className="px-4 py-3 text-ink-soft">{fila.tejerAntesDe}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )
        })}
      </div>
    </section>
  )
}
