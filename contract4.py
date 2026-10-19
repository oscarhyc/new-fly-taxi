import os,re,sys,json

def read(p):
    try: return open(p,encoding='utf-8',errors='replace').read()
    except: return ''

FIELDDEF = re.compile(r'(?:public|protected|private)?\s*static\s+(?:final\s+)?String\s+(\w+)\s*=\s*([^;]+);')
REF = re.compile(r'\b\w+\.(\w+)\b')

def build_defs(root):
    d={}
    for dp,_,fs in os.walk(root):
        for f in fs:
            if f.endswith('.java'):
                for n,e in FIELDDEF.findall(read(os.path.join(dp,f))): d.setdefault(n,e.strip())
    return d

def resolve(expr,defs,dep=0):
    if dep>12: return expr
    new=REF.sub(lambda m: '('+resolve(defs[m.group(1)],defs,dep+1)+')' if m.group(1) in defs else m.group(0), expr)
    return new if new==expr else resolve(new,defs,dep+1)

def ev(expr): return ''.join(re.findall(r'"((?:[^"\\]|\\.)*)"', expr))

def methods(t):
    """yield (signature, body) using brace depth relative to class body"""
    out=[]; depth=0; mstart=None; i=0; n=len(t)
    while i<n:
        c=t[i]
        if c=='{':
            depth+=1
            if depth==2: mstart=i
        elif c=='}':
            if depth==2 and mstart is not None:
                # find signature: from previous '}' or ';' at depth 1
                j=mstart-1
                while j>0 and t[j] not in '};\n': j-=1
                sig=t[j+1:mstart].strip().replace('\n',' ')
                out.append((re.sub(r'\s+',' ',sig)[:160], t[mstart+1:i]))
                mstart=None
            depth-=1
        i+=1
    return out

def scan(root):
    defs=build_defs(root); rows=[]
    for dp,_,fs in os.walk(root):
        for f in fs:
            if not f.endswith('.java'): continue
            p=os.path.join(dp,f); t=read(p)
            if '.put(' not in t: continue
            for sig,body in methods(t):
                if '.put(' not in body: continue
                paths=set()
                for n in set(re.findall(r'\b\w+\.(\w+)\b', body)):
                    if n in defs:
                        v=ev(resolve('X.'+n,defs))
                        if v.startswith('/') and len(v)>3 and ' ' not in v: paths.add(v)
                for lit in re.findall(r'"(/v\d/[A-Za-z0-9_/]*)"',body): paths.add(lit)
                for lit in re.findall(r'"(/(?:book|user|gps|pay|api|driver|fleet|cust|device|integrity|member|octopus|payme|wechat)/[A-Za-z0-9_/]*)"',body): paths.add(lit)
                if not paths: continue
                rows.append({'file':p.replace(root+'/',''),'sig':sig,'paths':sorted(paths),
                             'params':list(dict.fromkeys(re.findall(r'\.put\(\s*"([^"]+)"',body))),
                             'resp':sorted(set(re.findall(r'([A-Z][A-Za-z0-9_]*Item)\.class',body))),
                             'http':sorted(set(re.findall(r'new C\w+\(\s*(\d)',body))),
                             'encrypt':'EncryptionUtil' in body})
    return rows

if __name__=='__main__':
    root,out=sys.argv[1],sys.argv[2]
    rows=scan(root); json.dump(rows,open(out,'w'),ensure_ascii=False,indent=1)
    print(out,'methods:',len(rows),'paths:',len({p for r in rows for p in r['paths']}))
