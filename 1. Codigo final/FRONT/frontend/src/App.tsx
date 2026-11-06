import { Route, Routes } from 'react-router-dom'
import { ThemeProvider } from '@/theme/ThemeProvider'
import { AuthProvider } from '@/auth/AuthProvider'
import { useAuth } from '@/auth/auth-context'
import { ExperienceProvider } from '@/experience/ExperienceProvider'
import { SemanaProvider } from '@/semana/SemanaProvider'
import { MainLayout } from '@/components/layout/MainLayout'
import { LoginPage } from '@/pages/LoginPage'
import { PlanificacionSemanalPage } from '@/pages/PlanificacionSemanalPage'
import { DatosSemanalesPage } from '@/pages/DatosSemanalesPage'
import { DatosRegistradosPage } from '@/pages/DatosRegistradosPage'
import { MaquinasPage } from '@/pages/MaquinasPage'
import { IncidenciasPage } from '@/pages/IncidenciasPage'
import { HistorialPage } from '@/pages/HistorialPage'

/**
 * El árbol de rutas refleja src/config/navigation.ts: cada `path` del menú tiene su <Route>. Las
 * pestañas internas (Calendario, Sobrantes, Bloqueadas…) son estado de la página, no rutas.
 * Sin sesión no se monta ninguna otra pantalla (RF01).
 */
function PanelAutenticado() {
  const { usuario } = useAuth()
  if (!usuario) return <LoginPage />

  return (
    <ExperienceProvider>
      <SemanaProvider>
        <MainLayout>
          <Routes>
            <Route path="/" element={<PlanificacionSemanalPage />} />
            <Route path="/datos-semanales" element={<DatosSemanalesPage />} />
            <Route path="/datos-registrados" element={<DatosRegistradosPage />} />
            <Route path="/maquinas" element={<MaquinasPage />} />
            <Route path="/incidencias" element={<IncidenciasPage />} />
            <Route path="/historial" element={<HistorialPage />} />
          </Routes>
        </MainLayout>
      </SemanaProvider>
    </ExperienceProvider>
  )
}

function App() {
  return (
    <ThemeProvider>
      <AuthProvider>
        <PanelAutenticado />
      </AuthProvider>
    </ThemeProvider>
  )
}

export default App
