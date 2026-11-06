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
export type EstadoPlanificacion = 'ABIERTA' | 'CERRADA' | 'REEMPLAZADA' | 'INVALIDADA' | 'PROPUESTA'
export type EstadoMaquina = 'OPERATIVA' | 'INDISPONIBLE' | 'EN_MANTENIMIENTO'
export type TipoIndisponibilidad = 'INDISPONIBLE' | 'MANTENIMIENTO'
export type TipoDato = 'PEDIDOS' | 'PARTIDAS' | 'PROYECCION_TEJEDURIA'
export type TipoIncidencia = 'INCIDENCIA_MAQUINA' | 'PROBLEMA_COMPLEMENTO' | 'CAMBIO_PRIORIDAD'
export type EstadoIncidencia = 'PENDIENTE' | 'CONSIDERADA'
export type EstadoProyeccion = 'VIGENTE' | 'REEMPLAZADA' | 'ELIMINADA'
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
  /** F de la vigente evaluada en el escenario nuevo (semana completa). */
  fVigente: number
  vigenteFactible: boolean
  incumplimientosVigente: string[]
  indicadores: Indicadores | null
  detenidaPorTiempo: boolean
  advertencias: string[]
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
