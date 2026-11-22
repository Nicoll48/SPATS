import { useState } from 'react'
import { CheckCircle2, Trash2 } from 'lucide-react'
import { AvisoError, Cargando } from '@/components/common/AvisoError'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { CalendarioPlanificacion } from '@/components/planning/CalendarioPlanificacion'
import { MENSAJE_SIN_PERMISO } from '@/auth/auth-context'
import { incidencias as incidenciasApi, planificacion, replanificacion } from '@/api/servicios'
import { ResumenCambios } from './ResumenCambios'
import type { ErrorApi } from '@/api/cliente'
import type { ReplanificacionResponse } from '@/api/tipos'
import { intentar, useDatos } from '@/lib/useDatos'
import { compararVersiones } from '@/lib/versiones'

/**
 * Propuesta de re-planificación sin confirmar (HU-18): qué partidas cambian frente a la vigente, el calendario de la
 * propuesta y los botones para confirmarla o descartarla. Se muestra en Planificación semanal, junto al calendario.
 */
export function PropuestaReplanificacion({ lunes, idVigente, idPropuesta, numeroVersion, resultado, esPlanificador, onTerminar, onError }: {
  lunes: string; idVigente: number; idPropuesta: number; numeroVersion: number; resultado: ReplanificacionResponse | null; esPlanificador: boolean
  onTerminar: (mensaje: string) => Promise<void>; onError: (e: ErrorApi) => void
}) {
  const comparacion = useDatos(() => planificacion.comparar(idVigente, idPropuesta), [idVigente, idPropuesta])
  const detalle = useDatos(() => planificacion.detalle(idPropuesta), [idPropuesta])
  const detalleVigente = useDatos(() => planificacion.detalle(idVigente), [idVigente])
  // La propuesta considera todas las incidencias pendientes de la semana (D-53)
  const incidencias = useDatos(() => incidenciasApi.deLaSemana(lunes), [lunes, idPropuesta])
  const [confirmando, setConfirmando] = useState<'confirmar' | 'descartar' | null>(null)

  async function confirmar() {
    setConfirmando(null)
    const r = await intentar(() => replanificacion.confirmar(idPropuesta))
    if (!r.ok) { onError(r.error); await onTerminar(''); return }
    await onTerminar(`Re-planificación confirmada: la versión ${r.valor.numeroVersion} es la planificación vigente. `
      + `${r.valor.incidenciasConsideradas} incidencias consideradas; ${r.valor.pedidosActualizados} pedidos con fecha de entrega nueva.`)
  }

  async function descartar() {
    setConfirmando(null)
    const r = await intentar(() => replanificacion.descartar(idPropuesta))
    if (!r.ok) { onError(r.error); return }
    await onTerminar('La propuesta se descartó. La planificación vigente no cambió y las incidencias siguen pendientes.')
  }

  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-warning/40 bg-warning-soft p-4 text-sm text-warning">
        <p className="font-medium">Propuesta: versión {numeroVersion}, sin confirmar</p>
        <p className="mt-1">Si la confirmas, pasa a ser la planificación vigente, las incidencias quedan consideradas y los pedidos con cambio de prioridad toman su nueva fecha de entrega. Solo puedes confirmarla hoy; si no, se descarta.</p>
      </div>
      {resultado && !resultado.vigenteFactible
        ? <p className="text-sm text-warning">La vigente ya no cumple las reglas con estas incidencias: {resultado.incumplimientosVigente.join(' · ')}</p>
        : <p className="text-sm text-ink-soft">Con estas incidencias, la propuesta reparte la carga mejor que la planificación vigente.</p>}
      {comparacion.error && <AvisoError error={comparacion.error} />}
      {comparacion.datos && <ResumenCambios comparacion={comparacion.datos} vigente={detalleVigente.datos} propuesta={detalle.datos}
        incidencias={(incidencias.datos ?? []).filter((x) => x.estado === 'PENDIENTE')}
        calendario={detalle.cargando ? <Cargando texto="Cargando el calendario de la propuesta…" /> : detalle.datos && <CalendarioPlanificacion calendario={compararVersiones([{ version: detalle.datos.versionProyeccion, plan: detalle.datos }])} />} />}
      <div className="flex flex-wrap gap-2">
        <button type="button" disabled={!esPlanificador} title={esPlanificador ? undefined : MENSAJE_SIN_PERMISO} onClick={() => setConfirmando('confirmar')}
          className="inline-flex items-center gap-1.5 rounded-lg bg-accent px-3 py-2 text-sm font-medium text-accent-ink disabled:opacity-40"><CheckCircle2 className="h-4 w-4" />Confirmar propuesta</button>
        <button type="button" disabled={!esPlanificador} title={esPlanificador ? undefined : MENSAJE_SIN_PERMISO} onClick={() => setConfirmando('descartar')}
          className="inline-flex items-center gap-1.5 rounded-lg border border-line bg-panel px-3 py-2 text-sm font-medium text-ink disabled:opacity-40"><Trash2 className="h-4 w-4" />Descartar</button>
      </div>
      <ConfirmDialog open={confirmando === 'confirmar'} title="Confirmar la re-planificación"
        description="La propuesta pasa a ser la planificación vigente y la actual queda en el historial como reemplazada. Las incidencias quedan consideradas y los pedidos con cambio de prioridad toman su nueva fecha de entrega."
        confirmLabel="Confirmar" onConfirm={confirmar} onCancel={() => setConfirmando(null)} />
      <ConfirmDialog open={confirmando === 'descartar'} title="Descartar la propuesta" tone="danger"
        description="La propuesta se elimina y no queda en el historial. La planificación vigente no cambia y las incidencias siguen pendientes."
        confirmLabel="Descartar" onConfirm={descartar} onCancel={() => setConfirmando(null)} />
    </div>
  )
}
