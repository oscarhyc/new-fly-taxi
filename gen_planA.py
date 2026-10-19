import json,collections

COMMON={'did','v','lang','app','app_id','access_token','uid','version','app_date'}
drv=json.load(open('/home/oscar/rea-apk/pa2_drv.json'))
pax=json.load(open('/home/oscar/rea-apk/pa2_pax.json'))
st=json.load(open('/home/oscar/rea-apk/st_pax.json'))

def agg(rows):
    a=collections.defaultdict(lambda:{'d':set(),'l':set(),'f':set()})
    for r in rows:
        for p in r['paths']:
            a[p]['d']|=set(r['direct']); a[p]['l']|=set(r['listener_dtos']); a[p]['f']|=set(r['file_dtos'])
    return a

def table(a):
    L=['| endpoint | response DTO (confidence) |','|---|---|']
    for p in sorted(a):
        d=a[p]
        if d['d']: pick,how=sorted(d['d']),'direct'
        elif d['l']: pick,how=sorted(d['l']),'listener'
        elif d['f']: pick,how=sorted(d['f']),'file-level'
        else: pick,how=[],'unknown'
        cell=', '.join('`%s`'%x for x in pick) if pick else '_unknown_'
        L.append(f'| `{p}` | {cell} ({how}) |')
    return L

statuses=collections.defaultdict(set)
for r in st:
    for l in r['labels']: statuses[r['status']].add(l)

SEM={
 '-1':'no order / empty','11':'NEW — order created, broadcasting to drivers',
 '12':'CANCELLED (generic)','13':'EXPIRED (no driver accepted in time)','14':'ACCEPTED — driver confirmed',
 '21':'EN ROUTE TO PICKUP — taxi is coming','22':'accepted, driver late','24':'accepted, driver lost signal',
 '25':'accepted, user cancelled','26':'accepted, driver cancelled',
 '31':'TRIP STARTED — on the way to dropoff','32':'arrive-pickup stage, driver late',
 '34':'arrive-pickup stage, driver no signal','35':'arrive-pickup stage, user cancelled',
 '36':'arrive-pickup stage, driver cancelled','37':'arrive-pickup stage, user no-show',
 '41':'ARRIVED DROPOFF / ride in progress','42':'start-ride stage, driver late',
 '44':'start-ride stage, driver lost signal','45':'start-ride stage, user cancelled',
 '51':'COMPLETED','52':'arrive-dropoff stage, driver late',
 '54':'arrive-dropoff stage, driver no signal','59':'COMPLETED manually (user helped complete)',
}

out=['# Response map, state machine and error codes (Plan A)','',
'Recovered by call-site mining over the jadx output. Confidence is stated per row:',
'`direct` = `fromJson(..)` in the same method as the request; `listener` = inside the registered listener class;',
'`file-level` = only the containing file matched; `unknown` = not yet located.','',
'## Envelope','',
'```json',
'{ "status_code": "200", "message": "...", "data": { ... } }',
'```','',
'- Success is `status_code == "200"` (string compare throughout the code).',
'- `data` is either an object or an array depending on the endpoint; arrays are sometimes nested,',
'  e.g. nearby drivers is `data.driver_list[]`.',
'- Observed non-200 codes: pax `105` (address/geocode failure), `801` (driver payment auth empty),',
'  driver `2001`–`2005` (session/GPS authorization failures — the request is silently dropped and the',
'  driver is force-logged-out path), plus the generic error dialog that prints `status_code` + `message`.','',
'## Passenger (`com.flytaxi.hktaxi`) — endpoint → response model','']+table(agg(pax))+['',
'## Driver (`com.flytaxi`) — endpoint → response model','']+table(agg(drv))+['',
'## `book.status` — the order state machine','',
'Derived from both apps independently (identical result). The server is the only writer of `status`.','',
'| status | meaning | UI label key |','|---|---|---|']
for k in sorted(statuses,key=lambda x:int(x)):
    out.append(f"| `{k}` | {SEM.get(k,'_unmapped_')} | {', '.join('`'+x+'`' for x in sorted(statuses[k]))} |")
out+=['',
'### Structure of the encoding',
'',
'`status` is two digits: **tens = lifecycle stage**, **ones = terminal/exception reason**.',
'',
'```',
'stage 1x  created / dispatch      1=new 2=cancel 3=expire 4=accepted',
'stage 2x  accepted                1=en route to pickup 2=driver late 4=no signal 5=user cancel 6=driver cancel',
'stage 3x  arrived pickup          1=started ride 2=late 4=no signal 5=user cancel 6=driver cancel 7=user no-show',
'stage 4x  to dropoff              1=normal 2=late 4=no signal 5=user cancel',
'stage 5x  completed               1=normal 2=late 4=no-signal path 9=manual completion',
'```',
'',
'So `status` transitions observed in UI logic: `11 → 14 → 21 → 31 → 41 → 51`,',
'with `1x` and `2x/3x/4x` `x2/x4/x5/x6/x7` branches as terminal failures.',
'`59` = user-assisted manual completion (the app shows a countdown then a "Complete ride" button).','',
'## Fare / estimate models','',
'`/v6/fleet/fare` request body is `FleetFareBuildItem`:',
'```',
'access_token, bid, date_pickup, dropoff1_address, dropoff1_latitude, dropoff1_longitude,',
'fare_id, lang, opt, pickup_address, pickup_latitude, pickup_longitude, remark,',
'route: FleetFareRouteItem, selected: FleetFareSelectedItem, service_list, uid, v',
'```',
'`/v6/api/fare2` is the taxi-meter path (`/api/fare2`); the fleet path is the aggregator (Splyt/FastCab) path.','',
'## Remaining gaps','',
'- Response DTO for the dispatcher list on the driver side (`/v6/book/list`) is not yet pinned; the app',
'  parses it inside `JwtZipBookListAsyncTask` and `p429x3/*`, candidates `AllOrderItem` / `OrderItem`.',
'- `non-200` code meanings beyond the ones listed need the `strings.xml` cross-reference (dialog copy).',
'- No HTTP-level evidence of these shapes yet — that is what runtime capture (Plan B) would confirm.','']
open('/home/oscar/rea-apk/docs/response-map.md','w').write('\n'.join(out))
print('ok',len('\n'.join(out)))
