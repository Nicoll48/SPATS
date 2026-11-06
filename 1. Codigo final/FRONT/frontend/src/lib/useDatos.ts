import { useCallback, useEffect, useRef, useState, type DependencyList } from 'react'
import { ErrorApi } from '@/api/cliente'

/**
 * Carga datos de la API y expone su estado. `recargar` vuelve a pedirlos (por ejemplo, después de una
 * operación que cambió la planificación). Las respuestas de una carga anterior se descartan.
 */
export function useDatos<T>(cargar: () => Promise<T>, dependencias: DependencyList) {
  const [datos, setDatos] = useState<T | null>(null)
  const [error, setError] = useState<ErrorApi | null>(null)
  const [cargando, setCargando] = useState(true)
  const turno = useRef(0)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const cargarMemo = useCallback(cargar, dependencias)

  const recargar = useCallback(async () => {
    const mio = ++turno.current
    setCargando(true)
    try {
      const resultado = await cargarMemo()
      if (mio === turno.current) { setDatos(resultado); setError(null) }
    } catch (e) {
      if (mio === turno.current) { setDatos(null); setError(e instanceof ErrorApi ? e : new ErrorApi(0, null)) }
    } finally {
      if (mio === turno.current) setCargando(false)
    }
  }, [cargarMemo])

  useEffect(() => { void recargar() }, [recargar])

  return { datos, error, cargando, recargar, setDatos }
}

/** Ejecuta una operación y devuelve su error como ErrorApi. */
export async function intentar<T>(operacion: () => Promise<T>): Promise<{ ok: true; valor: T } | { ok: false; error: ErrorApi }> {
  try {
    return { ok: true, valor: await operacion() }
  } catch (e) {
    return { ok: false, error: e instanceof ErrorApi ? e : new ErrorApi(0, null) }
  }
}
