/**
 * Cliente de la API REST (componente "Cliente de API REST" del C4): centraliza la cabecera de
 * autenticación, la serialización JSON y la traducción de errores. Las respuestas de error del backend
 * son application/problem+json con `detail` (en español, se muestra tal cual) y `codigo` (estable).
 */

const BASE = `${import.meta.env.VITE_API_URL ?? ''}/api`

export interface ProblemaValidacion {
  hoja: string
  fila: number
  campo: string
  motivo: string
  bloqueante: boolean
}

export class ErrorApi extends Error {
  readonly status: number
  readonly codigo: string
  readonly problemas: ProblemaValidacion[]
  readonly errores: { campo: string; mensaje: string }[]
  readonly detalles: string[]

  constructor(status: number, cuerpo: Record<string, unknown> | null) {
    super(typeof cuerpo?.detail === 'string' ? cuerpo.detail : mensajePorEstado(status))
    this.status = status
    this.codigo = typeof cuerpo?.codigo === 'string' ? cuerpo.codigo : 'ERROR'
    this.problemas = Array.isArray(cuerpo?.problemas) ? (cuerpo.problemas as ProblemaValidacion[]) : []
    this.errores = Array.isArray(cuerpo?.errores) ? (cuerpo.errores as { campo: string; mensaje: string }[]) : []
    this.detalles = Array.isArray(cuerpo?.detalles) ? (cuerpo.detalles as string[]) : []
  }
}

function mensajePorEstado(status: number): string {
  if (status === 0) return 'No se pudo conectar con el servidor. Verifica que la API esté en ejecución.'
  if (status === 401) return 'Tu sesión no es válida o expiró. Inicia sesión nuevamente.'
  if (status === 403) return 'Tu perfil no permite ejecutar esta operación.'
  return 'Ocurrió un error inesperado. No se aplicó ningún cambio.'
}

let token: string | null = null
const alExpirarSesion = new Set<() => void>()

export function establecerToken(valor: string | null) {
  token = valor
}

/** Se llama cuando la API responde 401 a una solicitud autenticada: la sesión venció o no es válida. */
export function suscribirExpiracion(oyente: () => void) {
  alExpirarSesion.add(oyente)
  return () => { alExpirarSesion.delete(oyente) }
}

type Consulta = Record<string, string | number | boolean | null | undefined>

function url(ruta: string, consulta?: Consulta) {
  const params = new URLSearchParams()
  for (const [clave, valor] of Object.entries(consulta ?? {}))
    if (valor !== undefined && valor !== null && valor !== '') params.set(clave, String(valor))
  const texto = params.toString()
  return `${BASE}${ruta}${texto ? `?${texto}` : ''}`
}

async function enviar(metodo: string, ruta: string, opciones: { cuerpo?: unknown; consulta?: Consulta; formulario?: FormData } = {}) {
  const cabeceras: Record<string, string> = {}
  if (token) cabeceras.Authorization = `Bearer ${token}`
  let cuerpo: BodyInit | undefined
  if (opciones.formulario) cuerpo = opciones.formulario
  else if (opciones.cuerpo !== undefined) {
    cabeceras['Content-Type'] = 'application/json'
    cuerpo = JSON.stringify(opciones.cuerpo)
  }
  let respuesta: Response
  try {
    respuesta = await fetch(url(ruta, opciones.consulta), { method: metodo, headers: cabeceras, body: cuerpo })
  } catch {
    throw new ErrorApi(0, null)
  }
  if (!respuesta.ok) {
    let problema: Record<string, unknown> | null = null
    try { problema = await respuesta.json() } catch { /* sin cuerpo */ }
    if (respuesta.status === 401 && token) alExpirarSesion.forEach((oyente) => oyente())
    throw new ErrorApi(respuesta.status, problema)
  }
  return respuesta
}

async function json<T>(respuesta: Response): Promise<T> {
  if (respuesta.status === 204) return undefined as T
  const texto = await respuesta.text()
  return (texto ? JSON.parse(texto) : undefined) as T
}

function nombreArchivo(respuesta: Response, porDefecto: string) {
  const disposicion = respuesta.headers.get('Content-Disposition') ?? ''
  const coincidencia = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposicion)
  return coincidencia ? decodeURIComponent(coincidencia[1]) : porDefecto
}

export const api = {
  get: async <T>(ruta: string, consulta?: Consulta) => json<T>(await enviar('GET', ruta, { consulta })),
  post: async <T>(ruta: string, cuerpo?: unknown, consulta?: Consulta) => json<T>(await enviar('POST', ruta, { cuerpo, consulta })),
  put: async <T>(ruta: string, cuerpo: unknown, consulta?: Consulta) => json<T>(await enviar('PUT', ruta, { cuerpo, consulta })),
  eliminar: async <T>(ruta: string, consulta?: Consulta) => json<T>(await enviar('DELETE', ruta, { consulta })),

  subir: async <T>(ruta: string, archivo: File, consulta?: Consulta) => {
    const formulario = new FormData()
    formulario.append('archivo', archivo)
    return json<T>(await enviar('POST', ruta, { formulario, consulta }))
  },

  /** Descarga un archivo de la API y lo entrega al navegador para guardarlo. */
  descargar: async (ruta: string, porDefecto: string, consulta?: Consulta) => {
    const respuesta = await enviar('GET', ruta, { consulta })
    const blob = await respuesta.blob()
    const enlace = document.createElement('a')
    enlace.href = URL.createObjectURL(blob)
    enlace.download = nombreArchivo(respuesta, porDefecto)
    document.body.appendChild(enlace)
    enlace.click()
    enlace.remove()
    URL.revokeObjectURL(enlace.href)
  },
}
