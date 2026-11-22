import { Navigate, Route, Routes, useSearchParams } from 'react-router-dom'
import { ThemeProvider } from '@/theme/ThemeProvider'
import { AuthProvider } from '@/auth/AuthProvider'
import { useAuth } from '@/auth/auth-context'
import { ExperienceProvider } from '@/experience/ExperienceProvider'
import { SemanaProvider } from '@/semana/SemanaProvider'
import { MainLayout } from '@/components/layout/MainLayout'
import { AvisosProvider } from '@/avisos/AvisosProvider'
import { LoginPage } from '@/pages/LoginPage'
import { PlanificacionSemanalPage } from '@/pages/PlanificacionSemanalPage'
import { DatosSemanalesPage } from '@/pages/DatosSemanalesPage'
import { ArticulosPage, ColoresPage, PartidasPage, PedidosPage, ProyeccionPage } from '@/pages/DatosRegistradosPage'
import { MaquinasPage } from '@/pages/MaquinasPage'
import { IncidenciasPage } from '@/pages/IncidenciasPage'
import { HistorialPage } from '@/pages/HistorialPage'

/**
 * El árbol de rutas refleja src/config/navigation.ts: cada `path` del menú tiene su <Route>. Las
 * pestañas internas (Calendario, Sobrantes, Bloqueadas…) son estado de la página, no rutas.
 * Sin sesión no se monta ninguna otra pantalla (RF01).
 */
/** Enlaces antiguos (/datos-registrados?tab=partidas) llevan a la página propia de cada tipo de dato. */
function RedirigirDatosRegistrados() {
  const [params] = useSearchParams()
  const tab = params.get('tab')
  const destino = tab && ['pedidos', 'articulos', 'partidas', 'colores', 'proyeccion'].includes(tab) ? tab : 'pedidos'
  return <Navigate to={`/datos-registrados/${destino}`} replace />
}

function PanelAutenticado() {
  const { usuario } = useAuth()
  if (!usuario) return <LoginPage />

  return (
    <ExperienceProvider>
      <SemanaProvider>
        <AvisosProvider>
        <MainLayout>
          <Routes>
            <Route path="/" element={<PlanificacionSemanalPage />} />
            <Route path="/datos-semanales" element={<DatosSemanalesPage />} />
            <Route path="/datos-registrados" element={<RedirigirDatosRegistrados />} />
            <Route path="/datos-registrados/pedidos" element={<PedidosPage />} />
            <Route path="/datos-registrados/articulos" element={<ArticulosPage />} />
            <Route path="/datos-registrados/partidas" element={<PartidasPage />} />
            <Route path="/datos-registrados/colores" element={<ColoresPage />} />
            <Route path="/datos-registrados/proyeccion" element={<ProyeccionPage />} />
            <Route path="/maquinas" element={<MaquinasPage />} />
            <Route path="/incidencias" element={<IncidenciasPage />} />
            <Route path="/historial" element={<HistorialPage />} />
          </Routes>
        </MainLayout>
        </AvisosProvider>
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
