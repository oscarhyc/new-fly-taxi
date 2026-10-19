import json,re,os,collections

COMMON={'did','v','lang','app','app_id','access_token','uid','version','app_date'}
def load(f):
    rows=json.load(open(f))
    by=collections.defaultdict(lambda:{'params':[],'files':set(),'http':set()})
    for x in rows:
        for p in x['paths']:
            b=by[p]
            for k in x['params']:
                if k not in b['params']: b['params'].append(k)
            b['files'].add(x['file']); b['http']|=set(x['http'])
    return by

def fields(java):
    t=open(java,encoding='utf-8',errors='replace').read()
    out=[]
    for m in re.finditer(r'\n\s+private\s+([A-Za-z0-9_<>,\[\]\. ]+?)\s+(\w+);',t):
        ty,nm=m.group(1).strip(),m.group(2)
        if nm.startswith('f') and re.fullmatch(r'f\d+\w*',nm): continue
        out.append(f'{nm}: {ty}')
    return out

def section(title,by,root,models):
    L=[f'### {title}','',
       '| endpoint | verb | params (excl. common) | source |','|---|---|---|---|']
    for p in sorted(by):
        b=by[p]
        own=[x for x in b['params'] if x not in COMMON]
        verb='POST' if '1' in b['http'] else ('GET' if '0' in b['http'] else '?')
        src=', '.join(sorted(b['files']))[:70]
        L.append(f"| `{p}` | {verb} | {', '.join('`'+x+'`' for x in own) or '—'} | `{src}` |")
    L+=['','<details><summary>DTO field lists</summary>','']
    for cls in models:
        f=os.path.join(root,cls)
        if not os.path.exists(f): continue
        fl=fields(f)
        L.append(f'**{os.path.basename(cls)[:-5]}** ({len(fl)})')
        L.append('```')
        L+= [ '  '+x for x in fl ]
        L.append('```')
    L.append('</details>')
    return L

drv=load('/home/oscar/rea-apk/c_drv4.json')
pax=load('/home/oscar/rea-apk/c_pax4.json')
out=[]
out+=['# FlyTaxi `/v6` API contract (recovered from shipped APKs)','',
'Source: jadx 1.5.6 decompilation of `com.flytaxi` v356 (driver) and `com.flytaxi.hktaxi` v191 (passenger).',
'Every row below is a **static observation** from the binaries; response shapes marked as DTO fields are observed class declarations.',
'','## Conventions','',
'```',
'base URL   https://api.flytaxiapp.com          (also https://api2.flytaxiapp.com, https://member.flytaxiapp.com)',
'config     city.domain_core = api-hk-release-core-01.flytaxiapp.com   (runtime override)',
'version    path prefix /v6/',
'method     every request is POST (DPoP htm is hard-coded "POST")',
'pax body   two wrappers: C0829c = JSON body + Content-Type: application/json (no auth header);',
'           C0828b = Volley getParams() -> form-urlencoded; token travels in body as access_token',
'driver body JSON via C4800d; headers below',
'headers    (driver) Content-Type: application/json; charset=utf-8',
'           (driver) Authorization: Bearer <access_token>',
'           (driver) DPoP: <ES256 JWT>        # see Auth section',
'           (pax)    no Authorization header; access_token is a body param, no DPoP',
'app ids    app_id/app = 31 (passenger), 32 (driver)',
'user_type  2 (customer)',
'```','',
'## Auth / trust','',
'1. **Bearer token** — `access_token` also travels *inside* the JSON body on many endpoints.',
'2. **DPoP (RFC 9449)** — `p180W3/C1825l` builds an ES256 JWT with header `{alg:ES256, typ:dpop+jwt, jwk:{kty:EC,crv:P-256,use:sig,x,y}}`',
'   and claims `{iat, jti:<uuid>, htm:"POST", htu:<full URL>, exp: iat+1day}`; signature is raw r||s, base64url.',
'   The EC key pair is held in `C6189d` and persisted; header `DPoP` is added on every request (`p247d3/C4800d`).',
'3. **Play Integrity gate** — `/v6/device/integrity_challenge_test` → `{nonce, token, login_integrity_id, platform, error_message}`',
'   → `/v6/integrity/verify` → `/v6/device/jwt_authorization` returns `server_jwt_token`. Params: `nonce, token, login_integrity_id, platform, error_message, did`.',
'4. Client also sends `app_date` (formatted device time) on some calls, `crc`, `imei`, `device_name`, `environment`, `db_config_version` at login.','',
'Common body params on nearly every call: `did` (device id), `v` (app version int, 356 / 191), `lang`, `app`, `app_id`, `access_token`.','',
'## Passenger (`com.flytaxi.hktaxi`, app_id 31)','']
out+=section('Endpoints',pax,'/home/oscar/rea-apk/out/pax/sources',[
 'com/hktaxi/hktaxi/model/CustomerItem.java',
 'com/hktaxi/hktaxi/model/OrderItem.java',
 'com/hktaxi/hktaxi/model/EstimateFareItem.java',
 'com/hktaxi/hktaxi/model/FareItem.java',
 'com/hktaxi/hktaxi/model/LoginTokenItem.java',
 'com/hktaxi/hktaxi/model/MeetUpItem.java',
])
out+=['','## Driver (`com.flytaxi`, app_id 32)','']
out+=section('Endpoints',drv,'/home/oscar/rea-apk/out/drv/sources',[
 'com/flytaxi/model/LoginItem.java',
 'com/flytaxi/model/BatchInfoItem.java',
 'com/flytaxi/model/AllOrderItem.java',
 'com/flytaxi/model/OrderItem.java',
 'com/flytaxi/model/AcceptOrderItem.java',
 'com/flytaxi/model/DriverGpsItem.java',
 'com/flytaxi/model/OnlineStatusItem.java',
 'com/flytaxi/model/TimetableDetailItem.java',
])
out+=['','## Gaps (explicit unknowns)','',
'- Per-endpoint **response DTO** is not yet linked 1:1: the request classes return raw `JSONObject`; parsing happens in the calling Activity/Fragment. Call-site mining is the next step.',
'- Error envelope / status codes not yet extracted (`ResponseStatus` model exists in both apps).',
'- Whether the server validates DPoP `htu` strictly is unknown (static analysis cannot answer it).',
'- Rate limits, pagination and `book.status` enum values need call-site extraction or runtime capture.','']
os.makedirs('/home/oscar/rea-apk/docs',exist_ok=True)
open('/home/oscar/rea-apk/docs/api-contract.md','w').write('\n'.join(out)+'\n')
print('written', sum(len(x) for x in out),'chars')
