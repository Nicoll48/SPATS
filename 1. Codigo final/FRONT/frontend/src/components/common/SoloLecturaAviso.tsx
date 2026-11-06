import { Eye } from 'lucide-react'
import { MENSAJE_SIN_PERMISO, useAuth } from '@/auth/auth-context'

/**
 * Aviso que explica por qué las acciones de la pantalla están deshabilitadas
 * (HU-02). Se renderiza solo para el perfil Consulta; para Planificador no
 * ocupa espacio. Va en toda pantalla que tenga al menos una operación de
 * modificación.
 */
export function SoloLecturaAviso() {
  const { esPlanificador } = useAuth()
  if (esPlanificador) return null

  return (
    <div className="mb-5 flex items-start gap-2.5 rounded-xl border border-line bg-panel-muted p-3.5">
      <Eye className="mt-0.5 h-4 w-4 shrink-0 text-ink-soft" aria-hidden="true" />
      <p className="text-sm text-ink-soft">{MENSAJE_SIN_PERMISO}</p>
    </div>
  )
}
