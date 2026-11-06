import { useState } from 'react'
import { NavLink, useLocation, useNavigate } from 'react-router-dom'
import { BrainCircuit, ChevronDown, Compass, LogOut, Moon, PanelLeftClose, PanelLeftOpen, Settings, Sun, X } from 'lucide-react'
import { navigation, isNavGroup, type NavGroup, type NavLeaf } from '@/config/navigation'
import { useAuth } from '@/auth/auth-context'
import { useTheme } from '@/theme/theme-context'
import { useExperience } from '@/experience/experience-context'
import { PERFIL } from '@/lib/etiquetas'

interface SidebarProps {
  collapsed: boolean
  onToggleCollapsed: () => void
  mobileOpen: boolean
  onCloseMobile: () => void
}

function RolloTelaLogo() {
  return <svg viewBox="0 0 32 32" className="h-6 w-6" fill="none" aria-hidden="true"><ellipse cx="11" cy="9" rx="6" ry="4" stroke="currentColor" strokeWidth="2.2"/><path d="M5 9v12c0 2.2 2.7 4 6 4s6-1.8 6-4V9" stroke="currentColor" strokeWidth="2.2"/><ellipse cx="11" cy="9" rx="2.2" ry="1.4" fill="currentColor"/><path d="M17 10h6.5c1.9 0 3.5 1.6 3.5 3.5V27l-4-2-4 2V13.5c0-1.9-.8-3.5-2-3.5Z" fill="currentColor" opacity=".9"/></svg>
}

function groupsContainingPath(pathname: string): string[] {
  return navigation
    .filter((item): item is NavGroup => isNavGroup(item) && item.children.some((child) => child.path === pathname))
    .map((item) => item.id)
}

export function Sidebar({ collapsed, onToggleCollapsed, mobileOpen, onCloseMobile }: SidebarProps) {
  const location = useLocation()
  const { usuario, cerrarSesion, esPlanificador } = useAuth()
  const navigate = useNavigate()
  const { theme, toggleTheme } = useTheme()
  const { mode, setMode } = useExperience()
  const [configuracionVisible, setConfiguracionVisible] = useState(false)
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(
    () => new Set(groupsContainingPath(location.pathname)),
  )
  const [trackedPathname, setTrackedPathname] = useState(location.pathname)

  // Si la ruta activa cambia hacia un hijo de un grupo colapsado (por
  // ejemplo, un link desde otra página, o el botón "atrás" del navegador),
  // lo despliega para que el usuario siempre vea en qué sección está
  // parado. Se ajusta durante el render (patrón "adjusting state when a
  // prop changes" de React) en vez de con un efecto, para no perder un
  // frame mostrando el grupo todavía colapsado.
  if (location.pathname !== trackedPathname) {
    setTrackedPathname(location.pathname)
    const containing = groupsContainingPath(location.pathname)
    if (containing.length > 0) {
      setExpandedGroups((prev) => new Set([...prev, ...containing]))
    }
  }

  function handleGroupClick(group: NavGroup) {
    if (collapsed) {
      onToggleCollapsed()
      setExpandedGroups((prev) => new Set(prev).add(group.id))
      return
    }
    setExpandedGroups((prev) => {
      const next = new Set(prev)
      if (next.has(group.id)) {
        next.delete(group.id)
      } else {
        next.add(group.id)
      }
      return next
    })
  }

  function leafClasses({ isActive }: { isActive: boolean }) {
    return `flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
      isActive ? 'bg-white text-[#002F6D] shadow-sm' : 'text-white/80 hover:bg-white/10 hover:text-white'
    }`
  }

  function renderLeaf(item: NavLeaf) {
    const Icon = item.icon
    return (
      <NavLink
        key={item.id}
        to={item.path}
        end
        className={leafClasses}
        onClick={onCloseMobile}
        title={collapsed ? item.label : undefined}
      >
        <Icon className="h-[18px] w-[18px] shrink-0" aria-hidden="true" />
        <span className={collapsed ? 'sr-only' : 'truncate'}>{item.label}</span>
      </NavLink>
    )
  }

  function renderGroup(item: NavGroup) {
    const Icon = item.icon
    const expanded = expandedGroups.has(item.id) && !collapsed
    const hasActiveChild = item.children.some((child) => child.path === location.pathname)

    return (
      <div key={item.id}>
        <button
          type="button"
          onClick={() => handleGroupClick(item)}
          aria-expanded={expanded}
          title={collapsed ? item.label : undefined}
          className={`flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
            hasActiveChild ? 'text-white' : 'text-white/80 hover:bg-white/10 hover:text-white'
          }`}
        >
          <Icon className="h-[18px] w-[18px] shrink-0" aria-hidden="true" />
          <span className={collapsed ? 'sr-only' : 'flex-1 truncate text-left'}>{item.label}</span>
          {!collapsed && (
            <ChevronDown
              className={`h-4 w-4 shrink-0 transition-transform ${expanded ? 'rotate-180' : ''}`}
              aria-hidden="true"
            />
          )}
        </button>
        {expanded && (
          <div className="ml-[22px] mt-1 flex flex-col gap-0.5 border-l border-white/25 pl-3">
            {item.children.map((child) => renderLeaf(child))}
          </div>
        )}
      </div>
    )
  }

  return (
    <>
      {mobileOpen && (
        <button
          type="button"
          aria-label="Cerrar menú"
          onClick={onCloseMobile}
          className="fixed inset-0 z-40 bg-black/50 lg:hidden"
        />
      )}

      <aside
        className={`fixed inset-y-0 left-0 z-50 flex w-[260px] flex-col border-r border-[#002F6D] bg-[#002F6D] text-white transition-transform duration-200 ease-in-out lg:sticky lg:top-0 lg:h-dvh lg:translate-x-0 lg:transition-[width] ${
          collapsed ? 'lg:w-[76px]' : 'lg:w-[260px]'
        } ${mobileOpen ? 'translate-x-0' : '-translate-x-full'}`}
      >
        <div className="flex h-16 shrink-0 items-center justify-between gap-2 border-b border-white/20 px-4">
          <div className="flex min-w-0 items-center gap-2">
            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-white text-[#002F6D] shadow-sm">
              <RolloTelaLogo />
            </span>
            <span className={collapsed ? 'sr-only' : 'truncate text-lg font-bold tracking-[0.18em] text-white'}>PAT</span>
          </div>

          <button
            type="button"
            onClick={onCloseMobile}
            aria-label="Cerrar menú"
            className="text-white/75 hover:text-white lg:hidden"
          >
            <X className="h-5 w-5" aria-hidden="true" />
          </button>
          <button
            type="button"
            onClick={onToggleCollapsed}
            aria-label={collapsed ? 'Expandir menú' : 'Colapsar menú'}
            className="hidden shrink-0 text-white/75 hover:text-white lg:block"
          >
            {collapsed ? (
              <PanelLeftOpen className="h-5 w-5" aria-hidden="true" />
            ) : (
              <PanelLeftClose className="h-5 w-5" aria-hidden="true" />
            )}
          </button>
        </div>

        <nav aria-label="Navegación principal" className="flex-1 space-y-1 overflow-y-auto px-3 py-4">
          {navigation.map((item) => (isNavGroup(item) ? renderGroup(item) : renderLeaf(item)))}
        </nav>
        {usuario && (
          <div className="shrink-0 border-t border-white/20 p-3">
            {!collapsed && (
              <div className="mb-2 min-w-0 px-2">
                <p className="truncate text-sm font-medium text-white">{usuario.nombre}</p>
                <p className="text-xs text-white/60">{PERFIL[usuario.perfil]}</p>
                {configuracionVisible && (
                  <div className="mt-3 space-y-3 rounded-xl border border-white/20 bg-white/10 p-2.5">
                    {esPlanificador && <div>
                    <p className="mb-2 text-[10px] font-semibold uppercase tracking-wide text-white/60">Experiencia de uso</p>
                    <div className="grid grid-cols-2 gap-1 rounded-lg bg-[#001f49] p-1" role="group" aria-label="Modo de experiencia">
                      <button type="button" onClick={() => { setMode('guided'); navigate('/') }} aria-pressed={mode === 'guided'} className={`flex flex-col items-center gap-1 rounded-md px-2 py-2 text-[11px] font-semibold transition-colors ${mode === 'guided' ? 'bg-white text-[#002F6D] shadow-sm' : 'text-white/70 hover:text-white'}`}>
                        <Compass className="h-4 w-4" aria-hidden="true" />Guiado
                      </button>
                      <button type="button" onClick={() => setMode('expert')} aria-pressed={mode === 'expert'} className={`flex flex-col items-center gap-1 rounded-md px-2 py-2 text-[11px] font-semibold transition-colors ${mode === 'expert' ? 'bg-white text-[#002F6D] shadow-sm' : 'text-white/70 hover:text-white'}`}>
                        <BrainCircuit className="h-4 w-4" aria-hidden="true" />Experto
                      </button>
                    </div>
                    <p className="mt-2 text-[11px] leading-4 text-white/65">{mode === 'guided' ? 'Te acompañamos paso a paso, sin cambiar tus permisos.' : 'Acceso directo a todas las herramientas.'}</p>
                    </div>}
                    <div className={esPlanificador ? 'border-t border-white/15 pt-3' : ''}>
                    <p className="mb-2 text-[10px] font-semibold uppercase tracking-wide text-white/60">Apariencia</p>
                    <button type="button" onClick={toggleTheme} className="flex w-full items-center justify-between gap-3 rounded-md bg-white px-2.5 py-2 text-left text-xs font-medium text-[#002F6D] hover:bg-white/90">
                      <span className="flex items-center gap-2">{theme === 'dark' ? <Moon className="h-4 w-4" /> : <Sun className="h-4 w-4" />}Tema de pantalla</span>
                      <span>{theme === 'dark' ? 'Oscuro' : 'Claro'}</span>
                    </button>
                    </div>
                  </div>
                )}
              </div>
            )}
            <button type="button" onClick={() => { if (collapsed) onToggleCollapsed(); setConfiguracionVisible((visible) => !visible) }} title="Configuración" className="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm text-white/75 hover:bg-white/10 hover:text-white">
              <Settings className="h-[18px] w-[18px] shrink-0" /><span className={collapsed ? 'sr-only' : ''}>Configuración</span>
            </button>
            <button type="button" onClick={cerrarSesion} title="Cerrar sesión" className="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm text-white/75 hover:bg-white/10 hover:text-white">
              <LogOut className="h-[18px] w-[18px] shrink-0" /><span className={collapsed ? 'sr-only' : ''}>Cerrar sesión</span>
            </button>
          </div>
        )}
      </aside>
    </>
  )
}
