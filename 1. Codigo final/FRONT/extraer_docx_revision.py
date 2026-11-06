from pathlib import Path
from zipfile import ZipFile
import xml.etree.ElementTree as ET
import json

NS = {'w': 'http://schemas.openxmlformats.org/wordprocessingml/2006/main'}
W = '{%s}' % NS['w']
entrada = Path(r'Documento\20223270_NicollTone_EderQuispe_E1 (12).docx')
salida = Path('Entregables/revision_documento')
salida.mkdir(parents=True, exist_ok=True)

with ZipFile(entrada) as z:
    nombres = set(z.namelist())
    raiz = ET.fromstring(z.read('word/document.xml'))
    lineas = []
    estructura = []
    contador_tablas = 0
    for nodo in raiz.find('w:body', NS):
        if nodo.tag == W + 'p':
            texto = ''.join(t.text or '' for t in nodo.iter(W + 't')).strip()
            estilo_nodo = nodo.find('w:pPr/w:pStyle', NS)
            estilo = estilo_nodo.get(W + 'val') if estilo_nodo is not None else ''
            if texto:
                lineas.append(texto)
                estructura.append({'tipo': 'parrafo', 'estilo': estilo, 'texto': texto})
        elif nodo.tag == W + 'tbl':
            contador_tablas += 1
            filas = []
            for tr in nodo.findall('w:tr', NS):
                celdas = []
                for tc in tr.findall('w:tc', NS):
                    celdas.append(' '.join(''.join(t.text or '' for t in p.iter(W + 't')).strip() for p in tc.findall('.//w:p', NS)).strip())
                filas.append(celdas)
            estructura.append({'tipo': 'tabla', 'numero': contador_tablas, 'filas': filas})
            lineas.append(f'\n[TABLA {contador_tablas}]')
            lineas.extend(' | '.join(fila) for fila in filas)
    comentarios = []
    if 'word/comments.xml' in nombres:
        cr = ET.fromstring(z.read('word/comments.xml'))
        for comentario in cr.findall('w:comment', NS):
            comentarios.append({
                'autor': comentario.get(W + 'author'),
                'fecha': comentario.get(W + 'date'),
                'texto': ''.join(t.text or '' for t in comentario.iter(W + 't')),
            })
    propiedades = {}
    if 'docProps/app.xml' in nombres:
        app = ET.fromstring(z.read('docProps/app.xml'))
        for hijo in app:
            propiedades[hijo.tag.split('}')[-1]] = hijo.text
    resumen = {
        'propiedades': propiedades,
        'parrafos': sum(1 for e in estructura if e['tipo'] == 'parrafo'),
        'tablas': contador_tablas,
        'imagenes': sum(1 for n in nombres if n.startswith('word/media/')),
        'comentarios': comentarios,
        'inserciones_control_cambios': len(raiz.findall('.//w:ins', NS)),
        'eliminaciones_control_cambios': len(raiz.findall('.//w:del', NS)),
    }

(salida / 'texto_completo.txt').write_text('\n'.join(lineas), encoding='utf-8')
(salida / 'estructura.json').write_text(json.dumps(estructura, ensure_ascii=False, indent=2), encoding='utf-8')
(salida / 'resumen.json').write_text(json.dumps(resumen, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(resumen, ensure_ascii=False, indent=2))
