import os,re,sys,json

def read(p):
    try: return open(p,encoding='utf-8',errors='replace').read()
    except: return ''

def consts(root):
    m={}
    for dp,_,fs in os.walk(root):
        for f in fs:
            if f.endswith('.java'):
                for k,v in re.findall(r'public static String (\w+)\s*=\s*"([^"]*)"', read(os.path.join(dp,f))):
                    if v.startswith('/'): m[k]=v
    return m

def scan(root, cst):
    rows=[]
    for dp,_,fs in os.walk(root):
        for f in fs:
            if not f.endswith('.java'): continue
            p=os.path.join(dp,f); t=read(p)
            if not re.search(r'JSONObject|RequestBody|JSONObject\.put', t): continue
            paths=[]
            for k in set(re.findall(r'\b(?:Abstract\w+|F2\.\w+)\.(\w+)\b', t)):
                if k in cst: paths.append(cst[k])
            for lit in set(re.findall(r'"(/v\d/[A-Za-z0-9_/]*)"', t)): paths.append(lit)
            if not paths: continue
            keys=re.findall(r'\.put\(\s*"([^"]+)"', t)
            if not keys: continue
            resp=sorted(set(re.findall(r'([A-Z][A-Za-z0-9_]*Item)\.class', t)))
            http=set(re.findall(r'new C\d+[a-z]?\(\s*(\d)', t))
            rows.append({'file':p.replace(root+'/',''),'paths':sorted(set(paths)),
                         'http':sorted(http),'params':list(dict.fromkeys(keys)),'resp':resp})
    return rows

if __name__=='__main__':
    root,out=sys.argv[1],sys.argv[2]
    rows=scan(root,consts(root))
    json.dump(rows,open(out,'w'),ensure_ascii=False,indent=1)
    print(out,'files with contract:',len(rows),'distinct paths:',len({x for r in rows for x in r['paths']}))
