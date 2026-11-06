import { useState } from 'react'
import { CheckSquare, Loader2, Play, Trash2, UploadCloud } from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { SoloLecturaAviso } from '@/components/common/SoloLecturaAviso'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { ScenarioBanner } from '@/components/planning/ScenarioBanner'
import { MENSAJE_SIN_PERMISO, useAuth } from '@/auth/auth-context'
import { PlanningGrid, PlanningGridLegend } from '@/components/planning/PlanningGrid'
import { comparacionEscenario, semanas } from '@/data/mockData'

const semanaVigente = semanas.find((s) => s.vigente) ?? semanas[1]

/** Las dos salidas de un escenario piden confirmación: una descarta trabajo, la otra pisa el plan real (RF50). */
type CierreEscenario = 'aplicar' | 'descartar'

export function EscenariosPage() {
  const { esPlanificador } = useAuth()
  const [archivoAlternativo, setArchivoAlternativo] = useState<string | null>(null)
  const [generando, setGenerando] = useState(false)
  const [generado, setGenerado] = useState(false)
  const [resultado, setResultado] = useState<'aplicado' | 'descartado' | null>(null)
  const [confirmando, setConfirmando] = useState<CierreEscenario | null>(null)

  function generarEscenario() {
    setGenerando(true)
    window.setTimeout(() => {
      setGenerado(true)
      setGenerando(false)
      setResultado(null)
    }, 800)
  }

  function descartar() {
    setConfirmando(null)
    setGenerado(false)
    setArchivoAlternativo(null)
    setResultado('descartado')
  }

  function aplicarComoPlanReal() {
    setConfirmando(null)
    setResultado('aplicado')
  }

  return (
    <>
      <PageHeader title="Escenarios" description="Probá cambios sobre una copia de la semana vigente sin tocar el plan real." />
      <ScenarioBanner />
      <SoloLecturaAviso />

      <div className="rounded-2xl border-2 border-scenario p-4 sm:p-6">
        <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="text-sm font-medium text-ink">Escenario sobre la semana {semanaVigente.etiqueta}</p>
            <p className="mt-0.5 max-w-md text-xs text-ink-soft">
              Cargá una proyección de tejeduría alternativa solo para esta copia, o generá el escenario con los
              datos ya cargados.
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <label
              title={esPlanificador ? undefined : MENSAJE_SIN_PERMISO}
              className={`inline-flex items-center gap-1.5 rounded-lg border border-line bg-panel px-3 py-2 text-sm font-medium text-ink transition-colors ${
                esPlanificador ? 'cursor-pointer hover:bg-panel-muted' : 'pointer-events-none opacity-40'
              }`}
            >
              <UploadCloud className="h-4 w-4 shrink-0" aria-hidden="true" />
              <span className="max-w-40 truncate">{archivoAlternativo ?? 'Proyección alternativa'}</span>
              <input
                type="file"
                accept=".xlsx,.xls,.csv"
                disabled={!esPlanificador}
                className="sr-only"
                onChange={(event) => {
                  const archivo = event.target.files?.[0]
                  if (archivo) setArchivoAlternativo(archivo.name)
                  event.target.value = ''
                }}
              />
            </label>
            <button
              type="button"
              onClick={generarEscenario}
              disabled={!esPlanificador || generando}
              title={esPlanificador ? undefined : MENSAJE_SIN_PERMISO}
              className="inline-flex items-center gap-1.5 rounded-lg bg-scenario px-3 py-2 text-sm font-medium text-scenario-ink transition-colors hover:opacity-90 disabled:pointer-events-none disabled:opacity-40"
            >
              {generando ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> : <Play className="h-4 w-4" aria-hidden="true" />}
              {generando ? 'Generando…' : 'Generar escenario simulado'}
            </button>
          </div>
        </div>

        {!generado ? (
          <p className="rounded-xl border border-dashed border-scenario/50 bg-panel p-8 text-center text-sm text-ink-soft">
            Todavía no generaste un escenario simulado para esta semana.
          </p>
        ) : (
          <>
            <PlanningGridLegend />
            <PlanningGrid filas={semanaVigente.filasCalendario} />

            <h3 className="mt-6 mb-3 text-sm font-semibold text-ink">Comparación con el plan vigente</h3>
            <div className="overflow-hidden rounded-xl border border-line bg-panel">
              <div className="overflow-x-auto">
                <table className="w-full min-w-[480px] text-left text-sm">
                  <thead>
                    <tr className="border-b border-line text-xs tracking-wide text-ink-faint uppercase">
                      <th className="px-4 py-3 font-medium">Métrica</th>
                      <th className="px-4 py-3 font-medium">Plan vigente</th>
                      <th className="px-4 py-3 font-medium">Escenario</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-line">
                    {comparacionEscenario.map((fila) => (
                      <tr key={fila.metrica}>
                        <td className="px-4 py-3 text-ink-soft">{fila.metrica}</td>
                        <td className="px-4 py-3 font-mono tabular-nums text-ink-soft">{fila.vigente}</td>
                        <td className={`px-4 py-3 font-mono font-medium tabular-nums ${fila.mejora ? 'text-success' : 'text-ink'}`}>
                          {fila.escenario}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            <div className="mt-5 flex flex-wrap items-center gap-3">
              <button
                type="button"
                onClick={() => setConfirmando('descartar')}
                disabled={!esPlanificador}
                title={esPlanificador ? undefined : MENSAJE_SIN_PERMISO}
                className="disabled:pointer-events-none disabled:opacity-40 inline-flex items-center gap-1.5 rounded-lg border border-line bg-panel px-3 py-2 text-sm font-medium text-ink transition-colors hover:bg-panel-muted"
              >
                <Trash2 className="h-4 w-4" aria-hidden="true" />
                Descartar
              </button>
              <button
                type="button"
                onClick={() => setConfirmando('aplicar')}
                disabled={!esPlanificador || resultado === 'aplicado'}
                title={esPlanificador ? undefined : MENSAJE_SIN_PERMISO}
                className="inline-flex items-center gap-1.5 rounded-lg bg-scenario px-3 py-2 text-sm font-medium text-scenario-ink transition-colors hover:opacity-90 disabled:pointer-events-none disabled:opacity-60"
              >
                <CheckSquare className="h-4 w-4" aria-hidden="true" />
                {resultado === 'aplicado' ? 'Aplicado como plan real' : 'Aplicar como plan real'}
              </button>
              {resultado === 'aplicado' && (
                <p className="text-xs text-ink-soft">Va a quedar registrado como una nueva versión en Historial.</p>
              )}
            </div>
          </>
        )}
      </div>

      <ConfirmDialog
        open={confirmando === 'aplicar'}
        title="Aplicar el escenario como plan real"
        description={
          <>
            La proyección de tejeduría y el calendario reales de {semanaVigente.etiqueta} se reemplazan por los de
            este escenario, y la operación queda registrada en el historial como una versión originada en un
            escenario. Es la única acción de esta pantalla que toca los datos reales.
          </>
        }
        confirmLabel="Aplicar como plan real"
        tone="scenario"
        onConfirm={aplicarComoPlanReal}
        onCancel={() => setConfirmando(null)}
      />

      <ConfirmDialog
        open={confirmando === 'descartar'}
        title="Descartar el escenario"
        description="Se elimina la copia aislada con todo lo simulado en ella. Los datos reales no se ven afectados, pero el escenario no se puede recuperar."
        confirmLabel="Descartar"
        tone="danger"
        onConfirm={descartar}
        onCancel={() => setConfirmando(null)}
      />
    </>
  )
}
