import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import {
  AlertTriangle, Ban, CalendarCheck2, CalendarRange, CornerDownRight, Download, Info,
  Loader2, PackageMinus, PieChart, Play, RefreshCw, Scissors, ShoppingBag, UploadCloud,
} from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { SoloLecturaAviso } from '@/components/common/SoloLecturaAviso'
import { AvisoError, Cargando } from '@/components/common/AvisoError'
import { useAvisos } from '@/avisos/avisos-context'
import { Tabs, type TabItem } from '@/components/ui/Tabs'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { StatusPill } from '@/components/ui/StatusPill'
import { WeekSelector } from '@/components/planning/WeekSelector'
import { IndicadoresPlan } from '@/components/planning/IndicadoresPlan'
import { CalendarioPlanificacion, EtiquetaVersion } from '@/components/planning/CalendarioPlanificacion'
import {
  BarraIndicadoresVersiones, DialogoCambioVigente, DialogoPropuestaVersion, MAXIMO_MARCADAS, PanelVersiones,
} from '@/components/planning/VersionesProyeccion'
import { PropuestaReplanificacion } from '@/components/incidencias/PropuestaReplanificacion'
import { RecomendacionesTejido } from '@/components/planning/RecomendacionesTejido'
import { PartidasAsignadasDashboard } from '@/components/planning/PartidasAsignadasDashboard'
import { partidasDelPlan, type FilaPartidaPlan } from '@/components/planning/SobrantesTable'
import { ControlesPartidasPlan, ListaPartidasPlan, useFiltrosPartidasPlan } from '@/components/planning/FiltrosPartidasPlan'
import { FiltroLista } from '@/components/common/Filtros'
import { BloqueadasTable, type BloqueadaConVersion } from '@/components/planning/BloqueadasTable'
import { AlertasList } from '@/components/planning/AlertasList'
import { EmptyPlanState } from '@/components/planning/EmptyPlanState'
import { GuidedWorkflow } from '@/components/planning/GuidedWorkflow'
import { MENSAJE_SIN_PERMISO, useAuth } from '@/auth/auth-context'
import { useExperience } from '@/experience/experience-context'
import { useSemana } from '@/semana/semana-context'
import { planificacion, replanificacion } from '@/api/servicios'
import type { ErrorApi } from '@/api/cliente'
import type {
  Bolsa, ContextoCambioVigente, GeneracionResponse, PlanificacionDetalle, ReplanificacionResponse, VersionProyeccionPlan,
  VersionesSemana,
} from '@/api/tipos'
import { intentar, useDatos } from '@/lib/useDatos'
import { CLASIFICACION_RESULTADO, ESTADO_PLANIFICACION, TONO_PLANIFICACION, kg, numero } from '@/lib/etiquetas'
import { ahoraLocal, etiquetaSemana, fechaCorta, fechaHora, nombreDia } from '@/lib/fechas'
import { asignarColoresVersiones, compararVersiones, etiquetaVersion, unirPorVersion, type CalendarioComparado, type VersionMarcada } from '@/lib/versiones'
import { Paginado } from '@/components/ui/Paginacion'

const TABS: TabItem[] = [
  { id: 'calendario', label: 'Calendario', icon: CalendarRange },
  { id: 'asignadas', label: 'Partidas asignadas', icon: PieChart },
  { id: 'sobrantes', label: 'Partidas sobrantes', icon: PackageMinus },
  { id: 'bloqueadas', label: 'Partidas bloqueadas', icon: Ban },
  { id: 'recomendaciones', label: 'Recomendaciones', icon: Scissors },
  { id: 'arrastradas', label: 'Partidas arrastradas', icon: CornerDownRight },
  { id: 'alertas', label: 'Alertas', icon: AlertTriangle },
  { id: 'bolsas', label: 'Bolsas de tela', icon: ShoppingBag },
]

const btnBase = 'inline-flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-medium transition-colors disabled:pointer-events-none disabled:opacity-40'

/** Partidas conformadas de las versiones marcadas; la misma partida en la misma máquina y día va una sola vez. */
function filasConVersion(vistas: VersionMarcada[], conEtiqueta: boolean, clasificacion: 'ASIGNADA' | 'SOBRANTE'): FilaPartidaPlan[] {
  const listas = vistas.map((v) => ({ version: v.version, filas: partidasDelPlan(v.plan.maquinas).filter((f) => f.clasificacion === clasificacion) }))
  if (!conEtiqueta) return listas[0]?.filas ?? []
  return unirPorVersion(listas, (f) => `${f.numeroPartida}|${f.maquina}|${f.dia}`).map((x) => ({ ...x.fila, versiones: x.versiones }))
}

function bloqueadasConVersion(vistas: VersionMarcada[], conEtiqueta: boolean): BloqueadaConVersion[] {
  if (!conEtiqueta) return vistas[0]?.plan.bloqueadas ?? []
  return unirPorVersion(vistas.map((v) => ({ version: v.version, filas: v.plan.bloqueadas })), (b) => `${b.numeroPartida}|${b.motivo ?? ''}`)
    .map((x) => ({ ...x.fila, versiones: x.versiones }))
}

export function PlanificacionSemanalPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const { esPlanificador } = useAuth()
  const { isGuided } = useExperience()
  const { lunes } = useSemana()
  const [activeTab, setActiveTab] = useState(searchParams.get('tab') ?? 'calendario')
  const [confirmando, setConfirmando] = useState<'generar' | 'cerrar' | null>(null)
  /** Versión que se está generando: 'vigente' desde el botón principal o el número de la versión del panel. */
  const [generando, setGenerando] = useState<'vigente' | number | null>(null)
  /** D-52: versión que se genera sin vigente en la semana; queda como vigente, así que se confirma antes. */
  const [versionPorGenerar, setVersionPorGenerar] = useState<VersionProyeccionPlan | null>(null)
  const [error, setError] = useState<ErrorApi | null>(null)
  const [resultado, setResultado] = useState<GeneracionResponse | null>(null)
  const [mensaje, setMensaje] = useState<string | null>(null)
  const avisar = useAvisos()
  // Los mensajes de resultado salen como aviso flotante abajo a la derecha, no fijos en la pantalla.
  useEffect(() => { if (mensaje) { avisar({ tipo: 'success', titulo: mensaje }); setMensaje(null) } }, [mensaje, avisar])
  const [revisadas, setRevisadas] = useState<Set<string>>(new Set())
  const [marcadas, setMarcadas] = useState<number[]>([])
  const [recargas, setRecargas] = useState(0)
  const [cambio, setCambio] = useState<ContextoCambioVigente | null>(null)
  const [enviando, setEnviando] = useState(false)
  const [propuesta, setPropuesta] = useState<{ version: number; resultado: ReplanificacionResponse } | null>(null)

  const estado = useDatos(() => planificacion.estadoSemana(lunes), [lunes])
  const idVigente = estado.datos?.planificacionVigente?.id ?? null
  const plan = useDatos<PlanificacionDetalle | null>(
    () => (idVigente ? planificacion.detalle(idVigente) : Promise.resolve(null)), [idVigente, recargas])
  const arrastradas = useDatos(() => planificacion.arrastradas(), [])
  // D-52: versiones de la proyección con su planificación. El usuario Consulta no ve el panel: solo la vigente.
  const versiones = useDatos<VersionesSemana | null>(
    () => (esPlanificador ? planificacion.versionesProyeccion(lunes) : Promise.resolve(null)), [lunes, esPlanificador, recargas])

  useEffect(() => { setResultado(null); setMensaje(null); setError(null); setMarcadas([]) }, [lunes])

  const datos = plan.datos
  const cerrada = datos?.estado === 'CERRADA'
  /** La semana ya empezó (fecha local): volver a generar rehace también los días pasados. */
  const semanaEmpezada = ahoraLocal().slice(0, 10) >= lunes
  const sinProyeccion = estado.datos !== null && estado.datos.proyeccion === null
  const vs = versiones.datos?.lunes === lunes ? versiones.datos : null
  // Cada versión no eliminada de la semana tiene su propio color (hay tantos colores como versiones permitidas).
  if (vs) asignarColoresVersiones(vs.versiones.map((v) => v.version))
  const mostrarPanel = vs !== null && !vs.cerrada && vs.versiones.length >= 2

  // Versiones marcadas: como máximo dos y solo las que tienen planificación. Al abrir, la vigente.
  const marcadasValidas = useMemo(() => {
    if (!mostrarPanel || !vs) return []
    const conPlan = vs.versiones.filter((v) => v.planificacion !== null)
    const validas = marcadas.filter((n) => conPlan.some((v) => v.version === n)).slice(0, MAXIMO_MARCADAS)
    if (validas.length > 0) return validas
    const inicial = conPlan.find((v) => v.planificacion?.esVigente) ?? conPlan[0]
    return inicial ? [inicial.version] : []
  }, [mostrarPanel, vs, marcadas])
  const idsMarcadas = marcadasValidas.map((n) => vs?.versiones.find((v) => v.version === n)?.planificacion?.id).filter((id): id is number => id !== undefined)
  const claveMarcadas = idsMarcadas.join(',')
  const detalles = useDatos<PlanificacionDetalle[]>(
    () => Promise.all(claveMarcadas ? claveMarcadas.split(',').map((id) => planificacion.detalle(Number(id))) : []), [claveMarcadas, recargas])

  const vistas: VersionMarcada[] = useMemo(() => {
    if (!mostrarPanel) return datos ? [{ version: datos.versionProyeccion, plan: datos }] : []
    const cargados = detalles.datos ?? []
    return idsMarcadas.flatMap((id) => {
      const d = cargados.find((x) => x.id === id)
      return d ? [{ version: d.versionProyeccion, plan: d }] : []
    }).sort((a, b) => a.version - b.version)   // siempre V1, V2... en orden de versión
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mostrarPanel, datos, detalles.datos, claveMarcadas])
  const calendario = useMemo(() => compararVersiones(vistas), [vistas])
  const principal = vistas[0]?.plan ?? null
  const cargandoVistas = mostrarPanel ? detalles.cargando && vistas.length < idsMarcadas.length : plan.cargando && idVigente !== null

  const asignadas = useMemo(() => filasConVersion(vistas, vistas.length > 1, 'ASIGNADA'), [vistas])
  const sobrantes = useMemo(() => filasConVersion(vistas, vistas.length > 1, 'SOBRANTE'), [vistas])
  const bloqueadas = useMemo(() => bloqueadasConVersion(vistas, vistas.length > 1), [vistas])

  // Banner: hay una versión nueva de la proyección sin planificación; la vigente sigue basada en otra.
  const nuevaSinGenerar = mostrarPanel && datos && vs
    ? [...vs.versiones].reverse().find((v) => v.planificacion === null && v.version > datos.versionProyeccion) ?? null
    : null
  const vistaSinVigente = mostrarPanel && datos !== null && vistas.length > 0 && !vistas.some((v) => v.plan.esVigente)

  function cambiarTab(id: string) {
    setActiveTab(id)
    const next = new URLSearchParams(searchParams)
    if (id === 'calendario') next.delete('tab')
    else next.set('tab', id)
    setSearchParams(next, { replace: true })
  }

  async function recargarTodo() {
    setRecargas((n) => n + 1)
    await estado.recargar()
  }

  /** Genera con la versión vigente (botón principal) o con una versión del panel (D-52). */
  async function generar(version?: VersionProyeccionPlan) {
    setConfirmando(null)
    setVersionPorGenerar(null)
    setGenerando(version ? version.version : 'vigente')
    setError(null)
    setMensaje(null)
    const r = await intentar(() => planificacion.generar(lunes, version?.idProyeccion))
    setGenerando(null)
    if (!r.ok) { setError(r.error); return }
    setResultado(r.valor)
    const segundos = numero((r.valor.indicadores.tiempoEjecucionMs ?? 0) / 1000, 1)
    avisar({
      tipo: 'success',
      titulo: r.valor.vigente
        ? `Versión ${r.valor.numeroVersion} generada con la ${etiquetaVersion(r.valor.versionProyeccion)} y vigente, en ${segundos} s`
        : `Versión ${r.valor.numeroVersion} generada con la ${etiquetaVersion(r.valor.versionProyeccion)} en ${segundos} s`,
      detalle: <>
        {!r.valor.vigente && <p>No es la vigente: se marcó junto a ella para compararlas. Para usarla, elige «Usar como vigente».</p>}
        {r.valor.detenidaPorTiempo && <p className="text-warning">La búsqueda se detuvo al alcanzar el tiempo máximo de 60 s.</p>}
      </>,
      segundos: r.valor.vigente ? 6 : 10,
    })
    if (r.valor.advertencias.length > 0) avisar({ tipo: 'warning', titulo: 'Avisos de la generación', lista: r.valor.advertencias })
    // Una versión no vigente se marca junto a la vigente para compararlas
    if (!r.valor.vigente && datos) setMarcadas([datos.versionProyeccion, r.valor.versionProyeccion])
    else setMarcadas([r.valor.versionProyeccion])
    await recargarTodo()
  }

  function generarVersion(v: VersionProyeccionPlan) {
    if (idVigente === null) setVersionPorGenerar(v)   // quedará como vigente: se confirma (RF59)
    else void generar(v)
  }

  function alternarVersion(version: number) {
    setMarcadas(() => {
      const actuales = marcadasValidas
      if (actuales.includes(version)) return actuales.length > 1 ? actuales.filter((n) => n !== version) : actuales
      return actuales.length >= MAXIMO_MARCADAS ? actuales : [...actuales, version]
    })
  }

  async function abrirCambioVigente(v: VersionProyeccionPlan) {
    if (!v.planificacion) return
    setError(null)
    const r = await intentar(() => planificacion.contextoCambioVigente(v.planificacion!.id))
    if (!r.ok) { setError(r.error); return }
    setCambio(r.valor)
  }

  async function usarComoVigente(partidasEnMaquina: string[]) {
    if (!cambio) return
    const c = cambio
    setEnviando(true)
    const r = await intentar(() => planificacion.usarComoVigente(c.idCandidata, partidasEnMaquina))
    setEnviando(false)
    setCambio(null)
    if (!r.ok) { setError(r.error); return }
    if (r.valor.modo === 'DIRECTO') {
      setResultado(null)
      setMensaje(`La ${etiquetaVersion(c.versionProyeccion)} pasó a ser la planificación vigente (versión ${r.valor.numeroVersion}).`
        + (c.versionProyeccionVigente ? ` La ${etiquetaVersion(c.versionProyeccionVigente)} quedó guardada; puedes volver a ella.` : ''))
      setMarcadas([c.versionProyeccion])
    } else if (r.valor.replanificacion) {
      setPropuesta({ version: c.versionProyeccion, resultado: r.valor.replanificacion })
    }
    await recargarTodo()
  }

  async function confirmarPropuesta() {
    if (!propuesta?.resultado.idPropuesta) return
    const p = propuesta
    setEnviando(true)
    const r = await intentar(() => replanificacion.confirmar(p.resultado.idPropuesta!))
    setEnviando(false)
    setPropuesta(null)
    if (!r.ok) { setError(r.error); return }
    setResultado(null)
    setMensaje(`La ${etiquetaVersion(p.version)} es ahora la planificación vigente (versión ${r.valor.numeroVersion}), re-planificada desde el ${fechaCorta(p.resultado.dia)}.`)
    setMarcadas([p.version])
    await recargarTodo()
  }

  async function descartarPropuesta() {
    if (!propuesta?.resultado.idPropuesta) return
    const id = propuesta.resultado.idPropuesta
    setEnviando(true)
    const r = await intentar(() => replanificacion.descartar(id))
    setEnviando(false)
    setPropuesta(null)
    if (!r.ok) { setError(r.error); return }
    setMensaje('Se descartó la re-planificación. La vigente no cambió.')
    await recargarTodo()
  }

  async function exportar() {
    if (!datos) return
    const r = await intentar(() => planificacion.exportar(datos.id))
    if (!r.ok) setError(r.error)
  }

  async function completarArrastrada(numeroPartida: string) {
    const r = await intentar(() => planificacion.completarArrastrada(numeroPartida))
    if (!r.ok) { setError(r.error); return }
    setMensaje(`La partida ${numeroPartida} se marcó como completada.`)
    await arrastradas.recargar()
  }

  function motivoGenerarDeshabilitado(): string | null {
    if (!esPlanificador) return MENSAJE_SIN_PERMISO
    if (sinProyeccion) return 'Primero carga la proyección de tejeduría de esta semana'
    if (cerrada) return 'La planificación de esta semana ya se cerró'
    if (estado.datos?.propuestaPendiente) return 'Hay una re-planificación propuesta sin confirmar'
    return null
  }
  const motivoGenerar = motivoGenerarDeshabilitado()

  const tabs = TABS.map((tab) => {
    if (!principal) return tab.id === 'arrastradas' ? { ...tab, count: arrastradas.datos?.length } : tab
    const conteo: Record<string, number | undefined> = {
      asignadas: asignadas.length, sobrantes: sobrantes.length, bloqueadas: bloqueadas.length,
      arrastradas: arrastradas.datos?.length, alertas: principal.alertas.length,
    }
    return { ...tab, count: conteo[tab.id] }
  })

  const panel = mostrarPanel && vs ? (
    <PanelVersiones versiones={vs.versiones} marcadas={marcadasValidas} onAlternar={alternarVersion}
      onGenerar={generarVersion} onUsarComoVigente={(v) => void abrirCambioVigente(v)}
      generando={typeof generando === 'number' ? generando : null}
      bloqueado={generando !== null ? 'Se está generando una planificación' : estado.datos?.propuestaPendiente ? 'Hay una re-planificación propuesta sin confirmar' : null} />
  ) : undefined

  return (
    <>
      <PageHeader title="Planificación de abastecimiento semanal de tintorería"
        description="Asignación de partidas a máquinas de tintorería, día por día, a partir de la proyección de tejeduría." />
      <SoloLecturaAviso />

      {isGuided && esPlanificador && estado.datos && (
        <GuidedWorkflow
          needsData={sinProyeccion && !datos}
          hasPlan={datos !== null}
          isReviewed={revisadas.has(lunes) || cerrada}
          isClosed={cerrada}
          onPlan={() => setConfirmando('generar')}
          onReview={() => setRevisadas((prev) => new Set(prev).add(lunes))}
          onClose={() => setConfirmando('cerrar')}
        />
      )}

      <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <WeekSelector />
        <div className="flex flex-wrap items-center gap-2">
          <button type="button" onClick={() => setConfirmando('generar')} disabled={motivoGenerar !== null || generando !== null}
            title={motivoGenerar ?? (datos ? `Vuelve a generar la planificación vigente con su proyección (${etiquetaVersion(datos.versionProyeccion)})` : 'Ejecuta el algoritmo con los datos registrados de la semana')}
            className={`${btnBase} bg-accent text-accent-ink hover:opacity-90`}>
            {generando === 'vigente' ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> : <Play className="h-4 w-4" aria-hidden="true" />}
            {generando === 'vigente' ? 'Generando…' : datos ? 'Volver a generar' : 'Generar planificación'}
          </button>
          <button type="button" onClick={exportar} disabled={!datos} title={datos ? 'Descarga la planificación vigente en Excel' : 'No hay planificación vigente'}
            className={`${btnBase} border border-line bg-panel text-ink hover:bg-panel-muted`}>
            <Download className="h-4 w-4" aria-hidden="true" />Exportar
          </button>
          <button type="button" onClick={() => setConfirmando('cerrar')} disabled={!esPlanificador || !datos || cerrada || !!estado.datos?.propuestaPendiente}
            title={!esPlanificador ? MENSAJE_SIN_PERMISO : cerrada ? 'La semana ya está cerrada' : estado.datos?.propuestaPendiente ? 'Hay una re-planificación propuesta sin confirmar' : 'Confirma las partidas completadas y cierra la semana'}
            className={`${btnBase} border border-line bg-panel text-ink hover:bg-panel-muted`}>
            <CalendarCheck2 className="h-4 w-4" aria-hidden="true" />Cerrar planificación
          </button>
        </div>
      </div>

      <AvisoError error={error ?? estado.error ?? (plan.error?.status === 404 ? null : plan.error)} onCerrar={() => setError(null)} />
      {estado.datos?.propuestaPendiente && !propuesta && estado.datos.planificacionVigente && (
        <section aria-labelledby="titulo-propuesta" className="mb-6 rounded-2xl border border-warning/40 bg-panel p-5">
          <h2 id="titulo-propuesta" className="mb-1 flex items-center gap-2 text-base font-semibold text-ink"><RefreshCw className="h-5 w-5 text-warning" aria-hidden="true" />
            Re-planificación propuesta por las incidencias</h2>
          <p className="mb-4 text-sm text-ink-soft">Revisa qué cambia frente a la planificación vigente y confírmala o descártala hoy. Mientras tanto no se puede volver a generar ni cerrar la semana. Las incidencias se registran en <Link to="/incidencias" className="text-accent hover:underline">Incidencias</Link>.</p>
          <PropuestaReplanificacion lunes={lunes} idVigente={estado.datos.planificacionVigente.id} idPropuesta={estado.datos.propuestaPendiente.id}
            numeroVersion={estado.datos.propuestaPendiente.numeroVersion} resultado={null} esPlanificador={esPlanificador}
            onTerminar={async (texto) => { if (texto) setMensaje(texto); await recargarTodo() }} onError={setError} />
        </section>
      )}
      {datos && !cerrada && (estado.datos?.maquinasCambiadas.length ?? 0) > 0 && (
        <div role="status" className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-accent/30 bg-accent-soft p-3.5 text-sm text-ink">
          <p className="flex items-center gap-2"><Info className="h-4 w-4 shrink-0 text-accent" aria-hidden="true" />
            <span>Cambiaste las tonalidades que acepta {estado.datos!.maquinasCambiadas.join(', ')} después de generar esta planificación.
              {semanaEmpezada ? ' La semana ya empezó: el cambio se aplica desde la próxima planificación.' : ' Vuelve a generar para aplicarlo.'}</span></p>
          {!semanaEmpezada && esPlanificador && (
            <button type="button" onClick={() => setConfirmando('generar')} disabled={motivoGenerar !== null || generando !== null}
              title={motivoGenerar ?? 'Vuelve a generar la semana con la configuración actual de las máquinas'}
              className={`${btnBase} bg-accent text-accent-ink hover:opacity-90`}><Play className="h-4 w-4" aria-hidden="true" />Volver a generar</button>
          )}
        </div>
      )}
      {nuevaSinGenerar && datos && (
        <div role="status" className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-accent/30 bg-accent-soft p-3.5 text-sm text-ink">
          <p className="flex items-center gap-2"><Info className="h-4 w-4 shrink-0 text-accent" aria-hidden="true" />
            <span>Subiste una proyección nueva (<EtiquetaVersion versiones={[nuevaSinGenerar.version]} className="text-sm" />). La planificación que ves sigue basada en la <EtiquetaVersion versiones={[datos.versionProyeccion]} className="text-sm" />.</span></p>
          <button type="button" onClick={() => generarVersion(nuevaSinGenerar)} disabled={motivoGenerar !== null || generando !== null}
            title={motivoGenerar ?? `Genera la planificación de la ${etiquetaVersion(nuevaSinGenerar.version)} sin cambiar la vigente`}
            className={`${btnBase} bg-accent text-accent-ink hover:opacity-90`}>
            {generando === nuevaSinGenerar.version ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> : <Play className="h-4 w-4" aria-hidden="true" />}
            Generar {etiquetaVersion(nuevaSinGenerar.version)}
          </button>
        </div>
      )}
      {estado.datos?.stockDesactualizado && !resultado && (
        <div role="alert" className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-warning/40 bg-warning-soft p-3.5 text-sm text-warning">
          <p className="flex items-center gap-2"><AlertTriangle className="h-4 w-4 shrink-0" aria-hidden="true" />{estado.datos.stockDesactualizado}</p>
          {esPlanificador && (
            <button type="button" onClick={() => setConfirmando('generar')} disabled={motivoGenerar !== null || generando !== null}
              title={motivoGenerar ?? 'Vuelve a generar la planificación con el saldo actual de las bolsas'}
              className={`${btnBase} bg-accent text-accent-ink hover:opacity-90`}>
              <Play className="h-4 w-4" aria-hidden="true" />Generar
            </button>
          )}
        </div>
      )}
      {datos && (
        <div className="mb-4 flex flex-wrap items-center gap-3 text-sm text-ink-soft">
          <StatusPill tone={TONO_PLANIFICACION[datos.estado]} label={ESTADO_PLANIFICACION[datos.estado]} />
          <span>Versión {datos.numeroVersion} · generada el {fechaHora(datos.fechaGeneracion)} · proyección <EtiquetaVersion versiones={[datos.versionProyeccion]} className="text-sm" /></span>
          {datos.fechaCierre && <span>· cerrada el {fechaHora(datos.fechaCierre)}</span>}
        </div>
      )}
      {vistaSinVigente && datos && (
        <p role="status" className="mb-4 flex items-center gap-2 rounded-xl border border-line bg-panel-muted/60 p-3 text-sm text-ink-soft">
          <Info className="h-4 w-4 shrink-0 text-accent" aria-hidden="true" />
          Estás viendo una versión que no es la vigente. Exportar, el cierre y el usuario Consulta usan la vigente (<EtiquetaVersion versiones={[datos.versionProyeccion]} className="text-sm" />).
        </p>
      )}
      {vistas.length > 1
        ? <div className="mb-6"><BarraIndicadoresVersiones marcadas={vistas} /></div>
        : principal && <div className="mb-6"><IndicadoresPlan plan={principal} /></div>}

      {estado.cargando && !estado.datos ? <Cargando /> : (
        <>
          <Tabs tabs={tabs} activeId={activeTab} onChange={cambiarTab} wrap />
          {cargandoVistas ? <Cargando texto="Cargando la planificación…" /> : (
            <ContenidoPestana tab={activeTab} plan={principal} calendario={calendario} panel={panel} sinProyeccion={sinProyeccion}
              asignadas={asignadas} sobrantes={sobrantes} bloqueadas={bloqueadas} versionesVisibles={vistas.length > 1 ? vistas.map((v) => v.version) : []}
              arrastradas={arrastradas.datos ?? []} esPlanificador={esPlanificador} onCompletar={completarArrastrada}
              recomendaciones={<RecomendacionesTejido lunes={lunes} esPlanificador={esPlanificador} hayVigente={!!datos}
                versiones={vistas.map((v) => ({ version: v.version, idPlanificacion: v.plan.id }))} />} />
          )}
        </>
      )}

      <ConfirmDialog
        open={confirmando === 'generar'}
        title={datos ? 'Volver a generar la planificación' : 'Generar la planificación de la semana'}
        description={datos
          ? <>Se ejecuta el algoritmo con los datos actuales de la semana del {etiquetaSemana(lunes)} y la proyección de la vigente ({etiquetaVersion(datos.versionProyeccion)}). La versión {datos.numeroVersion} deja de ser vigente (se conserva en el historial) y la nueva pasa a ser la planificación vigente.</>
          : <>Se ejecuta el algoritmo con los datos registrados de la semana del {etiquetaSemana(lunes)}. El resultado queda como la planificación vigente.</>}
        confirmLabel={datos ? 'Volver a generar' : 'Generar'}
        onConfirm={() => void generar()}
        onCancel={() => setConfirmando(null)}
      />
      <ConfirmDialog
        open={versionPorGenerar !== null}
        title={`Generar la planificación de la ${versionPorGenerar ? etiquetaVersion(versionPorGenerar.version) : ''}`}
        description={<>La semana del {etiquetaSemana(lunes)} no tiene planificación vigente, así que el resultado quedará como la vigente.</>}
        confirmLabel="Generar"
        onConfirm={() => versionPorGenerar && void generar(versionPorGenerar)}
        onCancel={() => setVersionPorGenerar(null)}
      />
      {cambio && <DialogoCambioVigente contexto={cambio} enviando={enviando} onConfirmar={(p) => void usarComoVigente(p)} onCancelar={() => setCambio(null)} />}
      {propuesta && <DialogoPropuestaVersion version={propuesta.version} resultado={propuesta.resultado} enviando={enviando}
        onConfirmar={() => void confirmarPropuesta()} onDescartar={() => void descartarPropuesta()} onDespues={() => setPropuesta(null)} />}
      {confirmando === 'cerrar' && datos && (
        <DialogoCierre plan={datos} onCancelar={() => setConfirmando(null)}
          onCerrado={async (texto) => { setConfirmando(null); setMensaje(texto); setMarcadas([]); await Promise.all([recargarTodo(), arrastradas.recargar()]); cambiarTab('arrastradas') }}
          onError={(e) => { setConfirmando(null); setError(e) }} />
      )}
    </>
  )
}

interface ContenidoProps {
  tab: string
  plan: PlanificacionDetalle | null
  calendario: CalendarioComparado
  panel: ReactNode
  sinProyeccion: boolean
  asignadas: FilaPartidaPlan[]
  sobrantes: FilaPartidaPlan[]
  bloqueadas: BloqueadaConVersion[]
  /** Versiones mostradas cuando hay panel de versiones; vacío con una sola proyección. */
  versionesVisibles: number[]
  arrastradas: import('@/api/tipos').Arrastrada[]
  esPlanificador: boolean
  onCompletar: (numeroPartida: string) => void
  /** Pestaña Recomendaciones (RF30, D-56), armada con las versiones marcadas. */
  recomendaciones: ReactNode
}

function NotaVersion({ versiones, texto }: { versiones: number[]; texto: string }) {
  if (versiones.length === 0) return null
  return <p className="mb-3 flex items-center gap-1.5 text-xs text-ink-faint">{texto} <EtiquetaVersion versiones={versiones} className="text-xs" /></p>
}

function ContenidoPestana({ tab, plan, calendario, panel, sinProyeccion, asignadas, sobrantes, bloqueadas, versionesVisibles, arrastradas, esPlanificador, onCompletar, recomendaciones }: ContenidoProps) {
  if (tab === 'arrastradas') return <Arrastradas filas={arrastradas} esPlanificador={esPlanificador} onCompletar={onCompletar} />
  if (!plan) {
    if (tab === 'calendario' && sinProyeccion) return (
      <div className="rounded-2xl border border-dashed border-line bg-panel px-6 py-14 text-center">
        <span className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-panel-muted text-ink-faint"><UploadCloud className="h-6 w-6" /></span>
        <h3 className="mt-4 font-semibold text-ink">La semana todavía no tiene proyección de tejeduría</h3>
        <p className="mx-auto mt-2 max-w-lg text-sm leading-6 text-ink-soft">Carga la proyección de la semana; después se podrá generar la planificación.</p>
        <Link to="/datos-semanales" className="mt-5 inline-flex items-center gap-1.5 rounded-lg bg-accent px-4 py-2.5 text-sm font-semibold text-accent-ink hover:opacity-90">Ir a carga de datos</Link>
      </div>
    )
    return <>{panel && <div className="mb-3 flex flex-wrap items-center gap-2">{panel}</div>}<EmptyPlanState /></>
  }
  const primera = versionesVisibles.slice(0, 1)
  switch (tab) {
    case 'asignadas':
      return <div className="space-y-5"><NotaVersion versiones={primera} texto="Resumen de la versión" /><PartidasAsignadasDashboard plan={plan} /><ListaPartidasPlan filas={asignadas} vacio="No hay partidas asignadas." /></div>
    case 'sobrantes':
      return <><p className="mb-4 text-sm text-ink-soft">Partidas conformadas que exceden el objetivo semanal de su máquina o llegaron cuando los días restantes ya estaban llenos. También aparecen en rojo en el calendario, en su primer día disponible.{versionesVisibles.length > 1 && ' La columna Versión indica en cuál lo es; si está igual en ambas, aparece una sola vez.'}</p><ListaPartidasPlan filas={sobrantes} vacio="No hay partidas sobrantes." /></>
    case 'bloqueadas':
      return <><p className="mb-4 text-sm text-ink-soft">La bolsa de al menos uno de sus artículos tuvo kilos en la semana, pero no alcanzaron para completarlas. No consumen tela y se vuelven a evaluar la semana siguiente.{versionesVisibles.length > 1 && ' La columna Versión indica en cuál está bloqueada.'}</p><BloqueadasTable filas={bloqueadas} /></>
    case 'recomendaciones':
      return recomendaciones
    case 'alertas':
      return <><NotaVersion versiones={primera} texto="Alertas de la versión" /><AlertasList alertas={plan.alertas} /></>
    case 'bolsas':
      return <><NotaVersion versiones={primera} texto="Bolsas de la versión" /><Bolsas idPlanificacion={plan.id} /></>
    default:
      return <CalendarioPlanificacion calendario={calendario} panel={panel} />
  }
}

function Arrastradas({ filas, esPlanificador, onCompletar }: { filas: import('@/api/tipos').Arrastrada[]; esPlanificador: boolean; onCompletar: (n: string) => void }) {
  return (
    <div className="space-y-4">
      <p className="text-sm text-ink-soft">Lista única de las partidas arrastradas de cualquier semana, sin importar la semana seleccionada: estuvieron en una planificación cerrada como Asignadas o Sobrantes y no se confirmaron como completadas. Su tela ya se usó; no participan en nuevas planificaciones ni en sus indicadores. Al marcarla como completada sale de la lista; la historia de cada semana se consulta en su planificación cerrada, en el historial.</p>
      {filas.length === 0
        ? <div className="rounded-xl border border-line bg-panel p-8 text-center text-sm text-ink-soft">No hay partidas arrastradas pendientes.</div>
        : <Paginado items={filas}>{(pagina) => <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">{pagina.map((a) => (
            <article key={a.numeroPartida} className="rounded-xl border border-line bg-panel p-4">
              <div className="flex items-center justify-between"><strong className="font-mono text-ink">{a.numeroPartida}</strong><span className="text-xs text-ink-soft">{CLASIFICACION_RESULTADO[a.clasificacionOrigen]}</span></div>
              <p className="mt-2 text-sm text-ink-soft">Pedido {a.numeroOp} · {a.color} · {kg(a.kg)}</p>
              <p className="text-xs text-ink-faint">{a.maquina} · {a.dia ? `${nombreDia(a.dia)} ${fechaCorta(a.dia)}` : '—'} </p>
              <p className="text-xs text-ink-faint">Cerrada en la semana del {etiquetaSemana(a.semanaCierre)} · {a.semanasArrastrada === 1 ? 'lleva 1 semana arrastrada' : `lleva ${a.semanasArrastrada} semanas arrastrada`}</p>
              {esPlanificador && <button type="button" onClick={() => onCompletar(a.numeroPartida)} className="mt-4 rounded-lg bg-accent px-3 py-2 text-sm font-medium text-accent-ink">Marcar como completada</button>}
            </article>
          ))}</div>}</Paginado>}
    </div>
  )
}

/** Saldo de cada bolsa de artículo por día (bolsa_articulo), tal como se guardó con la versión. */
function Bolsas({ idPlanificacion }: { idPlanificacion: number }) {
  const { datos, error, cargando } = useDatos(() => planificacion.bolsas(idPlanificacion), [idPlanificacion])
  const [filtro, setFiltro] = useState('')
  if (cargando) return <Cargando />
  if (error) return <AvisoError error={error} />
  const lista: Bolsa[] = (datos ?? []).filter((b) => !filtro || `${b.codigoArticulo} ${b.nombreArticulo}`.toLowerCase().includes(filtro.toLowerCase()))
  return (
    <div className="space-y-3">
      <p className="text-sm text-ink-soft">La tela de un artículo se acumula en su bolsa, sin importar el pedido para el que se tejió. El saldo final del domingo pasa a la semana siguiente.</p>
      <input value={filtro} onChange={(e) => setFiltro(e.target.value)} placeholder="Filtrar por artículo…" className="w-full rounded-lg border border-line bg-panel px-3 py-2 text-sm sm:max-w-xs" />
      <Paginado items={lista} reinicio={filtro}>{(pagina) => <div className="overflow-x-auto rounded-xl border border-line bg-panel">
        <table className="w-full min-w-[820px] text-left text-sm">
          <thead><tr className="border-b border-line text-xs uppercase text-ink-faint"><th className="px-4 py-3">Artículo</th><th className="px-4 py-3">Día</th><th className="px-4 py-3 text-right">Saldo inicial</th><th className="px-4 py-3 text-right">Ingresos</th><th className="px-4 py-3 text-right" title="Tela que volvió a la bolsa en una re-planificación">Devuelto</th><th className="px-4 py-3 text-right">Consumo</th><th className="px-4 py-3 text-right">Saldo final</th></tr></thead>
          <tbody>{pagina.map((b) => (
            <tr key={b.codigoArticulo + b.fecha} className="border-b border-line last:border-0">
              <td className="px-4 py-2"><span className="font-mono text-ink">{b.codigoArticulo}</span><span className="block text-xs text-ink-faint">{b.nombreArticulo}</span></td>
              <td className="px-4 py-2 text-ink-soft">{nombreDia(b.fecha)} {fechaCorta(b.fecha)}</td>
              <td className="px-4 py-2 text-right font-mono">{numero(b.kgSaldoInicial, 3)}</td>
              <td className="px-4 py-2 text-right font-mono text-success">{numero(b.kgIngresados, 3)}</td>
              <td className="px-4 py-2 text-right font-mono text-ink-soft">{b.kgDevueltos > 0 ? numero(b.kgDevueltos, 3) : '—'}</td>
              <td className="px-4 py-2 text-right font-mono text-danger">{numero(b.kgConsumidos, 3)}</td>
              <td className="px-4 py-2 text-right font-mono font-semibold">{numero(b.kgSaldoFinal, 3)}</td>
            </tr>
          ))}</tbody>
        </table>
      </div>}</Paginado>
    </div>
  )
}

/** Cierre de la semana (HU-15): el planificador confirma qué Asignadas y Sobrantes se completaron. */
function DialogoCierre({ plan, onCancelar, onCerrado, onError }: {
  plan: PlanificacionDetalle
  onCancelar: () => void
  onCerrado: (mensaje: string) => void
  onError: (e: ErrorApi) => void
}) {
  const conformadas = useMemo(() => partidasDelPlan(plan.maquinas), [plan])
  const [seleccion, setSeleccion] = useState<Set<string>>(() => new Set(conformadas.map((p) => p.numeroPartida)))
  const [enviando, setEnviando] = useState(false)
  const filtrado = useFiltrosPartidasPlan(conformadas)
  const [marca, setMarca] = useState('')
  const visibles = filtrado.visibles.filter((p) => !marca || (marca === 'SI') === seleccion.has(p.numeroPartida))
  const hayFiltros = filtrado.filtros.activos || marca !== ''

  function cambiarVisibles(marcar: boolean) {
    setSeleccion((prev) => {
      const next = new Set(prev)
      for (const p of visibles) { if (marcar) next.add(p.numeroPartida); else next.delete(p.numeroPartida) }
      return next
    })
  }

  async function cerrar() {
    setEnviando(true)
    const r = await intentar(() => planificacion.cerrar(plan.id, [...seleccion]))
    setEnviando(false)
    if (!r.ok) { onError(r.error); return }
    onCerrado(`Planificación cerrada: ${r.valor.completadas} completadas, ${r.valor.arrastradas} arrastradas`
      + (r.valor.pedidosCompletados > 0 ? ` y ${r.valor.pedidosCompletados} pedidos completados.` : '.'))
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div role="dialog" aria-modal="true" aria-labelledby="cierre-title" className="flex h-[92vh] w-full max-w-5xl flex-col rounded-2xl border border-line bg-panel p-5 shadow-xl">
        <h2 id="cierre-title" className="text-lg font-semibold text-ink">Cerrar la planificación semanal</h2>
        <p className="mt-2 text-sm text-ink-soft">Marca las partidas que realmente se completaron. Las demás pasan a Arrastrada y seguirán visibles en las próximas semanas. Las bloqueadas no cambian. Esta acción no se puede deshacer.</p>
        <div className="mt-4">
          <ControlesPartidasPlan estado={filtrado} filas={conformadas} conClasificacion visibles={visibles.length} extraActivo={marca !== ''} onLimpiarExtra={() => setMarca('')}
            extra={<FiltroLista etiqueta="Selección" valor={marca} onChange={setMarca} opciones={[{ valor: 'SI', texto: 'Completadas (marcadas)' }, { valor: 'NO', texto: 'Sin completar (sin marcar)' }]} />} />
        </div>
        <div className="flex gap-3">
          <button type="button" onClick={() => cambiarVisibles(true)} disabled={visibles.length === 0} className="text-sm font-medium text-accent disabled:opacity-40">{hayFiltros ? `Marcar estas ${visibles.length}` : 'Seleccionar todas'}</button>
          <button type="button" onClick={() => cambiarVisibles(false)} disabled={visibles.length === 0} className="text-sm font-medium text-accent disabled:opacity-40">{hayFiltros ? `Desmarcar estas ${visibles.length}` : 'Quitar selección'}</button>
        </div>
        <Paginado items={visibles} reinicio={[filtrado.filtros.valores, marca]}>{(pagina) => <div className="mt-3 min-h-48 flex-1 overflow-y-auto rounded-lg border border-line">
          {visibles.length === 0 && <p className="px-3 py-8 text-center text-sm text-ink-faint">Ninguna partida coincide con los filtros.</p>}
          {(['ASIGNADA', 'SOBRANTE'] as const).filter((c) => pagina.some((p) => p.clasificacion === c)).map((c) => (
            <div key={c}>
              <h3 className="sticky top-0 bg-panel-muted px-3 py-2 text-sm font-semibold text-ink">{c === 'ASIGNADA' ? 'Partidas asignadas' : 'Partidas sobrantes'}</h3>
              {pagina.filter((p) => p.clasificacion === c).map((p) => (
                <label key={p.numeroPartida} className="flex cursor-pointer items-center gap-3 border-t border-line px-3 py-2 text-sm text-ink">
                  <input type="checkbox" checked={seleccion.has(p.numeroPartida)} onChange={(e) => setSeleccion((prev) => { const next = new Set(prev); if (e.target.checked) next.add(p.numeroPartida); else next.delete(p.numeroPartida); return next })} />
                  <span className="font-mono font-semibold">{p.numeroPartida}</span>
                  <span className="min-w-0 truncate text-ink-soft">{p.color} · {kg(p.kg)} · {p.maquina} · {nombreDia(p.dia)}</span>
                </label>
              ))}
            </div>
          ))}
        </div>}</Paginado>
        <p className="mt-3 text-sm text-ink-soft">Al cerrar: {seleccion.size} completadas · {conformadas.length - seleccion.size} arrastradas{hayFiltros && ' (cuenta todas las partidas, también las que los filtros ocultan)'}</p>
        <div className="mt-4 flex justify-end gap-2">
          <button type="button" autoFocus onClick={onCancelar} className="rounded-lg border border-line px-3 py-2 text-sm text-ink">Cancelar</button>
          <button type="button" onClick={cerrar} disabled={enviando} className="inline-flex items-center gap-1.5 rounded-lg bg-accent px-3 py-2 text-sm font-medium text-accent-ink disabled:opacity-50">
            {enviando && <Loader2 className="h-4 w-4 animate-spin" />}Cerrar planificación
          </button>
        </div>
      </div>
    </div>
  )
}
