import { Moon, Sun } from 'lucide-react'
import { useTheme } from '@/theme/theme-context'

export function ThemeToggle() {
  const { theme, toggleTheme } = useTheme()
  const isDark = theme === 'dark'

  return (
    <button
      type="button"
      onClick={toggleTheme}
      aria-pressed={isDark}
      aria-label={isDark ? 'Cambiar a modo día' : 'Cambiar a modo noche'}
      title={isDark ? 'Modo noche' : 'Modo día'}
      className="relative inline-flex h-9 w-9 shrink-0 items-center justify-center overflow-hidden rounded-lg border border-line bg-panel text-ink-soft transition-colors hover:bg-panel-muted hover:text-ink"
    >
      <Sun
        aria-hidden="true"
        className={`absolute h-[18px] w-[18px] transition-all duration-200 ${
          isDark ? 'scale-0 -rotate-90 opacity-0' : 'scale-100 rotate-0 opacity-100'
        }`}
      />
      <Moon
        aria-hidden="true"
        className={`absolute h-[18px] w-[18px] transition-all duration-200 ${
          isDark ? 'scale-100 rotate-0 opacity-100' : 'scale-0 rotate-90 opacity-0'
        }`}
      />
    </button>
  )
}
