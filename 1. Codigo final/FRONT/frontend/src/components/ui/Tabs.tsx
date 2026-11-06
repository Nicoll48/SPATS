import type { LucideIcon } from 'lucide-react'

export interface TabItem {
  id: string
  label: string
  icon?: LucideIcon
  /** Contador opcional (p. ej. cantidad de sobrantes) mostrado como insignia. */
  count?: number
}

interface TabsProps {
  tabs: TabItem[]
  activeId: string
  onChange: (id: string) => void
  wrap?: boolean
}

/**
 * Pestañas internas de una página (no son rutas propias — ver la nota en
 * navigation.ts). Se usa para "Pestañas internas" de Planificación semanal
 * y Escenarios, y para las vistas alternativas de Proyección de
 * planificación, escenarios e historial.
 */
export function Tabs({ tabs, activeId, onChange, wrap = false }: TabsProps) {
  return (
    <div role="tablist" aria-label="Secciones" className={`mb-6 flex gap-1 border-b border-line ${wrap ? 'flex-wrap' : 'overflow-x-auto'}`}>
      {tabs.map((tab) => {
        const Icon = tab.icon
        const active = tab.id === activeId
        return (
          <button
            key={tab.id}
            type="button"
            role="tab"
            aria-selected={active}
            onClick={() => onChange(tab.id)}
            className={`flex items-center justify-center gap-1.5 border-b-2 px-2 py-2.5 text-center text-sm font-medium transition-colors ${wrap ? 'min-w-0 grow basis-auto' : 'shrink-0'} ${
              active ? 'border-accent text-accent dark:text-white' : 'border-transparent text-ink-soft hover:text-ink dark:text-white dark:hover:text-white'
            }`}
          >
            {Icon && <Icon className="h-4 w-4 shrink-0" aria-hidden="true" />}
            {tab.label}
            {tab.count !== undefined && (
              <span
                className={`rounded-full px-1.5 py-0.5 text-xs font-semibold tabular-nums ${
                  active ? 'bg-accent-soft text-accent dark:text-white' : 'bg-panel-muted text-ink-faint dark:text-white'
                }`}
              >
                {tab.count}
              </span>
            )}
          </button>
        )
      })}
    </div>
  )
}
