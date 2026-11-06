import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import {
  AlertTriangle, Ban, CalendarCheck2, CalendarRange, CheckCircle2, CornerDownRight, Download, Droplets, Gauge, ListChecks,
  Loader2, PackageMinus, PieChart, Play, ShoppingBag, UploadCloud,
} from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { SoloLecturaAviso } from '@/components/common/SoloLecturaAviso'
import { AvisoError, Cargando, ListaAdvertencias } from '@/components/common/AvisoError'
import { Tabs, type TabItem } from '@/components/ui/Tabs'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { StatusPill } from '@/components/ui/StatusPill'
import { WeekSelector } from '@/components/planning/WeekSelector'
import { SummaryBar, type SummaryStat } from '@/components/planning/SummaryBar'
import { PlanningGrid, PlanningGridLegend } from '@/components/planning/PlanningGrid'
import { PartidasAsignadasDashboard } from '@/components/planning/PartidasAsignadasDashboard'
import { partidasDelPlan } from '@/components/planning/SobrantesTable'
import { ControlesPartidasPlan, ListaPartidasPlan, useFiltrosPartidasPlan } from '@/components/planning/FiltrosPartidasPlan'
import { FiltroLista } from '@/components/common/Filtros'
import { BloqueadasTable } from '@/components/planning/BloqueadasTable'
import { AlertasList } from '@/components/planning/AlertasList'
import { EmptyPlanState } from '@/components/planning/EmptyPlanState'
import { GuidedWorkflow } from '@/components/planning/GuidedWorkflow'
import { MENSAJE_SIN_PERMISO, useAuth } from '@/auth/auth-context'
import { useExperience } from '@/experience/experience-context'
import { useSemana } from '@/semana/semana-context'
import { planificacion } from '@/api/servicios'
import type { ErrorApi } from '@/api/cliente'
import type { Bolsa, GeneracionResponse, PlanificacionDetalle } from '@/api/tipos'
import { intentar, useDatos } from '@/lib/useDatos'
import { CLASIFICACION_RESULTADO, ESTADO_PLANIFICACION, TONO_PLANIFICACION, kg, numero } from '@/lib/etiquetas'
import { etiquetaSemana, fechaCorta, fechaHora, nombreDia } from '@/lib/fechas'
import { Paginado } from '@/components/ui/Paginacion'

const TABS: TabItem[] = [
  { id: 'calendario', label: 'Calendario', icon: CalendarRange },
  { id: 'asignadas', label: 'Partidas asignadas', icon: PieChart },
  { id: 'sobrantes', label: 'Partidas sobrantes', icon: PackageMinus },
  { id: 'bloqueadas', label: 'Partidas bloqueadas', icon: Ban },
  { id: 'arrastradas', label: 'Partidas arrastradas', icon: CornerDownRight },
  { id: 'alertas', label: 'Alertas', icon: AlertTriangle },
  { id: 'bolsas', label: 'Bolsas de tela', icon: ShoppingBag },
]

const btnBase = 'inline-flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-medium transition-colors disabled:pointer-events-none disabled:opacity-40'

export function PlanificacionSemanalPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const { esPlanificador } = useAuth()
  const { isGuided } = useExperience()
  const { lunes } = useSemana()
  const [activeTab, setActiveTab] = useState(searchParams.get('tab') ?? 'calendario')
  const [confirmando, setConfirmando] = useState<'generar' | 'cerrar' | null>(null)
  const [generando, setGenerando] = useState(false)
  const [error, setError] = useState<ErrorApi | null>(null)
  const [resultado, setResultado] = useState<GeneracionResponse | null>(null)
  const [mensaje, setMensaje] = useState<string | null>(null)
  const [revisadas, setRevisadas] = useState<Set<string>>(new Set())

  const estado = useDatos(() => planificacion.estadoSemana(lunes), [lunes])
  const idVigente = estado.datos?.planificacionVigente?.id ?? null
  const plan = useDatos<PlanificacionDetalle | null>(
    () => (idVigente ? planificacion.detalle(idVigente) : Promise.resolve(null)), [idVigente])
  const arrastradas = useDatos(() => planificacion.arrastradas(), [])

  useEffect(() => { setResultado(null); setMensaje(null); setError(null) }, [lunes])

  const datos = plan.datos
  const cerrada = datos?.estado === 'CERRADA'
  const filas = useMemo(() => (datos ? partidasDelPlan(datos.maquinas) : []), [datos])
  const sinProyeccion = estado.datos !== null && estado.datos.proyeccion === null

  function cambiarTab(id: string) {
    setActiveTab(id)
    const next = new URLSearchParams(searchParams)
    if (id === 'calendario') next.delete('tab')
    else next.set('tab', id)
    setSearchParams(next, { replace: true })
  }

  async function generar() {
    setConfirmando(null)
    setGenerando(true)
    setError(null)
    setMensaje(null)
    const r = await intentar(() => planificacion.generar(lunes))
    setGenerando(false)
    if (!r.ok) { setError(r.error); return }
    setResultado(r.valor)
    await estado.recargar()
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

  const stats: SummaryStat[] = datos ? [
    { id: 'asignadas', label: 'Partidas asignadas', value: String(datos.resumen.asignadas), icon: ListChecks },
    { id: 'sobrantes', label: 'Partidas sobrantes', value: String(datos.resumen.sobrantes), icon: PackageMinus, tone: datos.resumen.sobrantes > 0 ? 'warning' : undefined },
    { id: 'bloqueadas', label: 'Partidas bloqueadas', value: String(datos.resumen.bloqueadas), icon: Ban, tone: datos.resumen.bloqueadas > 0 ? 'danger' : undefined },
    { id: 'equilibradas', label: 'Máquinas en su objetivo', value: `${datos.resumen.maquinasEnObjetivo}/${datos.resumen.totalMaquinas}`, icon: Gauge, tone: datos.resumen.maquinasEnObjetivo === datos.resumen.totalMaquinas ? 'success' : 'warning' },
    { id: 'lavados', label: 'Lavados', value: String(datos.resumen.lavados), icon: Droplets },
  ] : []

  const asignadas = filas.filter((f) => f.clasificacion === 'ASIGNADA')
  const sobrantes = filas.filter((f) => f.clasificacion === 'SOBRANTE')
  const tabs = TABS.map((tab) => {
    if (!datos) return tab.id === 'arrastradas' ? { ...tab, count: arrastradas.datos?.length } : tab
    const conteo: Record<string, number | undefined> = {
      asignadas: asignadas.length, sobrantes: sobrantes.length, bloqueadas: datos.bloqueadas.length,
      arrastradas: arrastradas.datos?.length, alertas: datos.alertas.length,
    }
    return { ...tab, count: conteo[tab.id] }
  })

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
          <button type="button" onClick={() => setConfirmando('generar')} disabled={motivoGenerar !== null || generando}
            title={motivoGenerar ?? 'Ejecuta el algoritmo con los datos registrados de la semana'}
            className={`${btnBase} bg-accent text-accent-ink hover:opacity-90`}>
            {generando ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> : <Play className="h-4 w-4" aria-hidden="true" />}
            {generando ? 'Generando…' : datos ? 'Volver a generar' : 'Generar planificación'}
          </button>
          <button type="button" onClick={exportar} disabled={!datos} title={datos ? 'Descarga esta planificación en Excel' : 'No hay planificación vigente'}
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
      {estado.datos?.propuestaPendiente && (
        <div role="status" className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-warning/40 bg-warning-soft p-3.5 text-sm text-warning">
          <p className="flex items-center gap-2"><AlertTriangle className="h-4 w-4 shrink-0" aria-hidden="true" />
            Hay una re-planificación propuesta (versión {estado.datos.propuestaPendiente.numeroVersion}) sin confirmar. Mientras tanto no se puede volver a generar ni cerrar la semana.</p>
          <Link to="/incidencias" className={`${btnBase} border border-line bg-panel text-ink hover:bg-panel-muted`}>Revisar la propuesta</Link>
        </div>
      )}
      {estado.datos?.stockDesactualizado && !resultado && (
        <div role="alert" className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-warning/40 bg-warning-soft p-3.5 text-sm text-warning">
          <p className="flex items-center gap-2"><AlertTriangle className="h-4 w-4 shrink-0" aria-hidden="true" />{estado.datos.stockDesactualizado}</p>
          {esPlanificador && (
            <button type="button" onClick={() => setConfirmando('generar')} disabled={motivoGenerar !== null || generando}
              title={motivoGenerar ?? 'Vuelve a generar la planificación con el saldo actual de las bolsas'}
              className={`${btnBase} bg-accent text-accent-ink hover:opacity-90`}>
              <Play className="h-4 w-4" aria-hidden="true" />Generar
            </button>
          )}
        </div>
      )}
      {mensaje && <p role="status" className="mb-5 flex items-center gap-2 rounded-xl border border-success/40 bg-success-soft p-3.5 text-sm text-success"><CheckCircle2 className="h-4 w-4" />{mensaje}</p>}
      {resultado && (
        <div role="status" className="mb-5 rounded-xl border border-success/40 bg-success-soft p-4 text-sm text-success">
          <p className="flex items-center gap-2 font-medium"><CheckCircle2 className="h-4 w-4" />
            Versión {resultado.numeroVersion} generada y vigente, en {numero((resultado.indicadores.tiempoEjecucionMs ?? 0) / 1000, 1)} s
          </p>
          {resultado.detenidaPorTiempo && <p className="mt-1 text-warning">La búsqueda se detuvo al alcanzar el tiempo máximo de 60 s.</p>}
        </div>
      )}
      {resultado && <ListaAdvertencias titulo="Avisos de la generación" avisos={resultado.advertencias} />}

      {datos && (
        <div className="mb-4 flex flex-wrap items-center gap-3 text-sm text-ink-soft">
          <StatusPill tone={TONO_PLANIFICACION[datos.estado]} label={ESTADO_PLANIFICACION[datos.estado]} />
          <span>Versión {datos.numeroVersion} · generada el {fechaHora(datos.fechaGeneracion)} · proyección v{datos.versionProyeccion}</span>
          {datos.fechaCierre && <span>· cerrada el {fechaHora(datos.fechaCierre)}</span>}
        </div>
      )}
      {datos && <div className="mb-6"><SummaryBar stats={stats} /></div>}

      {estado.cargando && !estado.datos ? <Cargando /> : (
        <>
          <Tabs tabs={tabs} activeId={activeTab} onChange={cambiarTab} wrap />
          {plan.cargando && idVigente ? <Cargando texto="Cargando la planificación…" /> : (
            <ContenidoPestana tab={activeTab} plan={datos} sinProyeccion={sinProyeccion} asignadas={asignadas} sobrantes={sobrantes}
              arrastradas={arrastradas.datos ?? []} esPlanificador={esPlanificador} onCompletar={completarArrastrada} />
          )}
        </>
      )}

      <ConfirmDialog
        open={confirmando === 'generar'}
        title={datos ? 'Volver a generar la planificación' : 'Generar la planificación de la semana'}
        description={datos
          ? <>Se ejecuta el algoritmo con los datos actuales de la semana del {etiquetaSemana(lunes)}. La versión {datos.numeroVersion} deja de ser vigente (se conserva en el historial) y la nueva pasa a ser la planificación vigente.</>
          : <>Se ejecuta el algoritmo con los datos registrados de la semana del {etiquetaSemana(lunes)}. El resultado queda como la planificación vigente.</>}
        confirmLabel={datos ? 'Volver a generar' : 'Generar'}
        onConfirm={generar}
        onCancel={() => setConfirmando(null)}
      />
      {confirmando === 'cerrar' && datos && (
        <DialogoCierre plan={datos} onCancelar={() => setConfirmando(null)}
          onCerrado={async (texto) => { setConfirmando(null); setMensaje(texto); await Promise.all([estado.recargar(), arrastradas.recargar(), plan.recargar()]); cambiarTab('arrastradas') }}
          onError={(e) => { setConfirmando(null); setError(e) }} />
      )}
    </>
  )
}

interface ContenidoProps {
  tab: string
  plan: PlanificacionDetalle | null
  sinProyeccion: boolean
  asignadas: ReturnType<typeof partidasDelPlan>
  sobrantes: ReturnType<typeof partidasDelPlan>
  arrastradas: import('@/api/tipos').Arrastrada[]
  esPlanificador: boolean
  onCompletar: (numeroPartida: string) => void
}

function ContenidoPestana({ tab, plan, sinProyeccion, asignadas, sobrantes, arrastradas, esPlanificador, onCompletar }: ContenidoProps) {
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
    return <EmptyPlanState />
  }
  switch (tab) {
    case 'asignadas':
      return <div className="space-y-5"><PartidasAsignadasDashboard plan={plan} /><ListaPartidasPlan filas={asignadas} vacio="No hay partidas asignadas." /></div>
    case 'sobrantes':
      return <><p className="mb-4 text-sm text-ink-soft">Partidas conformadas que exceden el objetivo semanal de su máquina o llegaron cuando los días restantes ya estaban llenos. También aparecen en rojo en el calendario, en su primer día disponible.</p><ListaPartidasPlan filas={sobrantes} vacio="No hay partidas sobrantes." /></>
    case 'bloqueadas':
      return <><p className="mb-4 text-sm text-ink-soft">La bolsa de al menos uno de sus artículos tuvo kilos en la semana, pero no alcanzaron para completarlas. No consumen tela y se vuelven a evaluar la semana siguiente.</p><BloqueadasTable filas={plan.bloqueadas} /></>
    case 'alertas':
      return <AlertasList alertas={plan.alertas} />
    case 'bolsas':
      return <Bolsas idPlanificacion={plan.id} />
    default:
      return <><PlanningGridLegend /><PlanningGrid dias={plan.dias} maquinas={plan.maquinas} /></>
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
