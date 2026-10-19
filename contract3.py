import os,re,sys,json

def read(p):
    try: return open(p,encoding='utf-8',errors='replace').read()
    except: return ''

FIELDDEF = re.compile(r'(?:public|protected|private)?\s*static\s+(?:final\s+)?String\s+(\w+)\s*=\s*([^;]+);')
REF = re.compile(r'\b\w+\.(\w+)\b')

def build(root):
    defs={}
    for dp,_,fs in os.walk(root):
        for f in fs:
            if f.endswith('.java'):
                for n,e in FIELDDEF.findall(read(os.path.join(dp,f))):
                    defs.setdefault(n,e.strip())
    return defs

def resolve(expr, defs, depth=0):
    if depth>10: return expr
    def rep(m):
        n=m.group(1)
        if n in defs: return '('+resolve(defs[n],defs,depth+1)+')'
        return m.group(0)
    new=REF.sub(rep, expr)
    if new==expr: return new
    return resolve(new,defs,depth+1)

def evalexpr(expr):
    # concatenate adjacent string literals, drop anything else
    parts=re.findall(r'"((?:[^"\\]|\\.)*)"', expr)
    return ''.join(parts)

def scan(root):
    defs=build(root)
    rows=[]
    for dp,_,fs in os.walk(root):
        for f in fs:
            if not f.endswith('.java'): continue
            p=os.path.join(dp,f); t=read(p)
            if '.put(' not in t: continue
            paths=set()
            for n in set(REF.findall(t) if False else re.findall(r'\b\w+\.(\w+)\b',t)):
                if n in defs:
                    v=evalexpr(resolve('X.'+n,defs))
                    if v.startswith('/') and len(v)>3 and ' ' not in v: paths.add(v)
            for lit in re.findall(r'"(/v\d/[A-Za-z0-9_/]*)"',t): paths.add(lit)
            for lit in re.findall(r'"(/(?:book|user|gps|pay|api|driver|fleet|cust|device|integrity|member|octopus|payme|wechat)/[A-Za-z0-9_/]*)"',t): paths.add(lit)
            if not paths: continue
            params=list(dict.fromkeys(re.findall(r'\.put\(\s*"([^"]+)"',t)))
            resp=sorted(set(re.findall(r'([A-Z][A-Za-z0-9_]*Item)\.class',t)))
            http=sorted(set(re.findall(r'new C\w+\(\s*(\d)',t)))
            rows.append({'file':p.replace(root+'/',''),'paths':sorted(paths),'http':http,
                         'params':params,'resp':resp,
                         'encrypt':'EncryptionUtil' in t})
    return rows

if __name__=='__main__':
    root,out=sys.argv[1],sys.argv[2]
    rows=scan(root)
    json.dump(rows,open(out,'w'),ensure_ascii=False,indent=1)
    ps={x for r in rows for x in r['paths']}
    print(out,'files:',len(rows),'paths:',len(ps))
