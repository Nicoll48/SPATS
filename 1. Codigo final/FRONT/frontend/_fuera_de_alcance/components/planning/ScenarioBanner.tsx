import { FlaskConical } from 'lucide-react'

/**
 * Marca la pantalla de Escenarios como una copia aislada del plan real.
 * Usa el tono `scenario` (violeta) — no el accent ni ningún tono de
 * estado — para que nunca se confunda con la pantalla de Planificación
 * semanal aunque comparta exactamente el mismo esqueleto visual (ver
 * SKILL.md, "Por qué está hecho así"). El borde de color en todo el
 * contenedor de la página (ver EscenariosPage) refuerza la misma idea.
 */
export function ScenarioBanner() {
  return (
    <div className="mb-6 flex items-start gap-3 rounded-xl border-2 border-scenario bg-scenario-soft px-4 py-3">
      <FlaskConical className="mt-0.5 h-5 w-5 shrink-0 text-scenario" aria-hidden="true" />
      <div>
        <p className="text-sm font-semibold text-scenario">Espacio de escenario — esto no es el plan real</p>
        <p className="mt-0.5 text-xs text-ink-soft">
          Los cambios acá quedan aislados en esta copia. La planificación vigente no se ve afectada hasta que elijas
          «Aplicar como plan real».
        </p>
      </div>
    </div>
  )
}
