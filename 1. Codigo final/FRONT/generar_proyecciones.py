from pathlib import Path
import xlsxwriter

OUT = Path(__file__).parent / 'Entregables'
OUT.mkdir(exist_ok=True)
MAQUINAS = [('M1',600,16,'Color'),('M2',600,16,'Color'),('M3',400,16,'Color'),('M4',400,16,'Color'),('M5',300,16,'Blanco'),('M6',300,16,'Color'),('M7',200,16,'Color'),('M8',200,16,'Color'),('M9',200,16,'Blanco')]
DIAS = ['Lunes','Martes','Miércoles','Jueves','Viernes','Sábado','Domingo','Lunes siguiente']
COLORES = ['Azul Marino','Negro','Rojo Carmín','Verde Botella','Turquesa','Vino','Gris Perla','Amarillo Mostaza']

def formatos(wb):
    return {
        'titulo': wb.add_format({'bold':True,'font_size':18,'font_color':'#FFFFFF','bg_color':'#0F766E','align':'left','valign':'vcenter'}),
        'sub': wb.add_format({'font_color':'#475569','italic':True}),
        'head': wb.add_format({'bold':True,'font_color':'#FFFFFF','bg_color':'#0F766E','border':1,'align':'center','valign':'vcenter','text_wrap':True}),
        'cell': wb.add_format({'border':1,'border_color':'#D7DEE7','valign':'top'}),
        'num': wb.add_format({'border':1,'border_color':'#D7DEE7','num_format':'0.0'}),
        'ok': wb.add_format({'border':1,'bg_color':'#DCFCE7','font_color':'#166534'}),
        'bad': wb.add_format({'border':1,'bg_color':'#FEE2E2','font_color':'#991B1B'}),
        'warn': wb.add_format({'border':1,'bg_color':'#FEF3C7','font_color':'#92400E'}),
        'pct': wb.add_format({'border':1,'num_format':'0.0%'}),
    }

def cabecera(ws, title, subtitle, f, last_col):
    ws.hide_gridlines(2); ws.merge_range(0,0,0,last_col,title,f['titulo']); ws.set_row(0,28)
    ws.merge_range(1,0,1,last_col,subtitle,f['sub']); ws.freeze_panes(4,0)

def crear_balanceado(path):
    wb=xlsxwriter.Workbook(path); f=formatos(wb)
    ws=wb.add_worksheet('Proyección equilibrada'); cabecera(ws,'PROYECCIÓN DE TEJEDURÍA · SEMANA EQUILIBRADA','Cobertura por día y máquina; incluye reserva para el lunes siguiente.',f,14)
    headers=['Pedido','Artículo','Partida','Cliente','Tela principal','Complemento','Color','Kg','Máquina','Día disponible','Hora','Estado partida','Cobertura principal %','Cobertura complemento %','Lista para tintorería']
    ws.write_row(3,0,headers,f['head']); rows=[]
    n=1
    for mi,(maq,cap,obj,clas) in enumerate(MAQUINAS):
        for k in range(obj+2):
            dia=DIAS[k % 7] if k < obj else 'Lunes siguiente'
            color='Blanco Óptico' if clas=='Blanco' else COLORES[(mi+k)%len(COLORES)]
            kg=min(cap, max(120, cap-(k%3)*20)); pedido=f'PED-{26000+mi*100+k:05d}'; art=f'ART-{1000+mi*10+(k%4):04d}'; part=f'P-{5000+n:05d}'
            principal=round(kg*.9,1); comp=round(kg*.1,1)
            rows.append([pedido,art,part,f'Cliente {(mi+k)%12+1}',f'{principal} kg {"Jersey" if k%2==0 else "French terry"}',f'{comp} kg Rib',color,kg,maq,dia,'06:00','Pendiente',1,1,None]); n+=1
    for r,row in enumerate(rows,4):
        ws.write_row(r,0,row[:-1],f['cell']); ws.write_formula(r,14,f'=IF(AND(M{r+1}=100%,N{r+1}=100%,L{r+1}="Pendiente"),"Sí","No")',f['cell'])
    ws.autofilter(3,0,3+len(rows),14); ws.set_column('A:C',14); ws.set_column('D:D',20); ws.set_column('E:F',24); ws.set_column('G:G',19); ws.set_column('H:I',11); ws.set_column('J:L',18); ws.set_column('M:N',15); ws.set_column('O:O',18)
    ws.data_validation(4,11,3+len(rows),11,{'validate':'list','source':['Pendiente','Completado']}); ws.conditional_format(4,14,3+len(rows),14,{'type':'text','criteria':'containing','value':'Sí','format':f['ok']})
    rm=wb.add_worksheet('Resumen por máquina'); cabecera(rm,'RESUMEN DE ABASTECIMIENTO','El objetivo se cubre de forma pareja; la reserva queda para el lunes siguiente.',f,7); rm.write_row(3,0,['Máquina','Capacidad kg','Objetivo semanal','Listas semana','Reserva lunes','Diferencia','Cumplimiento','Resultado'],f['head'])
    for i,(maq,cap,obj,_) in enumerate(MAQUINAS,4):
        rm.write_row(i,0,[maq,cap,obj],f['cell']); rm.write_formula(i,3,f'=COUNTIFS(\'Proyección equilibrada\'!$I$5:$I$166,A{i+1},\'Proyección equilibrada\'!$J$5:$J$166,"<>Lunes siguiente",\'Proyección equilibrada\'!$O$5:$O$166,"Sí")',f['cell']); rm.write_formula(i,4,f'=COUNTIFS(\'Proyección equilibrada\'!$I$5:$I$166,A{i+1},\'Proyección equilibrada\'!$J$5:$J$166,"Lunes siguiente",\'Proyección equilibrada\'!$O$5:$O$166,"Sí")',f['cell']); rm.write_formula(i,5,f'=D{i+1}-C{i+1}',f['cell']); rm.write_formula(i,6,f'=D{i+1}/C{i+1}',f['pct']); rm.write_formula(i,7,f'=IF(F{i+1}=0,"Equilibrada",IF(F{i+1}>0,"Sobregirada","Faltante"))',f['cell'])
    rm.set_column('A:H',18); rm.conditional_format('H5:H13',{'type':'text','criteria':'containing','value':'Equilibrada','format':f['ok']})
    pedidos=wb.add_worksheet('Pedidos y artículos'); cabecera(pedidos,'MAESTRO DE PEDIDOS Y ARTÍCULOS','Universo de abastecimiento usado por la proyección.',f,5); pedidos.write_row(3,0,['Pedido','Artículo','Cliente','Color','Kg total','Estado'],f['head'])
    seen=set(); rr=4
    for row in rows:
        key=(row[0],row[1])
        if key in seen: continue
        seen.add(key); pedidos.write_row(rr,0,[row[0],row[1],row[3],row[6],row[7],'Pendiente'],f['cell']); rr+=1
    pedidos.set_column('A:B',15); pedidos.set_column('C:D',22); pedidos.set_column('E:F',14); wb.close()

def crear_desequilibrado(path):
    wb=xlsxwriter.Workbook(path); f=formatos(wb)
    ws=wb.add_worksheet('Proyección con brechas'); cabecera(ws,'PROYECCIÓN DE TEJEDURÍA · DESEQUILIBRADA','Escenario deliberado con máquinas sobregiradas y faltantes de abastecimiento.',f,13)
    ws.write_row(3,0,['Pedido','Artículo','Partida','Tela principal disponible kg','Complemento disponible kg','Kg requeridos','Máquina','Día','Estado','Cobertura principal %','Cobertura complemento %','Lista','Motivo de bloqueo','Impacto'],f['head'])
    counts={'M1':20,'M2':11,'M3':16,'M4':6,'M5':19,'M6':13,'M7':16,'M8':23,'M9':8}; rr=4; n=1
    for maq,_,_,_ in MAQUINAS:
        for k in range(counts[maq]):
            kg=180 if maq in ('M7','M8','M9') else 280 if maq in ('M5','M6') else 380 if maq in ('M3','M4') else 560
            cp=1 if k%7 else .55; cc=1 if k%9 else .35; principal=kg*.9*cp; comp=kg*.1*cc
            ws.write_row(rr,0,[f'PED-{28000+n:05d}',f'ART-{2000+(n%30):04d}',f'P-{7000+n:05d}',principal,comp,kg,maq,DIAS[k%7],'Pendiente',cp,cc],f['cell']); ws.write_formula(rr,11,f'=IF(AND(J{rr+1}=100%,K{rr+1}=100%),"Sí","No")',f['cell']); ws.write_formula(rr,12,f'=IF(L{rr+1}="Sí","",IF(J{rr+1}<100%,"Tela principal incompleta","Complemento incompleto"))',f['cell']); ws.write_formula(rr,13,f'=IF(L{rr+1}="Sí","Lista para tintorería","Bloqueada: faltan "&TEXT((1-MIN(J{rr+1},K{rr+1})),"0%"))',f['cell']); rr+=1; n+=1
    ws.autofilter(3,0,rr-1,13); ws.set_column('A:C',14); ws.set_column('D:F',22); ws.set_column('G:I',14); ws.set_column('J:L',17); ws.set_column('M:N',28); ws.conditional_format(4,11,rr-1,11,{'type':'text','criteria':'containing','value':'No','format':f['bad']})
    rm=wb.add_worksheet('Brechas por máquina'); cabecera(rm,'MÁQUINAS SOBREGIRADAS Y FALTANTES','La desviación se calcula contra el objetivo editable de cada máquina.',f,8); rm.write_row(3,0,['Máquina','Objetivo','Proyectadas','Listas completas','Diferencia','Estado','Desviación exacta','Partidas bloqueadas','Acción recomendada'],f['head'])
    for i,(maq,_,obj,_) in enumerate(MAQUINAS,4):
        rm.write_row(i,0,[maq,obj,counts[maq]],f['cell']); rm.write_formula(i,3,f'=COUNTIFS(\'Proyección con brechas\'!$G$5:$G$140,A{i+1},\'Proyección con brechas\'!$L$5:$L$140,"Sí")',f['cell']); rm.write_formula(i,4,f'=C{i+1}-B{i+1}',f['cell']); rm.write_formula(i,5,f'=IF(E{i+1}>0,"Sobregirada",IF(E{i+1}<0,"Faltante","Equilibrada"))',f['cell']); rm.write_formula(i,6,f'=IF(E{i+1}>0,"+"&E{i+1}&" partidas ("&TEXT(E{i+1}/B{i+1},"0%")&")",ABS(E{i+1})&" partidas faltantes ("&TEXT(ABS(E{i+1})/B{i+1},"0%")&")")',f['cell']); rm.write_formula(i,7,f'=COUNTIFS(\'Proyección con brechas\'!$G$5:$G$140,A{i+1},\'Proyección con brechas\'!$L$5:$L$140,"No")',f['cell']); rm.write_formula(i,8,f'=IF(E{i+1}>0,"Mover excedente a lunes siguiente",IF(E{i+1}<0,"Priorizar tejido compatible","Sin acción"))',f['cell'])
    rm.set_column('A:F',16); rm.set_column('G:I',30); rm.conditional_format('F5:F13',{'type':'text','criteria':'containing','value':'Sobregirada','format':f['bad']}); rm.conditional_format('F5:F13',{'type':'text','criteria':'containing','value':'Faltante','format':f['warn']})
    bl=wb.add_worksheet('Partidas faltantes'); cabecera(bl,'PARTIDAS FALTANTES POR MÁQUINA','Plantilla operativa para completar las brechas detectadas.',f,7); bl.write_row(3,0,['Máquina','Déficit','Pedido sugerido','Artículo sugerido','Color/clasificación','Kg objetivo','Fecha límite','Prioridad'],f['head'])
    rr=4
    for maq,cap,obj,clas in MAQUINAS:
        deficit=max(0,obj-counts[maq])
        for k in range(deficit): bl.write_row(rr,0,[maq,deficit,f'PED-SUG-{maq}-{k+1:02d}',f'ART-COMP-{maq}',clas,cap,'Viernes 18:00','Alta' if deficit>=5 else 'Media'],f['cell']); rr+=1
    bl.set_column('A:B',14); bl.set_column('C:E',22); bl.set_column('F:H',16); wb.close()

crear_balanceado(OUT/'Proyeccion_Tejeduria_Equilibrada.xlsx')
crear_desequilibrado(OUT/'Proyeccion_Tejeduria_Sobregiros_y_Faltantes.xlsx')
print(OUT)
