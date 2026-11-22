import { useRef, useState, type DragEvent } from 'react'
import { Download, FileSpreadsheet, Loader2, Upload, type LucideIcon } from 'lucide-react'
import { StatusPill, type StatusTone } from '@/components/ui/StatusPill'
import { Ayuda } from '@/components/ui/Ayuda'
import { Boton, claseBoton } from '@/components/ui/Boton'

export type ColorTipoDato = 'pedidos' | 'partidas' | 'proyeccion'

/** Cada tipo conserva su fondo; el ícono va en el azul de los íconos de los indicadores de Planificación semanal. */
const COLOR_ICONO: Record<ColorTipoDato, string> = {
  pedidos: 'bg-tipo-pedidos-soft text-accent',
  partidas: 'bg-tipo-partidas-soft text-accent',
  proyeccion: 'bg-tipo-proyeccion-soft text-accent',
}

interface UploadCardProps {
  titulo: string
  /** Una o dos líneas: qué contiene la plantilla. */
  descripcion: string
  /** Detalle ampliado (reglas de la carga), detrás del ícono «i». */
  ayuda: string
  icono: LucideIcon
  color: ColorTipoDato
  estado: { tone: StatusTone; label: string }
  /** Archivo o versión vigente de este tipo (RF14); null si no hay. */
  archivo: { nombre: string; detalle: string } | null
  sinArchivo: string
  onArchivoSeleccionado: (archivo: File) => void
  onDescargarPlantilla: () => void
  /** Perfil Consulta: la tarjeta muestra el estado, pero no permite subir archivos. */
  soloLectura: boolean
  cargando: boolean
}

/**
 * Tarjeta de carga de una plantilla .xlsx (HU-04), con el patrón File uploader de Carbon:
 * estado de la carga, archivo vigente, botón «Subir archivo» (tertiary), «Descargar plantilla»
 * (ghost) y zona para arrastrar y soltar el archivo.
 */
export function UploadCard({ titulo, descripcion, ayuda, icono: Icono, color, estado, archivo, sinArchivo, onArchivoSeleccionado, onDescargarPlantilla, soloLectura, cargando }: UploadCardProps) {
  const [arrastrando, setArrastrando] = useState(false)
  const input = useRef<HTMLInputElement>(null)
  const admiteArrastre = !soloLectura && !cargando

  function soltar(e: DragEvent) {
    e.preventDefault()
    setArrastrando(false)
    const archivoSoltado = e.dataTransfer.files?.[0]
    if (admiteArrastre && archivoSoltado) onArchivoSeleccionado(archivoSoltado)
  }

  return (
    <section aria-label={titulo}
      onDragOver={(e) => { if (admiteArrastre) { e.preventDefault(); setArrastrando(true) } }}
      onDragLeave={(e) => { if (!e.currentTarget.contains(e.relatedTarget as Node)) setArrastrando(false) }}
      onDrop={soltar}
      className={`flex flex-col rounded-2xl border bg-panel p-5 transition-colors ${arrastrando ? 'border-dashed border-accent bg-accent-soft' : 'border-line'}`}>
      <div className="flex items-start gap-4">
        <span className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-xl ${COLOR_ICONO[color]}`}><Icono className="h-6 w-6" aria-hidden="true" /></span>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-start justify-between gap-2">
            <h3 className="flex items-center gap-1.5 text-base font-semibold text-ink">{titulo}<Ayuda texto={ayuda} etiqueta={`Más información sobre ${titulo}`} /></h3>
            <StatusPill tone={estado.tone} label={estado.label} />
          </div>
          <p className="mt-1 text-sm leading-5 text-ink-soft">{descripcion}</p>
        </div>
      </div>

      <div className="mt-4 flex min-h-16 flex-1 items-center gap-3 rounded-xl border border-line bg-panel-muted/50 px-3 py-3" aria-live="polite">
        {cargando ? (
          <><Loader2 className="h-5 w-5 shrink-0 animate-spin text-accent" aria-hidden="true" /><p className="text-sm text-ink">Subiendo y validando el archivo…</p></>
        ) : archivo ? (
          <>
            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-success text-white"><FileSpreadsheet className="h-5 w-5" aria-hidden="true" /></span>
            <div className="min-w-0">
              <p className="truncate text-sm font-medium text-ink" title={archivo.nombre}>{archivo.nombre}</p>
              <p className="mt-0.5 truncate text-xs text-ink-soft">{archivo.detalle}</p>
            </div>
          </>
        ) : (
          <p className="text-sm text-ink-faint">{arrastrando ? 'Suelta el archivo para subirlo.' : sinArchivo}</p>
        )}
      </div>

      <div className="mt-4 flex flex-wrap items-center justify-between gap-2">
        {!soloLectura && (
          <>
            <Boton variante="tertiary" disabled={cargando} onClick={() => input.current?.click()} className="min-w-40">
              <Upload className="h-4 w-4" aria-hidden="true" />{cargando ? 'Subiendo…' : 'Subir archivo'}
            </Boton>
            <input ref={input} type="file" accept=".xlsx" className="sr-only" tabIndex={-1} aria-hidden="true"
              onChange={(event) => {
                const elegido = event.target.files?.[0]
                if (elegido) onArchivoSeleccionado(elegido)
                event.target.value = ''
              }} />
          </>
        )}
        <button type="button" onClick={onDescargarPlantilla} className={claseBoton('ghost')}>
          <Download className="h-4 w-4" aria-hidden="true" />Descargar plantilla
        </button>
      </div>
      {!soloLectura && <p className="mt-2 text-xs text-ink-faint">Archivo .xlsx con el formato de la plantilla. También puedes arrastrarlo a esta tarjeta.</p>}
    </section>
  )
}
