import { useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { CalendarDays, Check, ClipboardCheck, ClipboardList, Database, History, Layers3, RotateCcw, Trash2, TriangleAlert, Weight } from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { SoloLecturaAviso } from '@/components/common/SoloLecturaAviso'
import { AvisoError } from '@/components/common/AvisoError'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { StatusPill, type StatusTone } from '@/components/ui/StatusPill'
import { Notificacion } from '@/components/ui/Notificacion'
import { useAvisos } from '@/avisos/avisos-context'
import { MenuAcciones, type AccionMenu } from '@/components/ui/MenuAcciones'
import { claseBoton } from '@/components/ui/Boton'
import { FilaVacia, TablaCargando, TablaDatos, celda, filaTabla } from '@/components/ui/TablaDatos'
import { WeekSelector } from '@/components/planning/WeekSelector'
import { UploadCard, type ColorTipoDato } from '@/components/planning/UploadCard'
import { useAuth } from '@/auth/auth-context'
import { useExperience } from '@/experience/experience-context'
import { useSemana } from '@/semana/semana-context'
import { cargas, datos } from '@/api/servicios'
import type { ErrorApi } from '@/api/cliente'
import type { CargaHistorial, CargaResponse, TipoDato, VersionProyeccion } from '@/api/tipos'
import { intentar, useDatos } from '@/lib/useDatos'
import { TIPO_DATO, kg } from '@/lib/etiquetas'
import { etiquetaSemana, fechaHora } from '@/lib/fechas'
import { Paginado } from '@/components/ui/Paginacion'
import { MAXIMO_VERSIONES } from '@/lib/versiones'
import type { LucideIcon } from 'lucide-react'

interface TarjetaTipo { titulo: string; descripcion: string; ayuda: string; icono: LucideIcon; color: ColorTipoDato }

const TARJETA: Record<TipoDato, TarjetaTipo> = {
  PEDIDOS: {
    titulo: 'Pedidos y artículos', icono: ClipboardList, color: 'pedidos',
    descripcion: 'Datos acumulativos de pedidos y sus artículos.',
    ayuda: 'Pedidos, clientes y los artículos de cada pedido. Carga acumulable: lo ya registrado se conserva y solo se agregan los registros nuevos.',
  },
  PARTIDAS: {
    titulo: 'Partidas', icono: Layers3, color: 'partidas',
    descripcion: 'Partidas con su color, tonalidad, barca y componentes.',
    ayuda: 'Partidas con su color, tonalidad, barca y componentes (tela principal y complementos). Usa los pedidos y artículos ya registrados, así que cárgalas después de los pedidos. Carga acumulable.',
  },
  PROYECCION_TEJEDURIA: {
    titulo: 'Proyección de tejeduría', icono: CalendarDays, color: 'proyeccion',
    descripcion: 'Kilos de tejeduría por pedido-artículo y día de la semana elegida.',
    ayuda: 'Kilos que tejeduría tejerá por pedido-artículo y día, del sábado anterior al viernes. Cada carga crea una versión nueva (V1, V2…) de la proyección de la semana elegida, sin borrar las anteriores ni cambiar la planificación vigente. Cada versión se planifica aparte en Planificación.',
  },
}

const TIPOS: TipoDato[] = ['PEDIDOS', 'PARTIDAS', 'PROYECCION_TEJEDURIA']

const ESTADO_VERSION: Record<VersionProyeccion['estado'], { tone: StatusTone; label: string }> = {
  VIGENTE: { tone: 'success', label: 'Vigente' },
  ELIMINADA: { tone: 'danger', label: 'Eliminada' },
  CARGADA: { tone: 'neutral', label: 'Cargada' },
}

const PERIODOS = [{ valor: '', texto: 'Todo el historial' }, { valor: '7', texto: 'Últimos 7 días' }, { valor: '30', texto: 'Últimos 30 días' }]
const claseSelect = 'h-10 rounded-lg border border-line bg-panel px-3 text-sm text-ink'

/** Carga de datos por plantillas .xlsx (HU-04, HU-05) y versiones de la proyección de la semana. */
export function DatosSemanalesPage() {
  const { esPlanificador } = useAuth()
  const { isGuided } = useExperience()
  const { lunes } = useSemana()
  const [subiendo, setSubiendo] = useState<TipoDato | null>(null)
  const [error, setError] = useState<ErrorApi | null>(null)
  const [errorTipo, setErrorTipo] = useState<TipoDato | null>(null)
  const [resultado, setResultado] = useState<CargaResponse | null>(null)
  const [porEliminar, setPorEliminar] = useState<CargaHistorial | null>(null)
  const [versionPorEliminar, setVersionPorEliminar] = useState<VersionProyeccion | null>(null)
  // El resultado de cada acción sale como aviso flotante abajo a la derecha y se cierra solo.
  const avisar = useAvisos()
  const [filtroTipo, setFiltroTipo] = useState('')
  const [periodo, setPeriodo] = useState('')
  const historial = useDatos(() => cargas.historial(), [])
  const versiones = useDatos(() => datos.versionesProyeccion(lunes), [lunes])

  function limpiarAvisos() {
    setError(null)
    setErrorTipo(null)
    setResultado(null)
  }

  /** D-52: una proyección nueva se agrega como otra versión; no cambia la planificación vigente ni pide confirmación. */
  /** Con el máximo de versiones no eliminadas no se carga ni se restaura otra: avisa y pide eliminar una. */
  function sinEspacioParaVersion() {
    const activas = (versiones.datos ?? []).filter((v) => v.estado !== 'ELIMINADA').length
    if (activas < MAXIMO_VERSIONES) return false
    avisar({
      tipo: 'warning',
      titulo: 'Ya no se puede cargar otra versión de la proyección',
      detalle: `La semana ya tiene ${MAXIMO_VERSIONES} versiones, el máximo (una por cada color del calendario). Si quieres cargar otra, primero elimina alguna versión en la lista de versiones.`,
      segundos: 12,
    })
    return true
  }

  async function subir(tipo: TipoDato, archivo: File) {
    limpiarAvisos()
    if (tipo === 'PROYECCION_TEJEDURIA' && sinEspacioParaVersion()) return
    setSubiendo(tipo)
    const r = await intentar(() => cargas.importar(tipo, archivo, tipo === 'PROYECCION_TEJEDURIA' ? lunes : undefined))
    setSubiendo(null)
    if (!r.ok) { setError(r.error); setErrorTipo(tipo); return }
    setResultado(r.valor)
    const nuevos = Object.entries(r.valor.nuevos)
    avisar({
      tipo: 'success',
      titulo: `${TIPO_DATO[r.valor.tipo]}: «${r.valor.archivo}» importado el ${fechaHora(r.valor.fechaHora)}`,
      detalle: <>{nuevos.length === 0 ? 'No había registros nuevos.' : `Nuevos: ${nuevos.map(([k, v]) => `${v} ${k}`).join(', ')}.`}
        {r.valor.existentes > 0 && ` ${r.valor.existentes} registros ya existían y se conservaron sin cambios.`}</>,
    })
    await Promise.all([historial.recargar(), versiones.recargar()])
  }

  async function eliminarCarga() {
    const carga = porEliminar
    setPorEliminar(null)
    if (!carga) return
    limpiarAvisos()
    const r = await intentar(() => cargas.eliminar(carga.id))
    if (!r.ok) { setError(r.error); return }
    avisar({
      tipo: 'success',
      titulo: `${TIPO_DATO[r.valor.tipo]}: se eliminó «${r.valor.archivo}»`,
      detalle: `Registros eliminados: ${Object.entries(r.valor.eliminados).map(([k, v]) => `${v} ${k}`).join(', ')}.`,
    })
    await Promise.all([historial.recargar(), versiones.recargar()])
  }

  async function eliminarVersion() {
    const v = versionPorEliminar
    setVersionPorEliminar(null)
    if (!v) return
    limpiarAvisos()
    const r = await intentar(() => datos.eliminarVersionProyeccion(v.id))
    if (!r.ok) { setError(r.error); return }
    avisar({
      tipo: 'success',
      titulo: `Se eliminó la versión V${r.valor.version} de la proyección`,
      detalle: `Se quitaron ${r.valor.registros} registros y ${r.valor.planificaciones} planificaciones. Puedes volver a esta versión desde la lista de versiones.`,
    })
    await Promise.all([historial.recargar(), versiones.recargar()])
  }

  /** Volver a una versión eliminada: se copia como una versión nueva (D-46, D-52). */
  async function restaurar(id: number) {
    limpiarAvisos()
    if (sinEspacioParaVersion()) return
    const r = await intentar(() => datos.restaurarProyeccion(id))
    if (!r.ok) { setError(r.error); return }
    await versiones.recargar()
  }

  async function plantilla(tipo: TipoDato) {
    const r = await intentar(() => cargas.plantilla(tipo))
    if (!r.ok) setError(r.error)
  }

  const vigente = versiones.datos?.find((v) => v.estado === 'VIGENTE') ?? null

  /** Última carga vigente de cada tipo; la proyección depende de la semana elegida. */
  function ultimaCarga(tipo: TipoDato) {
    return historial.datos?.find((c) => c.tipo === tipo && !c.eliminada) ?? null
  }

  function archivoDe(tipo: TipoDato) {
    if (tipo !== 'PROYECCION_TEJEDURIA') {
      const c = ultimaCarga(tipo)
      return c ? { nombre: c.archivo, detalle: `Última carga: ${fechaHora(c.fechaHora)} · ${c.usuario}` } : null
    }
    if (!vigente) return null
    // La carga que creó la versión vigente: misma hora de registro (con un margen de un minuto).
    const t = new Date(vigente.fechaCarga).getTime()
    const c = historial.datos?.find((x) => x.tipo === tipo && !x.eliminada && Math.abs(new Date(x.fechaHora).getTime() - t) < 60_000)
    return {
      nombre: c?.archivo ?? `Proyección V${vigente.version}`,
      detalle: `Versión V${vigente.version} vigente · ${kg(vigente.kgTotales)} · ${fechaHora(vigente.fechaCarga)}`,
    }
  }

  function estadoDe(tipo: TipoDato): { tone: StatusTone; label: string } {
    if (subiendo === tipo) return { tone: 'neutral', label: 'Subiendo' }
    if (errorTipo === tipo) return { tone: 'danger', label: 'Con errores' }
    const cargado = tipo === 'PROYECCION_TEJEDURIA' ? vigente !== null : ultimaCarga(tipo) !== null
    return cargado ? { tone: 'success', label: 'Cargado' } : { tone: 'neutral', label: 'Sin cargar' }
  }

  const cargados = TIPOS.filter((t) => (t === 'PROYECCION_TEJEDURIA' ? vigente !== null : ultimaCarga(t) !== null)).length

  // Historial filtrado por tipo y periodo
  const desde = periodo ? Date.now() - Number(periodo) * 86_400_000 : 0
  const todasCargas = historial.datos ?? []
  const cargasFiltradas = todasCargas.filter((c) => (!filtroTipo || c.tipo === filtroTipo) && (!desde || new Date(c.fechaHora).getTime() >= desde))
  const columnasHistorial = ['Fecha y hora', 'Tipo', 'Archivo', 'Usuario', 'Estado', ...(esPlanificador ? [''] : [])]

  return (
    <>
      <PageHeader title="Carga de datos para la planificación" description="Importa y gestiona la información necesaria para la planificación de tintorería."
        actions={<WeekSelector />} />
      <SoloLecturaAviso />

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        {TIPOS.map((tipo) => (
          <UploadCard key={tipo} {...TARJETA[tipo]}
            titulo={TARJETA[tipo].titulo}
            estado={estadoDe(tipo)} archivo={archivoDe(tipo)}
            sinArchivo={tipo === 'PROYECCION_TEJEDURIA' ? `La semana del ${etiquetaSemana(lunes)} no tiene proyección cargada.` : 'Todavía no se cargó ningún archivo.'}
            cargando={subiendo === tipo} soloLectura={!esPlanificador}
            onArchivoSeleccionado={(archivo) => subir(tipo, archivo)} onDescargarPlantilla={() => plantilla(tipo)} />
        ))}
      </div>

      <div className="mt-4 empty:hidden">
        <AvisoError error={error} onCerrar={() => { setError(null); setErrorTipo(null) }} />
        {resultado && <AdvertenciasCarga resultado={resultado} />}
      </div>

      {isGuided && esPlanificador && <PreparacionDatos lunes={lunes} cargados={cargados} advertencias={resultado?.advertencias.length ?? 0} />}

      {/* Versiones de la proyección */}
      <section className="mt-6 rounded-2xl border border-line bg-panel" aria-labelledby="titulo-versiones">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line px-5 py-4">
          <div className="flex items-center gap-3">
            <Layers3 className="h-5 w-5 text-ink-soft" aria-hidden="true" />
            <div>
              <h3 id="titulo-versiones" className="flex items-center gap-2 text-base font-semibold text-ink">Versiones de la proyección
                {(versiones.datos?.length ?? 0) > 0 && <span className="rounded-full bg-accent-soft px-2 py-0.5 text-xs font-medium text-accent">{versiones.datos?.length} {versiones.datos?.length === 1 ? 'versión' : 'versiones'}</span>}
              </h3>
              <p className="text-sm text-ink-soft">Semana del {etiquetaSemana(lunes)}</p>
            </div>
          </div>
          <Link to="/datos-registrados/proyeccion" className={claseBoton('ghost', 'sm')}><Database className="h-4 w-4" aria-hidden="true" />Ver registros</Link>
        </div>
        <div className="p-5">
          {versiones.cargando ? <TablaCargando columnas={3} filas={2} /> : (versiones.datos ?? []).length === 0
            ? <p className="py-6 text-center text-sm text-ink-soft">La semana no tiene proyección cargada. Súbela en la tarjeta «Proyección de tejeduría».</p>
            : <Paginado items={versiones.datos ?? []} reinicio={lunes}>{(pagina) => (
              <ul className="space-y-3">
                {pagina.map((v) => <FilaVersion key={v.id} version={v} editable={esPlanificador}
                  onRestaurar={() => restaurar(v.id)} onEliminar={() => setVersionPorEliminar(v)} />)}
              </ul>
            )}</Paginado>}
        </div>
      </section>

      {/* Historial de cargas */}
      <section className="mt-6 rounded-2xl border border-line bg-panel" aria-labelledby="titulo-historial">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line px-5 py-4">
          <div className="flex items-center gap-3">
            <History className="h-5 w-5 text-ink-soft" aria-hidden="true" />
            <h3 id="titulo-historial" className="text-base font-semibold text-ink">Historial de cargas</h3>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <select aria-label="Tipo de carga" className={claseSelect} value={filtroTipo} onChange={(e) => setFiltroTipo(e.target.value)}>
              <option value="">Todos los tipos</option>
              {TIPOS.map((t) => <option key={t} value={t}>{TARJETA[t].titulo}</option>)}
            </select>
            <select aria-label="Periodo" className={claseSelect} value={periodo} onChange={(e) => setPeriodo(e.target.value)}>
              {PERIODOS.map((p) => <option key={p.valor} value={p.valor}>{p.texto}</option>)}
            </select>
          </div>
        </div>
        <div className="p-5">
          {historial.cargando ? <TablaCargando columnas={columnasHistorial.length} filas={4} /> : (
            <Paginado items={cargasFiltradas} reinicio={[filtroTipo, periodo]}>{(pagina) => (
              <TablaDatos cabeceras={columnasHistorial} minimo={760}>
                {pagina.length === 0
                  ? <FilaVacia columnas={columnasHistorial.length} filtrado={todasCargas.length > 0} titulo="Todavía no hay cargas registradas"
                      descripcion="Cada archivo que subas aparecerá aquí con su fecha, tipo y usuario." />
                  : pagina.map((c) => (
                    <tr key={c.id} className={filaTabla}>
                      <td className={`${celda} whitespace-nowrap`}>{fechaHora(c.fechaHora)}</td>
                      <td className={`${celda} whitespace-nowrap`}>{TARJETA[c.tipo].titulo}</td>
                      <td className={`${celda} font-mono text-xs`}>{c.archivo}</td>
                      <td className={`${celda} whitespace-nowrap text-ink-soft`}>{c.usuario}</td>
                      <td className={celda}>{c.eliminada ? <StatusPill tone="danger" label="Eliminada" /> : <StatusPill tone="success" label="Cargado" />}</td>
                      {esPlanificador && <td className={`${celda} w-12 py-2 text-right`}>
                        {!c.eliminada && <MenuAcciones etiqueta={`Acciones de la carga ${c.archivo}`}
                          acciones={[{ texto: 'Eliminar carga', icono: Trash2, peligro: true, onClick: () => setPorEliminar(c) }]} />}
                      </td>}
                    </tr>
                  ))}
              </TablaDatos>
            )}</Paginado>
          )}
        </div>
      </section>

      <ConfirmDialog open={versionPorEliminar !== null} title={`Eliminar la versión V${versionPorEliminar?.version ?? ''} de la proyección`}
        description="La versión quedará como Eliminada en el historial y podrás volver a ella. Las planificaciones generadas con esta versión sí se borran: al volver, hay que generarlas de nuevo. Las partidas siguen pendientes. No se podrá eliminar si la semana ya se cerró o tiene incidencias."
        confirmLabel="Eliminar versión" tone="danger" onConfirm={eliminarVersion} onCancel={() => setVersionPorEliminar(null)} />
      <ConfirmDialog open={porEliminar !== null} title={`Eliminar la carga «${porEliminar?.archivo ?? ''}»`}
        description={porEliminar?.tipo === 'PROYECCION_TEJEDURIA'
          ? 'Su versión quedará como Eliminada y podrás volver a ella desde Versiones de la proyección, en la semana que le corresponde. Las planificaciones generadas con esa versión sí se borran.'
          : 'Se eliminarán de la base de datos los registros que agregó este archivo; lo que ya existía antes no cambia. No se puede deshacer. Si otros datos los usan (por ejemplo, las partidas de estos pedidos o una planificación ya generada), no se eliminará nada y verás qué quitar primero.'}
        confirmLabel="Eliminar carga" tone="danger" onConfirm={eliminarCarga} onCancel={() => setPorEliminar(null)} />
    </>
  )
}

/** Una versión de la proyección como fila-tarjeta; la de la planificación vigente se marca con borde izquierdo verde. */
function FilaVersion({ version: v, editable, onRestaurar, onEliminar }: { version: VersionProyeccion; editable: boolean; onRestaurar: () => void; onEliminar: () => void }) {
  const acciones: AccionMenu[] = []
  // D-52: una versión Cargada se planifica directamente; solo una Eliminada necesita volver como copia
  if (editable && v.estado === 'ELIMINADA') acciones.push({ texto: 'Volver a esta versión', icono: RotateCcw, onClick: onRestaurar })
  if (editable && v.estado !== 'ELIMINADA') acciones.push({ texto: 'Eliminar versión', icono: Trash2, onClick: onEliminar, peligro: true })
  const estado = ESTADO_VERSION[v.estado]
  return (
    <li className={`flex items-center gap-4 rounded-xl border border-line px-4 py-3 ${v.estado === 'VIGENTE' ? 'border-l-[3px] border-l-success bg-success-soft/50' : ''}`}>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-lg font-semibold text-ink">V{v.version}</span>
          <StatusPill tone={estado.tone} label={estado.label} />
        </div>
        <div className="mt-1 flex flex-wrap gap-x-5 gap-y-1 text-sm text-ink-soft">
          <span className="inline-flex items-center gap-1.5"><Database className="h-4 w-4" aria-hidden="true" />{v.registros} registros</span>
          <span className="inline-flex items-center gap-1.5"><Weight className="h-4 w-4" aria-hidden="true" />{kg(v.kgTotales)}</span>
        </div>
      </div>
      <span className="shrink-0 text-sm text-ink-soft">{fechaHora(v.fechaCarga)}</span>
      <MenuAcciones etiqueta={`Acciones de la versión V${v.version}`} acciones={acciones} />
    </li>
  )
}

/**
 * Modo guiado: estado de preparación de los datos de la semana (Carbon: progress indicator).
 * Los archivos se validan al subirlos; uno con errores no se registra.
 */
function PreparacionDatos({ lunes, cargados, advertencias }: { lunes: string; cargados: number; advertencias: number }) {
  const completos = cargados === 3
  const pasos: { titulo: string; detalle: string; estado: 'hecho' | 'actual' | 'aviso' | 'pendiente'; accion?: ReactNode }[] = [
    { titulo: 'Archivos cargados', detalle: `${cargados} de 3 completados`, estado: completos ? 'hecho' : 'actual' },
    { titulo: 'Validación de datos', detalle: !completos ? 'Pendiente' : advertencias > 0 ? `${advertencias} ${advertencias === 1 ? 'advertencia' : 'advertencias'} en la última carga` : 'Sin observaciones', estado: !completos ? 'pendiente' : advertencias > 0 ? 'aviso' : 'hecho' },
    { titulo: 'Planificación', detalle: completos ? 'Lista para generar' : 'Pendiente', estado: completos ? 'actual' : 'pendiente', accion: completos && <Link to="/" className="text-sm font-medium text-accent hover:underline">Ir a planificación</Link> },
  ]
  return (
    <section className="mt-6 flex flex-col gap-5 rounded-2xl border border-line bg-panel p-5 xl:flex-row xl:items-center" aria-labelledby="titulo-preparacion">
      <div className="flex items-start gap-3 xl:w-80 xl:shrink-0 xl:border-r xl:border-line xl:pr-5">
        <ClipboardCheck className="mt-0.5 h-6 w-6 shrink-0 text-accent" aria-hidden="true" />
        <div>
          <h3 id="titulo-preparacion" className="text-base font-semibold text-ink">Estado de preparación de datos</h3>
          <p className="mt-0.5 text-sm text-ink-soft">Semana del {etiquetaSemana(lunes)}. Carga en orden: pedidos → partidas → proyección.</p>
        </div>
      </div>
      <ol className="grid flex-1 gap-3 sm:grid-cols-3" aria-label="Progreso de la preparación">
        {pasos.map((p, i) => (
          <li key={p.titulo} aria-current={p.estado === 'actual' ? 'step' : undefined}
            className={`flex items-start gap-3 border-t-[3px] pt-3 ${p.estado === 'hecho' ? 'border-success' : p.estado === 'aviso' ? 'border-warning' : p.estado === 'actual' ? 'border-accent' : 'border-line'}`}>
            <span className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full border-2 text-xs font-semibold ${p.estado === 'hecho' ? 'border-success text-success' : p.estado === 'aviso' ? 'border-warning text-warning' : p.estado === 'actual' ? 'border-accent text-accent' : 'border-line text-ink-faint'}`}>
              {p.estado === 'hecho' ? <Check className="h-4 w-4" aria-hidden="true" /> : p.estado === 'aviso' ? <TriangleAlert className="h-3.5 w-3.5" aria-hidden="true" /> : i + 1}
            </span>
            <div className="min-w-0">
              <p className="text-sm font-semibold text-ink">{p.titulo}</p>
              <p className="text-sm text-ink-soft">{p.detalle}</p>
              {p.accion}
            </div>
          </li>
        ))}
      </ol>
    </section>
  )
}

/** Advertencias de una carga: quedan en la pantalla porque son filas que hay que revisar (el aviso de éxito es flotante). */
function AdvertenciasCarga({ resultado }: { resultado: CargaResponse }) {
  return <>
    {resultado.advertencias.length > 0 && (
      <Notificacion tipo="warning" titulo={`La carga tiene ${resultado.advertencias.length} ${resultado.advertencias.length === 1 ? 'advertencia' : 'advertencias'}`}>
        <p>Los datos se registraron, pero revisa estas filas.</p>
        <Paginado items={resultado.advertencias}>{(pagina) => (
          <div className="mt-3 overflow-auto rounded-lg border border-line bg-panel">
            <table className="w-full text-left text-xs text-ink">
              <thead><tr className="bg-panel-muted"><th className="px-3 py-2 font-semibold">Hoja</th><th className="px-3 py-2 font-semibold">Fila</th><th className="px-3 py-2 font-semibold">Campo</th><th className="px-3 py-2 font-semibold">Advertencia</th></tr></thead>
              <tbody>{pagina.map((a, i) => <tr key={i} className="border-t border-line"><td className="px-3 py-1.5">{a.hoja}</td><td className="px-3 py-1.5 font-mono">{a.fila || '—'}</td><td className="px-3 py-1.5 font-mono">{a.campo || '—'}</td><td className="px-3 py-1.5">{a.motivo}</td></tr>)}</tbody>
            </table>
          </div>
        )}</Paginado>
      </Notificacion>
    )}
  </>
}
