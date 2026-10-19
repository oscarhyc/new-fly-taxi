# FlyTaxi `/v6` API contract (recovered from shipped APKs)

Source: jadx 1.5.6 decompilation of `com.flytaxi` v356 (driver) and `com.flytaxi.hktaxi` v191 (passenger).
Every row below is a **static observation** from the binaries; response shapes marked as DTO fields are observed class declarations.

## Conventions

```
base URL   https://api.flytaxiapp.com          (also https://api2.flytaxiapp.com, https://member.flytaxiapp.com)
config     city.domain_core = api-hk-release-core-01.flytaxiapp.com   (runtime override)
version    path prefix /v6/
method     every request is POST (DPoP htm is hard-coded "POST")
pax body   two wrappers: C0829c = JSON body + Content-Type: application/json (no auth header);
           C0828b = Volley getParams() -> form-urlencoded; token travels in body as access_token
driver body JSON via C4800d; headers below
headers    (driver) Content-Type: application/json; charset=utf-8
           (driver) Authorization: Bearer <access_token>
           (driver) DPoP: <ES256 JWT>        # see Auth section
           (pax)    no Authorization header; access_token is a body param, no DPoP
app ids    app_id/app = 31 (passenger), 32 (driver)
user_type  2 (customer)
```

## Auth / trust

1. **Bearer token** — `access_token` also travels *inside* the JSON body on many endpoints.
2. **DPoP (RFC 9449)** — `p180W3/C1825l` builds an ES256 JWT with header `{alg:ES256, typ:dpop+jwt, jwk:{kty:EC,crv:P-256,use:sig,x,y}}`
   and claims `{iat, jti:<uuid>, htm:"POST", htu:<full URL>, exp: iat+1day}`; signature is raw r||s, base64url.
   The EC key pair is held in `C6189d` and persisted; header `DPoP` is added on every request (`p247d3/C4800d`).
3. **Play Integrity gate** — `/v6/device/integrity_challenge_test` → `{nonce, token, login_integrity_id, platform, error_message}`
   → `/v6/integrity/verify` → `/v6/device/jwt_authorization` returns `server_jwt_token`. Params: `nonce, token, login_integrity_id, platform, error_message, did`.
4. Client also sends `app_date` (formatted device time) on some calls, `crc`, `imei`, `device_name`, `environment`, `db_config_version` at login.

Common body params on nearly every call: `did` (device id), `v` (app version int, 356 / 191), `lang`, `app`, `app_id`, `access_token`.

## Passenger (`com.flytaxi.hktaxi`, app_id 31)

### Endpoints

| endpoint | verb | params (excl. common) | source |
|---|---|---|---|
| `/v6/api/get_config_sqlite` | POST | `user_type` | `p038H2/C0266a.java` |
| `/v6/book/book_complete_ride` | POST | `city_id`, `bid`, `user_type` | `p058L2/C0376a.java` |
| `/v6/book/cancel` | POST | `bid`, `user_type`, `opt`, `group_id`, `date_pickup` | `p058L2/C0376a.java` |
| `/v6/book/get_book_address_nearby` | POST | `lat`, `lng`, `city_id` | `p083Q2/C0583a.java` |
| `/v6/book/get_book_autocomplete` | POST | `q`, `city_id` | `p083Q2/C0583a.java` |
| `/v6/book/get_book_direction` | POST | `city_id`, `pickup_lat`, `pickup_lon`, `dropoff1_lat`, `dropoff1_lon` | `p063M2/C0390a.java` |
| `/v6/book/get_meetup_data` | POST | `city_id`, `cid`, `bid`, `book_direction_id` | `p053K2/C0357a.java` |
| `/v6/book/update_book_direction` | POST | `city_id`, `pickup_lat`, `pickup_lon`, `dropoff1_lat`, `dropoff1_lon`, `points`, `distance`, `duration` | `p063M2/C0390a.java` |
| `/v6/book/user_book` | POST | — | `p058L2/C0376a.java` |
| `/v6/book/user_book_detail` | POST | `bid` | `p058L2/C0376a.java` |
| `/v6/book/user_cancel` | POST | `bid`, `city_id`, `user_type`, `opt`, `group_id`, `date_pickup` | `p058L2/C0376a.java` |
| `/v6/cust/update_user_option` | POST | `option_id`, `option_value` | `p088R2/C0612a.java` |
| `/v6/gps/get_user_nearby_drivers` | POST | `lat`, `lng`, `ct_list`, `app_list`, `city_id` | `p083Q2/C0583a.java` |
| `/v6/gps/update_user` | POST | `cid`, `bid`, `lat`, `lon`, `city_id` | `p083Q2/C0583a.java` |
| `/v6/pay/create_tran_netopia_verify` | POST | — | `p098T2/C0676a.java` |
| `/v6/pay/get_tran` | POST | `cid`, `currency_id` | `p098T2/C0677b.java` |
| `/v6/pay/get_user_wallet` | POST | — | `p098T2/C0677b.java` |
| `/v6/user/add_favourite_address` | POST | `city_id`, `address_name`, `address_lat`, `address_lon`, `address_tag`, `address_category`, `address_type`, `id` | `p033G2/C0231a.java` |
| `/v6/user/add_user_complain` | POST | `city_id`, `bid`, `complain_type_id`, `complain_desc`, `complain_value1`, `complain_value2`, `complain_value3` | `p028F2/C0202a.java` |
| `/v6/user/add_user_referral_driver` | POST | `city_id` | `p073O2/C0435a.java` |
| `/v6/user/create_login_token` | POST | — | `p048J2/C0343a.java` |
| `/v6/user/get_hk_user_tel_status` | POST | `cid`, `city_id` | `p093S2/C0643a.java` |
| `/v6/user/get_user_referral_driver` | POST | `city_id` | `p073O2/C0435a.java` |
| `/v6/user/reg` | POST | `auth_id`, `city_id`, `notification_id`, `sso_type`, `sso_token`, `address_name`, `t_and_c_date` | `p078P2/C0568a.java` |
| `/v6/user/remove_favourite_address` | POST | `city_id`, `id` | `p033G2/C0231a.java` |
| `/v6/user/remove_historical_address` | POST | `city_id` | `p033G2/C0231a.java` |
| `/v6/user/rider_refer_eligible` | POST | `city_id` | `p068N2/C0421a.java` |
| `/v6/user/rider_refer_submit` | POST | `city_id`, `doc_number` | `p068N2/C0421a.java` |
| `/v6/user/update_firebase_user_tel` | POST | `verify_type`, `sessionInfo`, `code`, `idToken`, `city_id` | `p093S2/C0643a.java` |
| `/v6/user/update_hk_user_tel` | POST | `cid`, `tel`, `city_id` | `p093S2/C0643a.java` |
| `/v6/user/update_user_city_by_cid` | POST | `cid`, `city_id` | `p078P2/C0568a.java` |

<details><summary>DTO field lists</summary>

**CustomerItem** (12)
```
  accessToken: String
  addressFavouriteList: List<AddressItem>
  cityId: String
  encryptAccessToken: String
  fcmToken: String
  languageCode: String
  languageId: String
  mobileVerified: String
  nearbyList: List<AddressItem>
  tel: String
  userAuthList: List<UserAuthItem>
  userOptionList: List<UserOptionItem>
```
**OrderItem** (53)
```
  amountFare: double
  bid: int
  bookCharge: BillDetailItem
  bookChargeId: String
  booking_id: String
  carOptionInputList: List<InputTypeItem>
  car_group: String
  car_number: String
  car_type: String
  city_id: String
  date_acc: Date
  date_cr: Date
  date_pickup: Date
  did: String
  direction_id: String
  driverAuthList: List<DriverAuthItem>
  driverCarModelList: List<DriverCarModelItem>
  dropoff1_address: String
  dropoff1_lat: String
  dropoff1_lon: String
  dropoff2_addr: String
  dropoff2_lat: String
  dropoff2_lon: String
  estimateDistance: double
  estimateDuration: double
  estimatePointItemList: List<EstimateRouteItem>
  fareCarOptionList: List<FareOptionItem>
  fareItemList: List<FareItem>
  group_id: String
  hotline: String
  json_fare: FleetFareItem
  json_fleet: JsonFleetItem
  name: String
  opt: String
  orderConfirmList: List<BillDetailItem>
  payMode: String
  pay_mode: String
  pickupTime: Date
  pickup_address: String
  pickup_lat: String
  pickup_lon: String
  pickup_wait_time: String
  pid: String
  polyline: String
  remark: String
  status: String
  taxiTip: int
  taxi_extra: String
  taxi_fixed: String
  taxi_number: String
  tel: String
  uid: String
  user_type: String
```
**EstimateFareItem** (0)
```
```
**FareItem** (15)
```
  amount_company_price: double
  amount_driver: double
  amount_fare: double
  amount_operation: double
  book_charge_details: List<BillDetailItem>
  book_charge_id: String
  car_option_charge_id: String
  car_type: String
  charge: String
  data: FareDetailItem
  meter_price_if_below: double
  quantity: String
  show_busy_traffic_dialog: String
  suggested_ct: boolean
  summary: List<BillDetailItem>
```
**LoginTokenItem** (1)
```
  login_token: String
```
**MeetUpItem** (3)
```
  date_up: Date
  lat: String
  lon: String
```
</details>

## Driver (`com.flytaxi`, app_id 32)

### Endpoints

| endpoint | verb | params (excl. common) | source |
|---|---|---|---|
| `/v6/api/get_config_sqlite` | POST | `user_type` | `p067I2/C0738a.java` |
| `/v6/book/accept` | POST | `bid`, `driver_lat`, `driver_lng`, `user_type`, `integrity`, `accessibility`, `log_list`, `driver_gps_list` | `p147S2/C1535a.java` |
| `/v6/book/book_complete_ride` | POST | `city_id`, `bid` | `p147S2/C1535a.java` |
| `/v6/book/book_start_ride` | POST | `city_id`, `bid` | `p147S2/C1535a.java` |
| `/v6/book/create` | POST | `book_charge_id`, `user_type`, `pickup_lat`, `pickup_lon`, `pickup_address`, `dropoff1_address`, `dropoff1_lat`, `dropoff1_lon`, `city_id`, `ct`, `date_pickup`, `group_id`, `remark`, `opt`, `pay_mode`, `tel` | `p147S2/C1535a.java` |
| `/v6/book/driver_arrived_dropoff1` | POST | `city_id`, `bid` | `p147S2/C1535a.java` |
| `/v6/book/driver_arrived_pickup` | POST | `bid` | `p147S2/C1535a.java` |
| `/v6/book/driver_book` | POST | `bid` | `p107N2/C1033a.java` |
| `/v6/book/driver_cancel` | POST | `bid`, `city_id` | `p147S2/C1535a.java` |
| `/v6/book/get_book_direction` | POST | `city_id`, `pickup_lat`, `pickup_lon`, `dropoff1_lat`, `dropoff1_lon` | `p171V2/C1712a.java` |
| `/v6/book/list` | POST | `lat`, `lng`, `t` | `p147S2/C1535a.java` |
| `/v6/book/lock` | POST | `bid`, `ct`, `date_pickup`, `driver_lat`, `driver_lng`, `accessibility`, `distance` | `p147S2/C1535a.java` |
| `/v6/book/update_book_direction` | POST | `city_id`, `pickup_lat`, `pickup_lon`, `dropoff1_lat`, `dropoff1_lon`, `points`, `distance`, `duration`, `source` | `p171V2/C1712a.java` |
| `/v6/book/user_no_show` | POST | `bid`, `city_id` | `p147S2/C1535a.java` |
| `/v6/device/integrity_challenge_test` | POST | `platform` | `p075J2/C0788a.java` |
| `/v6/device/jwt_authorization` | POST | `nonce`, `login_integrity_id`, `token`, `error_message`, `platform` | `p075J2/C0788a.java` |
| `/v6/driver/add_driver_car_model` | POST | `car_number`, `car_model_id` | `p051G2/C0596a.java` |
| `/v6/driver/add_driver_complain` | POST | `driver_complain_type_id`, `complain_desc`, `cid`, `bid`, `city_id` | `p059H2/C0651a.java` |
| `/v6/driver/batch_info2` | POST | — | `p187X2/C1935a.java` |
| `/v6/driver/delete_driver_car_model` | POST | `id` | `p051G2/C0596a.java` |
| `/v6/driver/get_driver_auth` | POST | `city_id`, `driver_auth_id`, `auth_type`, `auth_id`, `notification_id`, `driver_name`, `driver_email`, `driver_misc`, `driver_misc2` | `p163U2/C1662a.java` |
| `/v6/driver/get_hk_driver_tel_status` | POST | `city_id` | `p195Y2/C1967a.java` |
| `/v6/driver/login` | POST | `tel`, `city_id`, `imei`, `pwd`, `fcm_id`, `lat`, `lon`, `user_token`, `device_id`, `device_name`, `db_config_version`, `crc`, `environment`, `accessibility` | `p123P2/C1215a.java` |
| `/v6/driver/logout` | POST | — | `p187X2/C1936b.java` |
| `/v6/driver/reg` | POST | `tel`, `imei`, `device_id`, `pwd`, `ct`, `city_id`, `fcm_id`, `referrer` | `p091L2/C0905a.java` |
| `/v6/driver/reg_doc_detail` | POST | `city_id`, `ct`, `doc_type_id`, `doc_name`, `pic_type`, `doc_number`, `date_doc_valid_to` | `p083K2/C0815a.java` |
| `/v6/driver/reg_doc_verify` | POST | `ct` | `p083K2/C0815a.java` |
| `/v6/driver/select_driver_car_model2` | POST | `id` | `p051G2/C0596a.java` |
| `/v6/driver/uber_link_account` | POST | `redirect_uri` | `p179W2/C1810a.java` |
| `/v6/driver/update_driver_car_model` | POST | `driver_car_model_id`, `car_number`, `car_model_id` | `p051G2/C0596a.java` |
| `/v6/driver/update_driver_option` | POST | `option_id`, `option_value` | `p139R2/C1414a.java` |
| `/v6/driver/update_hk_driver_tel` | POST | `city_id` | `p195Y2/C1967a.java` |
| `/v6/fleet/get_car_driver_list` | POST | — | `p051G2/C0596a.java` |
| `/v6/fleet/select_car_driver` | POST | `id`, `service_list` | `p099M2/C0988a.java` |
| `/v6/gps/jwt_update_driver` | POST | `bid`, `lat`, `lng`, `direction`, `speed`, `lon`, `city_id`, `ct` | `p187X2/C1936b.java` |
| `/v6/gps/update_driver` | POST | `bid`, `lat`, `lng`, `direction`, `speed`, `lon`, `city_id`, `ct` | `p187X2/C1936b.java` |
| `/v6/integrity/verify` | POST | `nonce`, `token`, `error_message`, `platform` | `p115O2/C1137a.java` |
| `/v6/member/create_login_token` | POST | — | `p123P2/C1215a.java` |
| `/v6/octopus/remove_driver_auth` | POST | `city_id`, `driver_auth_id` | `p131Q2/C1301a.java` |
| `/v6/octopus/request_card_payment` | POST | `amount`, `bid` | `p131Q2/C1301a.java` |
| `/v6/octopus/request_driver_linkage` | POST | — | `p131Q2/C1301a.java` |
| `/v6/octopus/update_driver_linkage` | POST | `hashed_driver_id`, `mertAccRef` | `p131Q2/C1301a.java` |
| `/v6/payme/add_driver_auth` | POST | `city_id`, `driver_misc` | `p155T2/C1578a.java` |
| `/v6/payme/edit_driver_auth` | POST | `city_id`, `driver_auth_id`, `driver_misc` | `p155T2/C1578a.java` |
| `/v6/payme/remove_driver_auth` | POST | `city_id`, `driver_auth_id` | `p155T2/C1578a.java` |
| `/v6/user/get_user_complain_review_lock` | POST | `id`, `admin_did`, `date_up`, `city_id` | `p059H2/C0651a.java` |
| `/v6/user/update_user_complain_review_release` | POST | `id`, `suspend_reason_type`, `suspend_reason`, `suspend_by`, `suspend_date_force_lock`, `release_reason_type`, `release_reason`, `release_by`, `city_id` | `p059H2/C0651a.java` |
| `/v6/user/update_user_complain_review_suspend` | POST | `id`, `suspend_reason_type`, `suspend_reason`, `suspend_by`, `suspend_date_force_lock`, `city_id` | `p059H2/C0651a.java` |
| `/v6/wechat/add_driver_auth` | POST | `city_id` | `p203Z2/C2051a.java` |

<details><summary>DTO field lists</summary>

**LoginItem** (33)
```
  access_token: String
  android_version_driver: String
  announcement_link: String
  car_number: String
  city_id: String
  config_version: String
  date_cr: Date
  date_deprecation: Date
  date_last_login: Date
  date_up: Date
  driver_auth: List<DriverAuthItem>
  driver_doc: List<DriverDocTypeItem>
  driver_groups: List<DriverGroupItem>
  driver_jobs: List<DriverJobItem>
  driver_option: List<DriverOptionItem>
  driver_plan: DriverPlanItem
  driver_role: DriverRoleItem
  email: String
  fcm_id: String
  gps_booking_display_info: String
  group_create_num: String
  group_join_num: String
  lang: String
  name: String
  server_time: Date
  service_person_id: String
  show_refund_tab: boolean
  status: String
  status_takeb: String
  tel: String
  tel_verified: String
  uber: UberLoginItem
  user_token: String
```
**BatchInfoItem** (6)
```
  driver_app_banner: DriverAppBannerItem
  driver_info: DriverInfoItem
  driver_plan_point_energy_bar: DriverPlanPointItem
  driver_suspend_info: List<DriverSuspendInfoItem>
  online_status: OnlineStatusItem
  server_time: Date
```
**AllOrderItem** (8)
```
  bookZipDate: String
  certificateList: List<CertificateItem>
  deviceDate: String
  displayList: List<Object>
  gapOffset: long
  orderDisplayMode: EnumC6194i
  orderList: List<Object>
  rejectList: List<Object>
```
**OrderItem** (87)
```
  amount_driver: String
  amount_fare: String
  app_driver_list: String
  bookCharge: BillDetailItem
  bookChargeId: String
  booking_id: String
  carOptionInputList: List<InputTypeItem>
  city_id: String
  currency_id: String
  date_acc: Date
  date_cancel: Date
  date_complete_ride: Date
  date_cr: Date
  date_cr_diff: String
  date_driver_arrive_pickup: Date
  date_exp: Date
  date_pickup: Date
  date_start_ride: Date
  date_up: Date
  dcr: Date
  did: String
  diffTime: long
  direction_id: String
  distance: String
  distanceItem: DistanceItem
  district_offer_id: String
  dpu: Date
  driver_car_number: String
  driver_lat: double
  driver_lng: double
  driver_name: String
  driver_role: String
  driver_tel: String
  dropoff1_address: String
  dropoff1_lat: String
  dropoff1_lon: String
  dropoff2_address: String
  dropoff2_lat: String
  dropoff2_lon: String
  dup: Date
  estimateDistance: double
  estimateDuration: double
  estimatePointItemList: List<EstimateRouteItem>
  fareCarOptionList: List<FareOptionItem>
  fareItemList: List<FareItem>
  fewMeterDistance: int
  gid: String
  give_driver_id: String
  give_driver_name: String
  give_driver_tel: String
  groupName: String
  group_id: String
  isDisplayConfirmLayout: boolean
  isFewMeterOrder: boolean
  isManyMeterOrder: boolean
  isPlayNearBySound: boolean
  isRadiusMeterOrder: boolean
  is_lock: String
  json_fleet: JsonFleetItem
  log: String
  manyMeterDistance: int
  oldOpt: String
  opt: String
  option: String
  orderConfirmList: List<BillDetailItem>
  orderDetailOptionItem: List<OrderDetailOptionItem>
  orderMode: String
  pickup_address: String
  pickup_lat: String
  pickup_lon: String
  plat: String
  plon: String
  radiusExtendArea: float
  radiusMeterDistance: int
  remark: String
  rider_lat: String
  rider_lon: String
  rmk: String
  service: FleetCarServiceItem
  status: String
  take_driver_id: String
  take_driver_name: String
  take_driver_tel: String
  tel: String
  tunnel: String
  uid: String
  user_type: String
```
**AcceptOrderItem** (7)
```
  access_token: String
  app: int
  bid: int
  gps_list: ArrayList<LocationItem>
  lang: int
  log_list: ArrayList<LogItem>
  service_id: int
```
**DriverGpsItem** (2)
```
  lat: double
  lng: double
```
**OnlineStatusItem** (6)
```
  active_drivers: String
  total_accepted: String
  total_bookings: String
  traffic: String
  traffic_desc: String
  traffic_desc_html: String
```
**TimetableDetailItem** (4)
```
  close: String
  date_up: Date
  off: String
  open: String
```
</details>

## Gaps (explicit unknowns)

- Per-endpoint **response DTO** is not yet linked 1:1: the request classes return raw `JSONObject`; parsing happens in the calling Activity/Fragment. Call-site mining is the next step.
- Error envelope / status codes not yet extracted (`ResponseStatus` model exists in both apps).
- Whether the server validates DPoP `htu` strictly is unknown (static analysis cannot answer it).
- Rate limits, pagination and `book.status` enum values need call-site extraction or runtime capture.


## Passive-capture plan (no order creation)

These calls are observable without creating, accepting or cancelling an order — matching the
"capture data only" scope:

| app | call | why it is safe/valuable |
|---|---|---|
| both | `/v6/api/get_config_sqlite` | cold start; confirms server config version + tunables |
| both | `/v6/driver/login`, `/v6/member/create_login_token`, `/v6/user/create_login_token` | shows token shape, `server_jwt_token`, DPoP header, integrity chain |
| both | `/v6/device/integrity_challenge_test`, `/v6/device/jwt_authorization`, `/v6/integrity/verify` | proves how the challenge/nonce flow works |
| pax | `/v6/gps/get_user_nearby_drivers` | nearby-driver payload + refresh cadence |
| pax | `/v6/book/get_book_autocomplete`, `/v6/book/get_book_address_nearby` | geo/address search payloads |
| pax | `/v6/book/get_book_direction` | route + polyline + distance/duration shape |
| pax | `/v6/api/fare2`, `/v6/fleet/fare` | fare engine input/output (estimate only, no booking) |
| drv | `/v6/driver/batch_info2` | driver home snapshot (online status, plan, banner) |
| drv | `/v6/book/list` | dispatch queue payload — observe only, never `accept` |
| drv | `/v6/gps/update_driver` | heartbeat cadence + what telemetry is uploaded |

Deliberately **not** in scope: `book/create`, `book/accept`, `book/lock`, `book_start_ride`,
`book_complete_ride`, `driver_arrived_*`, any `*/cancel`, any payment call.

---

**See also:** `docs/response-map.md` (per-endpoint response DTOs, `book.status` state machine,
error codes) and `docs/manual-observation.md` (Plan C: what to record by hand on an unrooted phone).
