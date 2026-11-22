import type { ReactNode } from 'react'
import { CircleAlert, CircleCheck, Info, TriangleAlert, X, type LucideIcon } from 'lucide-react'

export type TipoNotificacion = 'success' | 'error' | 'warning' | 'info'

const ESTILO: Record<TipoNotificacion, { caja: string; icono: string; Icono: LucideIcon }> = {
  success: { caja: 'border-l-success bg-success-soft', icono: 'text-success', Icono: CircleCheck },
  error: { caja: 'border-l-danger bg-danger-soft', icono: 'text-danger', Icono: CircleAlert },
  warning: { caja: 'border-l-warning bg-warning-soft', icono: 'text-warning', Icono: TriangleAlert },
  info: { caja: 'border-l-accent bg-accent-soft', icono: 'text-accent', Icono: Info },
}

interface NotificacionProps {
  tipo: TipoNotificacion
  titulo: string
  children?: ReactNode
  /** Acción opcional a la derecha del texto (Carbon: actionable notification). */
  accion?: ReactNode
  onCerrar?: () => void
  className?: string
}

/**
 * Notificación en línea de Carbon: borde izquierdo del color del estado, ícono, título en negrita y
 * detalle. Se usa para el resultado de una acción que el usuario acaba de hacer en la página.
 */
export function Notificacion({ tipo, titulo, children, accion, onCerrar, className = 'mb-4' }: NotificacionProps) {
  const { caja, icono, Icono } = ESTILO[tipo]
  return (
    <div role={tipo === 'error' ? 'alert' : 'status'} className={`flex items-start gap-3 rounded-lg border border-l-[3px] border-line px-4 py-3 text-sm text-ink ${caja} ${className}`}>
      <Icono className={`mt-0.5 h-5 w-5 shrink-0 ${icono}`} aria-hidden="true" />
      <div className="min-w-0 flex-1">
        <p className="font-semibold">{titulo}</p>
        {children && <div className="mt-0.5 text-ink-soft">{children}</div>}
      </div>
      {accion && <div className="shrink-0 self-center">{accion}</div>}
      {onCerrar && (
        <button type="button" onClick={onCerrar} aria-label="Cerrar notificación" className="shrink-0 p-0.5 text-ink-soft hover:text-ink">
          <X className="h-4 w-4" aria-hidden="true" />
        </button>
      )}
    </div>
  )
}
