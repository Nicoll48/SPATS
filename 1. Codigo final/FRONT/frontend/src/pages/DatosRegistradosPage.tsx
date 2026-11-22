import { useState, type ReactNode } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { ArrowLeft, ChevronRight, Download, Link2, Pencil, Trash2, UploadCloud, X } from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { SoloLecturaAviso } from '@/components/common/SoloLecturaAviso'
import { AvisoError } from '@/components/common/AvisoError'
import { Drawer } from '@/components/ui/Drawer'
import { Field, inputClass } from '@/components/ui/FormField'
import { StatusPill } from '@/components/ui/StatusPill'
import { BarraFiltros, BusquedaTabla, FiltroLista, aplicado, enMes, sinRepetir, useFiltros } from '@/components/common/Filtros'
import { Paginado } from '@/components/ui/Paginacion'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Boton, claseBoton } from '@/components/ui/Boton'
import { MenuAcciones, type AccionMenu } from '@/components/ui/MenuAcciones'
import { Notificacion } from '@/components/ui/Notificacion'
import { FilaVacia, TablaCargando, TablaDatos, celda, filaTabla } from '@/components/ui/TablaDatos'
import { WeekSelector } from '@/components/planning/WeekSelector'
import { useAuth } from '@/auth/auth-context'
import { useSemana } from '@/semana/semana-context'
import { cargas, datos as api } from '@/api/servicios'
import type { ErrorApi } from '@/api/cliente'
import type { ArticuloRegistrado, ColorRegistrado, DetalleProyeccion, PartidaRegistrada, PedidoRegistrado, TipoDato } from '@/api/tipos'
import { intentar, useDatos } from '@/lib/useDatos'
import { CLASIFICACION_COLOR, ESTADO_PARTIDA, TONALIDAD, TONO_PARTIDA, kg } from '@/lib/etiquetas'
import { etiquetaSemana, fechaCorta, fechaHora } from '@/lib/fechas'

/*
 * Datos registrados (RF12, RF13, D-42): según Carbon, cada tipo de dato —con su propio listado,
 * filtros y acciones— tiene su propia página. Todas comparten la misma estructura:
 * encabezado con «Descargar en Excel» → barra de herramientas (búsqueda, filtros, filtros
 * aplicados) → tabla con menú ⋮ por fila → paginación. Todo cambio queda en la trazabilidad.
 */

// ------------------------------------------------------------ piezas comunes
function EncabezadoDatos({ titulo, descripcion, exportar, semana, extra }: {
  titulo: string
  descripcion: string
  exportar: TipoDato
  semana?: string
  extra?: ReactNode
}) {
  const [error, setError] = useState<ErrorApi | null>(null)
  async function descargar() {
    setError(null)
    const r = await intentar(() => cargas.exportar(exportar, semana))
    if (!r.ok) setError(r.error)
  }
  return <>
    <PageHeader title={titulo} description={descripcion}
      actions={<>{extra}<Boton variante="secondary" onClick={descargar}><Download className="h-4 w-4" aria-hidden="true" />Descargar en Excel</Boton></>} />
    <SoloLecturaAviso />
    <AvisoError error={error} onCerrar={() => setError(null)} />
  </>
}

function coincide(filtro: string, ...valores: (string | number | null | undefined)[]) {
  const f = filtro.trim().toLowerCase()
  return !f || valores.some((v) => String(v ?? '').toLowerCase().includes(f))
}

/** Lo último registrado va primero; el id crece con cada carga. */
function recientesPrimero<T extends { id: number }>(filas: T[]) {
  return [...filas].sort((a, b) => b.id - a.id)
}

/** Acciones de fila estándar: Modificar y Eliminar (esta última al final, en rojo). */
function accionesFila(onModificar: () => void, onEliminar: () => void, motivo?: string): AccionMenu[] {
  return [
    { texto: 'Modificar', icono: Pencil, onClick: onModificar, motivoDeshabilitada: motivo },
    { texto: 'Eliminar', icono: Trash2, onClick: onEliminar, peligro: true, motivoDeshabilitada: motivo },
  ]
}

/** Las cabeceras de una tabla, con la columna de acciones solo si el usuario puede editar. */
function cabeceras(columnas: string[], editable: boolean) {
  return editable ? [...columnas, ''] : columnas
}

/** Estado vacío de cada listado: sin datos lleva a Carga de datos; con filtros, permite limpiarlos. */
function Vacio({ columnas, filtrado, entidad, onLimpiar, editable }: { columnas: number; filtrado: boolean; entidad: string; onLimpiar: () => void; editable: boolean }) {
  return <FilaVacia columnas={columnas} filtrado={filtrado} titulo={`Todavía no hay ${entidad} registrados`}
    descripcion={`Los ${entidad} se registran al subir su plantilla en Carga de datos.`}
    accion={filtrado
      ? <Boton variante="ghost" tamano="sm" onClick={onLimpiar}>Limpiar búsqueda y filtros</Boton>
      : editable && <Link to="/datos-semanales" className={claseBoton('tertiary', 'sm')}><UploadCloud className="h-4 w-4" aria-hidden="true" />Ir a Carga de datos</Link>} />
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

function PieFormulario({ onCancelar, onGuardar, guardando }: { onCancelar: () => void; onGuardar: () => void; guardando: boolean }) {
  return <><Boton variante="secondary" onClick={onCancelar}>Cancelar</Boton>
    <Boton onClick={onGuardar} disabled={guardando}>{guardando ? 'Guardando…' : 'Guardar cambios'}</Boton></>
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

// ------------------------------------------------------- datos relacionados
/*
 * Navegación entre datos relacionados: un pedido lleva a sus artículos y partidas (no a la proyección, pedido de la autora); una partida, a su
 * pedido, su color y sus artículos; un color, a sus partidas. El dato de origen viaja en la URL
 * (?pedido=34275, ?articulo=…, ?color=…), así «Atrás» del navegador regresa y la vista se puede recargar.
 */
type Relacion = 'pedido' | 'articulo' | 'color'
const NOMBRE_RELACION: Record<Relacion, string> = { pedido: 'Pedido', articulo: 'Artículo', color: 'Color' }

function rutaDato(pagina: 'pedidos' | 'articulos' | 'partidas' | 'colores' | 'proyeccion', filtros: Partial<Record<Relacion, string>>) {
  const q = new URLSearchParams(Object.entries(filtros).filter((e): e is [string, string] => !!e[1]))
  return `/datos-registrados/${pagina}${q.size > 0 ? `?${q}` : ''}`
}

/** Filtros que llegan desde otra página, leídos de la URL; quitar uno lo borra de la URL. */
function useRelacion() {
  const [params, setParams] = useSearchParams()
  const valor = (k: Relacion) => params.get(k) || null
  const quitar = (k: Relacion | 'todos') => {
    const q = new URLSearchParams(params)
    for (const x of (k === 'todos' ? Object.keys(NOMBRE_RELACION) : [k])) q.delete(x)
    setParams(q, { replace: true })
  }
  const activos = (Object.keys(NOMBRE_RELACION) as Relacion[]).filter((k) => valor(k) !== null)
  return { pedido: valor('pedido'), articulo: valor('articulo'), color: valor('color'), activos, valor, quitar }
}

/** Enlace a un dato relacionado (código de pedido, artículo o color), sin activar el clic de la fila. */
function EnlaceDato({ a, titulo, children, className = '' }: { a: string; titulo: string; children: ReactNode; className?: string }) {
  return (
    <Link to={a} title={titulo} onClick={(e) => e.stopPropagation()}
      className={`rounded font-mono text-accent underline-offset-2 hover:underline focus-visible:underline ${className}`}>{children}</Link>
  )
}

/** Enlaces de la columna «Relacionados» de cada fila. */
function Relacionados({ enlaces }: { enlaces: { texto: string; a: string; titulo: string }[] }) {
  return (
    <span className="flex flex-wrap gap-x-3 gap-y-1 text-xs">
      {enlaces.map((e) => (
        <Link key={e.texto} to={e.a} title={e.titulo} onClick={(ev) => ev.stopPropagation()}
          className="inline-flex items-center gap-0.5 font-medium text-accent underline-offset-2 hover:underline">{e.texto}<ChevronRight className="h-3 w-3" aria-hidden="true" /></Link>
      ))}
    </span>
  )
}

/**
 * Franja que explica de dónde viene el filtro («Artículos del pedido 34275 · Cliente · entrega 30 nov»), con
 * «Volver» (atrás del navegador) y la etiqueta de cada filtro para quitarlo.
 */
function FranjaRelacion({ rel, titulo, detalle }: { rel: ReturnType<typeof useRelacion>; titulo: ReactNode; detalle?: ReactNode }) {
  const navegar = useNavigate()
  if (rel.activos.length === 0) return null
  return (
    <div role="status" className="mb-4 flex flex-wrap items-center gap-x-3 gap-y-2 rounded-lg border border-line border-l-[3px] border-l-accent bg-accent-soft px-4 py-2.5 text-sm text-ink">
      <Link2 className="h-4 w-4 shrink-0 text-accent" aria-hidden="true" />
      <span className="min-w-0"><span className="font-semibold">{titulo}</span>{detalle && <span className="text-ink-soft"> · {detalle}</span>}</span>
      <span className="flex flex-wrap gap-1.5">
        {rel.activos.map((k) => (
          <span key={k} className="inline-flex items-center gap-1 rounded-full border border-line bg-panel px-2 py-0.5 text-xs">
            {NOMBRE_RELACION[k]}: <span className="font-mono font-semibold">{rel.valor(k)}</span>
            <button type="button" onClick={() => rel.quitar(k)} aria-label={`Quitar el filtro ${NOMBRE_RELACION[k]}`} className="rounded p-0.5 text-ink-soft hover:text-ink"><X className="h-3 w-3" /></button>
          </span>
        ))}
      </span>
      <span className="ml-auto flex items-center gap-3 text-xs font-medium">
        <button type="button" onClick={() => navegar(-1)} className="inline-flex items-center gap-1 text-accent hover:underline"><ArrowLeft className="h-3.5 w-3.5" />Volver</button>
        <button type="button" onClick={() => rel.quitar('todos')} className="text-accent hover:underline">Ver todos</button>
      </span>
    </div>
  )
}

// ---------------------------------------------------------------- pedidos
const ESTADO_PEDIDO = [{ valor: 'PENDIENTE', texto: 'Pendiente' }, { valor: 'COMPLETADO', texto: 'Completado' }]

export function PedidosPage() {
  const { esPlanificador: editable } = useAuth()
  const navegar = useNavigate()
  const rel = useRelacion()
  const [busqueda, setBusqueda] = useState('')
  const lista = useDatos(() => api.pedidos(), [])
  const [editando, setEditando] = useState<PedidoRegistrado | null>(null)
  const [pesos, setPesos] = useState<Record<number, string>>({})
  const g = useGuardado()
  const borrar = useEliminacion<PedidoRegistrado>((p) => api.eliminarPedido(p.id), lista.recargar)
  const f = useFiltros({ cliente: '', estado: '', mes: '' })

  const todas = lista.datos ?? []
  const filas = recientesPrimero(todas).filter((p) => coincide(busqueda, p.numeroOp, p.cliente, ...p.detalles.map((d) => d.codigoArticulo))
    && (!rel.pedido || p.numeroOp === rel.pedido) && (!rel.articulo || p.detalles.some((d) => d.codigoArticulo === rel.articulo))
    && (!f.valores.cliente || p.cliente === f.valores.cliente) && (!f.valores.estado || p.estado === f.valores.estado)
    && enMes(p.fechaEntrega, f.valores.mes))
  const clientes = sinRepetir(todas.map((p) => p.cliente)).map((c) => ({ valor: c, texto: c }))
  const meses = opcionesMes(todas.map((p) => p.fechaEntrega))
  const limpiar = () => { f.limpiar(); setBusqueda('') }

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

  const columnas = cabeceras(['Pedido (OP)', 'Cliente', 'Fecha de entrega', 'Artículos', 'Estado', 'Relacionados'], editable)
  return <>
    <EncabezadoDatos titulo="Pedidos" exportar="PEDIDOS"
      descripcion="Pedidos de los clientes con su fecha de entrega y los artículos que incluyen. La fecha de entrega define la prioridad de sus partidas. Al elegir un pedido se ven sus artículos." />
    <FranjaRelacion rel={rel} titulo={rel.articulo ? `Pedidos que incluyen el artículo ${rel.articulo}` : `Pedido ${rel.pedido}`} />
    <AvisoError error={borrar.error} onCerrar={() => borrar.setError(null)} />
    {lista.error ? <AvisoError error={lista.error} /> : <>
      <BarraFiltros visibles={filas.length} total={todas.length} activos={f.activos} onLimpiar={f.limpiar}
        busqueda={<BusquedaTabla valor={busqueda} onChange={setBusqueda} placeholder="Buscar por pedido, cliente o artículo" />}
        aplicados={[
          ...aplicado('Cliente', f.valores.cliente, clientes, () => f.poner({ cliente: '' })),
          ...aplicado('Mes de entrega', f.valores.mes, meses, () => f.poner({ mes: '' })),
          ...aplicado('Estado', f.valores.estado, ESTADO_PEDIDO, () => f.poner({ estado: '' })),
        ]}>
        <FiltroLista etiqueta="Cliente" valor={f.valores.cliente} onChange={(cliente) => f.poner({ cliente })} opciones={clientes} />
        <FiltroLista etiqueta="Mes de entrega" valor={f.valores.mes} onChange={(mes) => f.poner({ mes })} opciones={meses} />
        <FiltroLista etiqueta="Estado" valor={f.valores.estado} onChange={(estado) => f.poner({ estado })} opciones={ESTADO_PEDIDO} />
      </BarraFiltros>
      {lista.cargando ? <TablaCargando columnas={columnas.length} /> : <Paginado items={filas} reinicio={[busqueda, f.valores]}>{(pagina) => <TablaDatos cabeceras={columnas} minimo={900}>
        {pagina.length === 0 ? <Vacio columnas={columnas.length} filtrado={todas.length > 0 || rel.activos.length > 0} entidad="pedidos" onLimpiar={() => { limpiar(); rel.quitar('todos') }} editable={editable} /> : pagina.map((p) => (
          <tr key={p.id} className={`${filaTabla} cursor-pointer align-top`} title={`Ver los artículos del pedido ${p.numeroOp}`}
            onClick={() => navegar(rutaDato('articulos', { pedido: p.numeroOp }))}>
            <td className={`${celda} font-mono font-semibold`}><span className="inline-flex items-center gap-1">{p.numeroOp}<ChevronRight className="h-3.5 w-3.5 text-ink-faint" aria-hidden="true" /></span></td><td className={celda}>{p.cliente}</td><td className={celda}>{fechaCorta(p.fechaEntrega)}</td>
            <td className={`${celda} text-xs text-ink-soft`}>{p.detalles.map((d) => <div key={d.id}><EnlaceDato a={rutaDato('articulos', { pedido: p.numeroOp, articulo: d.codigoArticulo })} titulo={`Ver el artículo ${d.codigoArticulo}`}>{d.codigoArticulo}</EnlaceDato> · {kg(d.pesoTotalRequerido)}</div>)}</td>
            <td className={celda}><StatusPill tone={p.estado === 'COMPLETADO' ? 'success' : 'neutral'} label={p.estado === 'COMPLETADO' ? 'Completado' : 'Pendiente'} /></td>
            <td className={celda}><Relacionados enlaces={[
              { texto: 'Artículos', a: rutaDato('articulos', { pedido: p.numeroOp }), titulo: `Artículos del pedido ${p.numeroOp}` },
              { texto: 'Partidas', a: rutaDato('partidas', { pedido: p.numeroOp }), titulo: `Partidas del pedido ${p.numeroOp}` },
            ]} /></td>
            {editable && <td className={`${celda} w-12 py-2 text-right`} onClick={(e) => e.stopPropagation()}><MenuAcciones etiqueta={`Acciones del pedido ${p.numeroOp}`}
              acciones={accionesFila(() => { setEditando(p); setPesos({}); g.setError(null) }, () => borrar.pedir(p))} /></td>}
          </tr>))}
      </TablaDatos>}</Paginado>}
    </>}
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
      confirmLabel="Eliminar pedido" tone="danger" onConfirm={borrar.confirmar} onCancel={borrar.cancelar} />
  </>
}

/** Meses en que caen las fechas, del más reciente al más antiguo, con su nombre legible («Noviembre de 2026»). */
const NOMBRE_MES = new Intl.DateTimeFormat('es-PE', { month: 'long', year: 'numeric' })
function opcionesMes(fechas: (string | null | undefined)[]) {
  return [...new Set(fechas.filter((x): x is string => !!x).map((x) => x.slice(0, 7)))].sort().reverse().map((m) => {
    const texto = NOMBRE_MES.format(new Date(Number(m.slice(0, 4)), Number(m.slice(5, 7)) - 1, 1))
    return { valor: m, texto: texto.charAt(0).toUpperCase() + texto.slice(1) }
  })
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

const LICRA = [{ valor: 'SI', texto: 'Con licra' }, { valor: 'NO', texto: 'Sin licra' }]

export function ArticulosPage() {
  const { esPlanificador: editable } = useAuth()
  const rel = useRelacion()
  const [busqueda, setBusqueda] = useState('')
  const lista = useDatos(() => api.articulos(), [])
  // Con ?pedido=…, solo los artículos de ese pedido y sus kilos en él
  const pedidos = useDatos(() => (rel.pedido ? api.pedidos() : Promise.resolve([] as PedidoRegistrado[])), [rel.pedido])
  const pedido = rel.pedido ? (pedidos.datos ?? []).find((p) => p.numeroOp === rel.pedido) ?? null : null
  const kgEnPedido = (codigo: string) => pedido?.detalles.filter((d) => d.codigoArticulo === codigo).reduce((s, d) => s + d.pesoTotalRequerido, 0) ?? 0
  const [editando, setEditando] = useState<ArticuloRegistrado | null>(null)
  const g = useGuardado()
  const borrar = useEliminacion<ArticuloRegistrado>((a) => api.eliminarArticulo(a.id), lista.recargar)
  const f = useFiltros({ tipo: '', licra: '' })
  const todas = lista.datos ?? []
  const filas = recientesPrimero(todas).filter((a) => coincide(busqueda, a.codigo, a.nombre)
    && (!rel.pedido || (pedido?.detalles.some((d) => d.codigoArticulo === a.codigo) ?? false)) && (!rel.articulo || a.codigo === rel.articulo)
    && (!f.valores.tipo || tipoDeTela(a.nombre) === f.valores.tipo) && (!f.valores.licra || (f.valores.licra === 'SI') === conLicra(a.nombre)))
  const tipos = sinRepetir(todas.map((a) => tipoDeTela(a.nombre))).map((t) => ({ valor: t, texto: t }))
  const limpiar = () => { f.limpiar(); setBusqueda('') }
  async function guardar() {
    if (!editando) return
    if (await g.guardar(() => api.modificarArticulo(editando.id, editando.nombre))) { setEditando(null); await lista.recargar() }
  }
  const columnas = cabeceras(['Código (clave de la bolsa)', 'Nombre', 'Tipo de tela', ...(pedido ? ['Kg en este pedido'] : []), 'Relacionados'], editable)
  return <>
    <EncabezadoDatos titulo="Artículos" exportar="PEDIDOS"
      descripcion="Telas que se piden y tejen. El código del artículo es la clave de su bolsa de tejeduría. Se registran junto con los pedidos y se descargan en el mismo Excel." />
    <FranjaRelacion rel={rel}
      titulo={rel.pedido ? (rel.articulo ? `Artículo ${rel.articulo} del pedido ${rel.pedido}` : `Artículos del pedido ${rel.pedido}`) : `Artículo ${rel.articulo}`}
      detalle={pedido ? `${pedido.cliente} · entrega ${fechaCorta(pedido.fechaEntrega)}` : undefined} />
    <AvisoError error={borrar.error} onCerrar={() => borrar.setError(null)} />
    {lista.error ? <AvisoError error={lista.error} /> : <>
      <BarraFiltros visibles={filas.length} total={todas.length} activos={f.activos} onLimpiar={f.limpiar}
        busqueda={<BusquedaTabla valor={busqueda} onChange={setBusqueda} placeholder="Buscar por código o nombre" />}
        aplicados={[...aplicado('Tipo de tela', f.valores.tipo, tipos, () => f.poner({ tipo: '' })), ...aplicado('Licra', f.valores.licra, LICRA, () => f.poner({ licra: '' }))]}>
        <FiltroLista etiqueta="Tipo de tela" valor={f.valores.tipo} onChange={(tipo) => f.poner({ tipo })} opciones={tipos} />
        <FiltroLista etiqueta="Licra" valor={f.valores.licra} onChange={(licra) => f.poner({ licra })} opciones={LICRA} />
      </BarraFiltros>
      {lista.cargando || pedidos.cargando ? <TablaCargando columnas={columnas.length} /> : <Paginado items={filas} reinicio={[busqueda, f.valores, rel.pedido, rel.articulo]}>{(pagina) => <TablaDatos cabeceras={columnas} minimo={800}>
        {pagina.length === 0 ? <Vacio columnas={columnas.length} filtrado={todas.length > 0 || rel.activos.length > 0} entidad="artículos" onLimpiar={() => { limpiar(); rel.quitar('todos') }} editable={editable} /> : pagina.map((a) => (
          <tr key={a.id} className={filaTabla}><td className={`${celda} font-mono font-semibold`}>{a.codigo}</td><td className={celda}>{a.nombre}</td><td className={`${celda} text-ink-soft`}>{tipoDeTela(a.nombre)}</td>
            {pedido && <td className={`${celda} font-mono`}>{kg(kgEnPedido(a.codigo))}</td>}
            <td className={celda}><Relacionados enlaces={[
              { texto: 'Pedidos', a: rutaDato('pedidos', { articulo: a.codigo }), titulo: `Pedidos que incluyen el artículo ${a.codigo}` },
              { texto: 'Partidas', a: rutaDato('partidas', { articulo: a.codigo, pedido: rel.pedido ?? undefined }), titulo: `Partidas con el artículo ${a.codigo}` },
            ]} /></td>
            {editable && <td className={`${celda} w-12 py-2 text-right`}><MenuAcciones etiqueta={`Acciones del artículo ${a.codigo}`}
              acciones={accionesFila(() => { setEditando(a); g.setError(null) }, () => borrar.pedir(a))} /></td>}</tr>))}
      </TablaDatos>}</Paginado>}
    </>}
    <Drawer open={editando !== null} onClose={() => setEditando(null)} title={`Modificar artículo ${editando?.codigo ?? ''}`}
      footer={<PieFormulario onCancelar={() => setEditando(null)} onGuardar={guardar} guardando={g.guardando} />}>
      {editando && <div className="space-y-4"><AvisoError error={g.error} />
        <Field label="Nombre"><input className={inputClass} value={editando.nombre} onChange={(e) => setEditando({ ...editando, nombre: e.target.value })} /></Field></div>}
    </Drawer>
    <ConfirmDialog open={borrar.fila !== null} title={`Eliminar el artículo ${borrar.fila?.codigo ?? ''}`}
      description="Solo se puede eliminar si ningún pedido lo usa." confirmLabel="Eliminar artículo" tone="danger" onConfirm={borrar.confirmar} onCancel={borrar.cancelar} />
  </>
}

// --------------------------------------------------------------- partidas
const OPC_TONALIDAD = Object.entries(TONALIDAD).map(([valor, texto]) => ({ valor, texto }))
const OPC_ESTADO_PARTIDA = Object.entries(ESTADO_PARTIDA).map(([valor, texto]) => ({ valor, texto }))
const OPC_COHERENCIA = [{ valor: 'INCOHERENTE', texto: 'Sin máquina compatible' }]

export function PartidasPage() {
  const { esPlanificador: editable } = useAuth()
  const rel = useRelacion()
  const [busqueda, setBusqueda] = useState('')
  const lista = useDatos(() => api.partidas(), [])
  // D-51: la compatibilidad depende de la capacidad y las tonalidades aceptadas de las máquinas; la decide el backend
  const conflicto = useDatos(() => api.partidasSinMaquinaCompatible(), [lista.datos])
  const colores = useDatos(() => api.colores(), [])
  const [editando, setEditando] = useState<PartidaRegistrada | null>(null)
  const [aviso, setAviso] = useState<string | null>(null)
  const [confirmar, setConfirmar] = useState<string | null>(null)
  const g = useGuardado()
  const borrar = useEliminacion<PartidaRegistrada>((p) => api.eliminarPartida(p.id), lista.recargar)
  const f = useFiltros({ barca: '', tonalidad: '', estado: '', coherencia: '' })
  const todas = lista.datos ?? []
  const idsIncoherentes = new Set((conflicto.datos ?? []).map((p) => p.id))
  const incoherente = (p: PartidaRegistrada) => idsIncoherentes.has(p.id)
  const filas = recientesPrimero(todas).filter((p) => coincide(busqueda, p.numeroPartida, p.numeroOp, p.color, p.codigoColor, ...p.componentes.map((c) => c.codigoArticulo))
    && (!f.valores.barca || (f.valores.barca === 'SIN' ? !p.barca : p.barca === f.valores.barca))
    && (!f.valores.tonalidad || p.tonalidad === f.valores.tonalidad) && (!f.valores.estado || p.estado === f.valores.estado)
    && (!f.valores.coherencia || incoherente(p))
    && (!rel.pedido || p.numeroOp === rel.pedido) && (!rel.color || p.codigoColor === rel.color)
    && (!rel.articulo || p.componentes.some((c) => c.codigoArticulo === rel.articulo)))
  const incoherentes = todas.filter(incoherente).length
  const barcas = [...sinRepetir(todas.map((p) => p.barca)).map((b) => ({ valor: b, texto: b })), ...(todas.some((p) => !p.barca) ? [{ valor: 'SIN', texto: 'Sin barca' }] : [])]
  const limpiar = () => { f.limpiar(); setBusqueda('') }

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

  const columnas = cabeceras(['Partida', 'Pedido', 'Color', 'Barca', 'Kg', 'Componentes', 'Estado', 'Registrada'], editable)
  return <>
    <EncabezadoDatos titulo="Partidas" exportar="PARTIDAS"
      descripcion="Lotes por teñir con su color, tonalidad, barca y componentes (tela principal y complementos). Solo las partidas pendientes se pueden modificar o eliminar." />
    <FranjaRelacion rel={rel} titulo={`Partidas ${[rel.pedido && `del pedido ${rel.pedido}`, rel.articulo && `con el artículo ${rel.articulo}`, rel.color && `del color ${rel.color}`].filter(Boolean).join(', ')}`} />
    <AvisoError error={borrar.error} onCerrar={() => borrar.setError(null)} />
    {aviso && <Notificacion tipo="warning" titulo="Partida modificada con una advertencia" onCerrar={() => setAviso(null)}>{aviso}</Notificacion>}
    {incoherentes > 0 && !f.valores.coherencia && (
      <Notificacion tipo="error" titulo={`${incoherentes} ${incoherentes === 1 ? 'partida pendiente no tiene' : 'partidas pendientes no tienen'} ninguna máquina de su capacidad que acepte su tonalidad`}
        accion={<Boton variante="ghost" tamano="sm" onClick={() => f.poner({ coherencia: 'INCOHERENTE' })}>Ver partidas</Boton>}>
        Al generar quedarán Bloqueadas con el motivo «Ninguna máquina de su capacidad acepta su tonalidad». Revisa las tonalidades que aceptan las máquinas en Máquinas, o corrige la barca o el color de la partida.
      </Notificacion>
    )}
    {lista.error ? <AvisoError error={lista.error} /> : <>
      <BarraFiltros visibles={filas.length} total={todas.length} activos={f.activos} onLimpiar={f.limpiar}
        busqueda={<BusquedaTabla valor={busqueda} onChange={setBusqueda} placeholder="Buscar por partida, pedido, color o artículo" />}
        aplicados={[
          ...aplicado('Barca', f.valores.barca, barcas, () => f.poner({ barca: '' })),
          ...aplicado('Tonalidad', f.valores.tonalidad, OPC_TONALIDAD, () => f.poner({ tonalidad: '' })),
          ...aplicado('Estado', f.valores.estado, OPC_ESTADO_PARTIDA, () => f.poner({ estado: '' })),
          ...aplicado('Compatibilidad', f.valores.coherencia, OPC_COHERENCIA, () => f.poner({ coherencia: '' })),
        ]}>
        <FiltroLista etiqueta="Barca" valor={f.valores.barca} onChange={(barca) => f.poner({ barca })} opciones={barcas} />
        <FiltroLista etiqueta="Tonalidad" valor={f.valores.tonalidad} onChange={(tonalidad) => f.poner({ tonalidad })} opciones={OPC_TONALIDAD} />
        <FiltroLista etiqueta="Estado" valor={f.valores.estado} onChange={(estado) => f.poner({ estado })} opciones={OPC_ESTADO_PARTIDA} />
        <FiltroLista etiqueta="Compatibilidad" valor={f.valores.coherencia} onChange={(coherencia) => f.poner({ coherencia })} opciones={OPC_COHERENCIA} />
      </BarraFiltros>
      {lista.cargando ? <TablaCargando columnas={columnas.length} /> : <Paginado items={filas} reinicio={[busqueda, f.valores, rel.pedido, rel.color, rel.articulo]}>{(pagina) => <TablaDatos cabeceras={columnas} minimo={1150}>
        {pagina.length === 0 ? <Vacio columnas={columnas.length} filtrado={todas.length > 0 || rel.activos.length > 0} entidad="partidas" onLimpiar={() => { limpiar(); rel.quitar('todos') }} editable={editable} /> : pagina.map((p) => (
          <tr key={p.id} className={`${filaTabla} align-top`}>
            <td className={`${celda} font-mono font-semibold`}>{p.numeroPartida}</td><td className={celda}><EnlaceDato a={rutaDato('pedidos', { pedido: p.numeroOp })} titulo={`Ver el pedido ${p.numeroOp}`}>{p.numeroOp}</EnlaceDato></td>
            <td className={celda}><EnlaceDato a={rutaDato('colores', { color: p.codigoColor })} titulo={`Ver el color ${p.codigoColor}`} className="font-sans">{p.color}</EnlaceDato><span className="block text-xs text-ink-faint">{p.codigoColor} · {TONALIDAD[p.tonalidad]} · {CLASIFICACION_COLOR[p.clasificacionColor]}</span></td>
            <td className={`${celda} font-mono`}>{p.barca ?? <span className="text-danger">Sin barca</span>}{incoherente(p) && <span className="mt-1 block"><StatusPill tone="danger" label="Sin máquina compatible" /></span>}</td>
            <td className={`${celda} font-mono`}>{kg(p.pesoTotal)}</td>
            <td className={`${celda} text-xs text-ink-soft`}>{p.componentes.map((c) => <div key={c.id}>{c.tipoComponente === 'PRINCIPAL' ? 'P' : 'C'} · <EnlaceDato a={rutaDato('articulos', { articulo: c.codigoArticulo })} titulo={`Ver el artículo ${c.codigoArticulo}`}>{c.codigoArticulo}</EnlaceDato> · {kg(c.pesoRequerido)}</div>)}</td>
            <td className={celda}><StatusPill tone={TONO_PARTIDA[p.estado]} label={ESTADO_PARTIDA[p.estado]} /></td>
            <td className={`${celda} text-xs text-ink-soft`}>{fechaHora(p.fechaRegistro)}</td>
            {editable && <td className={`${celda} w-12 py-2 text-right`}><MenuAcciones etiqueta={`Acciones de la partida ${p.numeroPartida}`}
              acciones={accionesFila(() => { setEditando(structuredClone(p)); setAviso(null); g.setError(null) }, () => borrar.pedir(p),
                p.estado === 'PENDIENTE' ? undefined : 'Solo se modifican o eliminan partidas pendientes')} /></td>}
          </tr>))}
      </TablaDatos>}</Paginado>}
    </>}
    <Drawer open={editando !== null} onClose={() => setEditando(null)} title={`Modificar partida ${editando?.numeroPartida ?? ''}`}
      description="Solo se modifican partidas pendientes. La letra de la barca debe coincidir con la clasificación del color. Si la partida está en la planificación vigente, esa planificación deja de ser vigente."
      footer={<PieFormulario onCancelar={() => setEditando(null)} onGuardar={() => guardar()} guardando={g.guardando} />}>
      {editando && <div className="space-y-4"><AvisoError error={g.error} />
        <Field label="Color"><select className={inputClass} value={editando.codigoColor} onChange={(e) => setEditando({ ...editando, codigoColor: e.target.value })}>
          {(colores.datos ?? []).map((c) => <option key={c.id} value={c.codigoColor}>{c.codigoColor} · {c.nombre} ({CLASIFICACION_COLOR[c.clasificacionColor]}, {TONALIDAD[c.tonalidad]})</option>)}</select></Field>
        <Field label="Barca" hint="Empieza con la capacidad, por ejemplo «400 C». La letra es informativa."><input className={inputClass} value={editando.barca ?? ''} onChange={(e) => setEditando({ ...editando, barca: e.target.value })} /></Field>
        <Field label="Peso total (kg)"><input type="number" min="0.001" step="0.001" className={inputClass} value={editando.pesoTotal} onChange={(e) => setEditando({ ...editando, pesoTotal: Number(e.target.value) })} /></Field>
        <h4 className="pt-2 text-sm font-semibold text-ink">Componentes</h4>
        {editando.componentes.map((c, i) => <div key={c.id} className="space-y-2 rounded-lg border border-line p-3">
          <p className="font-mono text-xs text-ink-soft">{c.codigoArticulo} · {c.nombreArticulo}</p>
          <div className="grid grid-cols-3 gap-2">
            <select aria-label="Tipo de componente" className={inputClass} value={c.tipoComponente} onChange={(e) => { const comps = [...editando.componentes]; comps[i] = { ...c, tipoComponente: e.target.value as typeof c.tipoComponente }; setEditando({ ...editando, componentes: comps }) }}><option value="PRINCIPAL">Principal</option><option value="COMPLEMENTO">Complemento</option></select>
            <select aria-label="Tubular o abierto" className={inputClass} value={c.tubularAbierto ? 'SI' : 'NO'} onChange={(e) => { const comps = [...editando.componentes]; comps[i] = { ...c, tubularAbierto: e.target.value === 'SI' }; setEditando({ ...editando, componentes: comps }) }}><option value="NO">Tubular</option><option value="SI">Abierto</option></select>
            <input aria-label="Peso requerido (kg)" type="number" min="0.001" step="0.001" className={inputClass} value={c.pesoRequerido} onChange={(e) => { const comps = [...editando.componentes]; comps[i] = { ...c, pesoRequerido: Number(e.target.value) }; setEditando({ ...editando, componentes: comps }) }} />
          </div></div>)}
      </div>}
    </Drawer>
    <ConfirmDialog open={confirmar !== null} title="Modificar una partida planificada" description={confirmar} confirmLabel="Continuar" tone="danger"
      onConfirm={() => guardar(true)} onCancel={() => setConfirmar(null)} />
    <ConfirmDialog open={borrar.fila !== null} title={`Eliminar la partida ${borrar.fila?.numeroPartida ?? ''}`}
      description={`Se eliminarán la partida y sus ${borrar.fila?.componentes.length ?? 0} componentes. Si ya aparece en una planificación, no se podrá eliminar.`}
      confirmLabel="Eliminar partida" tone="danger" onConfirm={borrar.confirmar} onCancel={borrar.cancelar} />
  </>
}

// ---------------------------------------------------------------- colores
const OPC_CLASIFICACION = Object.entries(CLASIFICACION_COLOR).map(([valor, texto]) => ({ valor, texto }))

export function ColoresPage() {
  const { esPlanificador: editable } = useAuth()
  const rel = useRelacion()
  const [busqueda, setBusqueda] = useState('')
  const lista = useDatos(() => api.colores(), [])
  const [editando, setEditando] = useState<ColorRegistrado | null>(null)
  const g = useGuardado()
  const borrar = useEliminacion<ColorRegistrado>((c) => api.eliminarColor(c.id), lista.recargar)
  const f = useFiltros({ clasificacion: '', tonalidad: '' })
  const todas = lista.datos ?? []
  const filas = recientesPrimero(todas).filter((c) => coincide(busqueda, c.codigoColor, c.nombre) && (!rel.color || c.codigoColor === rel.color)
    && (!f.valores.clasificacion || c.clasificacionColor === f.valores.clasificacion) && (!f.valores.tonalidad || c.tonalidad === f.valores.tonalidad))
  const limpiar = () => { f.limpiar(); setBusqueda('') }
  async function guardar() {
    if (!editando) return
    if (await g.guardar(() => api.modificarColor(editando.id, { nombre: editando.nombre, clasificacionColor: editando.clasificacionColor, tonalidad: editando.tonalidad }))) {
      setEditando(null); await lista.recargar()
    }
  }
  const columnas = cabeceras(['Código', 'Nombre', 'Clasificación', 'Tonalidad', 'Relacionados'], editable)
  return <>
    <EncabezadoDatos titulo="Colores" exportar="PARTIDAS"
      descripcion="Colores de teñido con su clasificación (Blanco o Color) y tonalidad. La tonalidad define los lavados entre partidas de una misma máquina. Se descargan junto con las partidas." />
    <FranjaRelacion rel={rel} titulo={`Color ${rel.color}`} />
    <AvisoError error={borrar.error} onCerrar={() => borrar.setError(null)} />
    {lista.error ? <AvisoError error={lista.error} /> : <>
      <BarraFiltros visibles={filas.length} total={todas.length} activos={f.activos} onLimpiar={f.limpiar}
        busqueda={<BusquedaTabla valor={busqueda} onChange={setBusqueda} placeholder="Buscar por código o nombre" />}
        aplicados={[...aplicado('Clasificación', f.valores.clasificacion, OPC_CLASIFICACION, () => f.poner({ clasificacion: '' })), ...aplicado('Tonalidad', f.valores.tonalidad, OPC_TONALIDAD, () => f.poner({ tonalidad: '' }))]}>
        <FiltroLista etiqueta="Clasificación" valor={f.valores.clasificacion} onChange={(clasificacion) => f.poner({ clasificacion })} opciones={OPC_CLASIFICACION} />
        <FiltroLista etiqueta="Tonalidad" valor={f.valores.tonalidad} onChange={(tonalidad) => f.poner({ tonalidad })} opciones={OPC_TONALIDAD} />
      </BarraFiltros>
      {lista.cargando ? <TablaCargando columnas={columnas.length} /> : <Paginado items={filas} reinicio={[busqueda, f.valores, rel.color]}>{(pagina) => <TablaDatos cabeceras={columnas} minimo={800}>
        {pagina.length === 0 ? <Vacio columnas={columnas.length} filtrado={todas.length > 0 || rel.activos.length > 0} entidad="colores" onLimpiar={() => { limpiar(); rel.quitar('todos') }} editable={editable} /> : pagina.map((c) => (
          <tr key={c.id} className={filaTabla}><td className={`${celda} font-mono font-semibold`}>{c.codigoColor}</td><td className={celda}>{c.nombre}</td>
            <td className={celda}>{CLASIFICACION_COLOR[c.clasificacionColor]}</td><td className={celda}>{TONALIDAD[c.tonalidad]}</td>
            <td className={celda}><Relacionados enlaces={[{ texto: 'Partidas', a: rutaDato('partidas', { color: c.codigoColor }), titulo: `Partidas del color ${c.codigoColor}` }]} /></td>
            {editable && <td className={`${celda} w-12 py-2 text-right`}><MenuAcciones etiqueta={`Acciones del color ${c.codigoColor}`}
              acciones={accionesFila(() => { setEditando(c); g.setError(null) }, () => borrar.pedir(c))} /></td>}</tr>))}
      </TablaDatos>}</Paginado>}
    </>}
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
      description="Solo se puede eliminar si ninguna partida lo usa." confirmLabel="Eliminar color" tone="danger" onConfirm={borrar.confirmar} onCancel={borrar.cancelar} />
  </>
}

// ------------------------------------------------------------- proyección
export function ProyeccionPage() {
  const { esPlanificador: editable } = useAuth()
  const rel = useRelacion()
  const [busqueda, setBusqueda] = useState('')
  const f = useFiltros({ mes: '', ventana: '' })
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
  const filas = todas.filter((d) => coincide(busqueda, d.numeroOp, d.codigoArticulo, d.nombreArticulo)
    && enMes(d.fechaProyectada, f.valores.mes) && (!f.valores.ventana || !d.enVentana)
    && (!rel.pedido || d.numeroOp === rel.pedido) && (!rel.articulo || d.codigoArticulo === rel.articulo))
  const meses = opcionesMes(todas.map((d) => d.fechaProyectada))
  const OPC_VENTANA = [{ valor: 'FUERA', texto: 'Fuera de la ventana de la semana' }]
  const limpiar = () => { f.limpiar(); setBusqueda('') }
  const columnas = cabeceras(['Pedido', 'Artículo', 'Fecha de tejido', 'Disponible en tintorería', 'Kg'], editable)
  const cargando = versiones.cargando || detalle.cargando

  return <>
    <EncabezadoDatos titulo="Proyección de tejeduría" exportar="PROYECCION_TEJEDURIA" semana={lunes} extra={<WeekSelector />}
      descripcion="Kilos que tejeduría tejerá por pedido y artículo cada día. Se muestra la versión vigente de la semana elegida; las demás versiones se gestionan en Carga de datos." />
    <AvisoError error={errorEliminar} onCerrar={() => setErrorEliminar(null)} />
    <FranjaRelacion rel={rel} titulo={`Proyección ${[rel.pedido && `del pedido ${rel.pedido}`, rel.articulo && `del artículo ${rel.articulo}`].filter(Boolean).join(', ')}`}
      detalle={`semana del ${etiquetaSemana(lunes)}`} />
    {!versiones.cargando && (vigente
      ? <p className="mb-4 flex flex-wrap items-center gap-2 text-sm text-ink-soft"><StatusPill tone="success" label={`Versión vigente V${vigente.version}`} />{vigente.registros} registros · {kg(vigente.kgTotales)} · cargada el {fechaHora(vigente.fechaCarga)}</p>
      : <Notificacion tipo="info" titulo={`La semana del ${etiquetaSemana(lunes)} no tiene proyección vigente`}
          accion={editable && <Link to="/datos-semanales" className={claseBoton('ghost', 'sm')}>Ir a Carga de datos</Link>}>
          Elige otra semana o sube la proyección de esta semana.
        </Notificacion>)}
    {detalle.error ? <AvisoError error={detalle.error} /> : vigente && <>
      <BarraFiltros visibles={filas.length} total={todas.length} activos={f.activos} onLimpiar={f.limpiar}
        busqueda={<BusquedaTabla valor={busqueda} onChange={setBusqueda} placeholder="Buscar por pedido o artículo" />}
        aplicados={[...aplicado('Mes de tejido', f.valores.mes, meses, () => f.poner({ mes: '' })), ...aplicado('Ventana', f.valores.ventana, OPC_VENTANA, () => f.poner({ ventana: '' }))]}>
        <FiltroLista etiqueta="Mes de tejido" valor={f.valores.mes} onChange={(mes) => f.poner({ mes })} opciones={meses} />
        <FiltroLista etiqueta="Ventana" valor={f.valores.ventana} onChange={(ventana) => f.poner({ ventana })} opciones={OPC_VENTANA} />
      </BarraFiltros>
      {cargando ? <TablaCargando columnas={columnas.length} /> : <Paginado items={filas} reinicio={[busqueda, f.valores, vigente.id, rel.pedido, rel.articulo]}>{(pagina) => <TablaDatos cabeceras={columnas} minimo={850}>
        {pagina.length === 0 ? <Vacio columnas={columnas.length} filtrado={todas.length > 0 || rel.activos.length > 0} entidad="registros de proyección" onLimpiar={() => { limpiar(); rel.quitar('todos') }} editable={editable} /> : pagina.map((d) => (
          <tr key={d.id} className={filaTabla}>
            <td className={celda}><EnlaceDato a={rutaDato('pedidos', { pedido: d.numeroOp })} titulo={`Ver el pedido ${d.numeroOp}`}>{d.numeroOp}</EnlaceDato></td><td className={celda}><EnlaceDato a={rutaDato('articulos', { articulo: d.codigoArticulo })} titulo={`Ver el artículo ${d.codigoArticulo}`}>{d.codigoArticulo}</EnlaceDato><span className="block text-xs text-ink-faint">{d.nombreArticulo}</span></td>
            <td className={celda}>{fechaCorta(d.fechaProyectada)}{!d.enVentana && <span className="ml-2"><StatusPill tone="warning" label="Fuera de la ventana" /></span>}</td>
            <td className={celda}>{fechaCorta(d.fechaDisponible)}</td><td className={`${celda} font-mono`}>{kg(d.kgProyectados)}</td>
            {editable && <td className={`${celda} w-12 py-2 text-right`}><MenuAcciones etiqueta={`Acciones del registro ${d.numeroOp} · ${d.codigoArticulo}`}
              acciones={accionesFila(() => { setEditando(d); g.setError(null) }, () => { setErrorEliminar(null); setPorEliminar(d) })} /></td>}
          </tr>))}
      </TablaDatos>}</Paginado>}
    </>}
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
      confirmLabel="Eliminar registro" tone="danger" onConfirm={() => eliminar(conPlanificacion !== null)} onCancel={() => { setPorEliminar(null); setConPlanificacion(null) }} />
  </>
}
