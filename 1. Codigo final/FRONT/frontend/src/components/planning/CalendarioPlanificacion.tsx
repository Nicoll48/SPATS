import { useState, type ReactNode } from 'react'
import { Ban, CalendarDays, CalendarRange, ChevronDown, ChevronRight, Droplets, HardDrive, Layers, Weight, X } from 'lucide-react'
import { CLASIFICACION_COLOR, MUESTRA_TONALIDAD, TONALIDAD, kg } from '@/lib/etiquetas'
import { diaMes, fechaCorta, nombreDia } from '@/lib/fechas'
import {
  agruparPorVersion, bordeVersion, cabeceraVersion, colorVersion, estadoCarga, fondoVersion, etiquetaVersion, type CalendarioComparado, type CeldaComparada,
  type ContadorVersion, type EstadoCarga, type MaquinaComparada, type TarjetaPartida,
} from '@/lib/versiones'

type Vista = 'semana' | 'dia' | 'maquina'
interface Seleccion { maquina: string; dia: string }

/** Todas las celdas tienen el mismo fondo gris claro; la carga del día solo se ve en el contador (D-52). */
const FONDO_CELDA = 'bg-panel-muted/50'
const FONDO_INDISPONIBLE = 'bg-panel-muted/50 bg-[repeating-linear-gradient(135deg,transparent,transparent_6px,var(--color-panel-muted)_6px,var(--color-panel-muted)_12px)]'

const TEXTO_CARGA: Record<EstadoCarga, string> = { baja: 'text-warning', completa: 'text-success', normal: 'text-ink-soft' }
const TITULO_CONTADOR = 'Asignadas del día / máximo diario. Ámbar: por debajo del mínimo del día · verde: día completo'

function detallePartida(t: TarjetaPartida) {
  const p = t.partida
  const sobranteEn = t.versiones.filter((v) => v.clasificacion === 'SOBRANTE').map((v) => etiquetaVersion(v.version))
  return `${p.numeroPartida} · pedido ${p.numeroOp} · ${p.color} (${TONALIDAD[p.tonalidad]}, ${CLASIFICACION_COLOR[p.clasificacionColor]}) · ${kg(p.kg)}`
    + `\nEntrega: ${fechaCorta(p.fechaEntrega)} · disponible desde ${fechaCorta(p.fechaDisponible)}`
    + `\nVersión: ${t.versiones.map((v) => etiquetaVersion(v.version)).join(' y ')}${t.caso === 'igual' ? ' (igual en ambas)' : ''}`
    + (t.versiones.some((v) => v.requiereLavado) ? '\nLavado previo: la partida anterior de la máquina es más oscura' : '')
    + (sobranteEn.length > 0 ? `\nSobrante${t.versiones.length > 1 ? ` en ${sobranteEn.join(' y ')}` : ''}: excede el objetivo semanal o ya no había espacio` : '')
    + (t.enLaOtra ? `\n${t.enLaOtra}` : '')
    + (p.recomendada ? `\nRecomendada: tejer ${kg(p.recomendada.kgATejer)} a más tardar el ${fechaCorta(p.recomendada.diaTejido)}` : '')
}

function Punto({ tonalidad, className = 'h-2.5 w-2.5' }: { tonalidad: keyof typeof MUESTRA_TONALIDAD; className?: string }) {
  return <span className={`${className} shrink-0 rounded-full border border-black/20`} style={{ backgroundColor: MUESTRA_TONALIDAD[tonalidad] }} aria-hidden="true" />
}

/** Franja vertical con el color de la versión; con dos versiones, mitad y mitad. */
export function FranjaVersion({ versiones, className = 'w-1' }: { versiones: number[]; className?: string }) {
  const fondo = versiones.length > 1
    ? `linear-gradient(to bottom, ${colorVersion(versiones[0])} 50%, ${colorVersion(versiones[1])} 50%)`
    : colorVersion(versiones[0])
  return <span aria-hidden="true" className={`absolute inset-y-0 left-0 rounded-l ${className}`} style={{ background: fondo }} />
}

/** Etiqueta «V1», «V2» o «V1·V2», cada parte en su color, para no depender solo del color. */
export function EtiquetaVersion({ versiones, className = 'text-[9px]' }: { versiones: number[]; className?: string }) {
  return (
    <span className={`shrink-0 font-sans font-bold ${className}`} aria-label={`Versión ${versiones.map(etiquetaVersion).join(' y ')}`}>
      {versiones.map((v, i) => <span key={v}>{i > 0 && <span className="text-ink-faint">·</span>}<span style={{ color: colorVersion(v) }}>{etiquetaVersion(v)}</span></span>)}
    </span>
  )
}

type Grupo = ReturnType<typeof agruparPorVersion>[number]

/**
 * Dónde va el contador de cada versión: en la cabecera de su propia caja; si en la celda no tiene partidas propias,
 * en la caja de las iguales en ambas; y si tampoco, queda «suelto» y se muestra sobre las cajas.
 */
function repartirContadores(grupos: Grupo[], contadores: ContadorVersion[]) {
  const porGrupo = new Map<string, ContadorVersion[]>()
  const sueltos: ContadorVersion[] = []
  const compartido = grupos.find((g) => g.versiones.length > 1)
  for (const c of contadores) {
    const destino = grupos.find((g) => g.versiones.length === 1 && g.versiones[0] === c.version) ?? compartido
    if (!destino) { sueltos.push(c); continue }
    const clave = destino.versiones.join('-')
    porGrupo.set(clave, [...(porGrupo.get(clave) ?? []), c])
  }
  return { porGrupo, sueltos }
}

/**
 * Caja de una versión en una celda, según imagenes/colores.png: borde claro y fondo suave de la versión; arriba, un
 * cuadrito con el color oscuro de la versión del tamaño de «V1» y, a la derecha, su contador «2/3» sobre el fondo de la
 * caja; debajo, las partidas en tarjetas blancas. Las iguales en ambas versiones van en una caja neutra con los
 * cuadritos «V1» y «V2» juntos, con un pequeño espacio. Con una sola versión marcada no hay caja ni color (D-52).
 */
function CajaVersion({ versiones, conColor, compacta = false, contadores = [], children }: {
  versiones: number[]; conColor: boolean; compacta?: boolean; contadores?: ContadorVersion[]; children: ReactNode
}) {
  const separacion = compacta ? 'gap-0.5' : 'gap-1'
  if (!conColor) return <div className={`flex flex-col ${separacion}`}>{children}</div>
  const dos = versiones.length > 1
  return (
    <div className={`flex min-w-0 flex-col rounded-md border ${compacta ? 'p-0.5' : 'p-1'} ${separacion}`}
      style={{ borderColor: dos ? 'var(--color-line)' : bordeVersion(versiones[0]), background: dos ? 'var(--color-panel-muted)' : fondoVersion(versiones[0]) }}>
      <div className={`flex items-center gap-1 font-sans font-bold leading-none ${compacta ? 'text-[9px]' : 'text-[11px]'}`}
        aria-label={`Versión ${versiones.map(etiquetaVersion).join(' y ')}`}>
        {/* Cuadritos «V1» y, en la caja de las iguales, «V2» al lado con un pequeño espacio. */}
        <span className="flex shrink-0 items-center gap-0.5">
          {versiones.map((v) => (
            <span key={v} className={`rounded-sm text-white ${compacta ? 'px-1 py-[3px]' : 'px-1.5 py-1'}`} style={{ background: cabeceraVersion(v) }}>
              {etiquetaVersion(v)}
            </span>
          ))}
        </span>
        <span className="flex-1" />
        {/* Contador a la derecha; en la caja de las iguales lleva la versión de cada uno («V1 2/3 · V2 2/3»). */}
        {contadores.length > 0 && (dos
          ? <Contador contadores={contadores} conVersion />
          : <span title={TITULO_CONTADOR} className={TEXTO_CARGA[estadoCarga(contadores[0])]}>{contadores[0].regulares}/{contadores[0].maximoDiario}</span>)}
      </div>
      {children}
    </div>
  )
}

/**
 * Partidas de una celda agrupadas por versión, en orden: primera versión, segunda y luego las iguales en ambas. Con
 * dos versiones, cada caja lleva en su cabecera el contador de su versión.
 */
function PorVersion({ tarjetas, versiones, contadores = [], compacta = false, vacio, render }: {
  tarjetas: TarjetaPartida[]; versiones: number[]; contadores?: ContadorVersion[]; compacta?: boolean
  vacio?: ReactNode; render: (t: TarjetaPartida) => ReactNode
}) {
  const conColor = versiones.length > 1
  const grupos = agruparPorVersion(tarjetas, versiones)
  const { porGrupo, sueltos } = conColor ? repartirContadores(grupos, contadores) : { porGrupo: new Map<string, ContadorVersion[]>(), sueltos: [] }
  return (
    <div className={`flex flex-col ${compacta ? 'gap-1' : 'gap-1.5'}`}>
      {sueltos.length > 0 && <span className={`flex justify-end font-semibold ${compacta ? 'text-[10px]' : 'text-xs'}`}><Contador contadores={sueltos} conVersion /></span>}
      {grupos.length === 0 && vacio}
      {grupos.map((g) => (
        <CajaVersion key={g.versiones.join('-')} versiones={g.versiones} conColor={conColor} compacta={compacta} contadores={porGrupo.get(g.versiones.join('-'))}>
          {g.tarjetas.map(render)}
        </CajaVersion>
      ))}
    </div>
  )
}

/** Marca de sobrante: la X; con dos versiones, en cuál de ellas lo es. */
function MarcaSobrante({ t, grande = false }: { t: TarjetaPartida; grande?: boolean }) {
  const en = t.versiones.filter((v) => v.clasificacion === 'SOBRANTE').map((v) => v.version)
  if (en.length === 0) return null
  return (
    <span className="flex shrink-0 items-center text-danger" aria-label={`Sobrante${t.versiones.length > 1 ? ` en ${en.map(etiquetaVersion).join(' y ')}` : ''}`}>
      <X className={`${grande ? 'h-3.5 w-3.5' : 'h-3 w-3'} stroke-[3]`} />
      {t.versiones.length > 1 && en.length < t.versiones.length && <span className="text-[9px] font-bold">{en.map(etiquetaVersion).join('·')}</span>}
    </span>
  )
}

/** Borde de la tarjeta de la partida: con una sola versión marcada (sin cajas), un tono más oscuro para que resalte. */
function bordePartida(t: TarjetaPartida) {
  return t.caso === null ? 'border-[var(--color-borde-partida)]' : 'border-line/70'
}

/** Estilo de la tarjeta: rojo si es Sobrante en todas sus versiones; punteado si en la otra versión está en otro lugar. */
function claseTarjeta(t: TarjetaPartida) {
  const todaSobrante = t.versiones.every((v) => v.clasificacion === 'SOBRANTE')
  // Vista previa de recomendaciones (D-56): la partida que se completaría tejiendo de más, con borde punteado
  if (t.partida.recomendada) return 'border-2 border-dotted border-accent bg-accent-soft/60 text-ink'
  const borde = t.caso === 'movida' ? 'border-dashed' : ''
  return `${borde} ${todaSobrante ? 'border-danger bg-danger-soft text-danger' : `${bordePartida(t)} bg-panel text-ink`}`
}

const conLavado = (t: TarjetaPartida) => t.versiones.some((v) => v.requiereLavado)

/** Tarjeta compacta de la vista semana. */
function TarjetaCompacta({ t }: { t: TarjetaPartida }) {
  return (
    <span key={t.clave} title={detallePartida(t)} className={`flex h-5 min-w-0 items-center gap-1 overflow-hidden rounded border px-1 ${claseTarjeta(t)}`}>
      {conLavado(t) && <Droplets className="h-3 w-3 shrink-0 text-lavado" aria-label="Lavado previo" />}
      <MarcaSobrante t={t} />
      {!t.versiones.every((v) => v.clasificacion === 'SOBRANTE') && <Punto tonalidad={t.partida.tonalidad} />}
      <span className="min-w-0 flex-1 truncate font-mono text-[11px] font-bold">{t.partida.numeroPartida}</span>
    </span>
  )
}

/** Versión reducida, cuando el panel de detalle está abierto: solo puntos con el color de la versión. */
function PuntoTarjeta({ t }: { t: TarjetaPartida }) {
  const sobrante = t.versiones.every((v) => v.clasificacion === 'SOBRANTE')
  return (
    <span key={t.clave} title={detallePartida(t)} className={`flex items-center gap-px rounded-sm ${t.caso === 'movida' ? 'outline-1 outline-dashed outline-ink-faint' : ''}`}>
      {conLavado(t) && <Droplets className="h-2.5 w-2.5 text-lavado" aria-label="Lavado previo" />}
      {sobrante ? <X className="h-3 w-3 stroke-[3] text-danger" aria-label={`Sobrante ${t.partida.numeroPartida}`} /> : <Punto tonalidad={t.partida.tonalidad} />}
    </span>
  )
}

/** Contador «2/3» (o «V1 2/3 · V2 3/3»): ámbar bajo el mínimo del día, verde si está completo, gris en otro caso. */
function Contador({ contadores, conVersion }: { contadores: ContadorVersion[]; conVersion: boolean }) {
  return (
    <span title={TITULO_CONTADOR} className="flex items-center gap-1">
      {contadores.map((c, i) => (
        <span key={c.version} className="flex items-center gap-0.5">
          {i > 0 && <span className="text-ink-faint">·</span>}
          {conVersion && <span style={{ color: colorVersion(c.version) }}>{etiquetaVersion(c.version)}</span>}
          <span className={TEXTO_CARGA[estadoCarga(c)]}>{c.regulares}/{c.maximoDiario}</span>
        </span>
      ))}
    </span>
  )
}

function agruparPorBarca(maquinas: MaquinaComparada[]) {
  const grupos = new Map<string, MaquinaComparada[]>()
  for (const m of [...maquinas].sort((a, b) => a.capacidad - b.capacidad || a.barca.localeCompare(b.barca) || a.codigo.localeCompare(b.codigo)))
    grupos.set(m.barca, [...(grupos.get(m.barca) ?? []), m])
  return [...grupos.entries()]
}

function CabeceraBarca({ barca, maquinas, abierta, onAlternar, columnas }: { barca: string; maquinas: MaquinaComparada[]; abierta: boolean; onAlternar: () => void; columnas: number }) {
  const partidas = maquinas.reduce((s, m) => s + (m.porVersion[0] ? m.porVersion[0].asignadas + m.porVersion[0].sobrantes : 0), 0)
  return (
    <button type="button" onClick={onAlternar} aria-expanded={abierta}
      className="flex w-full items-center gap-2 border-b border-linea-calendario bg-panel-muted px-3 py-1.5 text-left text-sm hover:bg-panel-muted/70"
      style={{ gridColumn: `1 / span ${columnas}` }}>
      {abierta ? <ChevronDown className="h-4 w-4 text-ink-soft" /> : <ChevronRight className="h-4 w-4 text-ink-soft" />}
      <span className="font-semibold text-ink">Barca {barca}</span>
      <span className="text-xs text-ink-soft">({maquinas.length} {maquinas.length === 1 ? 'máquina' : 'máquinas'})</span>
      <span className="ml-auto text-xs text-ink-soft">{partidas} partidas en la semana{maquinas[0]?.porVersion.length > 1 ? ` (${etiquetaVersion(maquinas[0].porVersion[0].version)})` : ''}</span>
    </button>
  )
}

interface PropsVista {
  calendario: CalendarioComparado
  ocultar: (t: TarjetaPartida) => boolean
  seleccion: Seleccion | null
  onSeleccionar: (s: Seleccion) => void
}

/** Vista semana: máquinas agrupadas por barca × 7 días. Con el detalle abierto, las partidas se reducen a puntos. */
function VistaSemana({ calendario, ocultar, seleccion, onSeleccionar }: PropsVista) {
  const [cerradas, setCerradas] = useState<Set<string>>(new Set())
  const reducida = seleccion !== null
  const dias = calendario.dias
  const conVersion = calendario.versiones.length > 1
  const columnas = `${reducida ? '96px' : '120px'} repeat(${dias.length}, minmax(0, 1fr))`
  return (
    <div className="overflow-x-auto rounded-xl border border-linea-calendario bg-panel">
      <div className={reducida ? 'min-w-[560px]' : conVersion ? 'min-w-[980px]' : 'min-w-[880px]'} style={{ display: 'grid', gridTemplateColumns: columnas }}>
        <div className="flex items-center justify-center border-b border-linea-calendario px-3 py-2 text-xs font-semibold text-ink-soft">Máquina</div>
        {dias.map((dia) => (
          <div key={dia} className="border-b border-l border-linea-calendario px-1 py-1.5 text-center">
            <p className="text-sm font-bold text-ink">{nombreDia(dia)}</p>
            <p className="text-[11px] text-ink-soft">{diaMes(dia)}</p>
          </div>
        ))}
        {agruparPorBarca(calendario.maquinas).map(([barca, grupo]) => {
          const abierta = !cerradas.has(barca)
          return [
            <CabeceraBarca key={`b-${barca}`} barca={barca} maquinas={grupo} abierta={abierta} columnas={dias.length + 1}
              onAlternar={() => setCerradas((s) => { const n = new Set(s); if (n.has(barca)) n.delete(barca); else n.add(barca); return n })} />,
            ...(abierta ? grupo.flatMap((m) => [
              <div key={`m-${m.codigo}`} className={`flex items-center gap-2 border-b border-linea-calendario py-1.5 ${reducida ? 'px-2' : 'px-3'}`}>
                {!reducida && <HardDrive className="h-4 w-4 shrink-0 text-ink-faint" aria-hidden="true" />}
                <span className="font-mono text-sm font-bold whitespace-nowrap text-ink">{m.codigo}</span>
              </div>,
              ...m.celdas.map((d) => {
                const elegida = seleccion?.maquina === m.codigo && seleccion.dia === d.fecha
                const visibles = d.tarjetas.filter((t) => !ocultar(t))
                return (
                  <button key={`${m.codigo}-${d.fecha}`} type="button" onClick={() => onSeleccionar({ maquina: m.codigo, dia: d.fecha })}
                    aria-label={`${m.codigo}, ${nombreDia(d.fecha, true)} ${diaMes(d.fecha)}: ${d.tarjetas.length} partidas`}
                    className={`min-w-0 border-b border-l border-linea-calendario p-1 text-left transition-shadow hover:ring-1 hover:ring-accent/50 ${d.indisponible ? FONDO_INDISPONIBLE : FONDO_CELDA} ${elegida ? 'relative z-10 ring-2 ring-accent' : ''}`}>
                    <div className="mb-0.5 flex h-3.5 items-center justify-end gap-1 text-[10px] font-semibold">
                      {d.indisponible && <span className="mr-auto flex items-center gap-0.5 text-danger" title={d.motivoIndisponibilidad ?? 'Máquina indisponible'}><Ban className="h-3 w-3" />{reducida || conVersion ? '' : 'No disponible'}</span>}
                      {(!d.indisponible || d.tarjetas.length > 0) && (reducida || !conVersion) && <Contador contadores={d.contadores} conVersion={conVersion && !reducida} />}
                    </div>
                    {reducida
                      ? <div className="flex min-h-3 flex-wrap items-center gap-1 px-0.5">{agruparPorVersion(visibles, calendario.versiones).map((g) => (
                          <span key={g.versiones.join('-')} className="flex flex-wrap items-center gap-1 rounded px-0.5"
                            style={conVersion ? { background: g.versiones.length > 1 ? 'var(--color-panel-muted)' : fondoVersion(g.versiones[0]), boxShadow: `inset 0 0 0 1px ${g.versiones.length > 1 ? 'var(--color-line)' : bordeVersion(g.versiones[0])}, inset 0 2px 0 0 ${cabeceraVersion(g.versiones[0])}` } : undefined}>
                            {g.tarjetas.map((t) => <PuntoTarjeta key={t.clave} t={t} />)}
                          </span>))}</div>
                      : visibles.length === 0 && !d.indisponible && !conVersion
                        ? <span className="block py-0.5 text-center text-xs text-ink-faint">—</span>
                        : <PorVersion tarjetas={visibles} versiones={calendario.versiones} contadores={d.indisponible ? [] : d.contadores} compacta
                            vacio={d.indisponible ? null : <span className="block py-0.5 text-center text-xs text-ink-faint">—</span>}
                            render={(t) => <TarjetaCompacta key={t.clave} t={t} />} />}
                  </button>
                )
              }),
            ]) : []),
          ]
        })}
      </div>
    </div>
  )
}

/** Cola del día de una máquina, de izquierda a derecha, con el lavado entre partidas. */
function ColaHorizontal({ tarjetas, versiones, contadores }: { tarjetas: TarjetaPartida[]; versiones: number[]; contadores: ContadorVersion[] }) {
  const sinPartidas = <span className="text-xs text-ink-faint">Sin partidas</span>
  if (versiones.length < 2) return tarjetas.length === 0 ? sinPartidas : <FilaCola tarjetas={tarjetas} />
  const grupos = agruparPorVersion(tarjetas, versiones)
  const { porGrupo, sueltos } = repartirContadores(grupos, contadores)
  return (
    <div className="flex flex-wrap items-start gap-2">
      {sueltos.length > 0 && <span className="text-xs font-semibold"><Contador contadores={sueltos} conVersion /></span>}
      {grupos.length === 0 && sinPartidas}
      {grupos.map((g) => (
        <CajaVersion key={g.versiones.join('-')} versiones={g.versiones} conColor contadores={porGrupo.get(g.versiones.join('-'))}>
          <FilaCola tarjetas={g.tarjetas} />
        </CajaVersion>
      ))}
    </div>
  )
}

function FilaCola({ tarjetas }: { tarjetas: TarjetaPartida[] }) {
  return (
    <div className="flex flex-wrap items-center gap-1.5">
      {tarjetas.map((t, i) => (
        <span key={t.clave} className="flex items-center gap-1.5">
          {i > 0 && !conLavado(t) && <ChevronRight className="h-3.5 w-3.5 text-ink-faint" aria-hidden="true" />}
          {conLavado(t) && <span className="flex items-center gap-0.5 rounded bg-lavado-soft px-1.5 py-0.5 text-[11px] font-medium text-lavado"><Droplets className="h-3 w-3" />Lavado</span>}
          <span title={detallePartida(t)} className={`flex items-center gap-1.5 overflow-hidden rounded-md border px-2 py-1 ${claseTarjeta(t)}`}>
            <MarcaSobrante t={t} />
            {!t.versiones.every((v) => v.clasificacion === 'SOBRANTE') && <Punto tonalidad={t.partida.tonalidad} />}
            <span className="font-mono text-xs font-bold">{t.partida.numeroPartida}</span>
            <span className="text-[11px] text-ink-soft">{kg(t.partida.kg)}</span>
          </span>
        </span>
      ))}
    </div>
  )
}

function VistaDia({ calendario, ocultar, seleccion, onSeleccionar }: PropsVista) {
  const dias = calendario.dias
  const [dia, setDia] = useState(seleccion?.dia ?? dias[0])
  const conVersion = calendario.versiones.length > 1
  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-1.5" role="group" aria-label="Día">
        {dias.map((d) => (
          <button key={d} type="button" onClick={() => setDia(d)} aria-pressed={d === dia}
            className={`rounded-lg border px-3 py-1.5 text-sm ${d === dia ? 'border-accent bg-accent text-accent-ink' : 'border-line bg-panel text-ink hover:bg-panel-muted'}`}>
            <span className="font-semibold">{nombreDia(d)}</span> <span className="text-xs opacity-80">{diaMes(d)}</span>
          </button>
        ))}
      </div>
      <div className="overflow-hidden rounded-xl border border-linea-calendario bg-panel">
        {agruparPorBarca(calendario.maquinas).map(([barca, grupo]) => (
          <div key={barca}>
            <p className="border-b border-linea-calendario bg-panel-muted px-3 py-1.5 text-sm font-semibold text-ink">Barca {barca} <span className="text-xs font-normal text-ink-soft">({grupo.length} {grupo.length === 1 ? 'máquina' : 'máquinas'})</span></p>
            {grupo.map((m) => {
              const d = m.celdas.find((x) => x.fecha === dia)
              if (!d) return null
              const elegida = seleccion?.maquina === m.codigo && seleccion.dia === dia
              return (
                <button key={m.codigo} type="button" onClick={() => onSeleccionar({ maquina: m.codigo, dia })}
                  className={`grid w-full items-center gap-2 border-b border-linea-calendario px-3 py-2 text-left last:border-b-0 hover:bg-panel-muted/60 ${conVersion ? 'grid-cols-[120px_1fr]' : 'grid-cols-[120px_56px_1fr]'} ${elegida ? 'ring-2 ring-inset ring-accent' : ''}`}>
                  <span className="flex items-center gap-2 font-mono text-sm font-bold text-ink"><HardDrive className="h-4 w-4 text-ink-faint" />{m.codigo}</span>
                  {!conVersion && <span className={`rounded px-1.5 py-0.5 text-center text-xs font-semibold ${FONDO_CELDA}`}>{d.indisponible ? <Ban className="mx-auto h-3.5 w-3.5 text-danger" /> : <Contador contadores={d.contadores} conVersion={false} />}</span>}
                  {d.indisponible ? <span className="flex items-center gap-1 text-xs text-danger"><Ban className="h-3.5 w-3.5" />No disponible{d.motivoIndisponibilidad ? `: ${d.motivoIndisponibilidad}` : ''}</span> : <ColaHorizontal tarjetas={d.tarjetas.filter((t) => !ocultar(t))} versiones={calendario.versiones} contadores={d.contadores} />}
                </button>
              )
            })}
          </div>
        ))}
      </div>
    </div>
  )
}

function VistaMaquina({ calendario, ocultar, seleccion, onSeleccionar }: PropsVista) {
  const ordenadas = agruparPorBarca(calendario.maquinas).flatMap(([, g]) => g)
  const [codigo, setCodigo] = useState(seleccion?.maquina ?? ordenadas[0]?.codigo)
  const m = ordenadas.find((x) => x.codigo === codigo) ?? ordenadas[0]
  const conVersion = calendario.versiones.length > 1
  if (!m) return null
  return (
    <div className="space-y-3">
      <label className="flex flex-wrap items-center gap-2 text-sm text-ink-soft">Máquina
        <select value={m.codigo} onChange={(e) => setCodigo(e.target.value)} className="rounded-lg border border-line bg-panel px-2 py-1.5 text-sm text-ink">
          {ordenadas.map((x) => <option key={x.codigo} value={x.codigo}>{x.codigo} · Barca {x.barca}</option>)}
        </select>
        {m.porVersion.map((r) => (
          <span key={r.version} className="text-xs">
            {conVersion && <span className="font-bold" style={{ color: colorVersion(r.version) }}>{etiquetaVersion(r.version)}: </span>}
            {r.asignadas + r.sobrantes} de {r.objetivoAplicado} partidas en la semana · {r.lavados} lavados
          </span>
        ))}
      </label>
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-4 xl:grid-cols-7">
        {m.celdas.map((d) => {
          const elegida = seleccion?.maquina === m.codigo && seleccion.dia === d.fecha
          return (
            <button key={d.fecha} type="button" onClick={() => onSeleccionar({ maquina: m.codigo, dia: d.fecha })}
              className={`flex min-h-32 flex-col gap-1.5 rounded-xl border border-linea-calendario p-2 text-left hover:ring-1 hover:ring-accent/50 ${d.indisponible ? FONDO_INDISPONIBLE : FONDO_CELDA} ${elegida ? 'ring-2 ring-accent' : ''}`}>
              <div className="flex flex-wrap items-baseline justify-between gap-1"><span className="text-sm font-bold text-ink">{nombreDia(d.fecha)} <span className="text-xs font-normal text-ink-soft">{diaMes(d.fecha)}</span></span>
                {!d.indisponible && !conVersion && <span className="text-[11px] font-semibold"><Contador contadores={d.contadores} conVersion={false} /></span>}</div>
              {d.indisponible && <span className="flex items-center gap-1 text-xs text-danger"><Ban className="h-3.5 w-3.5" />No disponible</span>}
              <PorVersion tarjetas={d.tarjetas.filter((t) => !ocultar(t))} versiones={calendario.versiones} contadores={d.indisponible ? [] : d.contadores} render={(t) => (
                <div key={t.clave} className="flex flex-col gap-1">
                  {conLavado(t) && <span className="flex items-center gap-1 rounded bg-lavado-soft px-1.5 py-0.5 text-[11px] font-medium text-lavado"><Droplets className="h-3 w-3" />Lavado</span>}
                  <div title={detallePartida(t)} className={`overflow-hidden rounded-md border px-2 py-1 ${claseTarjeta(t)}`}>
                    <p className="flex items-center gap-1.5 font-mono text-xs font-bold"><MarcaSobrante t={t} />{!t.versiones.every((v) => v.clasificacion === 'SOBRANTE') && <Punto tonalidad={t.partida.tonalidad} />}<span className="min-w-0 flex-1 truncate">{t.partida.numeroPartida}</span></p>
                    <p className="truncate text-[11px] text-ink-soft">{t.partida.color} · {kg(t.partida.kg)}</p>
                  </div>
                </div>
              )} />
            </button>
          )
        })}
      </div>
    </div>
  )
}

/**
 * Día desde el que la partida está disponible para tintorería (su tela ya llegó, `fecha_disponible`) y, si se ubicó
 * después, cuántos días esperó.
 */
function Disponibilidad({ desde, dia }: { desde: string | null; dia: string }) {
  if (!desde) return null
  const espera = Math.round((Date.parse(dia) - Date.parse(desde)) / 86_400_000)
  return (
    <p className="mt-0.5 flex flex-wrap items-center gap-x-1.5 text-xs text-ink-soft">
      <CalendarDays className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
      <span>Disponible desde <b className="font-semibold text-ink">{nombreDia(desde)} {diaMes(desde)}</b></span>
      {espera > 0 && <span className="text-ink-faint">· ubicada {espera} {espera === 1 ? 'día' : 'días'} después</span>}
    </p>
  )
}

/** Panel lateral con la secuencia del día de una máquina, de todas las versiones marcadas. */
function DetallePlanificacion({ maquina, celda, onCerrar }: { maquina: MaquinaComparada; celda: CeldaComparada; onCerrar: () => void }) {
  const totalKg = celda.tarjetas.reduce((s, t) => s + t.partida.kg, 0)
  const lavados = celda.tarjetas.filter(conLavado).length
  const conVersion = celda.contadores.length > 1
  const principal = celda.contadores[0]
  const porcentaje = principal && principal.maximoDiario > 0 ? Math.min(100, Math.round((principal.regulares / principal.maximoDiario) * 100)) : 0
  const entregaProxima = celda.tarjetas.map((t) => t.partida.fechaEntrega).sort()[0]
  const resumen = maquina.porVersion[0]
  return (
    <aside aria-label="Detalle de planificación" className="w-full shrink-0 rounded-xl border border-line bg-panel p-4 lg:sticky lg:top-4 lg:w-[340px] lg:self-start">
      <div className="mb-3 flex items-center justify-between">
        <h3 className="text-lg font-semibold text-ink">Detalle de planificación</h3>
        <button type="button" onClick={onCerrar} aria-label="Cerrar detalle" className="rounded-md p-1 text-ink-soft hover:bg-panel-muted"><X className="h-4 w-4" /></button>
      </div>
      <div className="flex items-center gap-3 rounded-xl bg-accent-soft p-3">
        <HardDrive className="h-6 w-6 shrink-0 text-accent" aria-hidden="true" />
        <div className="min-w-0">
          <p className="truncate font-semibold text-ink">{maquina.codigo} — {nombreDia(celda.fecha, true)}, {diaMes(celda.fecha)}</p>
          <p className="text-xs text-ink-soft">Barca {maquina.barca} · Capacidad {kg(maquina.capacidad)}</p>
        </div>
      </div>
      <div className="mt-3 grid grid-cols-[1fr_1fr_1fr_auto] items-center gap-2 border-b border-line pb-3 text-center">
        <div><p className="flex items-center justify-center gap-1 font-mono text-lg font-bold text-ink"><Layers className="h-4 w-4 text-ink-faint" />{celda.tarjetas.length}</p><p className="text-[11px] text-ink-soft">{conVersion ? 'Tarjetas' : 'Partidas'}</p></div>
        <div><p className="flex items-center justify-center gap-1 font-mono text-lg font-bold text-ink"><Droplets className="h-4 w-4 text-lavado" />{lavados}</p><p className="text-[11px] text-ink-soft">{lavados === 1 ? 'Lavado' : 'Lavados'}</p></div>
        <div><p className="flex items-center justify-center gap-1 font-mono text-lg font-bold text-ink"><Weight className="h-4 w-4 text-ink-faint" />{Math.round(totalKg)}</p><p className="text-[11px] text-ink-soft">kg</p></div>
        <div title={principal ? `${principal.regulares} de ${principal.maximoDiario} partidas del máximo diario` : ''} className="relative h-12 w-12 rounded-full"
          style={{ background: `conic-gradient(var(--color-success) ${porcentaje * 3.6}deg, var(--color-panel-muted) 0deg)` }}>
          <span className="absolute inset-1 flex items-center justify-center rounded-full bg-panel text-[11px] font-bold text-ink">{porcentaje}%</span>
        </div>
      </div>
      {conVersion && <p className="mt-2 text-xs text-ink-soft">{celda.contadores.length} versiones visibles</p>}

      <h4 className="mt-3 mb-2 text-sm font-semibold text-ink">Secuencia de partidas</h4>
      {celda.indisponible && <p className="mb-2 flex items-center gap-1.5 rounded-lg bg-danger-soft px-2.5 py-1.5 text-xs text-danger"><Ban className="h-3.5 w-3.5" />Máquina no disponible{celda.motivoIndisponibilidad ? `: ${celda.motivoIndisponibilidad}` : ''}</p>}
      {celda.tarjetas.length === 0
        ? <p className="text-sm text-ink-faint">No hay partidas este día.</p>
        : <PorVersion tarjetas={celda.tarjetas} versiones={celda.contadores.map((c) => c.version)} contadores={celda.indisponible ? [] : celda.contadores} render={(t) => {
            const i = celda.tarjetas.indexOf(t)
            return (
              <div key={t.clave} className="space-y-1.5">
                {conLavado(t) && <p className="ml-8 flex items-center gap-1.5 rounded-lg bg-lavado-soft px-2.5 py-1.5 text-xs font-medium text-lavado"><Droplets className="h-3.5 w-3.5" />Lavado: cambio a una tonalidad más clara</p>}
                <div className="flex items-start gap-2">
                  <span className="mt-1.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-ink text-xs font-bold text-panel">{i + 1}</span>
                  <div className={`min-w-0 flex-1 overflow-hidden rounded-lg border px-2.5 py-1.5 ${t.caso === 'movida' ? 'border-dashed' : ''} ${t.versiones.every((v) => v.clasificacion === 'SOBRANTE') ? 'border-danger bg-danger-soft' : `${t.caso === null ? 'border-[var(--color-borde-partida)]' : 'border-line'} bg-panel`}`}>
                    <p className="flex items-center gap-1.5 font-mono text-sm font-bold text-ink"><Punto tonalidad={t.partida.tonalidad} />{t.partida.numeroPartida}
                      <span className="ml-auto flex items-center gap-1"><MarcaSobrante t={t} grande /></span></p>
                    <p className="text-xs text-ink-soft">{kg(t.partida.kg)} · Pedido {t.partida.numeroOp}</p>
                    <p className="truncate text-xs text-ink-soft">{t.partida.color} · {TONALIDAD[t.partida.tonalidad]} · {CLASIFICACION_COLOR[t.partida.clasificacionColor]}</p>
                    <Disponibilidad desde={t.partida.fechaDisponible} dia={celda.fecha} />
                    {t.enLaOtra && <p className="mt-0.5 text-[11px] text-ink-faint">{t.enLaOtra}</p>}
                  </div>
                </div>
              </div>
            )
          }} />}

      <h4 className="mt-4 mb-1.5 text-sm font-semibold text-ink">Información adicional</h4>
      <dl className="divide-y divide-line text-sm">
        <div className="flex justify-between py-1.5"><dt className="flex items-center gap-1.5 text-ink-soft"><CalendarDays className="h-4 w-4" />Entrega más próxima</dt><dd className="text-ink">{fechaCorta(entregaProxima)}</dd></div>
        {resumen && <div className="flex justify-between py-1.5"><dt className="flex items-center gap-1.5 text-ink-soft"><CalendarRange className="h-4 w-4" />Reparto diario</dt><dd className="text-ink">{resumen.minimoDiario} a {resumen.maximoDiario} partidas</dd></div>}
        {maquina.porVersion.map((r) => (
          <div key={r.version} className="flex justify-between py-1.5"><dt className="flex items-center gap-1.5 text-ink-soft"><Layers className="h-4 w-4" />Semana de la máquina{conVersion ? ` (${etiquetaVersion(r.version)})` : ''}</dt><dd className="text-ink">{r.asignadas + r.sobrantes} de {r.objetivoAplicado}</dd></div>
        ))}
      </dl>
    </aside>
  )
}

/** Muestra en miniatura de la caja de una versión (cabecera de color, borde y fondo) para la leyenda. */
function MuestraCaja({ versiones }: { versiones: number[] }) {
  const dos = versiones.length > 1
  return (
    <span aria-hidden="true" className="flex h-4 w-6 flex-col overflow-hidden rounded-sm border"
      style={{ borderColor: dos ? 'var(--color-line)' : bordeVersion(versiones[0]), background: dos ? 'var(--color-panel-muted)' : fondoVersion(versiones[0]) }}>
      <span className="flex h-1.5">{versiones.map((v) => <span key={v} className="flex-1" style={{ background: cabeceraVersion(v) }} />)}</span>
    </span>
  )
}

export function LeyendaCalendario({ versiones }: { versiones: number[] }) {
  const dos = versiones.length > 1
  return (
    <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-2 rounded-xl border border-line bg-panel px-3 py-2 text-xs text-ink-soft">
      <span className="font-semibold text-ink">Leyenda:</span>
      {dos && versiones.map((v) => (
        <span key={v} className="flex items-center gap-1.5"><MuestraCaja versiones={[v]} />Partidas de la <EtiquetaVersion versiones={[v]} className="text-xs" /></span>
      ))}
      {dos && <span className="flex items-center gap-1.5"><MuestraCaja versiones={versiones} />Igual en ambas</span>}
      {dos && <span className="flex items-center gap-1.5"><span className="h-3.5 w-5 rounded border border-dashed border-ink-soft bg-panel" />En otra máquina o día en la otra versión</span>}
      <span className="text-line">|</span>
      {(Object.keys(MUESTRA_TONALIDAD) as (keyof typeof MUESTRA_TONALIDAD)[]).map((t) => (
        <span key={t} className="flex items-center gap-1.5"><Punto tonalidad={t} />{TONALIDAD[t]}</span>
      ))}
      <span className="flex items-center gap-1.5 text-lavado"><Droplets className="h-3.5 w-3.5" />Lavado previo</span>
      <span className="flex items-center gap-1.5 text-danger"><X className="h-3.5 w-3.5 stroke-[3]" />Sobrante{dos ? ' (con la versión en que lo es)' : ''}</span>
      <span className="flex items-center gap-1.5"><Ban className="h-3.5 w-3.5 text-danger" />Día indisponible</span>
      <span className="text-line">|</span>
      <span>«2/3»: asignadas del día / máximo diario —</span>
      <span className="font-semibold text-warning">bajo el mínimo del día</span>
      <span className="font-semibold text-success">día completo</span>
      <span className="font-semibold text-ink-soft">normal</span>
    </div>
  )
}

const VISTAS: { id: Vista; texto: string; icono: typeof CalendarRange }[] = [
  { id: 'semana', texto: 'Semana', icono: CalendarRange },
  { id: 'dia', texto: 'Día', icono: CalendarDays },
  { id: 'maquina', texto: 'Máquina', icono: HardDrive },
]

/**
 * Calendario de la planificación (RF31, RF38) con tres visualizaciones: semana (máquinas por barca × días), día y
 * máquina. Muestra una versión o compara dos (D-52). Al elegir una celda se abre el detalle con su secuencia; es de
 * solo lectura.
 *
 * @param panel panel de versiones, en una fila debajo de «Visualización»
 */
export function CalendarioPlanificacion({ calendario, panel }: { calendario: CalendarioComparado; panel?: ReactNode }) {
  const [vista, setVista] = useState<Vista>('semana')
  const [seleccion, setSeleccion] = useState<Seleccion | null>(null)
  const [soloDiferencias, setSoloDiferencias] = useState(false)
  const dos = calendario.versiones.length > 1
  const ocultar = (t: TarjetaPartida) => dos && soloDiferencias && t.caso === 'igual'
  const maquinaSel = seleccion ? calendario.maquinas.find((m) => m.codigo === seleccion.maquina) : undefined
  const celdaSel = maquinaSel?.celdas.find((d) => d.fecha === seleccion?.dia)
  const elegir = (s: Seleccion) => setSeleccion((actual) => actual?.maquina === s.maquina && actual.dia === s.dia ? null : s)
  const props = { calendario, ocultar, seleccion, onSeleccionar: elegir }

  return (
    <div>
      <div className="mb-3 flex flex-wrap items-center gap-2">
        <span className="text-sm font-semibold text-ink">Visualización:</span>
        {VISTAS.map((v) => {
          const Icono = v.icono
          return (
            <button key={v.id} type="button" onClick={() => setVista(v.id)} aria-pressed={vista === v.id}
              className={`inline-flex items-center gap-1.5 rounded-lg border px-3.5 py-1.5 text-sm font-medium ${vista === v.id ? 'border-accent bg-accent text-accent-ink' : 'border-line bg-panel text-ink hover:bg-panel-muted'}`}>
              <Icono className="h-4 w-4" />{v.texto}
            </button>
          )
        })}
        {!seleccion && <span className="ml-auto text-xs text-ink-faint">Elige un día de una máquina para ver su detalle.</span>}
      </div>
      {/* Versiones de la proyección: debajo de «Visualización», en su propia fila (D-52). */}
      {(panel || dos) && (
        <div className="mb-3 flex flex-wrap items-center gap-2">
          {panel}
          {dos && (
            <label className="flex cursor-pointer items-center gap-2 text-sm text-ink sm:ml-2 sm:border-l sm:border-line sm:pl-3" title="Oculta las partidas que están igual en las dos versiones (misma máquina, día y estado)">
              <input type="checkbox" checked={soloDiferencias} onChange={(e) => setSoloDiferencias(e.target.checked)} className="h-4 w-4 accent-[var(--color-accent)]" />
              Solo diferencias
            </label>
          )}
        </div>
      )}
      <div className="flex flex-col gap-4 lg:flex-row">
        <div className="min-w-0 flex-1">
          {vista === 'semana' && <VistaSemana {...props} />}
          {vista === 'dia' && <VistaDia {...props} />}
          {vista === 'maquina' && <VistaMaquina {...props} />}
          <LeyendaCalendario versiones={calendario.versiones} />
        </div>
        {maquinaSel && celdaSel && <DetallePlanificacion maquina={maquinaSel} celda={{ ...celdaSel, tarjetas: celdaSel.tarjetas.filter((t) => !ocultar(t)) }} onCerrar={() => setSeleccion(null)} />}
      </div>
    </div>
  )
}
