import { useEffect, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { Bell, ChevronDown, Compass, LogOut, Menu, UserRound } from 'lucide-react'
import { findNavLabel } from '@/config/navigation'
import { useAuth } from '@/auth/auth-context'
import { useExperience } from '@/experience/experience-context'
import { PERFIL } from '@/lib/etiquetas'

interface TopbarProps {
  onOpenMobileNav: () => void
}

/** Identidad y cierre de sesión: quién está operando el panel siempre visible (trazabilidad, RF51). */
function MenuUsuario() {
  const { usuario, cerrarSesion } = useAuth()
  const [abierto, setAbierto] = useState(false)
  const contenedor = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!abierto) return
    function handleClickFuera(event: MouseEvent) {
      if (!contenedor.current?.contains(event.target as Node)) setAbierto(false)
    }
    function handleKey(event: KeyboardEvent) {
      if (event.key === 'Escape') setAbierto(false)
    }
    // 'click' y no 'mousedown': con mousedown, un click que abre el menú puede cerrarse
    // solo si el listener llega a registrarse antes de que termine esa misma pulsación.
    document.addEventListener('click', handleClickFuera)
    window.addEventListener('keydown', handleKey)
    return () => {
      document.removeEventListener('click', handleClickFuera)
      window.removeEventListener('keydown', handleKey)
    }
  }, [abierto])

  if (!usuario) return null

  return (
    <div ref={contenedor} className="relative shrink-0">
      <button
        type="button"
        onClick={() => setAbierto((prev) => !prev)}
        aria-expanded={abierto}
        aria-haspopup="menu"
        className="inline-flex items-center gap-1.5 rounded-lg border border-line bg-panel px-2 py-1.5 text-sm text-ink transition-colors hover:bg-panel-muted"
      >
        <UserRound className="h-4 w-4 shrink-0 text-ink-soft" aria-hidden="true" />
        <span className="hidden max-w-32 truncate sm:inline">{usuario.nombre}</span>
        <ChevronDown className="h-3.5 w-3.5 shrink-0 text-ink-faint" aria-hidden="true" />
      </button>

      {abierto && (
        <div role="menu" className="absolute right-0 z-40 mt-1.5 w-64 rounded-xl border border-line bg-panel p-1.5 shadow-xl">
          <div className="border-b border-line px-2.5 pb-2.5 pt-1.5">
            <p className="truncate text-sm font-medium text-ink">{usuario.nombre}</p>
            <p className="mt-0.5 text-xs text-ink-soft">
              Perfil <span className="font-medium text-ink">{PERFIL[usuario.perfil]}</span> · @{usuario.usuario}
            </p>
          </div>
          <button
            type="button"
            role="menuitem"
            onClick={() => {
              setAbierto(false)
              cerrarSesion()
            }}
            className="mt-1 flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-left text-sm text-ink transition-colors hover:bg-panel-muted"
          >
            <LogOut className="h-4 w-4 shrink-0 text-ink-soft" aria-hidden="true" />
            Cerrar sesión
          </button>
        </div>
      )}
    </div>
  )
}

export function Topbar({ onOpenMobileNav }: TopbarProps) {
  const location = useLocation()
  const title = findNavLabel(location.pathname)
  const { isGuided } = useExperience()

  return (
    <header className="sticky top-0 z-30 flex h-16 shrink-0 items-center gap-3 border-b border-line bg-panel/90 px-4 backdrop-blur sm:px-6 lg:px-8">
      <button
        type="button"
        onClick={onOpenMobileNav}
        aria-label="Abrir menú"
        className="text-ink-soft hover:text-ink lg:hidden"
      >
        <Menu className="h-5 w-5" aria-hidden="true" />
      </button>

      <h1 className="min-w-0 flex-1 truncate text-base font-semibold text-ink sm:text-lg">{title}</h1>

      {isGuided && (
        <span className="hidden items-center gap-1.5 rounded-full border border-accent/25 bg-accent-soft px-2.5 py-1 text-xs font-semibold text-accent sm:inline-flex" title="Puedes cambiar a modo experto desde Configuración">
          <Compass className="h-3.5 w-3.5" aria-hidden="true" />Modo guiado
        </span>
      )}

      <Link
        to="/?tab=alertas"
        title="Alertas"
        aria-label="Alertas de la planificación vigente"
        className="relative inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-line bg-panel text-ink-soft transition-colors hover:bg-panel-muted hover:text-ink"
      >
        <Bell className="h-[18px] w-[18px]" aria-hidden="true" />
      </Link>

      <MenuUsuario />
    </header>
  )
}
