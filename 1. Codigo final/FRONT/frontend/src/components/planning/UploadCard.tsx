import { Download, Loader2, Upload } from 'lucide-react'
import { StatusPill } from '@/components/ui/StatusPill'

interface UploadCardProps {
  titulo: string
  descripcion: string
  /** Última carga registrada de este tipo (RF14), o null si nunca se cargó. */
  ultimaCarga: { archivo: string; fechaHora: string; usuario: string } | null
  onArchivoSeleccionado: (archivo: File) => void
  onDescargarPlantilla: () => void
  /** Perfil Consulta: la tarjeta muestra el estado, pero no permite subir archivos. */
  soloLectura: boolean
  motivoSoloLectura: string
  cargando: boolean
}

/** Tarjeta de carga de una plantilla .xlsx (HU-04). */
export function UploadCard({ titulo, descripcion, ultimaCarga, onArchivoSeleccionado, onDescargarPlantilla, soloLectura, motivoSoloLectura, cargando }: UploadCardProps) {
  return (
    <div className="flex flex-col rounded-xl border border-line bg-panel p-5">
      <div className="mb-2 flex items-start justify-between gap-3">
        <h3 className="text-sm font-semibold text-ink">{titulo}</h3>
        <StatusPill tone={ultimaCarga ? 'success' : 'neutral'} label={ultimaCarga ? 'Cargado' : 'Sin cargas'} />
      </div>
      <p className="mb-3 text-xs text-ink-soft">{descripcion}</p>
      {ultimaCarga ? (
        <div className="mb-4 flex-1 text-xs text-ink-soft">
          <p className="truncate font-mono text-ink">{ultimaCarga.archivo}</p>
          <p className="mt-1">Última carga: {ultimaCarga.fechaHora} · {ultimaCarga.usuario}</p>
        </div>
      ) : (
        <p className="mb-4 flex-1 text-xs text-ink-faint">Todavía no se cargó ningún archivo.</p>
      )}
      <div className="flex flex-wrap items-center gap-4 border-t border-line pt-3 text-xs font-medium">
        <label title={soloLectura ? motivoSoloLectura : 'Sube un archivo .xlsx con el formato de la plantilla'}
          className={`flex items-center gap-1 text-accent ${soloLectura || cargando ? 'pointer-events-none opacity-40' : 'cursor-pointer hover:underline'}`}>
          {cargando ? <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden="true" /> : <Upload className="h-3.5 w-3.5" aria-hidden="true" />}
          {cargando ? 'Validando…' : 'Subir archivo'}
          <input type="file" accept=".xlsx" disabled={soloLectura || cargando} className="sr-only"
            onChange={(event) => {
              const archivo = event.target.files?.[0]
              if (archivo) onArchivoSeleccionado(archivo)
              event.target.value = ''
            }} />
        </label>
        <button type="button" onClick={onDescargarPlantilla} className="flex items-center gap-1 text-ink-soft hover:text-ink">
          <Download className="h-3.5 w-3.5" aria-hidden="true" />
          Descargar plantilla
        </button>
      </div>
    </div>
  )
}
