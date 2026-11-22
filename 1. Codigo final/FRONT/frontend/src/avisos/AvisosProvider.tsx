import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { CircleCheck, Info, TriangleAlert, X, type LucideIcon } from 'lucide-react'
import { AvisosContext, type Aviso, type TipoAviso } from './avisos-context'

const ESTILO: Record<TipoAviso, { borde: string; icono: string; barra: string; Icono: LucideIcon }> = {
  success: { borde: 'border-l-success', icono: 'text-success', barra: 'bg-success/60', Icono: CircleCheck },
  warning: { borde: 'border-l-warning', icono: 'text-warning', barra: 'bg-warning/60', Icono: TriangleAlert },
  info: { borde: 'border-l-accent', icono: 'text-accent', barra: 'bg-accent/60', Icono: Info },
}

/**
 * Un aviso flotante: se cierra solo a los segundos indicados o con la X. Mientras el puntero o el foco están encima,
 * la cuenta se detiene para poder leerlo; la barra inferior muestra el tiempo que queda.
 */
function AvisoFlotante({ aviso, onCerrar }: { aviso: Aviso; onCerrar: () => void }) {
  const segundos = aviso.segundos ?? (aviso.tipo === 'warning' ? 10 : 6)
  const [pausado, setPausado] = useState(false)
  const restante = useRef(segundos * 1000)
  const cerrar = useRef(onCerrar)
  useEffect(() => { cerrar.current = onCerrar })

  useEffect(() => {
    if (pausado) return
    const inicio = Date.now()
    const t = window.setTimeout(() => cerrar.current(), restante.current)
    return () => { window.clearTimeout(t); restante.current -= Date.now() - inicio }
  }, [pausado])

  const { borde, icono, barra, Icono } = ESTILO[aviso.tipo]
  return (
    <div role="status" aria-live="polite"
      onMouseEnter={() => setPausado(true)} onMouseLeave={() => setPausado(false)}
      onFocus={() => setPausado(true)} onBlur={() => setPausado(false)}
      className={`pointer-events-auto w-full overflow-hidden rounded-xl border border-line border-l-[3px] bg-panel text-sm text-ink shadow-xl ${borde}`}>
      <div className="flex items-start gap-3 px-4 py-3">
        <Icono className={`mt-0.5 h-5 w-5 shrink-0 ${icono}`} aria-hidden="true" />
        <div className="min-w-0 flex-1">
          <p className="font-semibold break-words">{aviso.titulo}{aviso.lista ? ` (${aviso.lista.length})` : ''}</p>
          {aviso.detalle && <div className="mt-0.5 text-ink-soft">{aviso.detalle}</div>}
          {aviso.lista && (
            <ul className="mt-1.5 max-h-56 list-disc space-y-1 overflow-y-auto pl-4 text-ink-soft">
              {aviso.lista.map((a, i) => <li key={i}>{a}</li>)}
            </ul>
          )}
        </div>
        <button type="button" onClick={onCerrar} aria-label="Cerrar aviso" className="shrink-0 rounded p-0.5 text-ink-soft hover:bg-panel-muted hover:text-ink">
          <X className="h-4 w-4" aria-hidden="true" />
        </button>
      </div>
      <span aria-hidden="true" className={`block h-1 origin-left ${barra}`}
        style={{ animation: `toast-progress ${segundos}s linear forwards`, animationPlayState: pausado ? 'paused' : 'running' }} />
    </div>
  )
}

/** Pila de avisos flotantes abajo a la derecha, para el resultado de las acciones (cargas, generaciones, cambios). */
export function AvisosProvider({ children }: { children: ReactNode }) {
  const [avisos, setAvisos] = useState<(Aviso & { id: number })[]>([])
  const siguiente = useRef(1)
  const avisar = useCallback((aviso: Aviso) => {
    const id = siguiente.current++
    setAvisos((a) => [...a.slice(-3), { ...aviso, id }])
  }, [])
  return (
    <AvisosContext.Provider value={avisar}>
      {children}
      <div className="pointer-events-none fixed right-4 bottom-4 z-50 flex w-[min(400px,calc(100vw-2rem))] flex-col gap-2">
        {avisos.map((a) => <AvisoFlotante key={a.id} aviso={a} onCerrar={() => setAvisos((x) => x.filter((y) => y.id !== a.id))} />)}
      </div>
    </AvisosContext.Provider>
  )
}
