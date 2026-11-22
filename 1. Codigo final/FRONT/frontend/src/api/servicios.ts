import { REGISTROS_POR_PAGINA } from '@/components/ui/Paginacion'
import { api } from './cliente'
import type { RecomendacionesTejido } from './tipos'
import type {
  Arrastrada, ArticuloRegistrado, Bolsa, CargaEliminada, CargaHistorial, CargaResponse, ColorRegistrado, Comparacion, ComponenteRegistrado,
  ConfirmacionResponse, ContextoReplanificacion, DetallePedido, DetalleProyeccion, EntradaBitacora, EstadoSemana,
  CambioVigenteResponse, ContextoCambioVigente, GeneracionResponse, IncidenciaRegistrada, Indisponibilidad, ReplanificacionResponse,
  Maquina, ModoInteraccion, Pagina, PartidaRegistrada, PedidoRegistrado, PlanificacionDetalle, Sesion, TipoDato, Tonalidad, Usuario,
  Version, VersionProyeccion, VersionProyeccionEliminada, VersionesSemana,
} from './tipos'

/** Una función por endpoint de la API; las pantallas no arman rutas a mano. */

export const autenticacion = {
  iniciarSesion: (usuario: string, contrasena: string) => api.post<Sesion>('/auth/login', { usuario, contrasena }),
  renovar: () => api.post<Sesion>('/auth/renovacion'),
  cambiarModo: (modoInteraccion: ModoInteraccion) => api.put<Usuario>('/auth/yo/modo', { modoInteraccion }),
}

export const planificacion = {
  estadoSemana: (lunes: string) => api.get<EstadoSemana>(`/semanas/${lunes}/estado`),
  vigente: (lunes: string) => api.get<PlanificacionDetalle>(`/semanas/${lunes}/planificacion`),
  versiones: (lunes: string) => api.get<Version[]>(`/semanas/${lunes}/versiones`),
  /** Sin `idProyeccion` usa la versión vigente de la proyección. Con otra versión, la nueva queda como candidata (D-52). */
  generar: (lunes: string, idProyeccion?: number) =>
    api.post<GeneracionResponse>(`/semanas/${lunes}/planificaciones`, undefined, idProyeccion === undefined ? undefined : { proyeccion: idProyeccion }),
  /** Versiones de la proyección de la semana con su planificación (panel de versiones, D-52). */
  versionesProyeccion: (lunes: string) => api.get<VersionesSemana>(`/semanas/${lunes}/versiones-proyeccion`),
  contextoCambioVigente: (id: number) => api.get<ContextoCambioVigente>(`/planificaciones/${id}/cambio-vigente`),
  usarComoVigente: (id: number, partidasEnMaquina: string[]) =>
    api.post<CambioVigenteResponse>(`/planificaciones/${id}/cambio-vigente`, { partidasEnMaquina }),
  detalle: (id: number) => api.get<PlanificacionDetalle>(`/planificaciones/${id}`),
  bolsas: (id: number) => api.get<Bolsa[]>(`/planificaciones/${id}/bolsas`),
  comparar: (a: number, b: number) => api.get<Comparacion>('/planificaciones/comparacion', { a, b }),
  exportar: (id: number) => api.descargar(`/planificaciones/${id}/exportacion`, `planificacion_${id}.xlsx`),
  /** RF30 (D-56): tejido adicional recomendado para la misma semana de una versión. */
  recomendaciones: (id: number) => api.get<RecomendacionesTejido>(`/planificaciones/${id}/recomendaciones`),
  /** Excel para tejeduría de la vigente, ordenado por día de tejido. */
  exportarRecomendaciones: (lunes: string) => api.descargar(`/semanas/${lunes}/recomendaciones/exportacion`, `tejido_adicional_${lunes}.xlsx`),
  cerrar: (id: number, partidasCompletadas: string[]) =>
    api.post<{ completadas: number; arrastradas: number; pedidosCompletados: number }>(`/planificaciones/${id}/cierre`, { partidasCompletadas }),
  arrastradas: () => api.get<Arrastrada[]>('/partidas/arrastradas'),
  completarArrastrada: (numeroPartida: string) => api.post<void>(`/partidas/arrastradas/${encodeURIComponent(numeroPartida)}/completado`),
}

export const replanificacion = {
  contexto: (lunes: string) => api.get<ContextoReplanificacion>(`/semanas/${lunes}/replanificacion`),
  /** `porAnulacion`: re-planificar desde hoy para deshacer una incidencia anulada, sin exigir incidencias (D-57). */
  replanificar: (lunes: string, incidencias: number[], partidasEnMaquina: string[], porAnulacion = false) =>
    api.post<ReplanificacionResponse>(`/semanas/${lunes}/replanificacion`, { incidencias, partidasEnMaquina, porAnulacion }),
  confirmar: (id: number) => api.post<ConfirmacionResponse>(`/planificaciones/${id}/confirmacion`),
  descartar: (id: number) => api.eliminar<void>(`/planificaciones/${id}`),
}

export const cargas = {
  importar: (tipo: TipoDato, archivo: File, semana?: string, confirmado = false) =>
    api.subir<CargaResponse>(`/cargas/${tipo}`, archivo, { semana, confirmado }),
  historial: () => api.get<CargaHistorial[]>('/cargas'),
  eliminar: (id: number) => api.eliminar<CargaEliminada>(`/cargas/${id}`),
  plantilla: (tipo: TipoDato) => api.descargar(`/plantillas/${tipo}`, `plantilla_${tipo.toLowerCase()}.xlsx`),
  exportar: (tipo: TipoDato, semana?: string) => api.descargar(`/exportaciones/${tipo}`, `${tipo.toLowerCase()}.xlsx`, { semana }),
}

export const datos = {
  pedidos: () => api.get<PedidoRegistrado[]>('/pedidos'),
  modificarPedido: (id: number, cuerpo: { cliente: string; fechaEntrega: string }) => api.put<PedidoRegistrado>(`/pedidos/${id}`, cuerpo),
  eliminarPedido: (id: number) => api.eliminar<void>(`/pedidos/${id}`),
  modificarDetallePedido: (id: number, pesoTotalRequerido: number) => api.put<DetallePedido>(`/detalles-pedido/${id}`, { pesoTotalRequerido }),
  articulos: () => api.get<ArticuloRegistrado[]>('/articulos'),
  modificarArticulo: (id: number, nombre: string) => api.put<ArticuloRegistrado>(`/articulos/${id}`, { nombre }),
  eliminarArticulo: (id: number) => api.eliminar<void>(`/articulos/${id}`),
  partidas: () => api.get<PartidaRegistrada[]>('/partidas'),
  /** Partidas pendientes sin ninguna máquina de su capacidad que acepte su tonalidad (D-51). */
  partidasSinMaquinaCompatible: () => api.get<PartidaRegistrada[]>('/partidas/sin-maquina-compatible'),
  eliminarPartida: (id: number) => api.eliminar<void>(`/partidas/${id}`),
  modificarPartida: (id: number, cuerpo: { codigoColor: string; barca: string | null; pesoTotal: number }, confirmado = false) =>
    api.put<{ partida: PartidaRegistrada; advertencia: string | null }>(`/partidas/${id}`, cuerpo, { confirmado }),
  modificarComponente: (id: number, cuerpo: { tipoComponente: string; tubularAbierto: boolean; pesoRequerido: number }, confirmado = false) =>
    api.put<ComponenteRegistrado>(`/componentes/${id}`, cuerpo, { confirmado }),
  colores: () => api.get<ColorRegistrado[]>('/colores'),
  modificarColor: (id: number, cuerpo: { nombre: string; clasificacionColor: string; tonalidad: string }) =>
    api.put<ColorRegistrado>(`/colores/${id}`, cuerpo),
  eliminarColor: (id: number) => api.eliminar<void>(`/colores/${id}`),
  versionesProyeccion: (lunes: string) => api.get<VersionProyeccion[]>(`/semanas/${lunes}/proyecciones`),
  detalleProyeccion: (id: number) => api.get<DetalleProyeccion[]>(`/proyecciones/${id}/detalles`),
  modificarDetalleProyeccion: (id: number, cuerpo: { fechaProyectada: string; kgProyectados: number }, confirmado = false) =>
    api.put<DetalleProyeccion>(`/detalles-proyeccion/${id}`, cuerpo, { confirmado }),
  eliminarDetalleProyeccion: (id: number, confirmado = false) => api.eliminar<void>(`/detalles-proyeccion/${id}`, { confirmado }),
  eliminarVersionProyeccion: (id: number) => api.eliminar<VersionProyeccionEliminada>(`/proyecciones/${id}`, { confirmado: true }),
  restaurarProyeccion: (id: number) => api.post<VersionProyeccion>(`/proyecciones/${id}/restauracion`),
}

export interface GuardarMaquina {
  codigo?: string
  capacidad: number
  clasificacionColor: string
  objetivoSemanal: number
  estado: string
  /** D-51: al menos una. */
  tonalidadesAceptadas: Tonalidad[]
}

export const maquinas = {
  buscar: (busqueda?: string) => api.get<Maquina[]>('/maquinas', { busqueda }),
  registrar: (cuerpo: GuardarMaquina) => api.post<Maquina>('/maquinas', cuerpo),
  /** `confirmado`: el planificador ya vio el aviso de partidas afectadas por el cambio de tonalidades (D-51). */
  modificar: (id: number, cuerpo: GuardarMaquina, confirmado = false) => api.put<Maquina>(`/maquinas/${id}`, cuerpo, { confirmado }),
  eliminar: (id: number) => api.eliminar<void>(`/maquinas/${id}`),
  todasLasIndisponibilidades: () => api.get<Indisponibilidad[]>('/maquinas/indisponibilidades'),
  eliminarIndisponibilidad: (id: number) => api.eliminar<void>(`/maquinas/indisponibilidades/${id}`),
  indisponibilidades: (id: number) => api.get<Indisponibilidad[]>(`/maquinas/${id}/indisponibilidades`),
  registrarIndisponibilidad: (id: number, cuerpo: { fechaHoraInicio: string; fechaHoraFin: string; tipo: string; motivo: string }) =>
    api.post<Indisponibilidad>(`/maquinas/${id}/indisponibilidades`, cuerpo),
}

export interface RegistrarIncidencia {
  tipo: string
  codigoMaquina?: string
  numeroPartida?: string
  numeroOp?: string
  fechaHoraOcurrencia: string
  fechaHoraDisponibilidad?: string
  nuevaFechaEntrega?: string
  descripcion?: string
}

export const incidencias = {
  deLaSemana: (lunes: string) => api.get<IncidenciaRegistrada[]>(`/semanas/${lunes}/incidencias`),
  registrar: (lunes: string, cuerpo: RegistrarIncidencia) => api.post<IncidenciaRegistrada>(`/semanas/${lunes}/incidencias`, cuerpo),
  /** Cerrar sin re-planificar (D-54). */
  atender: (lunes: string, id: number, motivo?: string) => api.post<IncidenciaRegistrada>(`/semanas/${lunes}/incidencias/${id}/atencion`, { motivo }),
  /** Anular (D-57): registrada por error o que no ocurrió. */
  anular: (lunes: string, id: number, motivo?: string) => api.post<IncidenciaRegistrada>(`/semanas/${lunes}/incidencias/${id}/anulacion`, { motivo }),
}

export const trazabilidad = {
  consultar: (pagina: number, entidad?: string) => api.get<Pagina<EntradaBitacora>>('/bitacora', { pagina, tamanio: REGISTROS_POR_PAGINA, entidad }),
}
