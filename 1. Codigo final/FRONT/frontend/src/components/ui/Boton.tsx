import type { ButtonHTMLAttributes } from 'react'

/**
 * Botones con la jerarquía de Carbon Design System:
 * - primary: la acción principal del área (una sola por bloque).
 * - secondary: acción alternativa junto a la principal (Cancelar, Descargar).
 * - tertiary: acción importante dentro de una tarjeta, con borde (Subir archivo).
 * - ghost: acción de menor peso, sin borde (Descargar plantilla, Ver registros).
 * - danger: acción destructiva confirmada.
 */
export type VarianteBoton = 'primary' | 'secondary' | 'tertiary' | 'ghost' | 'danger'
export type TamanoBoton = 'sm' | 'md' | 'lg'

const VARIANTE: Record<VarianteBoton, string> = {
  primary: 'bg-accent text-accent-ink hover:opacity-90',
  secondary: 'border border-line bg-panel text-ink hover:bg-panel-muted',
  tertiary: 'border border-accent bg-transparent text-accent hover:bg-accent hover:text-accent-ink',
  ghost: 'bg-transparent text-accent hover:bg-panel-muted',
  danger: 'bg-danger text-white hover:opacity-90',
}

/** Alturas de Carbon: sm 32 px, md 40 px, lg 48 px. */
const TAMANO: Record<TamanoBoton, string> = {
  sm: 'h-8 px-3 text-sm',
  md: 'h-10 px-4 text-sm',
  lg: 'h-12 px-4 text-sm',
}

export function claseBoton(variante: VarianteBoton = 'primary', tamano: TamanoBoton = 'md') {
  return `inline-flex shrink-0 items-center justify-center gap-2 rounded-lg font-medium whitespace-nowrap transition-colors disabled:pointer-events-none disabled:opacity-40 ${VARIANTE[variante]} ${TAMANO[tamano]}`
}

interface BotonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variante?: VarianteBoton
  tamano?: TamanoBoton
}

export function Boton({ variante = 'primary', tamano = 'md', className = '', type = 'button', ...props }: BotonProps) {
  return <button type={type} className={`${claseBoton(variante, tamano)} ${className}`} {...props} />
}
