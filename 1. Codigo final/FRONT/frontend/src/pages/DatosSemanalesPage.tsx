import { useState } from 'react'
import { Link } from 'react-router-dom'
import { CheckCircle2, Database, FileSpreadsheet, History, RotateCcw, Trash2 } from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { SoloLecturaAviso } from '@/components/common/SoloLecturaAviso'
import { AvisoError, Cargando } from '@/components/common/AvisoError'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { StatusPill } from '@/components/ui/StatusPill'
import { WeekSelector } from '@/components/planning/WeekSelector'
import { UploadCard } from '@/components/planning/UploadCard'
import { MENSAJE_SIN_PERMISO, useAuth } from '@/auth/auth-context'
import { useExperience } from '@/experience/experience-context'
import { useSemana } from '@/semana/semana-context'
import { cargas, datos } from '@/api/servicios'
import type { ErrorApi } from '@/api/cliente'
import type { CargaEliminada, CargaHistorial, CargaResponse, TipoDato, VersionProyeccion, VersionProyeccionEliminada } from '@/api/tipos'
import { intentar, useDatos } from '@/lib/useDatos'
import { TIPO_DATO, kg } from '@/lib/etiquetas'
import { etiquetaSemana, fechaHora } from '@/lib/fechas'
import { Paginado } from '@/components/ui/Paginacion'

const DESCRIPCION: Record<TipoDato, string> = {
  PEDIDOS: 'Pedidos, clientes y los artículos de cada pedido. Carga acumulable: lo ya registrado se conserva.',
  PARTIDAS: 'Partidas con su color, tonalidad, barca y componentes (tela principal y complementos). Carga acumulable.',
  PROYECCION_TEJEDURIA: 'Kilos que tejeduría tejerá por pedido-artículo y día, del sábado anterior al viernes. Cada carga crea una versión nueva.',
}

interface Pendiente { tipo: TipoDato; archivo?: File; restaurar?: number; mensaje: string }

/** Carga de datos por plantillas .xlsx (HU-04, HU-05) y versiones de la proyección de la semana. */
export function DatosSemanalesPage() {
  const { esPlanificador } = useAuth()
  const { isGuided } = useExperience()
  const { lunes } = useSemana()
  const [subiendo, setSubiendo] = useState<TipoDato | null>(null)
  const [error, setError] = useState<ErrorApi | null>(null)
  const [resultado, setResultado] = useState<CargaResponse | null>(null)
  const [pendiente, setPendiente] = useState<Pendiente | null>(null)
  const [porEliminar, setPorEliminar] = useState<CargaHistorial | null>(null)
  const [eliminada, setEliminada] = useState<CargaEliminada | null>(null)
  const [versionPorEliminar, setVersionPorEliminar] = useState<VersionProyeccion | null>(null)
  const [versionEliminada, setVersionEliminada] = useState<VersionProyeccionEliminada | null>(null)
  const historial = useDatos(() => cargas.historial(), [])
  const versiones = useDatos(() => datos.versionesProyeccion(lunes), [lunes])

  async function subir(tipo: TipoDato, archivo: File, confirmado = false) {
    setSubiendo(tipo)
    setError(null)
    setResultado(null)
    setEliminada(null)
    const r = await intentar(() => cargas.importar(tipo, archivo, tipo === 'PROYECCION_TEJEDURIA' ? lunes : undefined, confirmado))
    setSubiendo(null)
    if (!r.ok) {
      if (r.error.codigo === 'CONFIRMACION_REQUERIDA') setPendiente({ tipo, archivo, mensaje: r.error.message })
      else setError(r.error)
      return
    }
    setResultado(r.valor)
    await Promise.all([historial.recargar(), versiones.recargar()])
  }

  async function eliminarCarga() {
    const carga = porEliminar
    setPorEliminar(null)
    if (!carga) return
    setError(null)
    setResultado(null)
    setEliminada(null)
    const r = await intentar(() => cargas.eliminar(carga.id))
    if (!r.ok) { setError(r.error); return }
    setEliminada(r.valor)
    await Promise.all([historial.recargar(), versiones.recargar()])
  }

  async function eliminarVersion() {
    const v = versionPorEliminar
    setVersionPorEliminar(null)
    if (!v) return
    setError(null)
    setResultado(null)
    setEliminada(null)
    setVersionEliminada(null)
    const r = await intentar(() => datos.eliminarVersionProyeccion(v.id))
    if (!r.ok) { setError(r.error); return }
    setVersionEliminada(r.valor)
    await Promise.all([historial.recargar(), versiones.recargar()])
  }

  async function restaurar(id: number, confirmado = false) {
    setError(null)
    const r = await intentar(() => datos.restaurarProyeccion(id, confirmado))
    if (!r.ok) {
      if (r.error.codigo === 'CONFIRMACION_REQUERIDA') setPendiente({ tipo: 'PROYECCION_TEJEDURIA', restaurar: id, mensaje: r.error.message })
      else setError(r.error)
      return
    }
    setVersionEliminada(null)
    await versiones.recargar()
  }

  function confirmarPendiente() {
    const p = pendiente
    setPendiente(null)
    if (!p) return
    if (p.archivo) void subir(p.tipo, p.archivo, true)
    else if (p.restaurar) void restaurar(p.restaurar, true)
  }

  function ultima(tipo: TipoDato) {
    const c = historial.datos?.find((x) => x.tipo === tipo)
    return c ? { archivo: c.archivo, fechaHora: fechaHora(c.fechaHora), usuario: c.usuario } : null
  }

  async function plantilla(tipo: TipoDato) {
    const r = await intentar(() => cargas.plantilla(tipo))
    if (!r.ok) setError(r.error)
  }

  const tipos: TipoDato[] = ['PEDIDOS', 'PARTIDAS', 'PROYECCION_TEJEDURIA']

  return (
    <>
      <PageHeader title="Carga de datos" description="Importa pedidos, artículos, partidas y la proyección de tejeduría con las plantillas de hoja de cálculo." />
      <SoloLecturaAviso />

      {isGuided && esPlanificador && (
        <section className="mb-6 rounded-2xl border border-accent/25 bg-accent-soft/60 p-5">
          <div className="flex items-start gap-3"><span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-accent text-accent-ink"><FileSpreadsheet className="h-5 w-5" /></span>
            <div><p className="text-xs font-semibold uppercase tracking-wide text-accent">Orden de carga</p>
              <h2 className="mt-1 text-lg font-semibold text-ink">1. Pedidos → 2. Partidas → 3. Proyección de la semana</h2>
              <p className="mt-1 text-sm text-ink-soft">Las partidas usan los pedidos y artículos ya registrados, y la proyección usa los artículos de cada pedido. Descarga la plantilla de cada tarjeta para ver el formato.</p></div></div>
        </section>
      )}

      <div className="mb-6"><WeekSelector /></div>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
        {tipos.map((tipo) => (
          <UploadCard key={tipo}
            titulo={tipo === 'PROYECCION_TEJEDURIA' ? `${TIPO_DATO[tipo]} · semana del ${etiquetaSemana(lunes)}` : TIPO_DATO[tipo]}
            descripcion={DESCRIPCION[tipo]} ultimaCarga={ultima(tipo)} cargando={subiendo === tipo}
            soloLectura={!esPlanificador} motivoSoloLectura={MENSAJE_SIN_PERMISO}
            onArchivoSeleccionado={(archivo) => subir(tipo, archivo)} onDescargarPlantilla={() => plantilla(tipo)} />
        ))}
      </div>

      <div className="mt-6">
        <AvisoError error={error} onCerrar={() => setError(null)} />
        {resultado && <ResultadoCarga resultado={resultado} />}
        {versionEliminada && <p role="status" className="flex items-center gap-2 rounded-xl border border-success/40 bg-success-soft p-4 text-sm font-medium text-success"><CheckCircle2 className="h-4 w-4 shrink-0" />Se eliminó la versión v{versionEliminada.version} de la proyección ({versionEliminada.registros} registros y {versionEliminada.planificaciones} planificaciones).</p>}
        {eliminada && <p role="status" className="flex items-center gap-2 rounded-xl border border-success/40 bg-success-soft p-4 text-sm font-medium text-success"><CheckCircle2 className="h-4 w-4 shrink-0" />{TIPO_DATO[eliminada.tipo]}: se eliminó «{eliminada.archivo}» ({Object.entries(eliminada.eliminados).map(([k, v]) => `${v} ${k}`).join(', ')}).</p>}
      </div>

      <section className="mt-8">
        <div className="mb-3 flex items-center justify-between"><h3 className="text-sm font-semibold text-ink">Versiones de la proyección · semana del {etiquetaSemana(lunes)}</h3>
          <Link to="/datos-registrados?tab=proyeccion" className="inline-flex items-center gap-1 text-xs font-medium text-accent hover:underline"><Database className="h-3.5 w-3.5" />Ver registros</Link></div>
        {versiones.cargando ? <Cargando /> : (versiones.datos ?? []).length === 0
          ? <p className="rounded-xl border border-line bg-panel p-6 text-center text-sm text-ink-soft">La semana no tiene proyección cargada.</p>
          : <Paginado items={versiones.datos ?? []} reinicio={lunes}>{(pagina) => <div className="overflow-x-auto rounded-xl border border-line bg-panel"><table className="w-full min-w-[700px] text-left text-sm">
              <thead><tr className="border-b border-line text-xs uppercase text-ink-faint"><th className="px-4 py-3">Versión</th><th className="px-4 py-3">Cargada</th><th className="px-4 py-3">Registros</th><th className="px-4 py-3">Kg</th><th className="px-4 py-3">Estado</th><th className="px-4 py-3" /></tr></thead>
              <tbody>{pagina.map((v) => (
                <tr key={v.id} className="border-b border-line last:border-0">
                  <td className="px-4 py-2.5 font-mono font-semibold">v{v.version}</td><td className="px-4 py-2.5 text-ink-soft">{fechaHora(v.fechaCarga)}</td>
                  <td className="px-4 py-2.5 font-mono">{v.registros}</td><td className="px-4 py-2.5 font-mono">{kg(v.kgTotales)}</td>
                  <td className="px-4 py-2.5"><StatusPill tone={v.estado === 'VIGENTE' ? 'success' : v.estado === 'ELIMINADA' ? 'danger' : 'neutral'} label={v.estado === 'VIGENTE' ? 'Vigente' : v.estado === 'ELIMINADA' ? 'Eliminada' : 'Reemplazada'} /></td>
                  <td className="px-4 py-2.5 text-right whitespace-nowrap">{v.estado !== 'VIGENTE' && esPlanificador && <button type="button" onClick={() => restaurar(v.id)} className="mr-4 inline-flex items-center gap-1 text-xs font-medium text-accent hover:underline"><RotateCcw className="h-3.5 w-3.5" />Volver a esta versión</button>}
                    {v.estado !== 'ELIMINADA' && <button type="button" disabled={!esPlanificador} title={esPlanificador ? 'Eliminar esta versión y sus planificaciones' : MENSAJE_SIN_PERMISO} onClick={() => setVersionPorEliminar(v)} className="inline-flex items-center gap-1 text-xs font-medium text-danger disabled:opacity-30"><Trash2 className="h-3.5 w-3.5" />Eliminar</button>}</td>
                </tr>))}</tbody></table></div>}</Paginado>}
      </section>

      <section className="mt-8">
        <h3 className="mb-3 flex items-center gap-2 text-sm font-semibold text-ink"><History className="h-4 w-4" />Historial de cargas</h3>
        {historial.cargando ? <Cargando /> : <Paginado items={historial.datos ?? []}>{(pagina) => <div className="overflow-x-auto rounded-xl border border-line bg-panel"><table className="w-full min-w-[640px] text-left text-sm">
          <thead><tr className="border-b border-line text-xs uppercase text-ink-faint"><th className="px-4 py-3">Fecha y hora</th><th className="px-4 py-3">Tipo</th><th className="px-4 py-3">Archivo</th><th className="px-4 py-3">Usuario</th><th className="px-4 py-3" /></tr></thead>
          <tbody>{(historial.datos ?? []).length === 0 ? <tr><td colSpan={5} className="px-4 py-8 text-center text-ink-faint">Todavía no hay cargas registradas.</td></tr>
            : pagina.map((c) => <tr key={c.id} className="border-b border-line last:border-0"><td className="px-4 py-2.5 text-ink-soft">{fechaHora(c.fechaHora)}</td><td className="px-4 py-2.5">{TIPO_DATO[c.tipo]}</td><td className="px-4 py-2.5 font-mono text-xs">{c.archivo}</td><td className="px-4 py-2.5 text-ink-soft">{c.usuario}</td>
              <td className="px-4 py-2.5 text-right">{c.eliminada ? <StatusPill tone="danger" label="Eliminada" /> : <button type="button" disabled={!esPlanificador} title={esPlanificador ? 'Eliminar lo que agregó este archivo' : MENSAJE_SIN_PERMISO} onClick={() => setPorEliminar(c)} className="inline-flex items-center gap-1 text-xs font-medium whitespace-nowrap text-danger disabled:opacity-30"><Trash2 className="h-3.5 w-3.5" />Eliminar carga</button>}</td></tr>)}</tbody></table></div>}</Paginado>}
      </section>

      <ConfirmDialog open={pendiente !== null} title="Cambiar la proyección de la semana" description={pendiente?.mensaje}
        confirmLabel="Continuar" tone="danger" onConfirm={confirmarPendiente} onCancel={() => setPendiente(null)} />
      <ConfirmDialog open={versionPorEliminar !== null} title={`Eliminar la versión v${versionPorEliminar?.version ?? ''} de la proyección`}
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

function ResultadoCarga({ resultado }: { resultado: CargaResponse }) {
  const nuevos = Object.entries(resultado.nuevos)
  return (
    <div role="status" className="rounded-xl border border-success/40 bg-success-soft p-4 text-sm">
      <p className="flex items-center gap-2 font-medium text-success"><CheckCircle2 className="h-4 w-4" />{TIPO_DATO[resultado.tipo]}: «{resultado.archivo}» importado el {fechaHora(resultado.fechaHora)}</p>
      <p className="mt-1 text-ink-soft">{nuevos.length === 0 ? 'No había registros nuevos.' : `Nuevos: ${nuevos.map(([k, v]) => `${v} ${k}`).join(', ')}.`}
        {resultado.existentes > 0 && ` ${resultado.existentes} registros ya existían y se conservaron sin cambios.`}</p>
      {resultado.advertencias.length > 0 && <Paginado items={resultado.advertencias}>{(pagina) => (
        <div className="mt-3 overflow-auto rounded-lg border border-warning/40 bg-panel">
          <table className="w-full text-left text-xs text-ink">
            <thead><tr className="border-b border-line text-ink-faint"><th className="px-3 py-2">Hoja</th><th className="px-3 py-2">Fila</th><th className="px-3 py-2">Campo</th><th className="px-3 py-2">Advertencia</th></tr></thead>
            <tbody>{pagina.map((a, i) => <tr key={i} className="border-b border-line last:border-0"><td className="px-3 py-1.5">{a.hoja}</td><td className="px-3 py-1.5 font-mono">{a.fila || '—'}</td><td className="px-3 py-1.5 font-mono">{a.campo || '—'}</td><td className="px-3 py-1.5">{a.motivo}</td></tr>)}</tbody>
          </table>
        </div>
      )}</Paginado>}
    </div>
  )
}
