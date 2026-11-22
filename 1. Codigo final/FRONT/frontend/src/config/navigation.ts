import type { LucideIcon } from 'lucide-react'
import {
  CalendarRange,
  UploadCloud,
  Cog,
  Database,
  AlertTriangle,
  History,
  FileText,
  Layers3,
  Boxes,
  Palette,
} from 'lucide-react'

/**
 * Punto de personalización principal del menú lateral.
 *
 * - Un item con `path` es una hoja: navega directo a esa ruta.
 * - Un item con `children` es un grupo desplegable: al hacer clic
 *   solo expande/colapsa, no navega. Cada hijo sí es una hoja con su
 *   propio ícono y ruta.
 *
 * Para agregar un apartado nuevo, alcanza con sumar (o editar) una
 * entrada acá — el Sidebar, el título del Topbar y el resaltado de
 * ruta activa se derivan todos de esta misma lista.
 *
 * Dentro de "Planificación semanal", las pestañas
 * (Calendario/Asignadas/Sobrantes/Bloqueadas/Arrastradas/Alertas/Bolsas) NO están acá — son pestañas internas de una sola
 * página (ver Tabs en PlanificacionSemanalPage), no rutas propias, tal
 * como las describe el documento ("Pestañas internas").
 */
export interface NavLeaf {
  id: string
  label: string
  icon: LucideIcon
  path: string
}

export interface NavGroup {
  id: string
  label: string
  icon: LucideIcon
  children: NavLeaf[]
}

export type NavItem = NavLeaf | NavGroup

export function isNavGroup(item: NavItem): item is NavGroup {
  return 'children' in item
}

/** Ruta de navegación (breadcrumb) de la página activa: [grupo, página] o solo [página]. */
export function findNavTrail(pathname: string): string[] {
  for (const item of navigation) {
    if (isNavGroup(item)) {
      const child = item.children.find((leaf) => leaf.path === pathname)
      if (child) return [item.label, child.label]
    } else if (item.path === pathname) {
      return [item.label]
    }
  }
  return ['Planificación Textil']
}

/** Resuelve el título de página (Topbar, <title>, breadcrumbs) a partir de la ruta activa. */
export function findNavLabel(pathname: string): string {
  for (const item of navigation) {
    if (isNavGroup(item)) {
      const child = item.children.find((leaf) => leaf.path === pathname)
      if (child) return child.label
    } else if (item.path === pathname) {
      return item.label
    }
  }
  return 'Planificación Textil'
}

export const navigation: NavItem[] = [
  { id: 'planificacion', label: 'Planificación semanal', icon: CalendarRange, path: '/' },
  { id: 'datos', label: 'Carga de datos', icon: UploadCloud, path: '/datos-semanales' },
  // Carbon: cada tipo de dato con su propio listado, filtros y acciones va en su propia página.
  {
    id: 'datos-registrados',
    label: 'Datos registrados',
    icon: Database,
    children: [
      { id: 'reg-pedidos', label: 'Pedidos', icon: FileText, path: '/datos-registrados/pedidos' },
      { id: 'reg-articulos', label: 'Artículos', icon: Layers3, path: '/datos-registrados/articulos' },
      { id: 'reg-partidas', label: 'Partidas', icon: Boxes, path: '/datos-registrados/partidas' },
      { id: 'reg-colores', label: 'Colores', icon: Palette, path: '/datos-registrados/colores' },
      { id: 'reg-proyeccion', label: 'Proyección de tejeduría', icon: CalendarRange, path: '/datos-registrados/proyeccion' },
    ],
  },
  { id: 'maquinas', label: 'Máquinas', icon: Cog, path: '/maquinas' },
  { id: 'incidencias', label: 'Incidencias y replanificación', icon: AlertTriangle, path: '/incidencias' },
  { id: 'historial', label: 'Historial', icon: History, path: '/historial' },
]
