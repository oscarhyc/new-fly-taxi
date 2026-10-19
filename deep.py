import sys, zipfile, struct, re, json
sys.path.insert(0,'/home/oscar/rea-apk')
from dex import uleb, mutf8, load

def parse_full(d):
    ss,so = struct.unpack_from('<II', d, 56)
    ts,to = struct.unpack_from('<II', d, 64)
    ps,po = struct.unpack_from('<II', d, 72)   # proto
    fs,fo = struct.unpack_from('<II', d, 80)   # field_ids
    ms,mo = struct.unpack_from('<II', d, 88)   # method_ids
    cs,co = struct.unpack_from('<II', d, 96)   # class_defs
    strings=[]
    for i in range(ss):
        off=struct.unpack_from('<I',d,so+4*i)[0]
        n,p=uleb(d,off); e=d.index(b'\0',p)
        strings.append(mutf8(d[p:e]))
    types=[strings[struct.unpack_from('<I',d,to+4*i)[0]] for i in range(ts)]
    fields=[(types[struct.unpack_from('<H',d,fo+8*i)[0]], strings[struct.unpack_from('<I',d,fo+8*i+4)[0]])
            for i in range(fs)]
    methods=[(types[struct.unpack_from('<HH',d,mo+8*i)[0]], strings[struct.unpack_from('<I',d,mo+8*i+4)[0]])
             for i in range(ms)]
    out={}
    for i in range(cs):
        cidx,flags,sup = struct.unpack_from('<III', d, co+32*i)
        cdoff = struct.unpack_from('<I', d, co+32*i+24)[0]
        cname = types[cidx]
        entry={'flags':flags,'super':types[sup] if sup<len(types) else '','fields':[],'methods':[]}
        if cdoff:
            p=cdoff
            sf,p=uleb(d,p); inf,p=uleb(d,p); dm,p=uleb(d,p); vm,p=uleb(d,p)
            fidx=0
            for _ in range(sf+inf):
                diff,p=uleb(d,p); af,p=uleb(d,p); fidx+=diff
                if fidx<len(fields): entry['fields'].append(fields[fidx][1])
            midx=0
            for _ in range(dm+vm):
                diff,p=uleb(d,p); am,p=uleb(d,p); code,p=uleb(d,p); midx+=diff
                if midx<len(methods): entry['methods'].append(methods[midx][1])
        out[cname]=entry
    return out, set(strings)

# ---------------- ARSC ----------------
def read_pool(d,off):
    t,h,sz,count,stylecount,flags,so,sto = struct.unpack_from('<HHIIIIII',d,off)
    utf8 = bool(flags & 0x100)
    offs = struct.unpack_from('<%dI'%count,d,off+h)
    res=[]
    for o in offs:
        p=off+so+o
        if utf8:
            n=d[p]; p+=2 if n&0x80 else 1
            n=d[p]; p+=2 if n&0x80 else 1
            res.append(d[p:p+n].decode('utf-8','replace'))
        else:
            n=struct.unpack_from('<H',d,p)[0]; p+=2
            if n&0x8000:
                n=((n&0x7fff)<<16)|struct.unpack_from('<H',d,p)[0]; p+=2
            res.append(d[p:p+n*2].decode('utf-16-le','replace'))
    return res,sz

def arsc_strings(path):
    with zipfile.ZipFile(path) as z: d=z.read('resources.arsc')
    gpool,gsz=read_pool(d,12)
    pkg=12+gsz
    ptype,phdr,psize = struct.unpack_from('<HHI',d,pkg)
    if ptype!=0x0200: return {}
    typeStrOff = struct.unpack_from('<I',d,pkg+268)[0]
    keyStrOff  = struct.unpack_from('<I',d,pkg+276)[0]
    tnames,_=read_pool(d,pkg+typeStrOff)
    knames,_=read_pool(d,pkg+keyStrOff)
    out={}
    off=pkg+phdr
    while off < len(d):
        if off+8>len(d): break
        t,h,sz=struct.unpack_from('<HHI',d,off)
        if sz==0: break
        if t==0x0201:
            idv=d[off+8]; ecount,estart=struct.unpack_from('<II',d,off+12)
            cfg=d[off+20:off+20+ (h-20)]
            tname=tnames[idv-1] if 0<idv<=len(tnames) else str(idv)
            for e in range(ecount):
                eo=struct.unpack_from('<I',d,off+h+4*e)[0]
                if eo==0xFFFFFFFF: continue
                ep=off+estart+eo
                esz,eflags,key=struct.unpack_from('<HHI',d,ep)
                if eflags & 1: continue  # complex
                vsz,res0,dt,data=struct.unpack_from('<HBBI',d,ep+esz)
                kn=knames[key] if key<len(knames) else str(key)
                if dt==0x03 and data<len(gpool):
                    out.setdefault(tname+'/'+kn, gpool[data])
                elif dt==0x10:
                    out.setdefault(tname+'/'+kn, data)
        off+=sz
    return out

if __name__=='__main__':
    apk,pref,out=sys.argv[1],sys.argv[2],sys.argv[3]
    alld={}; strings=set()
    for n,d in sorted(load(apk).items()):
        c,s=parse_full(d); alld.update(c); strings|=s
    classdump={k:v for k,v in alld.items() if k.startswith(pref)}
    try: arsc=arsc_strings(apk)
    except Exception as e: arsc={}; print('arsc error',e)
    json.dump({'classes':classdump,'strings_res':arsc,'all_strings':sorted(strings)},
              open(out,'w'),ensure_ascii=False)
    print(out,'classes',len(classdump),'res strings',len(arsc))
