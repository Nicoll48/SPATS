import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { AuthContext } from './auth-context'
import { ErrorApi, establecerToken, suscribirExpiracion } from '@/api/cliente'
import { autenticacion } from '@/api/servicios'
import type { ModoInteraccion, Sesion } from '@/api/tipos'

const SESION_KEY = 'spats:sesion'
/** Si hay actividad y al token le queda menos que esto, se renueva (el servidor fija la inactividad máxima). */
const RENOVAR_ANTES_MS = 10 * 60 * 1000
const ENTRE_RENOVACIONES_MS = 60 * 1000

function leerSesionGuardada(): Sesion | null {
  try {
    const crudo = window.sessionStorage.getItem(SESION_KEY)
    if (!crudo) return null
    const sesion = JSON.parse(crudo) as Sesion
    return new Date(sesion.expiraEn).getTime() > Date.now() ? sesion : null
  } catch {
    return null
  }
}

function guardar(sesion: Sesion | null) {
  establecerToken(sesion?.token ?? null)
  try {
    if (sesion) window.sessionStorage.setItem(SESION_KEY, JSON.stringify(sesion))
    else window.sessionStorage.removeItem(SESION_KEY)
  } catch { /* sin almacenamiento: la sesión vive solo en memoria */ }
}

/**
 * Sesión con token Bearer emitido por la API (HU-01). El token vence tras el periodo de inactividad que
 * fija el servidor; mientras haya actividad en la pantalla se renueva. Sin actividad, vence y la sesión
 * se cierra sola (RF04). Cerrar sesión descarta el token (RF03).
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [sesion, setSesion] = useState<Sesion | null>(() => {
    const guardada = leerSesionGuardada()
    establecerToken(guardada?.token ?? null)
    return guardada
  })
  const [sesionExpirada, setSesionExpirada] = useState(false)
  const ultimaRenovacion = useRef(0)

  const aplicar = useCallback((nueva: Sesion | null) => {
    guardar(nueva)
    setSesion(nueva)
  }, [])

  const cerrarSesion = useCallback(() => {
    setSesionExpirada(false)
    aplicar(null)
  }, [aplicar])

  const expirar = useCallback(() => {
    setSesionExpirada(true)
    aplicar(null)
  }, [aplicar])

  useEffect(() => suscribirExpiracion(expirar), [expirar])

  const iniciarSesion = useCallback(async (usuario: string, contrasena: string) => {
    try {
      const nueva = await autenticacion.iniciarSesion(usuario.trim(), contrasena)
      setSesionExpirada(false)
      ultimaRenovacion.current = Date.now()
      aplicar(nueva)
      return null
    } catch (e) {
      return e instanceof ErrorApi ? e.message : 'No se pudo iniciar sesión.'
    }
  }, [aplicar])

  // Vencimiento del token y renovación mientras hay actividad.
  useEffect(() => {
    if (!sesion) return
    const expira = new Date(sesion.expiraEn).getTime()
    const temporizador = window.setTimeout(expirar, Math.max(0, expira - Date.now()))
    const alActuar = () => {
      const ahora = Date.now()
      if (expira - ahora > RENOVAR_ANTES_MS || ahora - ultimaRenovacion.current < ENTRE_RENOVACIONES_MS) return
      ultimaRenovacion.current = ahora
      autenticacion.renovar().then(aplicar).catch(() => { /* si falla, el 401 siguiente cierra la sesión */ })
    }
    const eventos: (keyof WindowEventMap)[] = ['pointerdown', 'keydown', 'scroll']
    eventos.forEach((evento) => window.addEventListener(evento, alActuar, { passive: true }))
    return () => {
      window.clearTimeout(temporizador)
      eventos.forEach((evento) => window.removeEventListener(evento, alActuar))
    }
  }, [sesion, expirar, aplicar])

  const actualizarModo = useCallback((modo: ModoInteraccion) => {
    setSesion((actual) => {
      if (!actual) return actual
      const nueva = { ...actual, usuario: { ...actual.usuario, modoInteraccion: modo } }
      guardar(nueva)
      return nueva
    })
  }, [])

  const usuario = sesion?.usuario ?? null
  const valor = useMemo(() => ({
    usuario,
    esPlanificador: usuario?.perfil === 'PLANIFICADOR',
    iniciarSesion,
    cerrarSesion,
    sesionExpirada,
    actualizarModo,
  }), [usuario, iniciarSesion, cerrarSesion, sesionExpirada, actualizarModo])

  return <AuthContext.Provider value={valor}>{children}</AuthContext.Provider>
}
