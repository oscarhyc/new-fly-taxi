# new-fly-taxi — product (clean-room)

Our own taxi-hailing implementation, built from the **behaviour** we documented, not from
anyone's code, brand, assets or copy. The client is Android/Kotlin; the server contract is
defined in `spec/openapi.yaml`.

```
product/
  spec/openapi.yaml        driver-side API contract (source of truth)
  spec/state-machine.md    order lifecycle + tunables + fare rules
  server/src/server.mjs    runnable reference server (Node, zero dependencies)
  server/test/…            contract conformance test (full lifecycle)
  client/                  Android driver app (Kotlin + Compose)
  spec/reference-data/     business parameters extracted from the live config DB
                           + design tokens harvested from the app's resources
```

## Run the server + tests (no install needed)

```bash
node product/server/test/lifecycle.test.mjs   # 12-step conformance test
node product/server/src/server.mjs --port 8787
```

Seed account: tel `57170655` / pwd `test`, car `WN4881`. Seeded bookings are around
Mira Place, Tuen Mun (22.3089, 114.1576) so the dispatch radius is exercised.

Lifecycle covered by the test:

```
login → gps heartbeat → dispatch list → accept(11→14) → arrived_pickup(→21)
      → start_ride(→31) → arrived_dropoff(→41) → complete(→51, fare)
```
plus negative cases: bad password, missing params (101), unauthenticated (2001),
accept out of radius, double accept (409).

## Android client

Stack mirrors the observed production app: Kotlin, Compose, OkHttp/Retrofit, kotlinx.serialization,
Coroutines/Flow, DataStore, Play Services Location/Maps, FCM.

```bash
cd product/client
gradle wrapper --gradle-version 8.7      # or open in Android Studio
./gradlew :app:assembleDebug
```

Point it at the local reference server (`API_BASE_URL` in `app/build.gradle.kts`; `10.0.2.2`
is the host as seen from the emulator, use your LAN IP for a physical device).

**Status:** the Kotlin sources are written against `spec/openapi.yaml` but **have not been
compiled in this environment** (no Android SDK / no KVM here). The server and its test are
executed and green. First `assembleDebug` on a machine with the SDK will surface any
import/version drift — expect to fix Gradle/Compose version pins there.

## What is deliberately NOT here

- No copied source code, resources, icons, copy or brand from any third-party app.
- No client for the production FlyTaxi backend. The contract above is ours; the field names
  were chosen to be portable, the endpoints, auth model, behaviour and code are our own.
- No scraping or automation of third-party services.

## Next milestones

1. Compile the client; wire real location updates and the GPS heartbeat cadence
   (idle 600 s, on-order 20 s) through a foreground service.
2. Push notifications for new bookings (FCM) instead of polling only.
3. Driver document verification, plan/subscription and fleet (group) concepts.
4. Passenger app against the same contract.

## UI rebuild

The reference UI was reverse engineered from the binary layouts and resources, not copied:
`docs/ui-spec.md` (screen inventory + tokens), `research/ui/layouts/*.txt` (216 decoded view
trees with resource ids resolved), `research/ui/design-tokens.json`.

Our Compose implementation mirrors the observed structure and scale — e.g.
`ui/AcceptOrderScreen.kt` reproduces the accept-order screen (countdown timer, progress bar,
section cards, gold swipe-to-confirm bar) with our own components and our own brand tokens in
`ui/Theme.kt`.

## Capturing real sessions (deadline-bound)

`device-setup/run-capture.sh` starts mitmproxy with the redacting addon and records one
manually-driven trip on your own device. It needs the mitmproxy CA installed as a **system**
certificate (rooted phone); unrooted devices fall back to `docs/manual-observation.md`.
