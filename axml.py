import sys, zipfile, struct

RES_STRING_POOL=0x0001; RES_XML=0x0003; RES_XML_STARTELEMENT=0x0102

def read_pool(d,off):
    t,h,sz,count,stylecount,flags,so,sto = struct.unpack_from('<HHIIIIII',d,off)
    utf8 = bool(flags & 0x100)
    offs = struct.unpack_from('<%dI'%count,d,off+h)
    strings=[]
    for o in offs:
        p=off+so+o
        if utf8:
            # two length fields (utf16 len, then byte len)
            n=d[p]; p+=2 if n&0x80 else 1
            n=d[p]; p+=2 if n&0x80 else 1
            raw=d[p:p+n]; s=raw.decode('utf-8','replace')
        else:
            n=struct.unpack_from('<H',d,p)[0]; p+=2
            if n&0x8000:
                n=((n&0x7fff)<<16)|struct.unpack_from('<H',d,p)[0]; p+=2
            s=d[p:p+n*2].decode('utf-16-le','replace')
        strings.append(s)
    return strings,sz

def parse(path):
    with zipfile.ZipFile(path) as z:
        d=z.read('AndroidManifest.xml')
    if len(d)<8 or struct.unpack_from('<H',d,0)[0]!=RES_XML: return None
    off=8; strings=[]; tags=[]
    while off < len(d):
        t,h,sz = struct.unpack_from('<HHI',d,off)
        if t==RES_STRING_POOL: strings,_=read_pool(d,off)
        elif t==RES_XML_STARTELEMENT:
            ns,name=struct.unpack_from('<iI',d,off+h)
            attrStart=struct.unpack_from('<H',d,off+h+8)[0]
            attrSize,attrCount=struct.unpack_from('<HH',d,off+h+10)
            attrs={}
            base=off+h+attrStart
            for i in range(attrCount):
                p=base+i*attrSize
                ans,an,rawv,tsz,res0,dtype,data = struct.unpack_from('<iIIHBBI',d,p)
                nm = strings[an] if an < len(strings) else str(an)
                if dtype==0x03: val=strings[data] if data<len(strings) else str(data)
                elif dtype==0x12: val=bool(data)
                elif dtype in (0x10,0x11): val=data
                elif dtype==0x01: val='@0x%08x'%data
                else: val=data
                attrs[nm]=val
            tags.append((strings[name] if name<len(strings) else name,attrs))
        off += sz if sz else 4
    return tags

if __name__=='__main__':
    for p in sys.argv[1:]:
        tags=parse(p)
        print('===',p)
        m=[a for n,a in tags if n=='manifest'][0]
        print(' package     :',m.get('package'))
        print(' versionCode :',m.get('versionCode'))
        print(' versionName :',m.get('versionName'))
        print(' compileSdk  :',m.get('compileSdkVersion'))
        perm=[a.get('name') for n,a in tags if n=='uses-permission']
        print(' permissions : %d'%len(perm))
        for x in perm[:60]: print('   -',x)
        app=[a for n,a in tags if n=='application']
        if app: print(' app label ref:', app[0].get('label'), ' debuggable:', app[0].get('debuggable'), ' usesCleartext:', app[0].get('usesCleartextTraffic'))
