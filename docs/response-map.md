# Response map, state machine and error codes (Plan A)

Recovered by call-site mining over the jadx output. Confidence is stated per row:
`direct` = `fromJson(..)` in the same method as the request; `listener` = inside the registered listener class;
`file-level` = only the containing file matched; `unknown` = not yet located.

## Envelope

```json
{ "status_code": "200", "message": "...", "data": { ... } }
```

- Success is `status_code == "200"` (string compare throughout the code).
- `data` is either an object or an array depending on the endpoint; arrays are sometimes nested,
  e.g. nearby drivers is `data.driver_list[]`.
- Observed non-200 codes: pax `105` (address/geocode failure), `801` (driver payment auth empty),
  driver `2001`–`2005` (session/GPS authorization failures — the request is silently dropped and the
  driver is force-logged-out path), plus the generic error dialog that prints `status_code` + `message`.

## Passenger (`com.flytaxi.hktaxi`) — endpoint → response model

| endpoint | response DTO (confidence) |
|---|---|
| `/v6/api/get_config_sqlite` | _unknown_ (unknown) |
| `/v6/book/book_complete_ride` | `BillDetailItem`, `FleetFareItem`, `OrderItem`, `ResponseStatus` (listener) |
| `/v6/book/cancel` | `BillDetailItem`, `FleetFareItem`, `OrderItem`, `ResponseStatus` (listener) |
| `/v6/book/get_book_address_nearby` | `ResponseStatus` (listener) |
| `/v6/book/get_book_autocomplete` | `ResponseStatus` (listener) |
| `/v6/book/get_book_direction` | `CallTaxiDirectionItem`, `ResponseStatus` (listener) |
| `/v6/book/get_meetup_data` | `MeetUpItem`, `ResponseStatus` (listener) |
| `/v6/book/update_book_direction` | `CallTaxiDirectionItem`, `ResponseStatus` (listener) |
| `/v6/book/user_book` | `BillDetailItem`, `FleetFareItem`, `OrderItem`, `ResponseStatus` (listener) |
| `/v6/book/user_book_detail` | `BillDetailItem`, `FleetFareItem`, `OrderItem`, `ResponseStatus` (listener) |
| `/v6/book/user_cancel` | `BillDetailItem`, `FleetFareItem`, `OrderItem`, `ResponseStatus` (listener) |
| `/v6/cust/update_user_option` | _unknown_ (unknown) |
| `/v6/fleet/book_create` | `FleetFareItem`, `OrderItem`, `ResponseStatus` (listener) |
| `/v6/fleet/fare` | `FleetFareItem`, `OrderItem`, `ResponseStatus` (listener) |
| `/v6/gps/get_user_nearby_drivers` | `ResponseStatus` (listener) |
| `/v6/gps/update_user` | `ResponseStatus` (listener) |
| `/v6/pay/create_tran_netopia_verify` | _unknown_ (unknown) |
| `/v6/pay/get_tran` | `ResponseStatus` (listener) |
| `/v6/pay/get_user_wallet` | `ResponseStatus` (listener) |
| `/v6/user/add_favourite_address` | `ConfigKeyValueItem`, `ResponseStatus` (listener) |
| `/v6/user/add_user_complain` | _unknown_ (unknown) |
| `/v6/user/add_user_referral_driver` | _unknown_ (unknown) |
| `/v6/user/create_login_token` | `LoginTokenItem`, `ResponseStatus` (listener) |
| `/v6/user/get_hk_user_tel_status` | _unknown_ (unknown) |
| `/v6/user/get_user_referral_driver` | _unknown_ (unknown) |
| `/v6/user/reg` | _unknown_ (unknown) |
| `/v6/user/remove_favourite_address` | `ConfigKeyValueItem`, `ResponseStatus` (listener) |
| `/v6/user/remove_historical_address` | `ConfigKeyValueItem`, `ResponseStatus` (listener) |
| `/v6/user/rider_refer_eligible` | _unknown_ (unknown) |
| `/v6/user/rider_refer_submit` | _unknown_ (unknown) |
| `/v6/user/update_firebase_user_tel` | _unknown_ (unknown) |
| `/v6/user/update_hk_user_tel` | _unknown_ (unknown) |
| `/v6/user/update_user_city_by_cid` | _unknown_ (unknown) |

## Driver (`com.flytaxi`) — endpoint → response model

| endpoint | response DTO (confidence) |
|---|---|
| `/v6/api/get_config_sqlite` | `ConfigSqliteMetaItem` (listener) |
| `/v6/book/accept` | `ResponseStatus` (listener) |
| `/v6/book/book_complete_ride` | `ResponseStatus` (listener) |
| `/v6/book/book_start_ride` | `ResponseStatus` (listener) |
| `/v6/book/create` | `ResponseStatus` (listener) |
| `/v6/book/driver_arrived_dropoff1` | `ResponseStatus` (listener) |
| `/v6/book/driver_arrived_pickup` | `ResponseStatus` (listener) |
| `/v6/book/driver_book` | `AppDateItem`, `ResponseStatus` (listener) |
| `/v6/book/driver_cancel` | `ResponseStatus` (listener) |
| `/v6/book/get_book_direction` | _unknown_ (unknown) |
| `/v6/book/list` | `ResponseStatus` (listener) |
| `/v6/book/lock` | `ResponseStatus` (listener) |
| `/v6/book/update_book_direction` | _unknown_ (unknown) |
| `/v6/book/user_no_show` | `ResponseStatus` (listener) |
| `/v6/device/integrity_challenge_test` | `DeviceIntegrityItem` (file-level) |
| `/v6/device/jwt_authorization` | `DeviceIntegrityItem` (file-level) |
| `/v6/driver/add_driver_car_model` | `DeviceIntegrityItem`, `ResponseStatus` (listener) |
| `/v6/driver/add_driver_complain` | _unknown_ (unknown) |
| `/v6/driver/batch_info2` | `BatchInfoV2Item`, `ResponseStatus` (listener) |
| `/v6/driver/delete_driver_car_model` | `DeviceIntegrityItem`, `ResponseStatus` (listener) |
| `/v6/driver/get_driver_auth` | `ResponseStatus` (listener) |
| `/v6/driver/get_hk_driver_tel_status` | _unknown_ (unknown) |
| `/v6/driver/login` | `LoginItem`, `LoginTokenItem`, `ResponseStatus` (listener) |
| `/v6/driver/logout` | `ResponseStatus` (listener) |
| `/v6/driver/reg` | _unknown_ (unknown) |
| `/v6/driver/reg_doc_detail` | `ResponseStatus` (listener) |
| `/v6/driver/reg_doc_verify` | `ResponseStatus` (listener) |
| `/v6/driver/select_driver_car_model2` | `DeviceIntegrityItem`, `ResponseStatus` (listener) |
| `/v6/driver/uber_link_account` | _unknown_ (unknown) |
| `/v6/driver/update_driver_car_model` | `DeviceIntegrityItem`, `ResponseStatus` (listener) |
| `/v6/driver/update_driver_option` | _unknown_ (unknown) |
| `/v6/driver/update_hk_driver_tel` | _unknown_ (unknown) |
| `/v6/fleet/book_accept` | `ResponseStatus` (listener) |
| `/v6/fleet/get_car_driver_list` | `DeviceIntegrityItem`, `ResponseStatus` (listener) |
| `/v6/fleet/select_car_driver` | _unknown_ (unknown) |
| `/v6/gps/jwt_update_driver` | `ResponseStatus` (listener) |
| `/v6/gps/update_driver` | `ResponseStatus` (listener) |
| `/v6/integrity/verify` | _unknown_ (unknown) |
| `/v6/member/create_login_token` | `LoginItem`, `LoginTokenItem`, `ResponseStatus` (listener) |
| `/v6/octopus/remove_driver_auth` | `ResponseStatus` (listener) |
| `/v6/octopus/request_card_payment` | `ResponseStatus` (listener) |
| `/v6/octopus/request_driver_linkage` | `ResponseStatus` (listener) |
| `/v6/octopus/update_driver_linkage` | `ResponseStatus` (listener) |
| `/v6/payme/add_driver_auth` | `ResponseStatus` (listener) |
| `/v6/payme/edit_driver_auth` | `ResponseStatus` (listener) |
| `/v6/payme/remove_driver_auth` | `ResponseStatus` (listener) |
| `/v6/user/get_user_complain_review_lock` | _unknown_ (unknown) |
| `/v6/user/update_user_complain_review_release` | _unknown_ (unknown) |
| `/v6/user/update_user_complain_review_suspend` | _unknown_ (unknown) |
| `/v6/wechat/add_driver_auth` | `ResponseStatus` (listener) |

## `book.status` — the order state machine

Derived from both apps independently (identical result). The server is the only writer of `status`.

| status | meaning | UI label key |
|---|---|---|
| `-1` | no order / empty | `order_status_new` |
| `11` | NEW — order created, broadcasting to drivers | `order_status_new` |
| `12` | CANCELLED (generic) | `order_status_cancel`, `order_status_cancel_desc` |
| `13` | EXPIRED (no driver accepted in time) | `order_status_expiry`, `order_status_expiry_desc` |
| `14` | ACCEPTED — driver confirmed | `order_status_accept` |
| `21` | EN ROUTE TO PICKUP — taxi is coming | `order_status_arrive_pickup` |
| `22` | accepted, driver late | `order_status_accept_driver_late`, `order_status_accept_driver_late_desc` |
| `24` | accepted, driver lost signal | `order_status_accept_driver_no_signal`, `order_status_accept_driver_no_signal_desc` |
| `25` | accepted, user cancelled | `order_status_accept_user_cancel`, `order_status_accept_user_cancel_desc` |
| `26` | accepted, driver cancelled | `order_status_accept_driver_cancel`, `order_status_accept_driver_cancel_desc` |
| `31` | TRIP STARTED — on the way to dropoff | `order_status_start_ride` |
| `32` | arrive-pickup stage, driver late | `order_status_arrive_pickup_driver_late`, `order_status_arrive_pickup_driver_late_desc` |
| `34` | arrive-pickup stage, driver no signal | `order_status_arrive_pickup_driver_no_signal`, `order_status_arrive_pickup_driver_no_signal_desc` |
| `35` | arrive-pickup stage, user cancelled | `order_status_arrive_pickup_user_cancel`, `order_status_arrive_pickup_user_cancel_desc` |
| `36` | arrive-pickup stage, driver cancelled | `order_status_arrive_pickup_driver_cancel`, `order_status_arrive_pickup_driver_cancel_desc` |
| `37` | arrive-pickup stage, user no-show | `order_status_arrive_pickup_user_no_show`, `order_status_arrive_pickup_user_no_show_desc` |
| `41` | ARRIVED DROPOFF / ride in progress | `order_status_arrive_dropoff` |
| `42` | start-ride stage, driver late | `order_status_start_ride_driver_late`, `order_status_start_ride_driver_late_desc` |
| `44` | start-ride stage, driver lost signal | `order_status_start_ride_driver_no_signal`, `order_status_start_ride_driver_no_signal_desc` |
| `45` | start-ride stage, user cancelled | `order_status_start_ride_user_cancel`, `order_status_start_ride_user_cancel_desc` |
| `51` | COMPLETED | `order_status_complete_ride` |
| `52` | arrive-dropoff stage, driver late | `order_status_arrive_dropoff_driver_late`, `order_status_arrive_dropoff_driver_late_desc` |
| `54` | arrive-dropoff stage, driver no signal | `order_status_arrive_dropoff_driver_no_signal`, `order_status_arrive_dropoff_driver_no_signal_desc` |
| `59` | COMPLETED manually (user helped complete) | `order_status_complete_ride_manual` |

### Structure of the encoding

`status` is two digits: **tens = lifecycle stage**, **ones = terminal/exception reason**.

```
stage 1x  created / dispatch      1=new 2=cancel 3=expire 4=accepted
stage 2x  accepted                1=en route to pickup 2=driver late 4=no signal 5=user cancel 6=driver cancel
stage 3x  arrived pickup          1=started ride 2=late 4=no signal 5=user cancel 6=driver cancel 7=user no-show
stage 4x  to dropoff              1=normal 2=late 4=no signal 5=user cancel
stage 5x  completed               1=normal 2=late 4=no-signal path 9=manual completion
```

So `status` transitions observed in UI logic: `11 → 14 → 21 → 31 → 41 → 51`,
with `1x` and `2x/3x/4x` `x2/x4/x5/x6/x7` branches as terminal failures.
`59` = user-assisted manual completion (the app shows a countdown then a "Complete ride" button).

## Fare / estimate models

`/v6/fleet/fare` request body is `FleetFareBuildItem`:
```
access_token, bid, date_pickup, dropoff1_address, dropoff1_latitude, dropoff1_longitude,
fare_id, lang, opt, pickup_address, pickup_latitude, pickup_longitude, remark,
route: FleetFareRouteItem, selected: FleetFareSelectedItem, service_list, uid, v
```
`/v6/api/fare2` is the taxi-meter path (`/api/fare2`); the fleet path is the aggregator (Splyt/FastCab) path.

## `/v6/book/list` — dispatch payload (confirmed)

Driver-side dispatch list. Call chain: request class `p147S2/C1535a` → `MainListenerActivity.C3410c`
(`status_code == "200"`) → `JwtZipBookListAsyncTask`.

```json
{ "status_code": "200", "data": [ { OrderItem... } ] }
```

- `data` is a **flat array of `OrderItem`**, not an envelope object.
- Despite the class name `JwtZipBookListAsyncTask`, **no local inflate/unzip call exists** in the task:
  it Gson-parses `data[]` directly. The "zip" is only a name/semantic (server packs the dispatch bundle).
  Treat "the payload is compressed" as **unverified**.
- The wire keys are the **short/compact variants**, expanded on the client:

| wire | expands to | wire | expands to |
|---|---|---|---|
| `dcr` | `date_cr` (created) | `pa` | `pickup_address` |
| `dup` | `date_up` (updated) | `plat` / `plon` | `pickup_lat` / `pickup_lon` |
| `dpu` | `date_pickup` | `da` | `dropoff1_address` |
| `ut` | (0 normalised to 1) | `dn` | `driver_name` |
| `d` | `distance` | `rmk` | `remark` |
| `ct` | car type (defaults `"1"`) | `s` | service object |
| `s.at` | `amount_total` | `s.pm` | `pay_mode` |

`AppItem.server_time` comes from the same response and is used to correct device clock drift —
relevant because every request carries device time (`app_date`).

## `status_code` catalogue

| code | meaning | evidence |
|---|---|---|
| `200` | success | compared everywhere (both apps) |
| `105` | address / geocode lookup failed | `AddressToLocationAsyncTask`, `ResponseStatus.isSuccess()` excludes it |
| `188` | unknown (fleet flow branch) | `FleetListenerFragment` — no label resolved |
| `712` | SSO / access-token login error | splash handler → `sso_login_access_token_error_description` |
| `713` | login blocked (paired with 712) | same handler |
| `801` | driver payment auth empty | `PaymentListenerFragment` clears stored `DriverAuthItem` list |
| `964` / `965` | tolerated as success-ish in one flow | `p167f3/AbstractC3750d` — meaning unknown |
| `1005` | unknown (order detail branch) | `OrderDetailListenerFragment` |
| `1621` | **IP banned** — dialog shows ban time, customer id, api_error_code, phone | `EnumC4317h.IP_BANNED`, string `ce_ip_banned_with_code` |
| `2001`–`2005` | driver session / GPS authorisation failure → dialog **and** force-relogin path | `MainListenerActivity`, `AbstractServiceC1665c`, `AbstractServiceC1716b` |
| `2401` | fleet (aggregator) booking create failed | `book_detail_fleet_create_fail_description` |
| `2416` | fleet fare not found | `fleet_create_fare_not_found_description` |
| other | generic error dialog printing `api_error_code` + message + server time | `ce_server_error_with_code` |

Note: the client displays `status_code` verbatim in its error dialog. That makes Plan C error
screenshots the cheapest way to map the remaining codes.

## Fare models (passenger app)

```
FareItem (15)
  amount_company_price, amount_driver, amount_fare, amount_operation,
  book_charge_details[BillDetailItem], book_charge_id, car_option_charge_id,
  car_type, charge, data:FareDetailItem, meter_price_if_below,
  quantity, show_busy_traffic_dialog, suggested_ct, summary[BillDetailItem]

BillDetailItem (18)      # one line of the fare breakdown
  title, charge, factor, quantity, amount_fare, amount_driver, amount_operation,
  apply_to, car_option_name, charge_option_id, option_charge_name, option_charge_value,
  breakdown_display1, condition2_value, currency_id, direction_id, status, value1

FleetFareItem (7)        # aggregator quote
  amount_min, amount_max, fare_id, route, selected, services[], traffic

FleetFarePriceItem (4)
  amount_total, fare_mode, t85 (detail), t_green_cross_east_west (detail)
FleetFarePriceDetailItem (4)
  amount, base, meter, meter_below          # meter vs below-meter (negotiated) fare

CarOptionChargeItem (11) # option surcharges, incl. conditional pricing
  car_option_name, option_charge_name, option_charge_value,
  condition1_key, condition1_value, condition2_key, condition2_value,
  factor, factor_type, payment_method, breakdown_display1, currency_id

FareOptionItem (4)
  car_option_name, value1, value2, modify

SplytFareItem (2)
  lower, upper                              # aggregator price band
```

The estimate screen is built from `FareItem.summary[]` / `book_charge_details[]`; each row's
`title` + `charge` is what a user sees as a breakdown line. `meter_price_if_below` is the
"meter price if below X" rule, and `amount_company_price` is the company-price path (HKD 80/70 floor).

## Remaining gaps

- Codes `188`, `1005`, `964`, `965` remain unmapped (no resolvable label; needs an error screenshot).
- `AllOrderItem` (`orderList` / `rejectList` / `orderDisplayMode` / `gapOffset` / `bookZipDate`) is a
  **client-side aggregation** built by `p140R3/C1416b`, not the wire response.
- No HTTP-level evidence of these shapes yet — that is what runtime capture (Plan B) would confirm.
- `book/list` polling cadence is client-driven and not in the config DB; measure it (Plan C).
