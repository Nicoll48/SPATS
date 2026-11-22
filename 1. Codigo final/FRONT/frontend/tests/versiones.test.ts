/**
 * Comparación de dos versiones de la planificación en el calendario (D-52): casos a, b, c y d.
 * Se ejecuta con `npm test` (node --test, sin dependencias nuevas).
 */
import { test } from 'node:test'
import assert from 'node:assert/strict'
import type { ClasificacionResultado, PartidaBloqueada, PartidaCalendario, PlanificacionDetalle } from '../src/api/tipos.ts'
import { agruparPorVersion, compararVersiones, estadoCarga, unirPorVersion, type TarjetaPartida } from '../src/lib/versiones.ts'

const DIAS = ['2026-11-16', '2026-11-17', '2026-11-18', '2026-11-19', '2026-11-20', '2026-11-21', '2026-11-22']

interface Ubicada { numero: string; maquina: string; dia: string; clasificacion?: ClasificacionResultado; orden?: number }

function partida(u: Ubicada): PartidaCalendario {
  return {
    numeroPartida: u.numero, numeroOp: '34300', fechaEntrega: '2026-11-30', codigoColor: 'C1', color: 'Azul', clasificacionColor: 'C',
    tonalidad: 'MEDIO', kg: 400, clasificacion: u.clasificacion ?? 'ASIGNADA', requiereLavado: false, ordenSecuencia: u.orden ?? 1,
    fechaDisponible: u.dia, estadoConfirmacionCierre: null,
  }
}

function bloqueada(numero: string, motivo: string | null): PartidaBloqueada {
  return { numeroPartida: numero, numeroOp: '34245', fechaEntrega: '2026-11-30', color: 'Rojo', tonalidad: 'OSCURO', barca: '400 C', kg: 400, componentes: [], motivo }
}

function plan(id: number, ubicadas: Ubicada[], bloqueadas: PartidaBloqueada[] = []): PlanificacionDetalle {
  const maquinas = ['M400-1', 'M400-2'].map((codigo) => ({
    codigo, barca: '400 C', clasificacionColor: 'C' as const, capacidad: 400, objetivoAplicado: 12, minimoDiario: 1, maximoDiario: 3,
    asignadas: 0, sobrantes: 0, diferencia: 0, lavados: 0,
    dias: DIAS.map((fecha) => {
      const partidas = ubicadas.filter((u) => u.maquina === codigo && u.dia === fecha).map(partida)
      return { fecha, indisponible: false, motivoIndisponibilidad: null, regulares: partidas.filter((p) => p.clasificacion === 'ASIGNADA').length, partidas }
    }),
  }))
  return {
    id, lunes: DIAS[0], domingo: DIAS[6], numeroVersion: id, tipoGeneracion: 'INICIAL', fechaGeneracion: '2026-11-11T09:30:00', esVigente: id === 1,
    estado: id === 1 ? 'ABIERTA' : 'CANDIDATA', fechaCierre: null, versionProyeccion: id,
    indicadores: { valorFuncionObjetivo: 10, desbalanceSemanal: 5, desbalanceDiario: 5, numeroLavados: 0, evaluaciones: 1, tiempoEjecucionMs: 1 },
    resumen: { asignadas: 0, sobrantes: 0, bloqueadas: bloqueadas.length, maquinasEnObjetivo: 0, totalMaquinas: 2, lavados: 0 },
    dias: DIAS, maquinas, bloqueadas, distribucionColor: [], alertas: [],
  }
}

function tarjetasDe(c: ReturnType<typeof compararVersiones>, numero: string): (TarjetaPartida & { maquina: string; dia: string })[] {
  return c.maquinas.flatMap((m) => m.celdas.flatMap((d) => d.tarjetas.filter((t) => t.partida.numeroPartida === numero)
    .map((t) => ({ ...t, maquina: m.codigo, dia: d.fecha }))))
}

const v1 = plan(1, [
  { numero: 'P-IGUAL', maquina: 'M400-1', dia: '2026-11-17' },
  { numero: 'P-ESTADO', maquina: 'M400-1', dia: '2026-11-18', clasificacion: 'ASIGNADA' },
  { numero: 'P-MOVIDA', maquina: 'M400-1', dia: '2026-11-17', orden: 2 },
  { numero: 'P-SOLO-V1', maquina: 'M400-2', dia: '2026-11-19' },
  { numero: 'P-BLOQ-V2', maquina: 'M400-2', dia: '2026-11-18' },
])
const v2 = plan(2, [
  { numero: 'P-IGUAL', maquina: 'M400-1', dia: '2026-11-17' },
  { numero: 'P-ESTADO', maquina: 'M400-1', dia: '2026-11-18', clasificacion: 'SOBRANTE' },
  { numero: 'P-MOVIDA', maquina: 'M400-2', dia: '2026-11-19' },
  { numero: 'P-SOLO-V2', maquina: 'M400-2', dia: '2026-11-16' },
], [bloqueada('P-BLOQ-V2', 'Hay tela, pero no alcanza para completarla')])

const comparado = compararVersiones([{ version: 1, plan: v1 }, { version: 2, plan: v2 }])

test('a) misma máquina, día y estado: una sola tarjeta con las dos versiones', () => {
  const t = tarjetasDe(comparado, 'P-IGUAL')
  assert.equal(t.length, 1)
  assert.equal(t[0].caso, 'igual')
  assert.deepEqual(t[0].versiones.map((v) => v.version), [1, 2])
  assert.equal(t[0].enLaOtra, null)
})

test('b) misma máquina y día con distinto estado: una tarjeta que indica en qué versión es sobrante', () => {
  const t = tarjetasDe(comparado, 'P-ESTADO')
  assert.equal(t.length, 1)
  assert.equal(t[0].caso, 'estado')
  assert.deepEqual(t[0].versiones.map((v) => [v.version, v.clasificacion]), [[1, 'ASIGNADA'], [2, 'SOBRANTE']])
})

test('c) en otra máquina o día: una tarjeta en cada lugar con el detalle de ambas ubicaciones', () => {
  const t = tarjetasDe(comparado, 'P-MOVIDA')
  assert.equal(t.length, 2)
  assert.ok(t.every((x) => x.caso === 'movida' && x.versiones.length === 1))
  assert.deepEqual(t.map((x) => [x.versiones[0].version, x.maquina, x.dia]).sort(),
    [[1, 'M400-1', '2026-11-17'], [2, 'M400-2', '2026-11-19']])
  assert.equal(t[0].enLaOtra, 'En V1: M400-1 mar 17/11 · En V2: M400-2 jue 19/11')
})

test('d) ubicada solo en una versión: dice si en la otra quedó bloqueada (con su motivo) o sigue pendiente', () => {
  const soloV1 = tarjetasDe(comparado, 'P-SOLO-V1')
  assert.equal(soloV1.length, 1)
  assert.equal(soloV1[0].caso, 'solo')
  assert.equal(soloV1[0].enLaOtra, 'En V2: sigue Pendiente')
  const bloq = tarjetasDe(comparado, 'P-BLOQ-V2')
  assert.equal(bloq[0].enLaOtra, 'En V2: Bloqueada — Hay tela, pero no alcanza para completarla')
  const soloV2 = tarjetasDe(comparado, 'P-SOLO-V2')
  assert.deepEqual(soloV2[0].versiones.map((v) => v.version), [2])
  assert.equal(soloV2[0].enLaOtra, 'En V1: sigue Pendiente')
})

test('conteo por caso: cada partida cuenta una vez', () => {
  assert.deepEqual(comparado.conteo, { igual: 1, estado: 1, movida: 1, solo: 3 })
})

test('contador por celda: uno por versión, con su color de carga', () => {
  const celda = comparado.maquinas.find((m) => m.codigo === 'M400-1')!.celdas.find((d) => d.fecha === '2026-11-17')!
  assert.deepEqual(celda.contadores.map((c) => [c.version, c.regulares]), [[1, 2], [2, 1]])
  assert.equal(estadoCarga({ regulares: 0, minimoDiario: 1, maximoDiario: 3 }), 'baja')
  assert.equal(estadoCarga({ regulares: 3, minimoDiario: 1, maximoDiario: 3 }), 'completa')
  assert.equal(estadoCarga({ regulares: 2, minimoDiario: 1, maximoDiario: 3 }), 'normal')
})

test('una sola versión marcada: tarjetas de esa versión, sin caso', () => {
  const solo = compararVersiones([{ version: 1, plan: v1 }])
  const tarjetas = solo.maquinas.flatMap((m) => m.celdas.flatMap((d) => d.tarjetas))
  assert.equal(tarjetas.length, 5)
  assert.ok(tarjetas.every((t) => t.caso === null && t.versiones.length === 1 && t.versiones[0].version === 1))
})

test('agrupación por celda: primero todas las de V1, luego las de V2 y al final las iguales en ambas', () => {
  const celda = comparado.maquinas.find((m) => m.codigo === 'M400-1')!.celdas.find((d) => d.fecha === '2026-11-17')!
  const grupos = agruparPorVersion(celda.tarjetas, comparado.versiones)
  assert.deepEqual(grupos.map((g) => [g.versiones, g.tarjetas.map((t) => t.partida.numeroPartida)]),
    [[[1], ['P-MOVIDA']], [[1, 2], ['P-IGUAL']]])
  const lunesM2 = comparado.maquinas.find((m) => m.codigo === 'M400-2')!.celdas.find((d) => d.fecha === '2026-11-18')!
  assert.deepEqual(agruparPorVersion(lunesM2.tarjetas, comparado.versiones).map((g) => g.versiones), [[1]])
  const unaVersion = compararVersiones([{ version: 1, plan: v1 }])
  const celdaUna = unaVersion.maquinas[0].celdas.find((d) => d.fecha === '2026-11-17')!
  assert.deepEqual(agruparPorVersion(celdaUna.tarjetas, unaVersion.versiones).map((g) => g.tarjetas.length), [2])
})

test('listas de sobrantes y bloqueadas: la misma fila en las dos versiones va una sola vez', () => {
  const filas = unirPorVersion([
    { version: 1, filas: [bloqueada('P-X', null), bloqueada('P-Y', null)] },
    { version: 2, filas: [bloqueada('P-X', null)] },
  ], (b) => b.numeroPartida)
  assert.deepEqual(filas.map((f) => [f.clave, f.versiones]), [['P-X', [1, 2]], ['P-Y', [1]]])
})
