import { useState } from 'react'
import { PageHeader } from '@/components/common/PageHeader'
import { SoloLecturaAviso } from '@/components/common/SoloLecturaAviso'
import { inputClass } from '@/components/ui/FormField'
import { useAuth } from '@/auth/auth-context'
import { parametrosOperativos } from '@/data/mockData'

export function ParametrosPage() {
  const { esPlanificador } = useAuth()
  const [parametros, setParametros] = useState(parametrosOperativos)
  return (
    <>
      <PageHeader title="Parámetros operativos" description="Tiempos usados al generar y replanificar." />
      <SoloLecturaAviso />
      <div className="max-w-2xl divide-y divide-line overflow-hidden rounded-xl border border-line bg-panel">
        <div className="flex items-center justify-between gap-4 p-5">
          <div><p className="text-sm font-medium text-ink">Tiempo de lavado</p><p className="mt-0.5 text-xs text-ink-soft">Duración del lavado al cambiar de un color oscuro a uno claro.</p></div>
          <div className="flex shrink-0 items-center gap-1.5"><input type="number" step={0.5} min={0} value={parametros.tiempoLavadoHoras} onChange={(event) => setParametros((prev) => ({ ...prev, tiempoLavadoHoras: Number(event.target.value) }))} disabled={!esPlanificador} className={`${inputClass} w-20 text-right disabled:opacity-50`} /><span className="text-xs text-ink-faint">h</span></div>
        </div>
        <div className="flex items-center justify-between gap-4 p-5">
          <div><p className="text-sm font-medium text-ink">Horizonte congelado</p><p className="mt-0.5 text-xs text-ink-soft">Margen que no se modifica al replanificar.</p></div>
          <div className="flex shrink-0 items-center gap-1.5"><input type="number" step={0.5} min={0} value={parametros.horizonteCongeladoHoras} onChange={(event) => setParametros((prev) => ({ ...prev, horizonteCongeladoHoras: Number(event.target.value) }))} disabled={!esPlanificador} className={`${inputClass} w-20 text-right disabled:opacity-50`} /><span className="text-xs text-ink-faint">h</span></div>
        </div>
      </div>
    </>
  )
}
