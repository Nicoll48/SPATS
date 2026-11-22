/**
 * Tipos del contrato JSON con la API (espejo de los DTO del backend). Fechas en ISO: `YYYY-MM-DD` para
 * días y `YYYY-MM-DDTHH:mm:ss` para fecha y hora (zona del negocio, America/Lima).
 */
import type { ProblemaValidacion } from './cliente'

export type PerfilUsuario = 'PLANIFICADOR' | 'CONSULTA'
export type ModoInteraccion = 'GUIADO' | 'EXPERTO'
export type ClasificacionColor = 'C' | 'B'
export type Tonalidad = 'BLANCO' | 'CLARO' | 'MEDIO' | 'OSCURO'
export type EstadoPartida = 'PENDIENTE' | 'ARRASTRADA' | 'COMPLETADA'
export type TipoComponente = 'PRINCIPAL' | 'COMPLEMENTO'
export type ClasificacionResultado = 'ASIGNADA' | 'SOBRANTE' | 'BLOQUEADA'
export type EstadoConfirmacionCierre = 'COMPLETADA' | 'ARRASTRADA'
export type TipoGeneracion = 'INICIAL' | 'REPLANIFICACION'
/** CANDIDATA (D-52): generada con otra versión de la proyección; no es vigente hasta que se usa como vigente. */
export type EstadoPlanificacion = 'ABIERTA' | 'CERRADA' | 'REEMPLAZADA' | 'INVALIDADA' | 'PROPUESTA' | 'CANDIDATA'
export type EstadoMaquina = 'OPERATIVA' | 'INDISPONIBLE' | 'EN_MANTENIMIENTO'
export type TipoIndisponibilidad = 'INDISPONIBLE' | 'MANTENIMIENTO'
export type TipoDato = 'PEDIDOS' | 'PARTIDAS' | 'PROYECCION_TEJEDURIA'
export type TipoIncidencia = 'INCIDENCIA_MAQUINA' | 'PROBLEMA_COMPLEMENTO' | 'CAMBIO_PRIORIDAD'
export type EstadoIncidencia = 'PENDIENTE' | 'CONSIDERADA' | 'ATENDIDA' | 'ANULADA'
/** D-52: VIGENTE es la versión de la planificación vigente; CARGADA, cualquier otra que se puede planificar. */
export type EstadoProyeccion = 'VIGENTE' | 'CARGADA' | 'ELIMINADA'
export type TipoAlerta = 'FALTANTE' | 'EXCEDENTE' | 'DESBALANCE_DIARIO'

// ------------------------------------------------------------- sesión
export interface Usuario {
  id: number
  usuario: string
  nombre: string
  perfil: PerfilUsuario
  modoInteraccion: ModoInteraccion
}

export interface Sesion {
  token: string
  expiraEn: string
  usuario: Usuario
}

// ------------------------------------------------------- planificación
export interface Indicadores {
  valorFuncionObjetivo: number | null
  desbalanceSemanal: number | null
  desbalanceDiario: number | null
  numeroLavados: number | null
  evaluaciones: number | null
  tiempoEjecucionMs: number | null
}

export interface GeneracionResponse {
  idPlanificacion: number
  numeroVersion: number
  /** false si quedó como candidata: se generó con otra versión de la proyección (D-52). */
  vigente: boolean
  /** Versión de la proyección con la que se generó (V1, V2, ...). */
  versionProyeccion: number
  indicadores: Indicadores
  detenidaPorTiempo: boolean
  advertencias: string[]
}

export interface PartidaCalendario {
  numeroPartida: string
  numeroOp: string
  fechaEntrega: string
  codigoColor: string
  color: string
  clasificacionColor: ClasificacionColor
  tonalidad: Tonalidad
  kg: number
  clasificacion: ClasificacionResultado
  requiereLavado: boolean
  ordenSecuencia: number
  fechaDisponible: string
  estadoConfirmacionCierre: EstadoConfirmacionCierre | null
  /** Solo en la vista previa de las recomendaciones (D-56): partida que se completaría tejiendo de más. */
  recomendada?: { diaTejido: string; kgATejer: number }
}

export interface DiaMaquina {
  fecha: string
  indisponible: boolean
  motivoIndisponibilidad: string | null
  regulares: number
  partidas: PartidaCalendario[]
}

export interface MaquinaPlan {
  codigo: string
  barca: string
  clasificacionColor: ClasificacionColor
  capacidad: number
  objetivoAplicado: number
  minimoDiario: number
  maximoDiario: number
  asignadas: number
  sobrantes: number
  diferencia: number
  lavados: number
  dias: DiaMaquina[]
}

export interface ComponenteBloqueada {
  codigoArticulo: string
  nombreArticulo: string
  tipoComponente: TipoComponente
  kgRequeridos: number
  kgSaldoFinalArticulo: number
}

export interface PartidaBloqueada {
  numeroPartida: string
  numeroOp: string
  fechaEntrega: string
  color: string
  tonalidad: Tonalidad
  barca: string
  kg: number
  componentes: ComponenteBloqueada[]
  /** D-51: motivo del algoritmo; null en versiones generadas antes de guardarlo. */
  motivo: string | null
}

export interface Alerta {
  tipo: TipoAlerta
  maquina: string
  cantidad: number
  mensaje: string
}

export interface PlanificacionDetalle {
  id: number
  lunes: string
  domingo: string
  numeroVersion: number
  tipoGeneracion: TipoGeneracion
  fechaGeneracion: string
  esVigente: boolean
  estado: EstadoPlanificacion
  fechaCierre: string | null
  versionProyeccion: number
  indicadores: Indicadores
  resumen: { asignadas: number; sobrantes: number; bloqueadas: number; maquinasEnObjetivo: number; totalMaquinas: number; lavados: number }
  dias: string[]
  maquinas: MaquinaPlan[]
  bloqueadas: PartidaBloqueada[]
  distribucionColor: { clasificacionColor: ClasificacionColor; asignadas: number; sobrantes: number }[]
  alertas: Alerta[]
}

export interface Version {
  id: number
  lunes: string
  numeroVersion: number
  tipoGeneracion: TipoGeneracion
  fechaGeneracion: string
  esVigente: boolean
  estado: EstadoPlanificacion
  fechaCierre: string | null
  versionProyeccion: number
  indicadores: Indicadores
  asignadas: number
  sobrantes: number
  bloqueadas: number
}

export interface EstadoSemana {
  lunes: string
  domingo: string
  proyeccion: { id: number; version: number; fechaCarga: string } | null
  planificacionVigente: { id: number; numeroVersion: number; estado: EstadoPlanificacion; fechaGeneracion: string; idProyeccion: number } | null
  versiones: number
  proyeccionDesactualizada: boolean
  /** D-19: motivo si la semana anterior se volvió a planificar después de generar esta; si no, null. */
  stockDesactualizado: string | null
  /** Re-planificación propuesta que espera confirmación (solo vale el mismo día en que se generó). */
  propuestaPendiente: { id: number; numeroVersion: number; estado: EstadoPlanificacion; fechaGeneracion: string; idProyeccion: number } | null
  /** D-51: máquinas cuyas tonalidades aceptadas cambiaron después de generar la vigente. */
  maquinasCambiadas: string[]
}

export interface Arrastrada {
  numeroPartida: string
  numeroOp: string
  color: string
  kg: number
  clasificacionOrigen: ClasificacionResultado
  maquina: string | null
  dia: string | null
  /** Lunes de la semana cuyo cierre la dejó arrastrada. */
  semanaCierre: string
  semanasArrastrada: number
}

export interface Bolsa {
  codigoArticulo: string
  nombreArticulo: string
  fecha: string
  kgSaldoInicial: number
  kgIngresados: number
  /** Tela que volvió a la bolsa el día D de una re-planificación (D-30). */
  kgDevueltos: number
  kgConsumidos: number
  kgSaldoFinal: number
}

// ------------------------------------------------------------ re-planificación (HU-18)
export interface PartidaDelDia {
  numeroPartida: string
  maquina: string
  clasificacion: ClasificacionResultado
  ordenSecuencia: number | null
}

export interface ContextoReplanificacion {
  dia: string
  permitida: boolean
  motivo: string | null
  idVigente: number | null
  partidasDelDia: PartidaDelDia[]
  propuestaPendiente: EstadoSemana['propuestaPendiente']
}

export interface ReplanificacionResponse {
  resultado: 'PROPUESTA' | 'SE_CONSERVA_VIGENTE'
  dia: string
  idPropuesta: number | null
  numeroVersion: number | null
  idVigente: number
  fNueva: number
  /** F de la vigente evaluada sobre la misma instancia nueva (semana completa). */
  fVigente: number
  vigenteFactible: boolean
  incumplimientosVigente: string[]
  indicadores: Indicadores | null
  detenidaPorTiempo: boolean
  advertencias: string[]
}

// ------------------------------------------------------------ versiones de la proyección (D-52)
export interface PlanificacionDeVersion {
  id: number
  numeroVersion: number
  estado: EstadoPlanificacion
  esVigente: boolean
  tipoGeneracion: TipoGeneracion
  fechaGeneracion: string
  stockDesactualizado: boolean
  indicadores: Indicadores
  asignadas: number
  sobrantes: number
  bloqueadas: number
}

export interface VersionProyeccionPlan {
  idProyeccion: number
  /** Número de la versión: V1, V2, ... en orden de subida. */
  version: number
  fechaCarga: string
  estado: EstadoProyeccion
  kgTotales: number
  /** null si la versión todavía no tiene planificación generada. */
  planificacion: PlanificacionDeVersion | null
}

export interface VersionesSemana {
  lunes: string
  /** La planificación de la semana ya se cerró: todo queda en solo lectura. */
  cerrada: boolean
  idVigente: number | null
  versiones: VersionProyeccionPlan[]
}

export interface ContextoCambioVigente {
  idCandidata: number
  numeroVersion: number
  versionProyeccion: number
  idVigente: number | null
  numeroVersionVigente: number | null
  versionProyeccionVigente: number | null
  dia: string
  /** Hoy ya está en la semana: se re-planifica desde hoy y lo anterior se conserva. */
  semanaIniciada: boolean
  permitido: boolean
  motivo: string | null
  incidenciasPendientes: number
  incidenciasConsideradas: number
  replanificaciones: number
  stockDesactualizado: boolean
  partidasDelDia: PartidaDelDia[]
}

export interface CambioVigenteResponse {
  modo: 'DIRECTO' | 'REPLANIFICACION'
  idVigente: number | null
  numeroVersion: number | null
  replanificacion: ReplanificacionResponse | null
}

export interface ConfirmacionResponse {
  idPlanificacion: number
  numeroVersion: number
  incidenciasConsideradas: number
  pedidosActualizados: number
}

export interface Ubicacion {
  maquina: string | null
  dia: string | null
  clasificacion: ClasificacionResultado
}

export interface Comparacion {
  a: Version
  b: Version
  maquinas: { codigo: string; asignadasA: number | null; sobrantesA: number | null; diferenciaA: number | null; asignadasB: number | null; sobrantesB: number | null; diferenciaB: number | null }[]
  cambios: { numeroPartida: string; a: Ubicacion | null; b: Ubicacion | null }[]
}

// ------------------------------------------------------------ datos
export interface DetallePedido {
  id: number
  idArticulo: number
  codigoArticulo: string
  nombreArticulo: string
  pesoTotalRequerido: number
}

export interface PedidoRegistrado {
  id: number
  numeroOp: string
  cliente: string
  fechaEntrega: string
  estado: 'PENDIENTE' | 'COMPLETADO'
  detalles: DetallePedido[]
}

export interface ArticuloRegistrado {
  id: number
  codigo: string
  nombre: string
}

export interface ComponenteRegistrado {
  id: number
  idDetallePedido: number
  codigoArticulo: string
  nombreArticulo: string
  tipoComponente: TipoComponente
  tubularAbierto: boolean
  pesoRequerido: number
}

export interface PartidaRegistrada {
  id: number
  numeroPartida: string
  idPedido: number
  numeroOp: string
  cliente: string
  fechaEntrega: string
  codigoColor: string
  color: string
  clasificacionColor: ClasificacionColor
  tonalidad: Tonalidad
  barca: string | null
  pesoTotal: number
  fechaRegistro: string
  estado: EstadoPartida
  componentes: ComponenteRegistrado[]
}

export interface ColorRegistrado {
  id: number
  codigoColor: string
  nombre: string
  clasificacionColor: ClasificacionColor
  tonalidad: Tonalidad
}

export interface VersionProyeccion {
  id: number
  semana: string
  version: number
  fechaCarga: string
  estado: EstadoProyeccion
  registros: number
  kgTotales: number
}

export interface DetalleProyeccion {
  id: number
  idDetallePedido: number
  numeroOp: string
  codigoArticulo: string
  nombreArticulo: string
  fechaProyectada: string
  fechaDisponible: string
  kgProyectados: number
  enVentana: boolean
}

export interface CargaResponse {
  id: number
  tipo: TipoDato
  archivo: string
  fechaHora: string
  nuevos: Record<string, number>
  existentes: number
  advertencias: ProblemaValidacion[]
}

export interface VersionProyeccionEliminada {
  version: number
  registros: number
  planificaciones: number
}

export interface CargaEliminada {
  tipo: TipoDato
  archivo: string
  eliminados: Record<string, number>
}

export interface CargaHistorial {
  id: number
  tipo: TipoDato
  archivo: string
  fechaHora: string
  usuario: string
  eliminada: boolean
}

// ---------------------------------------------------------- máquinas
export interface Maquina {
  id: number
  codigo: string
  capacidad: number
  clasificacionColor: ClasificacionColor
  objetivoSemanal: number
  barca: string | null
  estado: EstadoMaquina
  estadoActual: EstadoMaquina
  /** D-51: tonalidades que puede teñir, de Blanco a Oscuro (al menos una). */
  tonalidadesAceptadas: Tonalidad[]
}

export interface Indisponibilidad {
  id: number
  idMaquina: number
  codigoMaquina: string
  fechaHoraInicio: string
  fechaHoraFin: string
  tipo: TipoIndisponibilidad
  motivo: string
  estadoResultante: EstadoMaquina
}

// -------------------------------------------------------- incidencias
export interface IncidenciaRegistrada {
  id: number
  tipo: TipoIncidencia
  entidadAfectada: string
  referencia: string
  descripcion: string | null
  fechaHoraOcurrencia: string
  fechaHoraDisponibilidad: string | null
  nuevaFechaEntrega: string | null
  fechaEntregaActual: string | null
  estado: EstadoIncidencia
  registradaPor: string
  versionPlanificacionOrigen: number
}

// ------------------------------------------------------- trazabilidad
export interface EntradaBitacora {
  id: number
  fechaHora: string
  usuario: string
  entidad: string
  idEntidad: number | null
  campo: string | null
  valorAnterior: string | null
  valorNuevo: string | null
  tipoOperacion: string
}

export interface Pagina<T> {
  contenido: T[]
  pagina: number
  tamanio: number
  total: number
}

// ------------------------------------------------------------ tejido adicional recomendado (RF30, D-56)
export interface RecomendacionTejido {
  id: number
  /** Día de la semana que llena en la máquina. */
  diaLlena: string
  partida: string
  numeroOp: string
  codigoArticulo: string
  nombreArticulo: string
  componente: 'PRINCIPAL' | 'COMPLEMENTO'
  kg: number
  /** Tejer a más tardar este día para que la tela llegue a tiempo. */
  diaTejido: string
  fechaEntrega: string | null
  codigoColor: string
  color: string
  clasificacionColor: ClasificacionColor
  tonalidad: Tonalidad
  /** Kg de la partida completa. */
  kgPartida: number
}

export interface MaquinaRecomendaciones {
  maquina: string
  faltan: number
  espacioLibre: number
  seCubren: number
  kg: number
  /** Por qué no se cubre todo el déficit, o null. */
  mensaje: string | null
  recomendaciones: RecomendacionTejido[]
}

export interface RecomendacionesTejido {
  idPlanificacion: number
  numeroVersion: number
  versionProyeccion: number
  esVigente: boolean
  cerrada: boolean
  totalKg: number
  totalPartidas: number
  maquinas: MaquinaRecomendaciones[]
}

