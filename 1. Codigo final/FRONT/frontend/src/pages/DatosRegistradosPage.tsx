import { useState, type ReactNode } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Boxes, CalendarRange, Download, FileText, Layers3, Palette, Pencil, Search, Trash2 } from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { SoloLecturaAviso } from '@/components/common/SoloLecturaAviso'
import { AvisoError, Cargando } from '@/components/common/AvisoError'
import { Drawer } from '@/components/ui/Drawer'
import { Field, inputClass } from '@/components/ui/FormField'
import { StatusPill } from '@/components/ui/StatusPill'
import { BarraFiltros, FiltroLista, FiltroMes, enMes, sinRepetir, useFiltros } from '@/components/common/Filtros'
import { Tabs, type TabItem } from '@/components/ui/Tabs'
import { Paginado } from '@/components/ui/Paginacion'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { WeekSelector } from '@/components/planning/WeekSelector'
import { MENSAJE_SIN_PERMISO, useAuth } from '@/auth/auth-context'
import { useSemana } from '@/semana/semana-context'
import { cargas, datos as api } from '@/api/servicios'
import type { ErrorApi } from '@/api/cliente'
import type { ArticuloRegistrado, ColorRegistrado, DetalleProyeccion, PartidaRegistrada, PedidoRegistrado } from '@/api/tipos'
import { intentar, useDatos } from '@/lib/useDatos'
import { CLASIFICACION_COLOR, ESTADO_PARTIDA, TONALIDAD, TONO_PARTIDA, kg } from '@/lib/etiquetas'
import { etiquetaSemana, fechaCorta, fechaHora } from '@/lib/fechas'

type Pestana = 'pedidos' | 'articulos' | 'partidas' | 'colores' | 'proyeccion'
const TABS: TabItem[] = [
  { id: 'pedidos', label: 'Pedidos', icon: FileText },
  { id: 'articulos', label: 'Artículos', icon: Layers3 },
  { id: 'partidas', label: 'Partidas', icon: Boxes },
  { id: 'colores', label: 'Colores', icon: Palette },
  { id: 'proyeccion', label: 'Proyección de tejeduría', icon: CalendarRange },
]
const th = 'px-4 py-3'
const td = 'px-4 py-2.5'

/** Consulta, modificación manual (RF13), eliminación (D-42) y descarga (RF12) de los datos registrados. Todo cambio queda en la trazabilidad. */
export function DatosRegistradosPage() {
  const [params, setParams] = useSearchParams()
  const [tab, setTab] = useState<Pestana>((params.get('tab') as Pestana) ?? 'pedidos')
  const [busqueda, setBusqueda] = useState('')
  const [error, setError] = useState<ErrorApi | null>(null)
  const { esPlanificador } = useAuth()
  const { lunes: lunesActual } = useSemana()

  function cambiar(id: string) {
    setTab(id as Pestana)
    setParams(id === 'pedidos' ? {} : { tab: id }, { replace: true })
  }

  async function descargar() {
    const tipo = tab === 'pedidos' || tab === 'articulos' ? 'PEDIDOS' : tab === 'proyeccion' ? 'PROYECCION_TEJEDURIA' : 'PARTIDAS'
    const r = await intentar(() => cargas.exportar(tipo, tab === 'proyeccion' ? lunesActual : undefined))
    if (!r.ok) setError(r.error)
  }

  return (
    <>
      <PageHeader title="Datos registrados" description="Consulta, corrige o elimina los datos cargados. Los cambios quedan registrados en la trazabilidad."
        actions={<button type="button" onClick={descargar} className="inline-flex items-center gap-1.5 rounded-lg border border-line bg-panel px-3 py-2 text-sm font-medium text-ink hover:bg-panel-muted"><Download className="h-4 w-4" />Descargar en Excel</button>} />
      <SoloLecturaAviso />
      <AvisoError error={error} onCerrar={() => setError(null)} />
      <Tabs tabs={TABS} activeId={tab} onChange={cambiar} />
      <div className="mb-4 flex items-center gap-2 rounded-lg border border-line bg-panel px-3 py-2 sm:max-w-sm">
        <Search className="h-4 w-4 text-ink-faint" /><input value={busqueda} onChange={(e) => setBusqueda(e.target.value)} placeholder="Buscar por código o nombre…" className="w-full bg-transparent text-sm text-ink focus:outline-none" />
      </div>
      {tab === 'pedidos' && <Pedidos filtro={busqueda} editable={esPlanificador} />}
      {tab === 'articulos' && <Articulos filtro={busqueda} editable={esPlanificador} />}
      {tab === 'partidas' && <Partidas filtro={busqueda} editable={esPlanificador} />}
      {tab === 'colores' && <Colores filtro={busqueda} editable={esPlanificador} />}
      {tab === 'proyeccion' && <Proyeccion filtro={busqueda} editable={esPlanificador} />}
    </>
  )
}

function coincide(filtro: string, ...valores: (string | number | null | undefined)[]) {
  const f = filtro.trim().toLowerCase()
  return !f || valores.some((v) => String(v ?? '').toLowerCase().includes(f))
}

/** Lo último registrado va primero; el id crece con cada carga. */
function recientesPrimero<T extends { id: number }>(filas: T[]) {
  return [...filas].sort((a, b) => b.id - a.id)
}

function BotonModificar({ editable, onClick }: { editable: boolean; onClick: () => void }) {
  return <button type="button" disabled={!editable} onClick={onClick} title={editable ? 'Modificar' : MENSAJE_SIN_PERMISO}
    className="inline-flex items-center gap-1 text-accent disabled:opacity-30"><Pencil className="h-4 w-4" />Modificar</button>
}

function BotonEliminar({ editable, onClick }: { editable: boolean; onClick: () => void }) {
  return <button type="button" disabled={!editable} onClick={onClick} title={editable ? 'Eliminar' : MENSAJE_SIN_PERMISO}
    className="ml-4 inline-flex items-center gap-1 text-danger disabled:opacity-30"><Trash2 className="h-4 w-4" />Eliminar</button>
}

/** Fila por eliminar: pide confirmación, ejecuta y muestra el motivo si el sistema no la deja eliminar. */
function useEliminacion<T>(operacion: (fila: T) => Promise<unknown>, alTerminar: () => Promise<unknown> | void) {
  const [fila, setFila] = useState<T | null>(null)
  const [error, setError] = useState<ErrorApi | null>(null)
  async function confirmar() {
    if (fila === null) return
    const actual = fila
    setFila(null)
    setError(null)
    const r = await intentar(() => operacion(actual))
    if (!r.ok) { setError(r.error); return }
    await alTerminar()
  }
  return { fila, pedir: (f: T) => { setError(null); setFila(f) }, cancelar: () => setFila(null), confirmar, error, setError }
}

function Tabla({ cabeceras, children, minimo = 900 }: { cabeceras: string[]; children: ReactNode; minimo?: number }) {
  return (
    <div className="overflow-hidden rounded-xl border border-line bg-panel"><div className="overflow-x-auto">
      <table className="w-full text-left text-sm" style={{ minWidth: minimo }}>
        <thead><tr className="border-b border-line text-xs uppercase text-ink-faint">{cabeceras.map((c, i) => <th key={i} className={th}>{c}</th>)}</tr></thead>
        <tbody>{children}</tbody>
      </table>
    </div></div>
  )
}

function Vacio({ columnas }: { columnas: number }) {
  return <tr><td colSpan={columnas} className="px-4 py-10 text-center text-ink-faint">No hay registros.</td></tr>
}

function PieFormulario({ onCancelar, onGuardar, guardando }: { onCancelar: () => void; onGuardar: () => void; guardando: boolean }) {
  return <><button type="button" onClick={onCancelar} className="rounded-lg border border-line px-3 py-2 text-sm">Cancelar</button>
    <button type="button" onClick={onGuardar} disabled={guardando} className="rounded-lg bg-accent px-3 py-2 text-sm font-medium text-accent-ink disabled:opacity-50">Guardar cambios</button></>
}

/** Ejecuta un guardado y devuelve su error, si lo hubo. */
function useGuardado() {
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<ErrorApi | null>(null)
  async function guardar<T>(operacion: () => Promise<T>) {
    setGuardando(true); setError(null)
    const r = await intentar(operacion)
    setGuardando(false)
    if (!r.ok) { setError(r.error); return null }
    return r.valor
  }
  return { guardando, error, setError, guardar }
}

// ---------------------------------------------------------------- pedidos
function Pedidos({ filtro, editable }: { filtro: string; editable: boolean }) {
  const lista = useDatos(() => api.pedidos(), [])
  const [editando, setEditando] = useState<PedidoRegistrado | null>(null)
  const [pesos, setPesos] = useState<Record<number, string>>({})
  const g = useGuardado()
  const borrar = useEliminacion<PedidoRegistrado>((p) => api.eliminarPedido(p.id), lista.recargar)
  const f = useFiltros({ cliente: '', estado: '', mes: '' })
  if (lista.cargando) return <Cargando />
  if (lista.error) return <AvisoError error={lista.error} />
  const todas = lista.datos ?? []
  const filas = recientesPrimero(todas).filter((p) => coincide(filtro, p.numeroOp, p.cliente, ...p.detalles.map((d) => d.codigoArticulo))
    && (!f.valores.cliente || p.cliente === f.valores.cliente) && (!f.valores.estado || p.estado === f.valores.estado)
    && enMes(p.fechaEntrega, f.valores.mes))

  async function guardar() {
    if (!editando) return
    const ok = await g.guardar(async () => {
      await api.modificarPedido(editando.id, { cliente: editando.cliente, fechaEntrega: editando.fechaEntrega })
      for (const d of editando.detalles) {
        const nuevo = Number(pesos[d.id] ?? d.pesoTotalRequerido)
        if (nuevo !== d.pesoTotalRequerido) await api.modificarDetallePedido(d.id, nuevo)
      }
      return true
    })
    if (ok) { setEditando(null); await lista.recargar() }
  }

  return <>
    <AvisoError error={borrar.error} onCerrar={() => borrar.setError(null)} />
    <BarraFiltros visibles={filas.length} total={todas.length} activos={f.activos} onLimpiar={f.limpiar}>
      <FiltroLista etiqueta="Cliente" valor={f.valores.cliente} onChange={(cliente) => f.poner({ cliente })} opciones={sinRepetir(todas.map((p) => p.cliente)).map((c) => ({ valor: c, texto: c }))} />
      <FiltroMes etiqueta="Mes de entrega" valor={f.valores.mes} fechas={todas.map((p) => p.fechaEntrega)} onChange={(mes) => f.poner({ mes })} />
      <FiltroLista etiqueta="Estado" valor={f.valores.estado} onChange={(estado) => f.poner({ estado })} opciones={[{ valor: 'PENDIENTE', texto: 'Pendiente' }, { valor: 'COMPLETADO', texto: 'Completado' }]} />
    </BarraFiltros>
    <Paginado items={filas} reinicio={[filtro, f.valores]}>{(pagina) => <Tabla cabeceras={['Pedido (OP)', 'Cliente', 'Fecha de entrega', 'Artículos', 'Estado', '']}>
      {pagina.length === 0 ? <Vacio columnas={6} /> : pagina.map((p) => (
        <tr key={p.id} className="border-b border-line align-top last:border-0">
          <td className={`${td} font-mono font-semibold`}>{p.numeroOp}</td><td className={td}>{p.cliente}</td><td className={td}>{fechaCorta(p.fechaEntrega)}</td>
          <td className={`${td} text-xs text-ink-soft`}>{p.detalles.map((d) => <div key={d.id}><span className="font-mono">{d.codigoArticulo}</span> · {kg(d.pesoTotalRequerido)}</div>)}</td>
          <td className={td}><StatusPill tone={p.estado === 'COMPLETADO' ? 'success' : 'neutral'} label={p.estado === 'COMPLETADO' ? 'Completado' : 'Pendiente'} /></td>
          <td className={`${td} text-right`}><span className="whitespace-nowrap"><BotonModificar editable={editable} onClick={() => { setEditando(p); setPesos({}); g.setError(null) }} /><BotonEliminar editable={editable} onClick={() => borrar.pedir(p)} /></span></td>
        </tr>))}
    </Tabla>}</Paginado>
    <Drawer open={editando !== null} onClose={() => setEditando(null)} title={`Modificar pedido ${editando?.numeroOp ?? ''}`}
      description="Un cambio de fecha de entrega cambia la prioridad de sus partidas en la próxima generación."
      footer={<PieFormulario onCancelar={() => setEditando(null)} onGuardar={guardar} guardando={g.guardando} />}>
      {editando && <div className="space-y-4">
        <AvisoError error={g.error} />
        <Field label="Cliente"><input className={inputClass} value={editando.cliente} onChange={(e) => setEditando({ ...editando, cliente: e.target.value })} /></Field>
        <Field label="Fecha de entrega"><input type="date" className={inputClass} value={editando.fechaEntrega} onChange={(e) => setEditando({ ...editando, fechaEntrega: e.target.value })} /></Field>
        {editando.detalles.map((d) => <Field key={d.id} label={`Peso total requerido · ${d.codigoArticulo} (kg)`}>
          <input type="number" min="0.01" step="0.01" className={inputClass} value={pesos[d.id] ?? String(d.pesoTotalRequerido)} onChange={(e) => setPesos({ ...pesos, [d.id]: e.target.value })} /></Field>)}
      </div>}
    </Drawer>
    <ConfirmDialog open={borrar.fila !== null} title={`Eliminar el pedido ${borrar.fila?.numeroOp ?? ''}`}
      description={`Se eliminarán el pedido y sus ${borrar.fila?.detalles.length ?? 0} artículos del pedido. Si tiene partidas o proyección, primero hay que eliminarlas.`}
      confirmLabel="Eliminar" tone="danger" onConfirm={borrar.confirmar} onCancel={borrar.cancelar} />
  </>
}

// -------------------------------------------------------------- artículos
/** Tipo de tela: las palabras del nombre antes de la primera medida, p. ej. «FRENCH TERRY 3 HILOS…» → «FRENCH TERRY». */
function tipoDeTela(nombre: string) {
  const palabras: string[] = []
  for (const p of nombre.trim().split(/\s+/)) { if (/\d/.test(p)) break; palabras.push(p) }
  return palabras.join(' ') || nombre.trim().split(/\s+/)[0]
}

function conLicra(nombre: string) {
  return /\bLYC|F\/LYC/i.test(nombre)
}

function Articulos({ filtro, editable }: { filtro: string; editable: boolean }) {
  const lista = useDatos(() => api.articulos(), [])
  const [editando, setEditando] = useState<ArticuloRegistrado | null>(null)
  const g = useGuardado()
  const borrar = useEliminacion<ArticuloRegistrado>((a) => api.eliminarArticulo(a.id), lista.recargar)
  const f = useFiltros({ tipo: '', licra: '' })
  if (lista.cargando) return <Cargando />
  if (lista.error) return <AvisoError error={lista.error} />
  const todas = lista.datos ?? []
  const filas = recientesPrimero(todas).filter((a) => coincide(filtro, a.codigo, a.nombre)
    && (!f.valores.tipo || tipoDeTela(a.nombre) === f.valores.tipo) && (!f.valores.licra || (f.valores.licra === 'SI') === conLicra(a.nombre)))
  async function guardar() {
    if (!editando) return
    if (await g.guardar(() => api.modificarArticulo(editando.id, editando.nombre))) { setEditando(null); await lista.recargar() }
  }
  return <>
    <AvisoError error={borrar.error} onCerrar={() => borrar.setError(null)} />
    <BarraFiltros visibles={filas.length} total={todas.length} activos={f.activos} onLimpiar={f.limpiar}>
      <FiltroLista etiqueta="Tipo de tela" valor={f.valores.tipo} onChange={(tipo) => f.poner({ tipo })} opciones={sinRepetir(todas.map((a) => tipoDeTela(a.nombre))).map((t) => ({ valor: t, texto: t }))} />
      <FiltroLista etiqueta="Licra" valor={f.valores.licra} onChange={(licra) => f.poner({ licra })} opciones={[{ valor: 'SI', texto: 'Con licra' }, { valor: 'NO', texto: 'Sin licra' }]} />
    </BarraFiltros>
    <Paginado items={filas} reinicio={[filtro, f.valores]}>{(pagina) => <Tabla cabeceras={['Código (clave de la bolsa)', 'Nombre', '']} minimo={640}>
      {pagina.length === 0 ? <Vacio columnas={3} /> : pagina.map((a) => (
        <tr key={a.id} className="border-b border-line last:border-0"><td className={`${td} font-mono font-semibold`}>{a.codigo}</td><td className={td}>{a.nombre}</td>
          <td className={`${td} text-right`}><span className="whitespace-nowrap"><BotonModificar editable={editable} onClick={() => { setEditando(a); g.setError(null) }} /><BotonEliminar editable={editable} onClick={() => borrar.pedir(a)} /></span></td></tr>))}
    </Tabla>}</Paginado>
    <Drawer open={editando !== null} onClose={() => setEditando(null)} title={`Modificar artículo ${editando?.codigo ?? ''}`}
      footer={<PieFormulario onCancelar={() => setEditando(null)} onGuardar={guardar} guardando={g.guardando} />}>
      {editando && <div className="space-y-4"><AvisoError error={g.error} />
        <Field label="Nombre"><input className={inputClass} value={editando.nombre} onChange={(e) => setEditando({ ...editando, nombre: e.target.value })} /></Field></div>}
    </Drawer>
    <ConfirmDialog open={borrar.fila !== null} title={`Eliminar el artículo ${borrar.fila?.codigo ?? ''}`}
      description="Solo se puede eliminar si ningún pedido lo usa." confirmLabel="Eliminar" tone="danger" onConfirm={borrar.confirmar} onCancel={borrar.cancelar} />
  </>
}

// --------------------------------------------------------------- partidas
function Partidas({ filtro, editable }: { filtro: string; editable: boolean }) {
  const lista = useDatos(() => api.partidas(), [])
  const colores = useDatos(() => api.colores(), [])
  const [editando, setEditando] = useState<PartidaRegistrada | null>(null)
  const [aviso, setAviso] = useState<string | null>(null)
  const [confirmar, setConfirmar] = useState<string | null>(null)
  const g = useGuardado()
  const borrar = useEliminacion<PartidaRegistrada>((p) => api.eliminarPartida(p.id), lista.recargar)
  const f = useFiltros({ barca: '', tonalidad: '', estado: '' })
  if (lista.cargando) return <Cargando />
  if (lista.error) return <AvisoError error={lista.error} />
  const todas = lista.datos ?? []
  const filas = recientesPrimero(todas).filter((p) => coincide(filtro, p.numeroPartida, p.numeroOp, p.color, p.codigoColor, ...p.componentes.map((c) => c.codigoArticulo))
    && (!f.valores.barca || (f.valores.barca === 'SIN' ? !p.barca : p.barca === f.valores.barca))
    && (!f.valores.tonalidad || p.tonalidad === f.valores.tonalidad) && (!f.valores.estado || p.estado === f.valores.estado))

  // D-24: si la partida está Asignada o Sobrante en una planificación vigente abierta, el backend pide confirmación
  async function guardar(confirmado = false) {
    if (!editando) return
    setConfirmar(null)
    g.setError(null)
    const original = lista.datos?.find((p) => p.id === editando.id)
    const r = await intentar(async () => {
      const respuesta = await api.modificarPartida(editando.id, { codigoColor: editando.codigoColor, barca: editando.barca || null, pesoTotal: editando.pesoTotal }, confirmado)
      for (const c of editando.componentes) {
        const antes = original?.componentes.find((x) => x.id === c.id)
        if (!antes || antes.tipoComponente !== c.tipoComponente || antes.tubularAbierto !== c.tubularAbierto || antes.pesoRequerido !== c.pesoRequerido)
          await api.modificarComponente(c.id, { tipoComponente: c.tipoComponente, tubularAbierto: c.tubularAbierto, pesoRequerido: c.pesoRequerido }, confirmado)
      }
      return respuesta
    })
    if (!r.ok) { if (r.error.codigo === 'CONFIRMACION_REQUERIDA') setConfirmar(r.error.message); else g.setError(r.error); return }
    setAviso(r.valor.advertencia); setEditando(null); await lista.recargar()
  }

  return <>
    <AvisoError error={borrar.error} onCerrar={() => borrar.setError(null)} />
    {aviso && <p className="mb-4 rounded-xl border border-warning/40 bg-warning-soft p-3 text-sm text-warning">{aviso}</p>}
    <BarraFiltros visibles={filas.length} total={todas.length} activos={f.activos} onLimpiar={f.limpiar}>
      <FiltroLista etiqueta="Barca" valor={f.valores.barca} onChange={(barca) => f.poner({ barca })} opciones={[...sinRepetir(todas.map((p) => p.barca)).map((b) => ({ valor: b, texto: b })), ...(todas.some((p) => !p.barca) ? [{ valor: 'SIN', texto: 'Sin barca' }] : [])]} />
      <FiltroLista etiqueta="Tonalidad" valor={f.valores.tonalidad} onChange={(tonalidad) => f.poner({ tonalidad })} opciones={Object.entries(TONALIDAD).map(([valor, texto]) => ({ valor, texto }))} />
      <FiltroLista etiqueta="Estado" valor={f.valores.estado} onChange={(estado) => f.poner({ estado })} opciones={Object.entries(ESTADO_PARTIDA).map(([valor, texto]) => ({ valor, texto }))} />
    </BarraFiltros>
    <Paginado items={filas} reinicio={[filtro, f.valores]}>{(pagina) => <Tabla cabeceras={['Partida', 'Pedido', 'Color', 'Barca', 'Kg', 'Componentes', 'Estado', 'Registrada', '']} minimo={1150}>
      {pagina.length === 0 ? <Vacio columnas={9} /> : pagina.map((p) => (
        <tr key={p.id} className="border-b border-line align-top last:border-0">
          <td className={`${td} font-mono font-semibold`}>{p.numeroPartida}</td><td className={`${td} font-mono`}>{p.numeroOp}</td>
          <td className={td}>{p.color}<span className="block text-xs text-ink-faint">{p.codigoColor} · {TONALIDAD[p.tonalidad]} · {CLASIFICACION_COLOR[p.clasificacionColor]}</span></td>
          <td className={`${td} font-mono`}>{p.barca ?? <span className="text-danger">sin barca</span>}</td><td className={`${td} font-mono`}>{kg(p.pesoTotal)}</td>
          <td className={`${td} text-xs text-ink-soft`}>{p.componentes.map((c) => <div key={c.id}>{c.tipoComponente === 'PRINCIPAL' ? 'P' : 'C'} · <span className="font-mono">{c.codigoArticulo}</span> · {kg(c.pesoRequerido)}</div>)}</td>
          <td className={td}><StatusPill tone={TONO_PARTIDA[p.estado]} label={ESTADO_PARTIDA[p.estado]} /></td>
          <td className={`${td} text-xs text-ink-soft`}>{fechaHora(p.fechaRegistro)}</td>
          <td className={`${td} text-right`}><span className="whitespace-nowrap"><BotonModificar editable={editable && p.estado === 'PENDIENTE'} onClick={() => { setEditando(structuredClone(p)); setAviso(null); g.setError(null) }} /><BotonEliminar editable={editable && p.estado === 'PENDIENTE'} onClick={() => borrar.pedir(p)} /></span></td>
        </tr>))}
    </Tabla>}</Paginado>
    <Drawer open={editando !== null} onClose={() => setEditando(null)} title={`Modificar partida ${editando?.numeroPartida ?? ''}`}
      description="Solo se modifican partidas pendientes. La letra de la barca debe coincidir con la clasificación del color. Si la partida está en la planificación vigente, esa planificación deja de ser vigente."
      footer={<PieFormulario onCancelar={() => setEditando(null)} onGuardar={() => guardar()} guardando={g.guardando} />}>
      {editando && <div className="space-y-4"><AvisoError error={g.error} />
        <Field label="Color"><select className={inputClass} value={editando.codigoColor} onChange={(e) => setEditando({ ...editando, codigoColor: e.target.value })}>
          {(colores.datos ?? []).map((c) => <option key={c.id} value={c.codigoColor}>{c.codigoColor} · {c.nombre} ({CLASIFICACION_COLOR[c.clasificacionColor]}, {TONALIDAD[c.tonalidad]})</option>)}</select></Field>
        <Field label="Barca" hint="Capacidad y letra, por ejemplo «400 C»."><input className={inputClass} value={editando.barca ?? ''} onChange={(e) => setEditando({ ...editando, barca: e.target.value })} /></Field>
        <Field label="Peso total (kg)"><input type="number" min="0.001" step="0.001" className={inputClass} value={editando.pesoTotal} onChange={(e) => setEditando({ ...editando, pesoTotal: Number(e.target.value) })} /></Field>
        <h4 className="pt-2 text-sm font-semibold text-ink">Componentes</h4>
        {editando.componentes.map((c, i) => <div key={c.id} className="space-y-2 rounded-lg border border-line p-3">
          <p className="font-mono text-xs text-ink-soft">{c.codigoArticulo} · {c.nombreArticulo}</p>
          <div className="grid grid-cols-3 gap-2">
            <select className={inputClass} value={c.tipoComponente} onChange={(e) => { const comps = [...editando.componentes]; comps[i] = { ...c, tipoComponente: e.target.value as typeof c.tipoComponente }; setEditando({ ...editando, componentes: comps }) }}><option value="PRINCIPAL">Principal</option><option value="COMPLEMENTO">Complemento</option></select>
            <select className={inputClass} value={c.tubularAbierto ? 'SI' : 'NO'} onChange={(e) => { const comps = [...editando.componentes]; comps[i] = { ...c, tubularAbierto: e.target.value === 'SI' }; setEditando({ ...editando, componentes: comps }) }}><option value="NO">Tubular</option><option value="SI">Abierto</option></select>
            <input type="number" min="0.001" step="0.001" className={inputClass} value={c.pesoRequerido} onChange={(e) => { const comps = [...editando.componentes]; comps[i] = { ...c, pesoRequerido: Number(e.target.value) }; setEditando({ ...editando, componentes: comps }) }} />
          </div></div>)}
      </div>}
    </Drawer>
    <ConfirmDialog open={confirmar !== null} title="Modificar una partida planificada" description={confirmar} confirmLabel="Continuar" tone="danger"
      onConfirm={() => guardar(true)} onCancel={() => setConfirmar(null)} />
    <ConfirmDialog open={borrar.fila !== null} title={`Eliminar la partida ${borrar.fila?.numeroPartida ?? ''}`}
      description={`Se eliminarán la partida y sus ${borrar.fila?.componentes.length ?? 0} componentes. Si ya aparece en una planificación, no se podrá eliminar.`}
      confirmLabel="Eliminar" tone="danger" onConfirm={borrar.confirmar} onCancel={borrar.cancelar} />
  </>
}

// ---------------------------------------------------------------- colores
function Colores({ filtro, editable }: { filtro: string; editable: boolean }) {
  const lista = useDatos(() => api.colores(), [])
  const [editando, setEditando] = useState<ColorRegistrado | null>(null)
  const g = useGuardado()
  const borrar = useEliminacion<ColorRegistrado>((c) => api.eliminarColor(c.id), lista.recargar)
  const f = useFiltros({ clasificacion: '', tonalidad: '' })
  if (lista.cargando) return <Cargando />
  if (lista.error) return <AvisoError error={lista.error} />
  const todas = lista.datos ?? []
  const filas = recientesPrimero(todas).filter((c) => coincide(filtro, c.codigoColor, c.nombre)
    && (!f.valores.clasificacion || c.clasificacionColor === f.valores.clasificacion) && (!f.valores.tonalidad || c.tonalidad === f.valores.tonalidad))
  async function guardar() {
    if (!editando) return
    if (await g.guardar(() => api.modificarColor(editando.id, { nombre: editando.nombre, clasificacionColor: editando.clasificacionColor, tonalidad: editando.tonalidad }))) {
      setEditando(null); await lista.recargar()
    }
  }
  return <>
    <AvisoError error={borrar.error} onCerrar={() => borrar.setError(null)} />
    <BarraFiltros visibles={filas.length} total={todas.length} activos={f.activos} onLimpiar={f.limpiar}>
      <FiltroLista etiqueta="Clasificación" valor={f.valores.clasificacion} onChange={(clasificacion) => f.poner({ clasificacion })} opciones={Object.entries(CLASIFICACION_COLOR).map(([valor, texto]) => ({ valor, texto }))} />
      <FiltroLista etiqueta="Tonalidad" valor={f.valores.tonalidad} onChange={(tonalidad) => f.poner({ tonalidad })} opciones={Object.entries(TONALIDAD).map(([valor, texto]) => ({ valor, texto }))} />
    </BarraFiltros>
    <Paginado items={filas} reinicio={[filtro, f.valores]}>{(pagina) => <Tabla cabeceras={['Código', 'Nombre', 'Clasificación', 'Tonalidad', '']} minimo={700}>
      {pagina.length === 0 ? <Vacio columnas={5} /> : pagina.map((c) => (
        <tr key={c.id} className="border-b border-line last:border-0"><td className={`${td} font-mono font-semibold`}>{c.codigoColor}</td><td className={td}>{c.nombre}</td>
          <td className={td}>{CLASIFICACION_COLOR[c.clasificacionColor]}</td><td className={td}>{TONALIDAD[c.tonalidad]}</td>
          <td className={`${td} text-right`}><span className="whitespace-nowrap"><BotonModificar editable={editable} onClick={() => { setEditando(c); g.setError(null) }} /><BotonEliminar editable={editable} onClick={() => borrar.pedir(c)} /></span></td></tr>))}
    </Tabla>}</Paginado>
    <Drawer open={editando !== null} onClose={() => setEditando(null)} title={`Modificar color ${editando?.codigoColor ?? ''}`}
      description="La tonalidad define los lavados (paso a una más clara en la misma máquina)."
      footer={<PieFormulario onCancelar={() => setEditando(null)} onGuardar={guardar} guardando={g.guardando} />}>
      {editando && <div className="space-y-4"><AvisoError error={g.error} />
        <Field label="Nombre"><input className={inputClass} value={editando.nombre} onChange={(e) => setEditando({ ...editando, nombre: e.target.value })} /></Field>
        <Field label="Clasificación de color"><select className={inputClass} value={editando.clasificacionColor} onChange={(e) => setEditando({ ...editando, clasificacionColor: e.target.value as 'C' | 'B' })}><option value="C">Color (C)</option><option value="B">Blanco (B)</option></select></Field>
        <Field label="Tonalidad"><select className={inputClass} value={editando.tonalidad} onChange={(e) => setEditando({ ...editando, tonalidad: e.target.value as ColorRegistrado['tonalidad'] })}>
          {(Object.keys(TONALIDAD) as ColorRegistrado['tonalidad'][]).map((t) => <option key={t} value={t}>{TONALIDAD[t]}</option>)}</select></Field>
      </div>}
    </Drawer>
    <ConfirmDialog open={borrar.fila !== null} title={`Eliminar el color ${borrar.fila?.codigoColor ?? ''}`}
      description="Solo se puede eliminar si ninguna partida lo usa." confirmLabel="Eliminar" tone="danger" onConfirm={borrar.confirmar} onCancel={borrar.cancelar} />
  </>
}

// ------------------------------------------------------------- proyección
function Proyeccion({ filtro, editable }: { filtro: string; editable: boolean }) {
  const f = useFiltros({ mes: '' })
  const { lunes } = useSemana()
  const versiones = useDatos(() => api.versionesProyeccion(lunes), [lunes])
  const vigente = versiones.datos?.find((v) => v.estado === 'VIGENTE') ?? null
  const detalle = useDatos<DetalleProyeccion[]>(() => (vigente ? api.detalleProyeccion(vigente.id) : Promise.resolve([])), [vigente?.id])
  const [editando, setEditando] = useState<DetalleProyeccion | null>(null)
  const [confirmar, setConfirmar] = useState<string | null>(null)
  const [porEliminar, setPorEliminar] = useState<DetalleProyeccion | null>(null)
  const [conPlanificacion, setConPlanificacion] = useState<string | null>(null)
  const [errorEliminar, setErrorEliminar] = useState<ErrorApi | null>(null)
  const g = useGuardado()

  async function eliminar(confirmado = false) {
    if (!porEliminar) return
    setConPlanificacion(null)
    setErrorEliminar(null)
    const fila = porEliminar
    const r = await intentar(() => api.eliminarDetalleProyeccion(fila.id, confirmado))
    if (!r.ok) {
      if (r.error.codigo === 'CONFIRMACION_REQUERIDA') { setConPlanificacion(r.error.message); return }
      setPorEliminar(null); setErrorEliminar(r.error); return
    }
    setPorEliminar(null)
    await Promise.all([detalle.recargar(), versiones.recargar()])
  }

  async function guardar(confirmado = false) {
    if (!editando) return
    setConfirmar(null)
    g.setError(null)
    const r = await intentar(() => api.modificarDetalleProyeccion(editando.id, { fechaProyectada: editando.fechaProyectada, kgProyectados: editando.kgProyectados }, confirmado))
    if (!r.ok) { if (r.error.codigo === 'CONFIRMACION_REQUERIDA') setConfirmar(r.error.message); else g.setError(r.error); return }
    setEditando(null)
    await detalle.recargar()
  }

  const todas = detalle.datos ?? []
  const filas = todas.filter((d) => coincide(filtro, d.numeroOp, d.codigoArticulo, d.nombreArticulo)
    && enMes(d.fechaProyectada, f.valores.mes))

  return <>
    <AvisoError error={errorEliminar} onCerrar={() => setErrorEliminar(null)} />
    <div className="mb-4 flex flex-wrap items-center gap-3"><WeekSelector />
      <span className="text-sm text-ink-soft">{vigente ? `Versión vigente v${vigente.version} · ${vigente.registros} registros` : `Sin proyección para la semana del ${etiquetaSemana(lunes)}`}</span></div>
    <BarraFiltros visibles={filas.length} total={todas.length} activos={f.activos} onLimpiar={f.limpiar}>
      <FiltroMes etiqueta="Mes de tejido" valor={f.valores.mes} fechas={todas.map((d) => d.fechaProyectada)} onChange={(mes) => f.poner({ mes })} />
    </BarraFiltros>
    {detalle.cargando ? <Cargando /> : <Paginado items={filas} reinicio={[filtro, f.valores, vigente?.id]}>{(pagina) => <Tabla cabeceras={['Pedido', 'Artículo', 'Fecha de tejido', 'Disponible en tintorería', 'Kg', '']} minimo={850}>
      {pagina.length === 0 ? <Vacio columnas={6} /> : pagina.map((d) => (
        <tr key={d.id} className="border-b border-line last:border-0">
          <td className={`${td} font-mono`}>{d.numeroOp}</td><td className={td}><span className="font-mono">{d.codigoArticulo}</span><span className="block text-xs text-ink-faint">{d.nombreArticulo}</span></td>
          <td className={td}>{fechaCorta(d.fechaProyectada)}{!d.enVentana && <span className="ml-2 text-xs text-warning">fuera de la ventana</span>}</td>
          <td className={td}>{fechaCorta(d.fechaDisponible)}</td><td className={`${td} font-mono`}>{kg(d.kgProyectados)}</td>
          <td className={`${td} text-right`}><span className="whitespace-nowrap"><BotonModificar editable={editable} onClick={() => { setEditando(d); g.setError(null) }} /><BotonEliminar editable={editable} onClick={() => { setErrorEliminar(null); setPorEliminar(d) }} /></span></td>
        </tr>))}
    </Tabla>}</Paginado>}
    <Drawer open={editando !== null} onClose={() => setEditando(null)} title="Corregir registro de proyección"
      description="Si la semana tiene planificación vigente, esta corrección la deja sin vigencia hasta volver a generarla."
      footer={<PieFormulario onCancelar={() => setEditando(null)} onGuardar={() => guardar()} guardando={g.guardando} />}>
      {editando && <div className="space-y-4"><AvisoError error={g.error} />
        <p className="text-sm text-ink-soft">Pedido {editando.numeroOp} · {editando.codigoArticulo}</p>
        <Field label="Fecha de tejido"><input type="date" className={inputClass} value={editando.fechaProyectada} onChange={(e) => setEditando({ ...editando, fechaProyectada: e.target.value })} /></Field>
        <Field label="Kg proyectados"><input type="number" min="0.001" step="0.001" className={inputClass} value={editando.kgProyectados} onChange={(e) => setEditando({ ...editando, kgProyectados: Number(e.target.value) })} /></Field>
      </div>}
    </Drawer>
    <ConfirmDialog open={confirmar !== null} title="Corregir la proyección de la semana" description={confirmar} confirmLabel="Continuar" tone="danger"
      onConfirm={() => guardar(true)} onCancel={() => setConfirmar(null)} />
    <ConfirmDialog open={porEliminar !== null} title="Eliminar registro de proyección"
      description={conPlanificacion ?? `Se eliminarán ${porEliminar ? kg(porEliminar.kgProyectados) : ''} de ${porEliminar?.codigoArticulo ?? ''} del pedido ${porEliminar?.numeroOp ?? ''}.`}
      confirmLabel="Eliminar" tone="danger" onConfirm={() => eliminar(conPlanificacion !== null)} onCancel={() => { setPorEliminar(null); setConPlanificacion(null) }} />
  </>
}
