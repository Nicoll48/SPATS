import { createContext, useContext } from 'react'
import type { ModoInteraccion, PerfilUsuario, Usuario } from '@/api/tipos'

export type { PerfilUsuario, Usuario }

export interface AuthContextValue {
  usuario: Usuario | null
  /** Quién puede ejecutar operaciones de modificación (RF05). Consulta solo lee (RF06). */
  esPlanificador: boolean
  /** Devuelve el mensaje de error si las credenciales no son válidas (RF02). */
  iniciarSesion: (usuario: string, contrasena: string) => Promise<string | null>
  cerrarSesion: () => void
  /** La sesión anterior terminó por inactividad (RF04); la pantalla de ingreso lo explica. */
  sesionExpirada: boolean
  actualizarModo: (modo: ModoInteraccion) => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth debe usarse dentro de <AuthProvider>')
  return ctx
}

/** Mensaje único para toda operación bloqueada por perfil (HU-02). */
export const MENSAJE_SIN_PERMISO =
  'Tu perfil es Consulta: puedes ver toda la información, pero no ejecutar operaciones que modifiquen datos ni la planificación.'
