"""Convierte los datos reales de la empresa a las plantillas de carga de SPATS para la semana del 9 al 15 de
noviembre de 2026 (requiere openpyxl). Todas las fechas se mueven 56 días (8 semanas exactas): así se conservan
los días de la semana y la distancia entre los pedidos y la proyección.

Fuentes:
  - Especificaciones/PROG SETIEMBRE (1).xlsx: pedidos, artículos y pesos.
  - Especificaciones/PROG TEJIDO (1).xlsx: proyección; solo la ventana 12-18/09, que pasa a ser 07-13/11.
  - Experimentacion_R2.2/.../instancias: fecha de entrega, tonalidad, barca, partidas y componentes, tal como
    los armó R/01_preparar_datos.R a partir de PARTIDAS (1).xlsx. Los archivos reales no traen fecha de entrega.
"""
import collections
import csv
import datetime as dt
import os
import re

import openpyxl
from openpyxl.styles import Font
from openpyxl.utils import get_column_letter

SALIDA = os.path.dirname(os.path.abspath(__file__))
ESPECIFICACIONES = os.path.dirname(SALIDA)
RAIZ = os.path.dirname(ESPECIFICACIONES)
INSTANCIAS = os.path.join(RAIZ, "Experimentacion_R2.2", "Experimentacion_R2.2", "R_experimentacion", "instancias")

DESPLAZAMIENTO = dt.timedelta(days=56)
LUNES = dt.date(2026, 11, 9)
INICIO_VENTANA, FIN_VENTANA = LUNES - dt.timedelta(days=2), LUNES + dt.timedelta(days=4)
TONALIDAD = {"1": "BLANCO", "2": "CLARO", "3": "MEDIO", "4": "OSCURO"}


def limpiar(valor):
    return " ".join(str(valor).replace("\xa0", " ").split()) if valor is not None else ""


def clave(nombre):
    """Misma clave que el R2.2: los archivos escriben un artículo con pequeñas diferencias."""
    return re.sub(r"[\s.\-]", "", limpiar(nombre).upper())


def sin_prefijo(codigo):
    """D-25: «(313811)TW2021AA00P03» es el artículo «TW2021AA00P03»."""
    return re.sub(r"^\(\d+\)\s*", "", limpiar(codigo))


def numero_op(valor):
    return re.sub(r"\.0+$", "", limpiar(valor))


def leer_csv(nombre):
    with open(os.path.join(INSTANCIAS, nombre), encoding="utf-8") as archivo:
        return list(csv.DictReader(archivo))


def mover(fecha_iso):
    return (dt.date.fromisoformat(fecha_iso) + DESPLAZAMIENTO).isoformat()


def escribir(nombre, hojas):
    libro = openpyxl.Workbook()
    libro.remove(libro.active)
    for titulo, cabecera, filas in hojas:
        hoja = libro.create_sheet(titulo)
        hoja.append(cabecera)
        for celda in hoja[1]:
            celda.font = Font(bold=True)
        for fila in filas:
            hoja.append(fila)
        hoja.freeze_panes = "A2"
        for i, titulo_columna in enumerate(cabecera):
            ancho = max([len(str(titulo_columna))] + [len(str(fila[i])) for fila in filas])
            hoja.column_dimensions[get_column_letter(i + 1)].width = min(ancho + 2, 60)
    libro.save(os.path.join(SALIDA, nombre))


pedidos = {r["pedido"]: r for r in leer_csv("datos_pedidos.csv")}
partidas = leer_csv("datos_partidas.csv")
componentes = leer_csv("datos_componentes.csv")

# Catálogo de artículos: clave del nombre -> código sin prefijo -> primer nombre encontrado
codigo_de = {}
nombre_de = {}


def registrar_articulo(clave_articulo, codigo, nombre):
    codigo = sin_prefijo(codigo)
    if codigo_de.setdefault(clave_articulo, codigo) != codigo:
        raise SystemExit(f"{clave_articulo} tiene dos códigos: {codigo_de[clave_articulo]} y {codigo}")
    nombre_de.setdefault(codigo, limpiar(nombre))


for c in componentes:
    registrar_articulo(c["clave_articulo"], c["cod_articulo"], c["articulo"])

# Pedidos: se omiten los RE### (sin partidas ni proyección, y fuera de los pedidos del R2.2)
detalle = collections.OrderedDict()
hoja = openpyxl.load_workbook(os.path.join(ESPECIFICACIONES, "PROG SETIEMBRE (1).xlsx"), data_only=True).active
for fila in hoja.iter_rows(min_row=5, values_only=True):
    op = numero_op(fila[4])
    if op in pedidos and fila[6]:
        registrar_articulo(clave(fila[5]), fila[6], fila[5])
        par = (op, codigo_de[clave(fila[5])])
        detalle[par] = detalle.get(par, 0) + float(fila[7])

# Proyección real
proyeccion = collections.OrderedDict()
hoja = openpyxl.load_workbook(os.path.join(ESPECIFICACIONES, "PROG TEJIDO (1).xlsx"), data_only=True).active
filas = list(hoja.iter_rows(min_row=3, values_only=True))
fechas = filas[1]
for fila in filas[2:]:
    if not fila[2]:
        continue
    op, codigo = numero_op(fila[2]), codigo_de[clave(fila[3])]
    for j in range(6, len(fila)):
        if fila[j]:
            registro = (op, codigo, fechas[j].date())
            proyeccion[registro] = proyeccion.get(registro, 0) + float(fila[j])

# Componentes. Un par pedido-artículo sin fila en PROG toma el peso de sus partidas o de su proyección.
partida_op = {p["partida"]: p["pedido"] for p in partidas}
componentes_partida = collections.OrderedDict()
faltantes = collections.Counter()
for c in componentes:
    codigo = codigo_de[c["clave_articulo"]]
    llave = (c["partida"], codigo)
    if llave in componentes_partida:
        componentes_partida[llave]["kg"] += float(c["kg_requeridos"])
    else:
        componentes_partida[llave] = {"rol": c["rol"].upper(), "ta": c["tipo_ta"], "kg": float(c["kg_requeridos"])}
    par = (partida_op[c["partida"]], codigo)
    if par not in detalle:
        faltantes[par] += float(c["kg_requeridos"])
proyectado = collections.Counter()
for (op, codigo, _), kg in proyeccion.items():
    proyectado[(op, codigo)] += kg
for par, kg in proyectado.items():
    if par not in detalle and par not in faltantes:
        faltantes[par] = kg
detalle.update(faltantes)

color_nombre = {}
for p in partidas:
    color_nombre.setdefault(limpiar(p["cod_color"]), limpiar(p["color"]))

filas_pedidos = [[op, limpiar(pedidos[op]["cliente"]), mover(pedidos[op]["fecha_entrega"]), codigo, nombre_de[codigo], round(kg, 2)]
                 for (op, codigo), kg in sorted(detalle.items())]
escribir("1_pedidos_semana_2026-11-09.xlsx", [("Pedidos", ["numero_op", "cliente", "fecha_entrega", "codigo_articulo",
                                                           "nombre_articulo", "peso_total_requerido"], filas_pedidos)])

# Parque real: 200 B (3 máquinas), 200 C (2) y el resto de color. Hasta 200 kg, los claros y medios (y los blancos)
# van a 200 B y los oscuros a 200 C. Un color tiene una sola clasificación (D-07): si también tiene partidas de más
# de 200 kg, que van a barcas C, todo el color queda como C.
barcas_color = collections.defaultdict(set)
for p in partidas:
    barcas_color[limpiar(p["cod_color"])].add(p["barca"])


def clasificacion(p):
    es_oscuro = TONALIDAD[p["tonalidad"]] == "OSCURO"
    return "B" if not es_oscuro and barcas_color[limpiar(p["cod_color"])] == {"200 C"} else "C"


filas_partidas = [[p["partida"], p["pedido"], limpiar(p["cod_color"]), color_nombre[limpiar(p["cod_color"])], clasificacion(p),
                   TONALIDAD[p["tonalidad"]], "200 B" if clasificacion(p) == "B" else p["barca"], float(p["peso"])]
                  for p in partidas]
filas_componentes = [[partida, codigo, v["rol"], "SI" if v["ta"] == "A" else "NO", v["kg"]]
                     for (partida, codigo), v in componentes_partida.items()]
escribir("2_partidas_semana_2026-11-09.xlsx", [
    ("Partidas", ["numero_partida", "numero_op", "codigo_color", "nombre_color", "clasificacion_color", "tonalidad",
                  "barca", "peso_total"], filas_partidas),
    ("Componentes", ["numero_partida", "codigo_articulo", "tipo_componente", "tubular_abierto", "peso_requerido"], filas_componentes)])

filas_proyeccion = []
for (op, codigo, fecha), kg in proyeccion.items():
    nueva = fecha + DESPLAZAMIENTO
    if INICIO_VENTANA <= nueva <= FIN_VENTANA:
        filas_proyeccion.append([op, codigo, nueva.isoformat(), round(kg, 3)])
filas_proyeccion.sort(key=lambda f: (f[2], f[0], f[1]))
escribir("3_proyeccion_tejeduria_semana_2026-11-09.xlsx", [("Proyeccion", ["numero_op", "codigo_articulo", "fecha_proyectada",
                                                                          "kg_proyectados"], filas_proyeccion)])

print(f"pedidos {len(pedidos)}, detalles {len(filas_pedidos)}, partidas {len(filas_partidas)}, componentes {len(filas_componentes)}, "
      f"proyeccion {len(filas_proyeccion)} filas y {sum(f[3] for f in filas_proyeccion)} kg")
print("pares sin fila en PROG:", sorted(faltantes))
print("partidas por barca:", sorted(collections.Counter(f[6] for f in filas_partidas).items()))
