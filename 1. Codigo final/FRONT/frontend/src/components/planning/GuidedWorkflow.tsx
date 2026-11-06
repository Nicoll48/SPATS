import { CalendarCheck2, Check, ChevronRight, CircleHelp, Compass, Database, Eye, Play } from 'lucide-react'
import { Link } from 'react-router-dom'

interface GuidedWorkflowProps {
  needsData: boolean
  hasPlan: boolean
  isReviewed: boolean
  isClosed: boolean
  onPlan: () => void
  onReview: () => void
  onClose: () => void
}

const steps = [
  { label: 'Preparar datos', icon: Database },
  { label: 'Generar plan', icon: Play },
  { label: 'Revisar', icon: Eye },
  { label: 'Cerrar semana', icon: CalendarCheck2 },
]

/**
 * Modo guiado (HU-03 C1): presenta en secuencia el flujo de la semana con las mismas funciones y
 * permisos del modo experto. Cada generación exitosa queda como planificación vigente (no hay paso de
 * publicación).
 */
export function GuidedWorkflow({ needsData, hasPlan, isReviewed, isClosed, onPlan, onReview, onClose }: GuidedWorkflowProps) {
  const current = needsData ? 0 : !hasPlan ? 1 : !isReviewed ? 2 : !isClosed ? 3 : 4
  const reviewing = hasPlan && !isReviewed
  const percent = Math.min(100, Math.round((current / steps.length) * 100))
  const boton = 'inline-flex items-center gap-1.5 rounded-lg bg-accent px-4 py-2.5 text-sm font-semibold text-accent-ink hover:opacity-90'
  const task = needsData
    ? { eyebrow: 'Nueva semana', title: 'Carga la proyección de tejeduría', text: 'Es el dato que se actualiza cada semana. Los pedidos, artículos y partidas ya registrados se conservan; solo vuelve a cargarlos cuando existan datos nuevos.', action: <Link to="/datos-semanales" className={boton}>Ir a carga de datos<ChevronRight className="h-4 w-4" /></Link> }
    : !hasPlan
      ? { eyebrow: 'Todo listo para comenzar', title: 'Genera la planificación semanal', text: 'El sistema decide qué partidas se conforman con la tela de cada artículo y a qué máquina de su barca va cada una. El resultado queda como la planificación vigente de la semana.', action: <button type="button" onClick={onPlan} className={boton}><Play className="h-4 w-4" />Generar planificación</button> }
      : !isReviewed
        ? { eyebrow: 'Planificación generada', title: 'Revisa el resultado', text: 'Recorre las pestañas para comprender qué se asignó, qué quedó como sobrante o bloqueada y qué desviaciones tiene cada máquina.', action: <button type="button" onClick={onReview} className={boton}>Siguiente paso<ChevronRight className="h-4 w-4" /></button> }
        : !isClosed
          ? { eyebrow: 'Planificación vigente', title: 'Al terminar la semana, confirma lo completado y ciérrala', text: 'Marca las partidas Asignadas y Sobrantes que se tiñeron. Las demás pasarán a Arrastradas y seguirán visibles en las próximas semanas.', action: <button type="button" onClick={onClose} className={boton}><CalendarCheck2 className="h-4 w-4" />Revisar y cerrar</button> }
          : { eyebrow: 'Semana cerrada', title: 'Planificación semanal cerrada', text: 'Las partidas confirmadas quedaron completadas. Puedes revisar las pendientes en la pestaña Partidas arrastradas.', action: <Link to="/historial" className="inline-flex items-center gap-1.5 rounded-lg border border-line bg-panel px-4 py-2.5 text-sm font-semibold text-ink hover:bg-panel-muted">Ver historial<ChevronRight className="h-4 w-4" /></Link> }

  return (
    <section className="mb-6 overflow-hidden rounded-2xl border border-accent/25 bg-panel shadow-sm" aria-labelledby="guided-title">
      <div className="border-b border-line bg-accent-soft/70 px-5 py-4 sm:px-6">
        <div className="flex items-start justify-between gap-4">
          <div className="flex gap-3"><span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-accent text-accent-ink"><Compass className="h-5 w-5" /></span><div><h2 id="guided-title" className="font-semibold text-ink">Tu ruta para planificar esta semana</h2><p className="mt-0.5 text-sm text-ink-soft">Avanza con seguridad. Puedes volver a cualquier paso cuando lo necesites.</p></div></div>
          <span className="shrink-0 text-xs font-semibold text-accent">{percent}%</span>
        </div>
        <div className="mt-4 h-1.5 overflow-hidden rounded-full bg-panel"><div className="h-full rounded-full bg-accent transition-all" style={{ width: `${percent}%` }} /></div>
        <ol className="mt-4 grid grid-cols-4 gap-1" aria-label="Progreso de la planificación">
          {steps.map((step, index) => { const Icon = step.icon; const done = index < current; const active = index === current; return <li key={step.label} className={`flex min-w-0 flex-col items-center gap-1.5 text-center text-[10px] sm:text-xs ${active ? 'font-semibold text-accent' : done ? 'text-success' : 'text-ink-faint'}`} aria-current={active ? 'step' : undefined}><span className={`flex h-7 w-7 items-center justify-center rounded-full border ${active ? 'border-accent bg-accent text-accent-ink' : done ? 'border-success bg-success-soft text-success' : 'border-line bg-panel text-ink-faint'}`}>{done ? <Check className="h-3.5 w-3.5" /> : <Icon className="h-3.5 w-3.5" />}</span><span className="w-full truncate">{step.label}</span></li> })}
        </ol>
      </div>
      <div className="flex flex-col gap-4 p-5 sm:flex-row sm:items-center sm:justify-between sm:px-6">
        <div className="max-w-2xl"><p className="text-xs font-semibold uppercase tracking-wide text-accent">{task.eyebrow}</p><h3 className="mt-1 text-lg font-semibold text-ink">{task.title}</h3><p className="mt-1 text-sm leading-6 text-ink-soft">{task.text}</p></div>
        <div className="shrink-0">{task.action}</div>
      </div>
      {reviewing && (
        <div className="grid gap-2 border-t border-line bg-panel-muted/35 px-5 py-4 sm:grid-cols-2 sm:px-6 lg:grid-cols-4">
          <div className="rounded-xl border border-line bg-panel p-3"><p className="text-sm font-semibold text-ink">Asignadas</p><p className="mt-1 text-xs leading-5 text-ink-soft">Se conformaron y quedaron dentro del objetivo semanal de su máquina, en un día del calendario.</p></div>
          <div className="rounded-xl border border-line bg-panel p-3"><p className="text-sm font-semibold text-ink">Sobrantes</p><p className="mt-1 text-xs leading-5 text-ink-soft">Se conformaron pero exceden el objetivo o ya no había espacio. Aparecen en rojo en su primer día disponible.</p></div>
          <div className="rounded-xl border border-line bg-panel p-3"><p className="text-sm font-semibold text-ink">Bloqueadas</p><p className="mt-1 text-xs leading-5 text-ink-soft">Su artículo tuvo tela, pero no alcanzó para completarlas. Siguen pendientes para la semana siguiente.</p></div>
          <div className="rounded-xl border border-line bg-panel p-3"><p className="text-sm font-semibold text-ink">Alertas</p><p className="mt-1 text-xs leading-5 text-ink-soft">Indican cuántas partidas faltan o sobran en cada máquina frente a su objetivo.</p></div>
        </div>
      )}
      <div className="border-t border-line bg-panel-muted/50 px-5 py-3 text-xs text-ink-soft sm:px-6"><div className="flex items-start gap-2"><CircleHelp className="mt-0.5 h-4 w-4 shrink-0 text-accent" /><p><strong className="text-ink">Vigente</strong> es la única versión activa de la semana. Si vuelves a generar, la anterior se conserva en el historial.</p></div><div className="mt-2 flex items-start gap-2"><CircleHelp className="mt-0.5 h-4 w-4 shrink-0 text-warning" /><p><strong className="text-ink">Si ocurre una incidencia:</strong> regístrala en Incidencias y replanificación. Solo se usa cuando un cambio afecta la planificación vigente.</p></div></div>
    </section>
  )
}
