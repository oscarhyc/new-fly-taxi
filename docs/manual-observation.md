# Plan C — manual observation checklist (no root, no order)

You are the sensor here. No proxy, no root, no automation. Everything below is what static
analysis **cannot** see: real timing, real payload values shown in UI, real error codes.

Rules: only your own accounts, only passive screens, never tap 確認叫車 / 接單 / 取消 / 付款.

---

## 0. What to prepare

- Phone: XQ-FE72 (Xperia 10 VII), stock, signed into your own Play account, both apps installed
  (`com.flytaxi.hktaxi` = passenger, `com.flytaxi` = driver).
- Screenshot hotkey ready. Name files `NN_description.png` in order.
- A note with: date, time, app version (設定 → 關於 shows build 191 / 356), network (Wi-Fi / 4G).
- Second phone or take turns for the driver app (one device is fine — just note which role).

## 1. Passenger — cold start & login (no booking)

| # | do | record |
|---|---|---|
| 1 | Force stop, launch, **watch the first screen** | time from tap to first frame; is there a splash `SplashScreenActivity`; any "更新" dialog |
| 2 | If an update dialog appears | screenshot; exact version numbers offered |
| 3 | Login screen | screenshot every field; is login by phone OTP, Facebook AccountKit, or SSO? any "沒有香港電話號碼" path (tourist flow) |
| 4 | Tap the OTP request | **how many digits**, countdown seconds, resend cooldown |
| 5 | Log in | landing screen; any permission prompts in order (notification / location / phone) |
| 6 | Deliberately trigger a login error (wrong OTP once) | **screenshot the dialog** — it prints `status_code` + `message` (this is the error-code decoder) |

## 2. Passenger — map, search, estimate (still no booking)

| # | do | record |
|---|---|---|
| 7 | Stay on map 60 s, note the cars | do nearby cars move without refresh? how often? |
| 8 | Search an address, then a landmark (e.g. 機場) | autocomplete list size, what fields show (address / district / distance?) |
| 9 | Set pickup + dropoff | does the route line appear before or after fare? |
| 10 | Open the fare/estimate panel | **screenshot the itemised breakdown**: 起步價 / 里程 / 隧道費 / 附加費 / 預估總額. These names map to `FareItem` fields |
| 11 | Change car type (紅的 / 綠的 / 藍的 / 6 座 / 輪椅 / 混能) | which options appear/disappear; does the estimate change and by how much |
| 12 | Open the 隧道 (tunnel) selector | options + the +$25 return-toll note — screenshot |
| 13 | Open 選項 (options) | list of options and their defaults (`car_option` table has 48 rows — check which are actually shown) |
| 14 | Pick a **scheduled** pickup time | allowed range (how far ahead?), "15 minutes before" auto-assign note |
| 15 | Start a company/fixed price quote | what is shown; do NOT confirm |
| 16 | Back out | is there an "are you sure" dialog with a fee warning? screenshot the text |

## 3. Passenger — read-only corners

| # | screen | record |
|---|---|---|
| 17 | 訂單紀錄 (history) | row layout, status labels shown, what a cancelled vs expired row looks like |
| 18 | 錢包 / 交易紀錄 | currency, transaction types, reload cadence (config says 5 s) |
| 19 | 邀請 (referral) | reward wording, eligibility text |
| 20 | 條款 / 私隱 | note version/date shown |
| 21 | 客服 / 投訴 | **the complaint type list** (maps to `user_complain_type` table) — screenshot |
| 22 | 失物 (lost & found) | what it asks for |

## 4. Driver — without going on duty

| # | do | record |
|---|---|---|
| 23 | Launch driver app | integrity/root screen? any `RootDeviceActivity` / `IntegrityFailActivity`? screenshot |
| 24 | Log in | what the login asks (車牌? 密碼?) |
| 25 | Do NOT go online | home screen: what counters show (`active_drivers`, `total_bookings`, `traffic`) |
| 26 | Open 計算機 (Calculator) | meter rules — screenshot every row; compare with `ConfigHKTaxiMeterPriceItem` |
| 27 | Open 公告 / banner | announcements text + link |
| 28 | Open 支付設定 (Octopus / PayMe / WeChat / Alipay) | which are linked, what the linking flow asks — screenshot, do not complete |
| 29 | Open 司機資料 / 文件 (docs) | doc types listed (`driver_doc_type`) — screenshot |
| 30 | Receive an FCM push (banner/announcement) while idle | **exact notification title + body**; tap it and note which screen opens |

## 5. Timing table (the part only you can measure)

Fill this in — it validates the config values I extracted:

| quantity | static value | measured |
|---|---|---|
| accept countdown (driver) | `takeb_countdown_sec` = 5 / 20 | |
| nearby-driver refresh | unknown | |
| order status poll interval | unknown | |
| GPS heartbeat idle | 600 s | |
| GPS heartbeat on order | 20–60 s | |
| cancel fee | HKD 6 | |
| min company price (red / green) | 80 / 70 | |
| arrival radius warning | 800–1000 m pickup / 1800–2000 m dropoff | |

## 6. How to hand it back

Put screenshots + a filled copy of the tables into `~/rea-apk/manual/` (or just paste them in chat,
one screen per message). I will convert each one into a line in `docs/api-contract.md` /
`docs/response-map.md` marked as **observed in UI**, separate from the static claims.

## 7. Explicitly out of scope

- Tapping 確認叫車 / 接單 / 取消 / 付款 (no orders, per your instruction).
- Any automation, mock GPS, accessibility auto-accept, or API calls by hand.
- Sharing full screenshots that contain your phone number, plate, or token — crop those.
