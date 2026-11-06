import { useState } from 'react'
import { CircleAlert, Loader2, LogIn } from 'lucide-react'
import { Field, inputClass } from '@/components/ui/FormField'
import { ThemeToggle } from '@/components/ui/ThemeToggle'
import { useAuth } from '@/auth/auth-context'
import fondoTextil from '@/assets/fondo-login-textil.png'

function RolloTelaLogo() {
  return (
    <svg viewBox="0 0 32 32" className="h-8 w-8" fill="none" aria-hidden="true">
      <ellipse cx="11" cy="9" rx="6" ry="4" stroke="currentColor" strokeWidth="2.2" />
      <path d="M5 9v12c0 2.2 2.7 4 6 4s6-1.8 6-4V9" stroke="currentColor" strokeWidth="2.2" />
      <ellipse cx="11" cy="9" rx="2.2" ry="1.4" fill="currentColor" />
      <path d="M17 10h6.5c1.9 0 3.5 1.6 3.5 3.5V27l-4-2-4 2V13.5c0-1.9-.8-3.5-2-3.5Z" fill="currentColor" opacity=".9" />
    </svg>
  )
}

/** Inicio de sesión con credenciales individuales (HU-01). La validación la hace la API. */
export function LoginPage() {
  const { iniciarSesion, sesionExpirada } = useAuth()
  const [usuario, setUsuario] = useState('')
  const [contrasena, setContrasena] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  async function enviar(event: React.FormEvent) {
    event.preventDefault()
    if (!usuario.trim() || !contrasena) {
      setError('Ingresa usuario y contraseña para continuar.')
      return
    }
    setEnviando(true)
    setError(await iniciarSesion(usuario, contrasena))
    setEnviando(false)
  }

  return (
    <div className="relative flex min-h-dvh items-center justify-center overflow-hidden bg-[#061426] px-4 py-10">
      <img src={fondoTextil} alt="" className="absolute inset-0 h-full w-full object-cover" aria-hidden="true" />
      <div className="absolute inset-0 bg-[#001a3d]/70 backdrop-blur-[1px] dark:bg-black/75" aria-hidden="true" />

      <div className="absolute right-4 top-4 z-10 rounded-xl bg-panel/95 p-1 shadow-lg backdrop-blur">
        <ThemeToggle />
      </div>

      <main className="relative z-10 w-full max-w-md">
        <div className="login-card rounded-[2.75rem] border border-white/70 bg-white p-7 shadow-2xl sm:px-10 sm:py-9">
          <div className="mb-7 flex flex-col items-center text-center">
            <span className="mb-3 flex h-14 w-14 items-center justify-center rounded-2xl bg-[#002F6D] text-white shadow-lg">
              <RolloTelaLogo />
            </span>
            <h1 className="text-3xl font-bold tracking-[0.18em] text-ink">PAT</h1>
            <p className="mt-2 text-sm text-ink-soft">Ingresa con tu cuenta para continuar.</p>
          </div>

          <form onSubmit={enviar}>
            {sesionExpirada && (
              <p role="status" className="mb-4 rounded-lg border border-warning/40 bg-warning-soft p-3 text-sm text-warning">
                La sesión se cerró por inactividad. Ingresa nuevamente para continuar.
              </p>
            )}
            <div className="flex flex-col gap-4">
              <Field label="Usuario">
                <input type="text" autoComplete="username" value={usuario} onChange={(event) => { setUsuario(event.target.value); setError(null) }} className={inputClass} />
              </Field>
              <Field label="Contraseña">
                <input type="password" autoComplete="current-password" value={contrasena} onChange={(event) => { setContrasena(event.target.value); setError(null) }} className={inputClass} />
              </Field>
              {error && (
                <p role="alert" className="flex items-start gap-2 text-sm text-danger">
                  <CircleAlert className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                  {error}
                </p>
              )}
              <button type="submit" disabled={enviando} className="inline-flex items-center justify-center gap-1.5 rounded-lg bg-accent px-3 py-2.5 text-sm font-medium text-accent-ink transition-opacity hover:opacity-90 disabled:opacity-60">
                {enviando ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> : <LogIn className="h-4 w-4" aria-hidden="true" />}
                Ingresar
              </button>
            </div>
          </form>
        </div>
      </main>
    </div>
  )
}
