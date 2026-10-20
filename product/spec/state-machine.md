# Order state machine (new-fly-taxi, driver side)

Clean-room reimplementation of the lifecycle observed in the public HK taxi app.
We implement **our own** codes and endpoints; the numbering below is our own
contract, chosen to be a superset-compatible mapping for anyone porting.

## States

| code | label | meaning |
|---|---|---|
| `11` | New | booking created, broadcasting to nearby drivers |
| `12` | Cancelled | cancelled before a driver was bound |
| `13` | Expired | no driver accepted before the deadline |
| `14` | Confirmed | driver accepted and locked the booking |
| `21` | Taxi is coming | driver is en route to the pickup point |
| `31` | On the way | trip started, heading to the drop-off |
| `41` | To drop-off | arrived at the drop-off point |
| `51` | Completed | ride finished, fare settled |
| `59` | Completed (manual) | completed by the passenger/operator instead of the driver |

## Terminal reasons (suffix on a stage)

| suffix | reason | allowed stages |
|---|---|---|
| `2` | driver too late | 2x, 3x, 4x |
| `4` | driver lost GPS signal | 2x, 3x, 4x, 5x |
| `5` | passenger cancelled | 2x, 3x, 4x |
| `6` | driver cancelled | 2x, 3x |
| `7` | passenger no-show | 3x |

Stage digit = lifecycle stage, suffix = exception reason. This is the encoding the
observed app uses, and it is cheap to extend (new stage → new tens digit).

## Driver happy path

```
11 ──accept──▶ 14 ──arrived_pickup──▶ 21 ──start_ride──▶ 31 ──arrived_dropoff──▶ 41 ──complete──▶ 51
```

Legal transitions (server-enforced, see `server/src/server.mjs`):

| from | action | to | guard |
|---|---|---|---|
| `11` | `orders/accept` | `14` | driver within `arrive_pickup_max_radius_m` of pickup; booking still `11` |
| `14` | `orders/arrived_pickup` | `21` | booking bound to this driver |
| `21` | `orders/start_ride` | `31` | idem |
| `31` | `orders/arrived_dropoff` | `41` | idem |
| `41` | `orders/complete` | `51` | idem; computes fare |
| `14`/`21`/`31` | `orders/cancel` | `26` | idem (driver cancel) |

Any transition attempted from the wrong state returns `409` with the current status,
so a client can always resynchronise from the error payload.

## Tunables (server `CONFIG`)

| key | value | provenance |
|---|---|---|
| `dispatch_radius_m` | 1500 | `pickup_nearby_drivers_radius` |
| `accept_countdown_s` | 20 | `takeb_order.takeb_countdown_sec` (5 for take-b) |
| `gps_idle_interval_s` | 600 | `gps_update_normal_sec` |
| `gps_on_order_interval_s` | 20 | `gps_update_accepted_book_sec` (60 in the city table) |
| `gps_lost_signal_warn_s` | 180 | `warning_driver_lose_signal_mins` = 3 |
| `gps_lost_signal_error_s` | 300 | `error_driver_lose_signal_mins` = 5 |
| `cancel_fee_hkd` | 6 | `cancel_fee.book_accept_cancel_fee` |
| `arrive_pickup_max_radius_m` | 1000 | `book_accept_driver_arrive_pickup_max_radius` (800–1000) |
| `arrive_dropoff_max_radius_m` | 2000 | `book_accept_driver_arrive_dropoff1_max_radius` (1800–2000) |
| `company_price_floor` | red 80 / green 70 | `user.red_taxi_min_company_price`, `user.green_taxi_min_company_price` |

The observed app also ships an anti-spam rule on accept (`click_threshold_sec` 60 /
`click_threshold_count` 20) — implement it server-side as a per-driver rate limit
before you let a client auto-accept.

## Fare

Base implementation uses the published HK meter tariffs:

| class | first 1.3 km | then per 200 m |
|---|---|---|
| red / blue taxi | HK$27 | HK$2.3 |
| green taxi | HK$22 | HK$1.9 |

plus tunnel/bridge tolls (HK$25 per crossing, matching the app's own copy) and an
optional company-price discount floored at the configured minimum.

`spec/openapi.yaml` is the source of truth for field names; the reference server
implements it exactly and `server/test/lifecycle.test.mjs` asserts the transitions.
