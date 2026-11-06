import { CircleAlert, Loader2, X } from 'lucide-react'
import type { ErrorApi } from '@/api/cliente'
import { Paginado } from '@/components/ui/Paginacion'

/** Muestra el error de la API tal como lo redacta el backend, con sus detalles si los hay. */
export function AvisoError({ error, onCerrar }: { error: ErrorApi | null; onCerrar?: () => void }) {
  if (!error) return null
  const bloqueantes = error.problemas.filter((p) => p.bloqueante)
  return (
    <div role="alert" className="mb-5 rounded-xl border border-danger/40 bg-danger-soft p-4 text-sm text-danger">
      <div className="flex items-start gap-2.5">
        <CircleAlert className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
        <div className="min-w-0 flex-1">
          <p className="font-medium">{error.message}</p>
          {error.errores.length > 0 && (
            <ul className="mt-2 list-disc space-y-0.5 pl-5">{error.errores.map((e) => <li key={e.campo}>{e.mensaje}</li>)}</ul>
          )}
          {error.detalles.length > 0 && (
            <ul className="mt-2 list-disc space-y-0.5 pl-5">{error.detalles.slice(0, 10).map((d) => <li key={d}>{d}</li>)}</ul>
          )}
          {bloqueantes.length > 0 && <Paginado items={bloqueantes}>{(pagina) => (
            <div className="mt-3 overflow-auto rounded-lg border border-danger/30 bg-panel">
              <table className="w-full text-left text-xs text-ink">
                <thead><tr className="border-b border-line text-ink-faint"><th className="px-3 py-2">Hoja</th><th className="px-3 py-2">Fila</th><th className="px-3 py-2">Campo</th><th className="px-3 py-2">Problema</th></tr></thead>
                <tbody>{pagina.map((p, i) => <tr key={i} className="border-b border-line last:border-0"><td className="px-3 py-1.5">{p.hoja || '—'}</td><td className="px-3 py-1.5 font-mono">{p.fila || '—'}</td><td className="px-3 py-1.5 font-mono">{p.campo || '—'}</td><td className="px-3 py-1.5">{p.motivo}</td></tr>)}</tbody>
              </table>
            </div>
          )}</Paginado>}
        </div>
        {onCerrar && <button type="button" onClick={onCerrar} aria-label="Cerrar aviso" className="shrink-0 hover:opacity-70"><X className="h-4 w-4" /></button>}
      </div>
    </div>
  )
}

export function Cargando({ texto = 'Cargando…' }: { texto?: string }) {
  return (
    <div className="flex items-center justify-center gap-2 rounded-xl border border-line bg-panel px-6 py-12 text-sm text-ink-soft">
      <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
      {texto}
    </div>
  )
}

/** Lista de avisos no bloqueantes (advertencias de una carga o de una generación). */
export function ListaAdvertencias({ titulo, avisos }: { titulo: string; avisos: string[] }) {
  if (avisos.length === 0) return null
  return (
    <details className="mb-5 rounded-xl border border-warning/40 bg-warning-soft p-4 text-sm text-warning" open={avisos.length <= 5}>
      <summary className="cursor-pointer font-medium">{titulo} ({avisos.length})</summary>
      <Paginado items={avisos}>{(pagina) => <ul className="mt-2 list-disc space-y-1 pl-5 text-ink-soft">{pagina.map((a, i) => <li key={i}>{a}</li>)}</ul>}</Paginado>
    </details>
  )
}
