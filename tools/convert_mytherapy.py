#!/usr/bin/env python3
"""Convert a Portuguese MyTherapy PDF into a reviewable Dose import.

Requires pdfplumber. The source PDF is embedded in the output; keep the output private.
No real medication data belongs in this script or the source repository.
"""
from pathlib import Path
import argparse, base64, calendar, collections, datetime as dt, hashlib, json, math, re, unicodedata
import pdfplumber

def clean(value): return re.sub(r'\s+', ' ', value or '').strip()
def folded(value): return ''.join(c for c in unicodedata.normalize('NFD', value.lower()) if not unicodedata.combining(c))
def iso_date(value): return dt.datetime.strptime(value, '%d/%m/%Y').date().isoformat()

def lines(page):
    groups=[]
    for word in sorted(page.extract_words(), key=lambda w:(w['top'],w['x0'])):
        group=next((g for g in groups if abs(g[0]-word['top'])<2),None)
        if group is None: group=[word['top'],[]];groups.append(group)
        group[1].append(word)
    return [(y,sorted(words,key=lambda w:w['x0'])) for y,words in sorted(groups)]

def medication_rows(pages,report_date):
    result=[];section=None
    weekdays=['segunda-feira','terca-feira','quarta-feira','quinta-feira','sexta-feira','sabado','domingo']
    for page_number,page in enumerate(pages,1):
        if 'Relatório mensal' in (page.extract_text() or ''):continue
        for y,words in lines(page):
            text=clean(' '.join(w['text'] for w in words))
            if text=='Medicamentos atualmente programados':section='active';continue
            if text=='Medicamentos sob demanda':section='as_needed';continue
            if text=='Não ativo':section='inactive';continue
            if not section or y<112:continue
            cells=['','','','']
            for w in words:
                index=0 if w['x0']<260 else 1 if w['x0']<462 else 2 if w['x0']<623 else 3
                cells[index]+=(' ' if cells[index] else '')+w['text']
            name,frequency,condition,note=map(clean,cells)
            if not name or not frequency:continue
            if not (re.match(r'^\d+(?:[.,]\d+)?\s+[^,]+\s*,',frequency) or 'quando necessário' in frequency):continue
            original_name=name
            name=re.sub(r'\s*\|\s*Estoque.*$','',name).strip()
            dose=re.match(r'^(\d+(?:[.,]\d+)?)\s+(.+?)\s*,',frequency)
            amount=float(dose.group(1).replace(',','.')) if dose else None
            unit=dose.group(2).strip() if dose else ''
            times=re.findall(r'(?<!\d)(?:[01]\d|2[0-3]):[0-5]\d(?!\d)',frequency)
            days=[i+1 for i,d in enumerate(weekdays) if d in folded(frequency)] or list(range(1,8))
            end=re.search(r'(?:termina a|terminado em)\s+(\d{2}/\d{2}/\d{4})',note)
            start=re.search(r'a partir de\s+(\d{2}/\d{2}/\d{4})',note)
            duration=re.search(r'por\s+(\d+)\s+dias,\s*(\d+)\s+dias restantes',note)
            status=section
            if section=='inactive':status='future' if start else 'ended' if end else 'paused'
            warnings=[]
            reported_count=re.search(r'(\d+)\s+vezes',frequency)
            if section!='as_needed' and reported_count and len(set(times))!=int(reported_count.group(1)):
                warnings.append('A quantidade de horários legíveis difere da frequência informada. Confira os horários antes de ativar os lembretes.')
            if amount is None:warnings.append('A quantidade por dose não consta no campo de frequência. Confira-a antes de registrar tomadas.')
            entry={'source_page':page_number,'source_group':section,'name':name,'amount':amount,'unit':unit,
                   'times':sorted(set(times)),'weekdays':days if section!='as_needed' else [],'status':status,
                   'start_date':iso_date(start.group(1)) if start else None,
                   'end_date':iso_date(end.group(1)) if end else (dt.date.fromisoformat(report_date)+dt.timedelta(days=int(duration.group(2)))).isoformat() if duration else None,
                   'end_date_calculated':bool(duration and not end),
                   'remaining_days_from_date':report_date if duration else None,
                   'condition':'' if condition=='-' else condition,'notes':note,
                   'frequency_text':frequency,'original_name':original_name,'stock':None,
                   'duration_days':int(duration.group(1)) if duration else None,
                   'remaining_days':int(duration.group(2)) if duration else None,
                   'review_reasons':warnings}
            entry['source_id']=hashlib.sha256(json.dumps([name,frequency,status,entry['start_date'],entry['end_date']],ensure_ascii=False).encode()).hexdigest()
            result.append(entry)
    if not result:raise ValueError('Nenhuma tabela MyTherapy em português foi reconhecida.')
    if len({e['source_id'] for e in result})!=len(result):raise ValueError('Há registros de medicamento indistinguíveis; revise a conversão.')
    return result

def color(curve):return tuple(round(float(v),3) for v in curve.get('non_stroking_color',()))
GRAY=(.894,.894,.894);DARK=(.435,.435,.435);GREEN=(0.,.792,.478)

def feature(circle,curves):
    width=circle['x1']-circle['x0'];height=circle['bottom']-circle['top']
    inner=[c for c in curves if color(c)==DARK and c['x0']>=circle['x0']-.03 and c['x1']<=circle['x1']+.03 and c['top']>=circle['top']-.03 and c['bottom']<=circle['bottom']+.03]
    if not inner:return [0,0,0,0,0]
    return [len(inner),sum(c['x1']-c['x0'] for c in inner)/width,
            sum(c['bottom']-c['top'] for c in inner)/height,
            (max(c['x1'] for c in inner)-min(c['x0'] for c in inner))/width,
            (max(c['bottom'] for c in inner)-min(c['top'] for c in inner))/height]

def monthly_history(page,medications):
    text=page.extract_text() or ''
    match=re.search(r'Relatório mensal MyTherapy para\s+(\w+)\s+(\d{4})',text)
    if not match:return None
    months=['janeiro','fevereiro','marco','abril','maio','junho','julho','agosto','setembro','outubro','novembro','dezembro']
    month=months.index(folded(match.group(1)))+1;year=int(match.group(2))
    words=page.extract_words();header=next(w for w in words if w['text']=='Histórico')
    day_words=[w for w in words if abs(w['top']-header['top'])<2 and w['text'].isdigit()]
    day_centers={int(w['text']):(w['x0']+w['x1'])/2 for w in day_words}
    expected_days=calendar.monthrange(year,month)[1]
    if set(day_centers)!=set(range(1,expected_days+1)):raise ValueError('Cabeçalho do histórico mensal incompleto.')
    legend=next(w for w in words if w['text']=='confirmado')
    row_lines=[]
    for y,line_words in lines(page):
        if not header['top']+10<y<legend['top']-8:continue
        left=[w for w in line_words if w['x0']<min(day_centers.values())-15]
        if not left:continue
        name=clean(' '.join(w['text'] for w in left)).split('|')[0].strip()
        if not name:continue
        med=next((m for m in medications if m['name']==name and m['source_group']=='active'),None)
        if med is None:raise ValueError('Medicamento do gráfico sem correspondência na tabela: '+name)
        row_lines.append({'name':name,'source_id':med['source_id'],'cy':sum((w['top']+w['bottom'])/2 for w in left)/len(left)})
    circles=[c for c in page.curves if (c.get('fill') or c.get('stroke')) and abs((c['x1']-c['x0'])-(c['bottom']-c['top']))<.05 and 8.0<c['x1']-c['x0']<8.9]
    templates={}
    for state,label in [('planned','planejado'),('unknown','desconhecido'),('skipped','ignorado'),('interrupted','interrompido')]:
        word=next(w for w in words if w['text']==label)
        choices=[c for c in circles if 0<word['x0']-c['x1']<12 and abs((c['top']+c['bottom'])/2-(word['top']+word['bottom'])/2)<3]
        if not choices:raise ValueError('Legenda de estado ausente: '+label)
        templates[state]=feature(min(choices,key=lambda c:word['x0']-c['x1']),page.curves)
    slots=collections.defaultdict(list)
    spacing=min(b['cy']-a['cy'] for a,b in zip(row_lines,row_lines[1:]))
    for c in circles:
        cx=(c['x0']+c['x1'])/2;cy=(c['top']+c['bottom'])/2
        if cx<min(day_centers.values())-6 or not header['bottom']<cy<legend['top']-8:continue
        row=min(range(len(row_lines)),key=lambda i:abs(row_lines[i]['cy']-cy))
        day=min(day_centers,key=lambda d:abs(day_centers[d]-cx))
        if abs(row_lines[row]['cy']-cy)>spacing*.48 or abs(day_centers[day]-cx)>3:raise ValueError('Marcador mensal fora da grade reconhecida.')
        if color(c)==GREEN:state='confirmed'
        elif color(c)==GRAY:
            vector=feature(c,page.curves)
            distances={s:sum((a-b)**2 for a,b in zip(vector,v)) for s,v in templates.items()}
            state=min(distances,key=distances.get)
            if distances[state]>.01:raise ValueError('Ícone de histórico não reconhecido; não será adivinhado.')
        else:raise ValueError('Cor desconhecida no histórico mensal.')
        slots[(row,day)].append((cy,state))
    rows=[]
    for i,row in enumerate(row_lines):
        rows.append({'name':row['name'],'source_id':row['source_id'],
                     'days':[{'date':dt.date(year,month,d).isoformat(),'states':[state for _,state in sorted(slots[(i,d)])]} for d in range(1,expected_days+1)]})
    return {'year':year,'month':month,'precision':'day','actual_times_available':False,'rows':rows}

def convert(source,output):
    data=Path(source).read_bytes()
    if len(data)>1_000_000:raise ValueError('Este conversor aceita PDFs de até 1 MB.')
    digest=hashlib.sha256(data).hexdigest()
    with pdfplumber.open(source) as doc:
        text=doc.pages[0].extract_text() or ''
        stamp=re.search(r'(\d{2}/\d{2}/\d{4})\s*[—–-]\s*(\d{2}:\d{2})',text)
        if not stamp:raise ValueError('Data de geração não reconhecida.')
        profile=re.search(r'Profile:\s*(.+)',text)
        medications=medication_rows(doc.pages,iso_date(stamp.group(1)))
        monthly=[h for p in doc.pages if (h:=monthly_history(p,medications)) is not None]
        result={'format':'dose-medication-import','version':1,
                'source':{'app':'MyTherapy','filename':Path(source).name,'sha256':digest,
                          'generated_at':iso_date(stamp.group(1))+'T'+stamp.group(2)+':00',
                          'timezone':None,'profile':profile.group(1).strip() if profile else ''},
                'medications':medications,'monthly_history':monthly,
                'document':{'mime_type':'application/pdf','sha256':digest,'data_base64':base64.b64encode(data).decode()}}
    encoded=json.dumps(result,ensure_ascii=False,indent=2)
    if len(encoded.encode())>1_500_000:raise ValueError('Arquivo de importação muito grande.')
    Path(output).parent.mkdir(parents=True,exist_ok=True);Path(output).write_text(encoded,encoding='utf-8')
    counts=collections.Counter(m['source_group'] for m in medications)
    states=collections.Counter(s for h in monthly for r in h['rows'] for d in r['days'] for s in d['states'])
    print(json.dumps({'medications':len(medications),'groups':dict(counts),'history_states':dict(states),'bytes':len(encoded.encode())}))
    return result

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('pdf');parser.add_argument('output')
    args=parser.parse_args();convert(args.pdf,args.output)
