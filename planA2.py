import os,re,sys,json,collections

def read(p):
    try: return open(p,encoding='utf-8',errors='replace').read()
    except: return ''

FIELDDEF=re.compile(r'(?:public|protected|private)?\s*static\s+(?:final\s+)?String\s+(\w+)\s*=\s*([^;]+);')
REF=re.compile(r'\b\w+\.(\w+)\b')
def defs(root):
    d={}
    for dp,_,fs in os.walk(root):
        for f in fs:
            if f.endswith('.java'):
                for n,e in FIELDDEF.findall(read(os.path.join(dp,f))): d.setdefault(n,e.strip())
    return d
def resolve(x,d,dep=0):
    if dep>12: return x
    n=REF.sub(lambda m:'('+resolve(d[m.group(1)],d,dep+1)+')' if m.group(1) in d else m.group(0),x)
    return n if n==x else resolve(n,d,dep+1)
def ev(x): return ''.join(re.findall(r'"((?:[^"\\]|\\.)*)"',x))

def blocks(t):
    """all brace blocks whose '{' follows a ')', at any nesting depth"""
    out=[];i=0;n=len(t)
    for m in re.finditer(r'\)\s*\{', t):
        start=m.end()-1
        depth=0;j=start
        while j<n:
            if t[j]=='{': depth+=1
            elif t[j]=='}':
                depth-=1
                if depth==0: break
            j+=1
        k=start-1
        while k>0 and t[k] not in '{};': k-=1
        hdr=re.sub(r'\s+',' ',t[k+1:start]).strip()
        out.append((hdr[:120], t[start+1:j]))
    return out

def class_bodies(t):
    out={}
    for m in re.finditer(r'\b(?:class|interface|enum)\s+(\w+)', t):
        start=t.find('{', m.end())
        if start<0: continue
        depth=0;j=start
        while j<len(t):
            if t[j]=='{': depth+=1
            elif t[j]=='}':
                depth-=1
                if depth==0: break
            j+=1
        out.setdefault(m.group(1), t[start+1:j])
    return out

def build(root):
    D=defs(root)
    req={}         # class -> {paths, methods}
    files={}       # relpath -> text
    for dp,_,fs in os.walk(root):
        for f in fs:
            if not f.endswith('.java'): continue
            p=os.path.join(dp,f); rel=p.replace(root+'/',''); t=read(p)
            files[rel]=t
            paths=set()
            for n in set(re.findall(r'\b\w+\.(\w+)\b',t)):
                if n in D:
                    v=ev(resolve('X.'+n,D))
                    if v.startswith('/') and len(v)>3 and ' ' not in v: paths.add(v)
            if paths:
                req[f[:-5]]={'paths':sorted(paths),
                             'methods':sorted(set(re.findall(r'public\s+\w[\w<>\[\], ]*\s+(m\d+\w*)\(',t)))}
    # listener type -> file
    cbodies={rel:class_bodies(t) for rel,t in files.items()}
    cdtos={}
    for rel,cb in cbodies.items():
        for cls,body in cb.items():
            cdtos.setdefault(cls,set()).update(re.findall(r'fromJson\([^;]*?,\s*([A-Z]\w*)(?:\[\])?\.class',body))
    ltype={}
    for rel,t in files.items():
        base=os.path.basename(rel)[:-5]
        for m in re.finditer(r'class\s+('+re.escape(base)+r'\$?\w*)\b', t):
            ltype.setdefault(m.group(1), rel)
        for m in re.finditer(r'class\s+(\w+)\b', t):
            ltype.setdefault(m.group(1), rel)
    return D,req,files,ltype,cdtos

def main(root):
    D,req,files,ltype,cdtos=build(root)
    rows=[]
    file_dtos={rel:set(re.findall(r'fromJson\([^;]*?,\s*([A-Z]\w*)(?:\[\])?\.class',t)) for rel,t in files.items()}
    file_codes={rel:set(re.findall(r'getStatus_code\(\)\.equals\("([^"]+)"\)',t)) for rel,t in files.items()}
    file_status={rel:set(re.findall(r'(?:getStatus\(\)\.equals|equals)\("([A-Z][A-Z0-9_]{2,})"\)',t)) for rel,t in files.items()}
    for rel,t in files.items():
        for hdr,body in blocks(t):
            eps=set()
            for cls,info in req.items():
                if cls not in body: continue
                for m in info['methods']:
                    if re.search(r'\b'+re.escape(cls)+r'\.m\d+\w*\(\)\.'+m+r'\(', body):
                        eps|=set(info['paths'])
            if not eps: continue
            direct=set(re.findall(r'fromJson\([^;]*?,\s*([A-Z]\w*)(?:\[\])?\.class',body))
            listeners=set(re.findall(r'new\s+([A-Z]\w*(?:\.[A-Z]\w*)+)\(', body)) | set(re.findall(r'\bnew\s+(\w+Listener\w*)\(',body))
            lfiles={ltype.get(l.split('.')[-1]) for l in listeners if ltype.get(l.split('.')[-1])}
            ldtos=set()
            for l in listeners:
                ldtos |= cdtos.get(l.split('.')[-1], set())
            rows.append({'file':rel,'hdr':hdr,'paths':sorted(eps),'direct':sorted(direct),
                         'listeners':sorted(l.split('.')[-1] for l in listeners)[:6],
                         'listener_dtos':sorted(ldtos),'file_dtos':sorted(file_dtos.get(rel,set())),
                         'codes':sorted(file_codes.get(rel,set())),'status':sorted(file_status.get(rel,set()))})
    return rows

if __name__=='__main__':
    root,out=sys.argv[1],sys.argv[2]
    rows=main(root); json.dump(rows,open(out,'w'),ensure_ascii=False,indent=1)
    print(out,'blocks',len(rows),'endpoints',len({p for r in rows for p in r['paths']}),
          'with dto',len({p for r in rows for p in r['paths'] if r['direct'] or r['listener_dtos'] or r['file_dtos']}))
