# FlyTaxi (飛的) — Teardown & Rebuild Plan

Targets (both current Play builds, pulled from APKPure CDN, verified SHA-256):

| App | Package | Ver | File |
|---|---|---|---|
| Passenger / 叫車 | `com.flytaxi.hktaxi` | 191 | `apks/com.flytaxi.hktaxi.apk` (17.0 MB) |
| Driver / 司機版 | `com.flytaxi` | 356 | `apks/com.flytaxi.apk` (24.4 MB) |

Working dir: `~/rea-apk/` — `recon.py`, `dex.py`, `axml.py` (dependency-free DEX + binary-XML
readers), `r_pax.json`, `r_drv.json` (extracted string/class/component inventories),
`x/assets/` (extracted config DBs).

> Legal/scope note: this is analysis of publicly distributed binaries for interoperability and
> learning. Rebuilding means writing your own client/server — not shipping their assets, brand,
> terms text or decompiled code. Their backend is private; studying it does not grant usage rights.

---

## 1. What the teardown has already established

Evidence type: **observations** from DEX string/type tables, AndroidManifest binary XML, and the
config SQLite shipped in `assets/`. Provenance is reproducible with `python3 recon.py <apk> <prefix> <out>`.

### 1.1 Architecture in one line

Config-driven thin client over a server-authoritative REST backend, plus FCM push and a GPS
heartbeat loop. No WebSocket/MQTT in either app; dispatch is **driver-polling + push notification**.

### 1.2 Backend

- Deeplink/API host: `https://api.flytaxiapp.com` (also `lostfound.flytaxiapp.com`)
- **Real API host is dynamic**, resolved from the seeded DB: `api-hk-release-core-01.flytaxiapp.com`
  (`city.domain_core`, `city.domain_temp`), asset bucket `cargrid-hk-release-butcket.s3.ap-east-1.amazonaws.com`
  (`city.domain_file`), plus `api_force_staging` config flag → staging/prod switch by config, not code.
- API version prefix `/v6/`, passenger legacy paths also under `/api/` and bare `/book/`, `/user/`,
  `/gps/`, `/pay/`, `/fleet/`.
- ~90 distinct endpoints recovered (driver set is cleanest, with full `/v6/` prefix):

```
auth/session   /v6/driver/login  /v6/driver/reg  /v6/member/create_login_token
               /v6/device/jwt_authorization  /v6/device/integrity_challenge_test  /v6/integrity/verify
               /v6/driver/logout  /user/reg  /user/create_login_token
driver         /v6/driver/get_driver_auth  /v6/driver/get_hk_driver_tel_status  /v6/driver/update_hk_driver_tel
               /v6/driver/reg_doc_detail  /v6/driver/reg_doc_verify
               /v6/driver/add_driver_car_model  /v6/driver/update_driver_car_model  /v6/driver/select_driver_car_model2
               /v6/driver/delete_driver_car_model  /v6/driver/update_driver_option  /v6/driver/batch_info2
               /v6/driver/uber_link_account  /v6/driver/add_driver_complain
dispatch/book  /v6/book/list  /v6/book/accept  /v6/book/lock  /v6/book/driver_book  /v6/book/driver_cancel
               /v6/book/create  /v6/book/recreate  /v6/book/user_book  /v6/book/user_book_detail
               /v6/book/user_cancel  /v6/book/cancel  /v6/book/book_start_ride  /v6/book/book_complete_ride
               /v6/book/driver_arrived_pickup  /v6/book/driver_arrived_dropoff1  /v6/book/user_no_show
               /v6/book/get_book_direction  /v6/book/update_book_direction  /v6/book/get_meetup_data
               /v6/book/score_driver  /v6/book/get_book_autocomplete  /v6/book/get_book_address_nearby
               /v6/book/add_book_charge  /v6/fleet/book_create  /v6/fleet/book_accept
               /v6/fleet/get_car_driver_list  /v6/fleet/select_car_driver  /v6/fleet/fare
location       /v6/gps/update_driver  /v6/gps/jwt_update_driver  /gps/get_user_nearby_drivers  /gps/update_user
fare/config    /api/fare2  /api/get_config_sqlite  /v6/api/get_config_sqlite  /cloudbridge_settings
payment        /v6/octopus/request_driver_linkage  /v6/octopus/update_driver_linkage  /v6/octopus/remove_driver_auth
               /v6/octopus/request_card_payment  /v6/payme/{add,edit,remove}_driver_auth
               /v6/wechat/add_driver_auth  /pay/create_tran_netopia  /pay/create_tran_netopia_verify
               /pay/get_tran  /pay/get_user_wallet
user/misc      /user/add_favourite_address  /user/remove_favourite_address  /user/remove_historical_address
               /user/add_user_complain  /user/add_user_referral_driver  /user/get_user_referral_driver
               /user/rider_refer_{eligible,submit}  /user/update_user_city_by_cid  /user/update_firebase_user_tel
               /v6/user/update_user_complain_review_{release,suspend}  /v6/user/get_user_complain_review_lock
               /v6/lostitem?token=  /cust/update_user_option
```

### 1.3 Auth & anti-abuse (this is the hard part of any rebuild)

- `Authorization: Bearer <token>` + persisted credentials `login_key`, `server_jwt_token`,
  `login_integrity_id` (all present as getters/setters in the driver app).
- **Play Integrity** flow with a server-issued challenge: `integrity_challenge_test` → verdict →
  `/v6/integrity/verify` → JWT. `IntegrityFailActivity` in both apps. This gates login on the
  client being a genuine, unmodified install.
- Root/emulator detection strings (`/system/xbin/su`, `/system/app/Superuser.apk`), driver
  `RootDeviceActivity`, driver `AccessibilityServiceActivity` (checks accessibility service misuse,
  i.e. auto-accept bots), `TimeChangedReceiver` (clock tampering), screen-saver video + wake locks.
- Consequence for teardown: **runtime probing against prod will be blocked** unless you pass
  integrity, so plan for (a) static-only contract recovery, or (b) a test device with a Google
  account that legitimately passes Integrity, or (c) build your own server and test your client.

### 1.4 Business rules recovered from the seeded config DB (`assets/user_config_sqlite.sqlite`)

`config_key_value` has 134 rows across 16 `config_type`s — this is effectively their tunables file:

```
gps_update_normal_sec = 600            idle heartbeat (10 min)
gps_update_accepted_book_sec = 20/60   heartbeat while on an order
gps_update_accepted_book_before_x_minute / after_x_minute = 15 / 75
book_accept_driver_arrive_pickup_max_radius = 800-1000 m
book_accept_driver_arrive_dropoff1_max_radius = 1800-2000 m
pickup_nearby_drivers_radius = 1500 m
take_order: click_threshold_sec=60 count=20 ; click_threshold2_sec=300 count=60   (anti-spam accept)
take_b_order: takeb_countdown_sec = 5/20                                          (accept countdown)
acc_order_cooldown_sec = 300/480/600                                              (post-order cooldown)
cancel_fee: book_accept_cancel_fee = 6 (HKD)
user: red_taxi_min_company_price=80, green_taxi_min_company_price=70
driver_general: warning_driver_lose_signal_mins=3/15, error_driver_lose_signal_mins=5/20,
                warning_driver_arrive_pickup_mins=15, batch_info_reload_sec=300
wallet: wallet_get_tran_reload_sec=5 timeout=30 ; driver_wallet_reload_sec=10
```

Taxonomy (from same DB): cities (HK + a Romanian city — the app is a white-label of **CarGrid/FastCab**),
`car_group` = taxi | car, `car_type` = red_taxi / green_taxi / blue_taxi / "FastCab Standard",
48 `car_option` rows, `pay_mode` bitfields, `hotline`, `driver_doc_type`, `driver_suspend_type`,
`user_complain_type`, `language` + `language_key_value` (i18n served from server).

### 1.5 Mapping / routing stack

- Google Maps + Directions (map, autocomplete, `res/raw/google_map_style.json`)
- Mapbox Directions v5 (`api.mapbox.com/directions/v5/mapbox/driving/`)
- HK Government TDAS: `https://tdas-api.hkemobility.gov.hk/tdas/api/route` + bundled
  `assets/CENTERLINE.json`, `assets/INTERSECTION.json` and **proj4j** (HK1980 ⇄ WGS84 grid conversion)
  → they compute taxi-legal routes, not just shortest path
- Device positioning via cell towers (`CellTowersItem`) as a GPS-loss fallback

### 1.6 Third-party surface (informs what NOT to rebuild from scratch)

Firebase (Messaging/Analytics/Crashlytics/RemoteConfig), Facebook AccountKit (dead SDK, still shipped),
Uber SDK + `com.flytaxi.uberauth` OAuth redirect + `uber_link_account` (ride aggregation),
**Splyt** model family (`SplytOrderItem`, `SplytEstimateItem`, `SplytechProvidersItem`…) = mobility
aggregator integration, Volley + Gson (passenger), OkHttp/Moshi/Kotlin (driver), Glide, Netopia
(card payments), Octopus / PayMe / AlipayHK / WeChat Pay HK.

### 1.7 Screens already known (manifest + class names)

Passenger: Splash → PhoneVerify → Main(map, `fleet`/`history`/`orderDetail` listener fragments) →
PinLocation → OrderDetail (+meetup) → Wallet (`NetopiaPaymentActivity`, `RecordTransactionActivity`) →
RiderRefer → AppUpdate/Terms/Hotline → UberPopup.
Driver: Splash → Login → SignUp → PhoneVerify → DriverLicense → DriverCarModel → FleetCarService →
Rules → PermissionSetting → RootDevice → IntegrityFail → Main(+MainGpsService) → Calculator →
Payment(Octopus/PayMe/Alipay/WeChat) → Complain/CustomerService/TakePhoto → WebView → Sunset.

### 1.8 Client-side state stores (no server round-trip needed)

`History.db` (past addresses), `Schedule.db` (scheduled/advance bookings: `bid, broadcast_id,
pickup_time, start_time, end_time`), driver `Order.db`, `LoginIntegrity.db`, `Contact.db`, `Log.db`,
`schedule3.db`, `driver_config_sqlite.sqlite`, `sso_config.json`.

---

## 2. Operation flow to verify (working hypothesis)

Server-authoritative order state machine; clients are dumb observers of a `book` record.

```
PASSENGER                              SERVER                       DRIVER
splash → config_version check ───────► /api/get_config_sqlite
phone verify (SMS OTP) ──────────────► /user/reg | /v6/driver/reg
login → bearer + jwt ────────────────► /user/create_login_token
                                              ◄──── /v6/book/list (poll, e.g. 5-10 s) + FCM push
map: nearby cars ◄─────────────────── /gps/get_user_nearby_drivers  (1500 m, FCM-updated)
pickup pin / autocomplete ───────────► /book/get_book_autocomplete, /book/get_book_address_nearby
fare estimate ───────────────────────► /api/fare2 | /fleet/fare    (car_option + charges)
/book/create  ───────────────────────► book{status=pending, bid}
   poll /book/user_book ─────────────►  ├── driver /v6/book/accept → /v6/book/lock (countdown 5-20 s)
   FCM "accepted"                     │    → /v6/book/driver_arrived_pickup (≤800-1000 m)
meetup / pin / call ─────────────────► /book/get_meetup_data, /book/get_book_direction
                                              ├── /v6/book/book_start_ride
ride tracking (GPS 20 s) ────────────► /v6/gps/update_driver
                                              └── /v6/book/book_complete_ride (+ charges)
score / tip ─────────────────────────► /book/score_driver
pay (wallet | octopus | payme | alipay | wechat | netopia card) ◄─► /pay/*, /v6/octopus/*
history / receipt ◄────────────────── /book/user_book_detail
cancel ──────────────────────────────► /book/user_cancel | /book/cancel   (fee HKD 6 after accept)
```

Driver lifecycle: offline → online (heartbeat 600 s) → order-offered (list + push) → accept (rate-limited
20 clicks/60 s) → lock → en-route → arrived-pickup → start → arrived-dropoff → complete →
cooldown 300-600 s → free. Signal loss is scored: warn at 3 min, error at 5 min.

**Unknowns to close:** actual `book.status` enum values; whether `/v6/book/list` is long-poll,
offset or delta; FCM payload schema; whether fare is computed client- or server-side (both apps
ship a Calculator + `FareItem`s → likely dual, server wins); integrity challenge TTL; per-endpoint
request signing (the `EncryptionUtil` class exists in both — need to confirm whether it's used for
payload crypto or only local storage).

---

## 3. Teardown plan (phased, each phase has a deliverable)

### Phase 0 — prerequisites (one-time, ~20 min, needs your approval)
Nothing in the box can decompile yet: no Java, no jadx, no apktool, no mitmproxy, no Frida.

```bash
sudo apt-get install -y openjdk-17-jre-headless          # required by jadx/apktool
mkdir -p ~/tools && cd ~/tools
curl -LO https://github.com/skylot/jadx/releases/latest/download/jadx-1.5.1.zip
unzip -q jadx-*.zip -d jadx                             # ~/tools/jadx/bin/jadx
curl -LO https://github.com/iBotPeaches/Apktool/releases/latest/download/apktool_2.11.1.jar
pip install --user mitmproxy frida-tools                # runtime phases only
```
Deliverable: `jadx` + `apktool` working. (Alternative: register the REA MCP server and set
`REA_JADX_MCP_JAR` — same Java requirement, plus structured Evidence output.)

### Phase 1 — static extraction (no device) **← highest value / lowest risk**
```bash
jadx -d out/pax --deobf --show-bad-code apks/com.flytaxi.hktaxi.apk
jadx -d out/drv --deobf --show-bad-code apks/com.flytaxi.apk
apktool d -f -o out/pax-res apks/com.flytaxi.hktaxi.apk   # strings.xml, layouts, manifest, smali
```
Deliverables:
1. `out/*/sources/` readable Java/Kotlin; package map already known (`com.hktaxi.hktaxi.*`, `com.flytaxi.*`).
2. Endpoint → caller mapping: for every path in §1.2, the concrete method, HTTP verb, request params
   and response model (Gson `@SerializedName` field names give the wire schema for free).
3. Full **API contract table**: endpoint, verb, auth header, params, response JSON, error codes.
4. `strings.xml` diff → all user-facing copy and error messages (reveals hidden states).
5. Deobfuscated enum dumps: `book.status`, `pay_mode`, doc-verify states, complaint types.

### Phase 2 — models & state machine
- Dump every `model/*Item` class field list → the domain model (already enumerated: 150+ models in
  each app). These are the server DTOs; they define the rebuild's schema almost completely.
- Recover `book` state transitions from `OrderDetailListenerFragment` / `MainListenerActivity` switch
  tables and from which endpoint each fragment calls on resume.
- Deliverable: `docs/state-machine.md` (states, events, guards, timeouts, who may transition).

### Phase 3 — fare & dispatch logic
- `asyncTask/*` + `FareItem`/`FleetFare*`/`CarOptionChargeItem` + `/api/fare2` caller: reproduce the
  estimate formula, taxi meter rules (`ConfigHKTaxiMeterPriceItem`), surcharges, min company price,
  cancel fee, coupon/promo paths.
- Dispatch: `take_order` thresholds, `minute_backoff` per car type, accept radius, `acc_order_cooldown`,
  `batch_info2` batching, `Schedule.db` advance bookings.
- Deliverable: `docs/pricing-and-dispatch.md` with formulas and tunables.

### Phase 4 — packaged data & local persistence
- Already extracted: config SQLite (16 tables), History/Schedule/Order/LoginIntegrity DBs.
  Dump every table + the `language_key_value` i18n pack.
- Deliverable: `data/config-dump.csv`, `docs/local-storage.md`.

### Phase 5 — runtime observation (needs device/emulator; do AFTER static)
- mitmproxy (or REA `inspect_web_network_capture`) on a test device with a **legitimate** install so
  Play Integrity passes; capture: login, config sync, create/accept/complete, payment callbacks.
- FCM payload capture (log `PushMessageItem` fields) → the push schema and its `action` vocabulary.
- Reconcile static assumptions vs. real traffic; record mismatches as unknowns, not as "the static
  analysis was wrong".
- Blocker to expect: certificate pinning. Check `CertificatePinner` usage in `okhttp`-based driver app;
  if pinned, this phase needs a Frida script or a patched network-security-config — say so rather
  than silently failing.

### Phase 6 — cross-version / cross-app comparison
- Passenger vs driver share `/v6/book/*` and the same config DB: diff the two DTO sets to find
  server-only fields, and confirm the state machine is symmetric.
- Optional: pull 2-3 older APK versions to see what changed recently (feature roadmap by diff).

### Phase 7 — write the spec (the rebuild input)
Produce `docs/api-spec.yaml` (OpenAPI), `docs/state-machine.md`, `docs/pricing-and-dispatch.md`,
`docs/screens.md` (screen list + transitions + the endpoint each screen needs). At that point the
rebuild no longer depends on the APK.

### Phase 8 — rebuild (two independent workstreams)
- **Client**: the existing UI is plain Activities/Fragments + Volley/OkHttp, so a clean re-implementation
  (e.g. Compose Multiplatform or React Native) is a straightforward mapping of `screens.md` onto
  `api-spec.yaml`. Do not reuse their drawables, icon set, wording, or the "飛的" marks.
- **Server**: you must build it — config service (the `config_key_value` model is a good design:
  config-driven tunables), auth (OTP + JWT + optional integrity), dispatch (geospatial queue,
  radius 1.5 km, accept countdown, cooldown, rate limiting), fare engine, order state machine,
  payment adapters, push (FCM), and an admin/ops console. Their dispatch is polling-based, which is
  simple but noisy; a push/WebSocket design is strictly better for your own build.
- **Sequencing**: build client against your own server first, using their app only as a behavioural
  reference (screen flow, field semantics, edge cases like no-show, GPS loss, cancel fees).

---

## 4. Immediate next steps (in order, cheapest first)

1. ✅ Static inventory (done) — endpoints, models, config dump, screens.
2. Dump `strings.xml` + all `model/*` field lists **without Java** (pure Python on `resources.arsc`);
   this alone yields most of the wire schema. — ~30 min, no installs.
3. Install Java + jadx (Phase 0) → real decompilation, endpoint⇄caller mapping (Phase 1).
4. Decide runtime work: is a test device/emulator available, and is a legitimate Play login acceptable?
   If not, the spec will be static-only and every "does the server actually behave this way" claim
   stays an explicit unknown.
5. Decide rebuild stack and whether the goal is (a) a learning clone, (b) a competing HK taxi app
   (needs your own driver supply — the real moat), or (c) an interoperable client for your own backend.

## 5. Approvals needed from you

- `sudo apt-get install openjdk-17-jre-headless` + downloading jadx/apktool into `~/tools`
- (optional, later) `pip install --user mitmproxy frida-tools`, and a device/emulator target
- (optional) registering the REA MCP server + `REA_JADX_MCP_JAR` so Android analysis returns structured Evidence
