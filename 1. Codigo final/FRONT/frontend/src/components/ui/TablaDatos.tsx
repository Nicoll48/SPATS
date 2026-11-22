import type { ReactNode } from 'react'
import { Inbox, SearchX } from 'lucide-react'

/**
 * Tabla de datos de Carbon: encabezado sobre fondo gris con títulos en tipo oración (sin
 * mayúsculas sostenidas), filas de 48 px con resaltado al pasar el mouse y desplazamiento
 * horizontal en pantallas angostas.
 */
export function TablaDatos({ cabeceras, children, minimo = 900 }: { cabeceras: string[]; children: ReactNode; minimo?: number }) {
  return (
    <div className="overflow-x-auto rounded-xl border border-line bg-panel">
      <table className="w-full text-left text-sm" style={{ minWidth: minimo }}>
        <thead>
          <tr className="bg-panel-muted text-ink">
            {cabeceras.map((c, i) => <th key={i} scope="col" className="h-12 px-4 font-semibold whitespace-nowrap">{c ? c : <span className="sr-only">Acciones</span>}</th>)}
          </tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  )
}

/** Clases de una fila y de sus celdas, para usarlas igual en todas las tablas. */
export const filaTabla = 'border-t border-line transition-colors hover:bg-panel-muted/60'
export const celda = 'px-4 py-3'

/**
 * Estado vacío de Carbon: dice qué pasa y qué puede hacer el usuario. Distingue «no hay datos»
 * de «los filtros no dejan ver nada».
 */
export function FilaVacia({ columnas, filtrado, titulo, descripcion, accion }: {
  columnas: number
  filtrado: boolean
  titulo: string
  descripcion?: string
  accion?: ReactNode
}) {
  const Icono = filtrado ? SearchX : Inbox
  return (
    <tr>
      <td colSpan={columnas} className="px-4 py-12">
        <div className="mx-auto flex max-w-md flex-col items-center text-center">
          <Icono className="h-8 w-8 text-ink-faint" aria-hidden="true" />
          <p className="mt-3 text-base font-semibold text-ink">{filtrado ? 'Ningún registro coincide con la búsqueda' : titulo}</p>
          <p className="mt-1 text-sm text-ink-soft">{filtrado ? 'Prueba con otro texto o quita alguno de los filtros aplicados.' : descripcion}</p>
          {accion && <div className="mt-4">{accion}</div>}
        </div>
      </td>
    </tr>
  )
}

/** Esqueleto de carga (Carbon: skeleton state): mantiene la forma de la tabla mientras llegan los datos. */
export function TablaCargando({ columnas = 5, filas = 6 }: { columnas?: number; filas?: number }) {
  return (
    <div className="overflow-hidden rounded-xl border border-line bg-panel" aria-busy="true" aria-label="Cargando registros">
      <div className="flex h-12 items-center gap-6 bg-panel-muted px-4">
        {Array.from({ length: columnas }, (_, i) => <span key={i} className="h-3 flex-1 animate-pulse rounded bg-line" />)}
      </div>
      {Array.from({ length: filas }, (_, f) => (
        <div key={f} className="flex h-12 items-center gap-6 border-t border-line px-4">
          {Array.from({ length: columnas }, (_, i) => <span key={i} className="h-3 flex-1 animate-pulse rounded bg-panel-muted" />)}
        </div>
      ))}
    </div>
  )
}
