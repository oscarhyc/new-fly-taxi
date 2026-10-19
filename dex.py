import sys, zipfile, struct, re, collections

def uleb(d,p):
    r=0;s=0
    while True:
        b=d[p];p+=1
        r|=(b&0x7f)<<s
        if not b&0x80: return r,p
        s+=7

def mutf8(b):
    # decode MUTF-8 (CESU-8-ish): handles surrogate pairs as separate 3-byte seqs
    out=[];i=0;n=len(b)
    while i<n:
        c=b[i]
        if c<0x80: out.append(chr(c));i+=1
        elif c&0xe0==0xc0 and i+1<n:
            out.append(chr(((c&0x1f)<<6)|(b[i+1]&0x3f)));i+=2
        elif c&0xf0==0xe0 and i+2<n:
            out.append(chr(((c&0x0f)<<12)|((b[i+1]&0x3f)<<6)|(b[i+2]&0x3f)));i+=3
        else: out.append('\ufffd');i+=1
    return ''.join(out)

def load(path):
    with zipfile.ZipFile(path) as z:
        names=[n for n in z.namelist() if re.fullmatch(r'classes\d*\.dex',n)]
        data={n:z.read(n) for n in names}
    return data

def parse(d):
    ss,so = struct.unpack_from('<II', d, 56)
    ts,to = struct.unpack_from('<II', d, 64)
    ms,mo = struct.unpack_from('<II', d, 88)
    cs,co = struct.unpack_from('<II', d, 96)
    strings=[]
    for i in range(ss):
        off=struct.unpack_from('<I',d,so+4*i)[0]
        n,p=uleb(d,off)
        e=d.index(b'\0',p)
        strings.append(mutf8(d[p:e]))
    types=[]
    for i in range(ts):
        types.append(strings[struct.unpack_from('<I',d,to+4*i)[0]])
    methods=[]
    for i in range(ms):
        cidx,_p = struct.unpack_from('<HH', d, mo+8*i)
        nidx = struct.unpack_from('<I', d, mo+8*i+4)[0]
        methods.append((types[cidx] if cidx<len(types) else '?', strings[nidx]))
    classes=[]
    for i in range(cs):
        cidx,flags = struct.unpack_from('<II', d, co+32*i)
        classes.append((types[cidx] if cidx<len(types) else '?', flags))
    return strings, types, methods, classes

if __name__=='__main__':
    for apk in sys.argv[1:]:
        print('#'*70); print('APK:',apk)
        dexs=load(apk)
        allstr=[];allcls=[];allm=[]
        for n,d in sorted(dexs.items()):
            s,t,m,c=parse(d)
            print(f'  {n}: {len(s)} strings, {len(c)} classes')
            allstr+=s; allcls+=c; allm+=m
        st=set(allstr)
        urls=sorted({x for x in allstr if re.match(r'^https?://',x) and len(x)<200})
        print(f'\n  distinct http(s) URLs: {len(urls)}')
        for u in urls[:80]: print('   ',u)
        hosts=collections.Counter()
        for u in urls:
            m=re.match(r'https?://([^/:]+)',u)
            if m: hosts[m.group(1)]+=1
        print('\n  top hosts:', hosts.most_common(15))
        pk=collections.Counter()
        for c,_ in allcls:
            parts=c.split('/')
            pk['/'.join(parts[:3])]+=1
        print('\n  top class packages:')
        for k,v in pk.most_common(35): print(f'    {v:5d} {k}')
