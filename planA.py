import os,re,sys,json,collections

def read(p):
    try: return open(p,encoding='utf-8',errors='replace').read()
    except: return ''

FIELDDEF=re.compile(r'(?:public|protected|private)?\s*static\s+(?:final\s+)?String\s+(\w+)\s*=\s*([^;]+);')
REF=re.compile(r'\b\w+\.(\w+)\b')
def build_defs(root):
    d={}
    for dp,_,fs in os.walk(root):
        for f in fs:
            if f.endswith('.java'):
                for n,e in FIELDDEF.findall(read(os.path.join(dp,f))): d.setdefault(n,e.strip())
    return d
def resolve(x,d,depth=0):
    if depth>12: return x
    n=REF.sub(lambda m:'('+resolve(d[m.group(1)],d,depth+1)+')' if m.group(1) in d else m.group(0),x)
    return n if n==x else resolve(n,d,depth+1)
def ev(x): return ''.join(re.findall(r'"((?:[^"\\]|\\.)*)"',x))

def methods(t):
    out=[];depth=0;start=None;i=0
    while i<len(t):
        c=t[i]
        if c=='{':
            depth+=1
            if depth==2: start=i
        elif c=='}':
            if depth==2 and start is not None:
                j=start-1
                while j>0 and t[j] not in '};\n': j-=1
                out.append((re.sub(r'\s+',' ',t[j+1:start]).strip()[:160], t[start+1:i]))
                start=None
            depth-=1
        i+=1
    return out

def main(root):
    defs=build_defs(root)
    # pass 1: request classes -> endpoints & public method names
    reqmap={}   # class simple name -> {'paths':..., 'methods':set()}
    for dp,_,fs in os.walk(root):
        for f in fs:
            if not f.endswith('.java') or f.startswith('Abstract'): continue
            p=os.path.join(dp,f); t=read(p)
            paths=set()
            for n in set(re.findall(r'\b\w+\.(\w+)\b',t)):
                if n in defs:
                    v=ev(resolve('X.'+n,defs))
                    if v.startswith('/') and len(v)>3 and ' ' not in v: paths.add(v)
            if not paths: continue
            name=f[:-5]
            reqmap[name]={'paths':sorted(paths),'methods':sorted(set(re.findall(r'public\s+\w[\w<>\[\], ]*\s+(m\d+\w*)\(',t))),'file':p.replace(root+'/','')}
    # pass 2: call sites
    rows=[]
    for dp,_,fs in os.walk(root):
        for f in fs:
            if not f.endswith('.java'): continue
            p=os.path.join(dp,f); t=read(p)
            file_dtos=sorted(set(re.findall(r'fromJson\([^;]*?,\s*([A-Z]\w*)\.class',t)))
            file_status=sorted(set(re.findall(r'getStatus\(\)\.equals\("([^"]+)"\)',t)))
            file_codes=sorted(set(re.findall(r'getStatus_code\(\)\.equals\("([^"]+)"\)',t)))
            for sig,body in methods(t):
                eps=set()
                for cls,info in reqmap.items():
                    if cls not in body: continue
                    for m in info['methods']:
                        if re.search(r'\b'+cls+r'\.m\d+b\(\)\.'+m+r'\(',body) or re.search(r'\b'+cls+r'\.\w+\(\)\.'+m+r'\(',body):
                            eps|=set(info['paths'])
                if not eps: continue
                dtos=sorted(set(re.findall(r'fromJson\([^;]*?,\s*([A-Z]\w*)\.class',body)))
                statuses=sorted(set(re.findall(r'getStatus\(\)\.equals\("([^"]+)"\)',body))|
                                set(re.findall(r'equals\("([^"]+)"\)\s*\)?\s*\{?[^\n]*getStatus\(\)',body)))
                codes=sorted(set(re.findall(r'getStatus_code\(\)\.equals\("([^"]+)"\)',body)))
                rows.append({'file':p.replace(root+'/',''),'sig':sig,'paths':sorted(eps),'resp':dtos,
                             'status_values':statuses,'status_codes':codes,
                             'file_dtos':file_dtos,'file_status':file_status,'file_codes':file_codes})
    return rows,reqmap

if __name__=='__main__':
    root,out=sys.argv[1],sys.argv[2]
    rows,reqmap=main(root)
    json.dump({'callsites':rows,'requests':reqmap},open(out,'w'),ensure_ascii=False,indent=1)
    ep={p for r in rows for p in r['paths']}
    withdto={p for r in rows for p in r['paths'] if r['resp']}
    print(out,'callsites',len(rows),'| endpoints touched',len(ep),'| endpoints with DTO',len(withdto))
