import sys, re, zipfile, json, collections, struct
sys.path.insert(0,'/home/oscar/rea-apk')
from dex import load, parse
from axml import parse as axml_parse

KW = {
 'net':      re.compile(r'(socket|mqtt|pubnub|websocket|wss://|firebase-messaging|fcm|pusher|signalr|grpc|retrofit|okhttp)',re.I),
 'auth':     re.compile(r'(otp|sms_code|verify|login|logout|signup|register|session|refresh_token|access_token|bearer|accountkit|jwt)',re.I),
 'booking':  re.compile(r'(booking|book_order|dispatch|order|hail|ride|trip|job|driver|passenger|customer|queue|match)',re.I),
 'fare':     re.compile(r'(fare|price|estimate|quote|meter|toll|surcharge|discount|coupon|payment|wallet|charge)',re.I),
 'geo':      re.compile(r'(latitude|longitude|geofence|polyline|bearing|heading|hk1980|wgs84|proj4j|distance_matrix|nearby)',re.I),
}
PATH = re.compile(r'^/[A-Za-z0-9_\-/{}.$:]{2,80}$')
APIISH = re.compile(r'^[a-z0-9_\-]*(api|v\d|endpoint|service|flytaxi)[a-z0-9_\-]*$',re.I)

def bits(path):
    dexs=load(path)
    strings=[];classes=[];methods=[]
    for n,d in sorted(dexs.items()):
        s,t,m,c=parse(d); strings+=s; classes+=c; methods+=m
    return strings,classes,methods,sorted(dexs)

def manifest(path):
    tags=axml_parse(path)
    out=collections.defaultdict(list); cur=None; pending=None
    for name,attrs in tags:
        if name=='application': continue
        if name in ('activity','service','receiver','provider','activity-alias'):
            cur={'tag':name,'name':attrs.get('name'),'exported':attrs.get('exported'),
                 'permission':attrs.get('permission'),'filters':[],'authorities':attrs.get('authorities'),
                 'process':attrs.get('process'),'foreground':attrs.get('foregroundServiceType')}
            out[name].append(cur); pending=cur
        elif name=='intent-filter' and pending is not None:
            pending['filters'].append({'actions':[],'categories':[],'data':[]})
        elif name=='action' and pending and pending['filters']:
            pending['filters'][-1]['actions'].append(attrs.get('name'))
        elif name=='category' and pending and pending['filters']:
            pending['filters'][-1]['categories'].append(attrs.get('name'))
        elif name=='data' and pending and pending['filters']:
            pending['filters'][-1]['data'].append({k:v for k,v in attrs.items() if k in('scheme','host','path','pathPrefix','pathPattern','mimeType')})
    return out

def report(apk,appprefix):
    strings,classes,methods,dexnames=bits(apk)
    st=set(strings)
    comps=manifest(apk)
    with zipfile.ZipFile(apk) as z:
        assets=[n for n in z.namelist() if n.startswith('assets/')][:400]
        raw=[n for n in z.namelist() if re.match(r'res/raw',n)]
    appcls=sorted({c for c,_ in classes if c.startswith(appprefix)})
    paths=sorted({s for s in st if PATH.match(s) and sum(ch.isalpha() for ch in s)>2})
    apiish=sorted({s for s in st if APIISH.match(s) and len(s)<40})
    buckets={k:sorted({s for s in st if rx.search(s) and len(s)<120}) for k,rx in KW.items()}
    r={'apk':apk,'dex':dexnames,'classes':len(classes),'strings':len(strings),
       'app_classes':appcls,'components':comps,'paths':paths,'apiish':apiish,
       'buckets':{k:v[:400] for k,v in buckets.items()},'assets':assets,'raw':raw[:50],
       'strings_all':sorted(st)}
    return r

if __name__=='__main__':
    apk=sys.argv[1]; pref=sys.argv[2]; out=sys.argv[3]
    r=report(apk,pref)
    json.dump(r,open(out,'w'),ensure_ascii=False,indent=1)
    print(out,'written; app classes:',len(r['app_classes']),'paths:',len(r['paths']))
