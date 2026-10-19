import re,sys,os,json,collections

def read(p):
    try: return open(p,encoding='utf-8',errors='replace').read()
    except: return ''

def r_strings(root):
    """id(int) -> R.string name, from any R.java"""
    m={}
    for dp,_,fs in os.walk(root):
        for f in fs:
            if f!='R.java': continue
            t=read(os.path.join(dp,f))
            # find the 'string' inner class block
            i=t.find('class string')
            if i<0: continue
            for name,val in re.findall(r'int (\w+) = (0x[0-9a-fA-F]+|\d+);', t[i:]):
                try: m[int(val,0)]=name
                except: pass
    return m

def const_ids(root):
    """field -> int value, for obfuscated R aliases with many int fields"""
    m={}
    for dp,_,fs in os.walk(root):
        for f in fs:
            if not f.endswith('.java') or f=='R.java': continue
            t=read(os.path.join(dp,f))
            hits=re.findall(r'public static int (f\w+) = (\d+);', t)
            if len(hits)>100:
                for n,v in hits: m[n]=int(v)
    return m

def main(root, targets):
    rs=r_strings(root); cid=const_ids(root)
    rows=[]
    for rel in targets:
        t=read(os.path.join(root,rel))
        # block per status branch
        for m in re.finditer(r'getStatus\(\)\.equals\("([^"]+)"\)', t):
            chunk=t[m.end():m.end()+900]
            nxt=re.search(r'getStatus\(\)\.equals\("', chunk)
            if nxt: chunk=chunk[:nxt.start()]
            names=re.findall(r'R\.string\.(\w+)', chunk)
            for fld in re.findall(r'\b\w+\.(f\w+)\b', chunk):
                v=cid.get(fld)
                if v in rs: names.append(rs[v])
            rows.append({'status':m.group(1),'labels':names[:3],'file':rel})
    return rows

if __name__=='__main__':
    root=sys.argv[1]
    targets=[a for a in sys.argv[3:]]
    rows=main(root,targets)
    json.dump(rows,open(sys.argv[2],'w'),ensure_ascii=False,indent=1)
    seen=collections.defaultdict(set)
    for r in rows:
        for l in r['labels']: seen[r['status']].add(l)
    for k in sorted(seen, key=lambda x:(len(x),x)):
        print(f'  {k:>4} -> {", ".join(sorted(seen[k]))}')
