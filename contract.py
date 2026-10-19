import os,re,sys,json,collections

def read(p):
    try: return open(p,encoding='utf-8',errors='replace').read()
    except: return ''

FIELD = re.compile(r'public static String (\w+)\s*=\s*"([^"]*)"')
def consts(root):
    m={}
    for dp,_,fs in os.walk(root):
        for f in fs:
            if not f.endswith('.java'): continue
            t=read(os.path.join(dp,f))
            for k,v in FIELD.findall(t):
                if v.startswith('/'): m[k]=(v,os.path.join(dp,f))
    return m

def methods(t):
    # split into method bodies by brace matching from "{" following a signature
    out=[]
    for mo in re.finditer(r'\n    (?:public|private|protected|static|final|\s)+[^\n;=]*?\([^;{]*\)\s*\{', t):
        start=mo.end(); i=start; depth=1
        while i < len(t) and depth:
            c=t[i]
            if c=='{': depth+=1
            elif c=='}': depth-=1
            i+=1
        out.append((mo.group(0).strip(), t[start:i-1]))
    return out

def analyze(root, wrapper):
    C=consts(root)
    rows=[]
    for dp,_,fs in os.walk(root):
        for f in fs:
            if not f.endswith('.java'): continue
            p=os.path.join(dp,f); t=read(p)
            if wrapper not in t: continue
            for sig,body in methods(t):
                if wrapper not in body: continue
                paths=[]
                for k in re.findall(r'AbstractC\w+\.(\w+)', body):
                    if k in C: paths.append((k,C[k][0]))
                if not paths:
                    for lit in re.findall(r'"(/v\d/[^"]*)"', body): paths.append(('-',lit))
                if not paths: continue
                keys=re.findall(r'\.put\(\s*"([^"]+)"', body)
                code=re.search(re.escape(wrapper)+r'\s*(\d+)', body)
                resp=sorted(set(re.findall(r'([A-Z]\w*Item)\.class', body)))
                gson=sorted(set(re.findall(r'fromJson\([^;]*?([A-Z]\w*)\.class', body)))
                rows.append({'file':p.replace(root+'/',''),'method':re.sub(r'\s+',' ',sig)[:110],
                             'paths':paths,'http':code.group(1) if code else '?','params':keys,
                             'resp':resp,'fromJson':gson})
    return rows

if __name__=='__main__':
    root,wrapper,out=sys.argv[1],sys.argv[2],sys.argv[3]
    rows=analyze(root,wrapper)
    json.dump(rows,open(out,'w'),ensure_ascii=False,indent=1)
    print(out,'rows',len(rows))
