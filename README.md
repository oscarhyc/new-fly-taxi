# new-fly-taxi — teardown & rebuild notes

Static reverse-engineering notes for two Android apps (FlyTaxi 飛的, Hong Kong taxi hailing):

| role | package | version analysed |
|---|---|---|
| passenger / 叫車 | `com.flytaxi.hktaxi` | 191 |
| driver / 司機版 | `com.flytaxi` | 356 |

Goal: recover the operation flow (API contract, order state machine, fare rules) well enough to
design and build an independent implementation. This repository holds **our own analysis output
and tooling only** — no APKs and no decompiled application code.

## Contents

| path | what |
|---|---|
| `docs/api-contract.md` | endpoint inventory, parameters, transport/auth conventions |
| `docs/response-map.md` | per-endpoint response models, `book.status` state machine, error codes, fare models |
| `docs/manual-observation.md` | Plan C: passive UI observation checklist for an unrooted phone |
| `TEARDOWN-PLAN.md` | the teardown/rebuild plan and phase breakdown |
| `device-setup/README.md` | runtime capture runbook (rooted phone / emulator, Play Integrity caveats) |
| `device-setup/capture-addon.py` | mitmproxy addon: contract-focused, PII-redacting capture |
| `*.py` | dependency-free analysis tools (DEX, binary XML, resources.arsc, call-site mining) |
| `*.json` | small machine-readable extracts (endpoint↔params, response map, status map) |

## Tooling

No Android SDK, Java, or third-party Python packages are required for the static passes:

- `axml.py` — Android binary XML (manifest, network security config, resources)
- `dex.py` — DEX header/string/type/method/class tables
- `deep.py` — class fields + `resources.arsc` string extraction
- `recon.py` — inventory: URLs, permissions, components, app classes, keyword buckets
- `contract*.py` — endpoint ↔ parameter extraction from decompiled sources
- `planA*.py` — call-site mining: endpoint ↔ response DTO, status values, error codes
- `status_map.py` — `book.status` → UI label mapping

Decompilation itself uses `jadx` (installed as a user-local JRE + jadx under `~/tools`); the
decompiled tree is intentionally not committed.

## Findings in one paragraph

The apps are config-driven thin clients over a server-authoritative REST backend
(`https://api.flytaxiapp.com`, `/v6/` prefix, all requests POST). The driver client adds
`Authorization: Bearer` plus an RFC 9449 **DPoP** proof (ES256, `htm:"POST"`, `htu:<url>`), and login
is gated by **Play Integrity** with a server-issued nonce. Dispatch is **driver polling +
FCM push**, not a socket. `book.status` is a two-digit code: tens = lifecycle stage,
ones = exception reason (`11 → 14 → 21 → 31 → 41 → 51` on the happy path; `x2` late, `x4` no
signal, `x5/x6` cancels, `x7` no-show, `59` manual completion). Business tunables ship inside the
APK as a seeded SQLite config database (heartbeat intervals, accept radius, countdowns, cancel fee,
price floors).

## Scope / legal

Analysis of publicly distributed binaries for interoperability and learning. Rebuilding means
writing an independent client and server — not reusing their assets, branding, copy, or code.
The backend is private; studying it does not grant a right to use it. Runtime work is limited to
passive observation on accounts we own, with no order creation or automation.
