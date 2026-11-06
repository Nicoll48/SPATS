# SPATS — Aplicación web

Interfaz de SPATS (React 19 + Vite + TypeScript + Tailwind CSS v4) para el Planificador y el perfil Consulta. Todos los datos vienen de la API (`backend/`): la aplicación no calcula indicadores, saldos ni clasificaciones, solo muestra lo guardado.

## Uso

```bash
npm install
npm run dev       # http://localhost:5173 ; /api se reenvía a http://localhost:8080
npm run build     # chequeo de tipos + build de producción
npm run lint      # oxlint sobre src/
```

En producción, `VITE_API_URL` indica la dirección base de la API (por ejemplo `https://spats.empresa.pe`).

## Pantallas

| Ruta | Pantalla | Historias |
|---|---|---|
| `/` | Planificación semanal: calendario por máquina y día, asignadas, sobrantes, bloqueadas, arrastradas, alertas, bolsas; generar, exportar y cerrar | HU-11 a HU-16 |
| `/datos-semanales` | Carga de datos: plantillas .xlsx, versiones de la proyección, historial de cargas | HU-04, HU-05 |
| `/datos-registrados` | Consulta, corrección manual y descarga de pedidos, artículos, partidas, colores y proyección | HU-05, HU-06 |
| `/maquinas` | Máquinas e indisponibilidades | HU-07, HU-08 |
| `/incidencias` | Registro de incidencias y re-planificación desde hoy: propuesta, comparación con la vigente, confirmar o descartar | HU-17, HU-18 |
| `/historial` | Versiones, comparación y trazabilidad | HU-19, HU-21 |

## Dónde personalizar

| Qué | Archivo |
|---|---|
| Menú, íconos, rutas | `src/config/navigation.ts` y `src/App.tsx` |
| Llamadas a la API | `src/api/servicios.ts` (tipos en `src/api/tipos.ts`) |
| Textos de estados y clasificaciones | `src/lib/etiquetas.ts` |
| Colores claro y oscuro | `src/index.css` |

`_fuera_de_alcance/` guarda las pantallas y los datos simulados de la versión anterior que quedaron fuera del alcance del contexto (Escenarios, Reportes, parámetros en horas, proyección simulada y mock). No se compilan.
