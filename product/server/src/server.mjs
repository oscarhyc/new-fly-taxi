// new-fly-taxi — reference server (driver side), zero external dependencies.
//
// Implements OUR contract (product/spec/openapi.yaml). Field names mirror the
// observed wire schema so the Android client we write can be pointed at either
// this server or a production backend without changing DTOs.
//
// Run:  node product/server/src/server.mjs [--port 8787]
// Test: node product/server/test/lifecycle.test.mjs

import http from 'node:http';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const PORT = Number(process.argv.includes('--port')
  ? process.argv[process.argv.indexOf('--port') + 1] : 8787);

// ---------------------------------------------------------------- tunables
// Seeded from the tunables recovered in the shipped app's config database
// (product/spec/state-machine.md has the provenance table).
export const CONFIG = {
  dispatch_radius_m: 1500,          // pickup_nearby_drivers_radius
  accept_countdown_s: 20,           // takeb_countdown_sec (5 for 'take b' orders)
  accept_lock_s: 30,                // how long an accept hold survives
  gps_idle_interval_s: 600,         // gps_update_normal_sec
  gps_on_order_interval_s: 20,      // gps_update_accepted_book_sec
  gps_lost_signal_warn_s: 180,      // warning_driver_lose_signal_mins
  gps_lost_signal_error_s: 300,     // error_driver_lose_signal_mins
  cancel_fee_hkd: 6,                // book_accept_cancel_fee
  // driver behaviour risk caps — ratios of terminal events over a rolling window
  risk_window: 50,
  risk_caps: {
    cancel: 0.30,                  // driver_general.cancel_ratio_max
    late: 0.34,                    // driver_general.driver_too_late_ratio_max
    no_signal: 0.21,               // driver_general.driver_lose_signal_ratio_max
    rider_cancel: 0.18,            // driver_general.rider_cancel_ratio_max
    no_show: 0.14,                 // driver_general.no_show_ratio_max
    manual_complete: 0.20,         // driver_general.complete_ride_manual_ratio_max
  },
  company_price_floor: { red_taxi: 80, green_taxi: 70 },
  arrive_pickup_max_radius_m: 1000, // book_accept_driver_arrive_pickup_max_radius
  arrive_dropoff_max_radius_m: 2000,// book_accept_driver_arrive_dropoff1_max_radius
};

// HK published taxi tariffs (Transport Department). Meter tariffs only.
const TARIFF = {
  red_taxi:  { first_km: 1.3, first: 27, per_200m: 2.3 },
  blue_taxi: { first_km: 1.3, first: 27, per_200m: 2.3 },
  green_taxi:{ first_km: 1.3, first: 22, per_200m: 1.9 },
};
const TUNNEL_FEE_HKD = 25;   // app string: confirm_order_tunnel_return_fee_* = 25

// ---------------------------------------------------------------- store
const store = {
  drivers: new Map(),      // id -> driver
  byTel: new Map(),        // tel -> driver
  tokens: new Map(),       // token -> driverId
  orders: new Map(),       // bid -> order
  gps: new Map(),          // driverId -> {lat,lng,at}
  risk: new Map(),         // driverId -> [{kind, at}]
  logs: [],
};

/* ------------------------------------------------------- driver risk engine
 * The reference app enforces per-driver behaviour ratios (extracted from its
 * live config DB). A driver above a cap loses the right to take bookings.
 */
const RISK_KINDS = ['cancel', 'late', 'no_signal', 'rider_cancel', 'no_show', 'manual_complete', 'completed', 'expired'];

function recordRisk(driverId, kind) {
  if (!driverId || !RISK_KINDS.includes(kind)) return;
  const list = store.risk.get(driverId) || [];
  list.push({ kind, at: Date.now() });
  while (list.length > CONFIG.risk_window) list.shift();
  store.risk.set(driverId, list);
}

function riskProfile(driverId) {
  const list = store.risk.get(driverId) || [];
  const total = list.filter((e) => e.kind !== 'completed' && e.kind !== 'expired').length;
  const ratios = {};
  for (const kind of Object.keys(CONFIG.risk_caps)) {
    const n = list.filter((e) => e.kind === kind).length;
    ratios[kind] = total ? Number((n / total).toFixed(4)) : 0;
  }
  const breaches = Object.entries(ratios)
    .filter(([k, v]) => v > CONFIG.risk_caps[k])
    .map(([k, v]) => ({ kind: k, ratio: v, cap: CONFIG.risk_caps[k] }));
  return { ratios, caps: CONFIG.risk_caps, window: CONFIG.risk_window, events: total, breaches, restricted: breaches.length > 0 };
}

export const ORDER_STATUS = {
  NEW: '11', CANCEL: '12', EXPIRY: '13', ACCEPTED: '14',
  TO_PICKUP: '21', START_RIDE: '31', TO_DROPOFF: '41', COMPLETE: '51',
  DRIVER_LATE: '22', DRIVER_NO_SIGNAL: '24', USER_CANCEL: '25', DRIVER_CANCEL: '26',
  USER_NO_SHOW: '37', COMPLETE_MANUAL: '59',
};
export const STATUS_LABEL = {
  '11': 'New', '12': 'Cancelled', '13': 'Expired', '14': 'Confirmed',
  '21': 'Taxi is coming', '22': 'Driver too late', '24': 'Driver lost signal',
  '25': 'User canceled', '26': 'Driver canceled', '31': 'On the way',
  '32': 'Arrived - driver late', '34': 'Arrived - no signal', '35': 'Arrived - user canceled',
  '36': 'Arrived - driver canceled', '37': 'Arrived - user no show',
  '41': 'On the way to drop-off', '42': 'Ride - driver late', '44': 'Ride - no signal',
  '45': 'Ride - user canceled', '51': 'Completed', '52': 'Drop-off - driver late',
  '54': 'Drop-off - no signal', '59': 'Completed (manual)',
};

const nowIso = () => new Date().toISOString().replace('T', ' ').slice(0, 19);

function seed() {
  const d = {
    id: '1001', tel: '57170655', pwd: 'test', name: '司機 A', car_number: 'WN4881',
    car_type: 'red_taxi', car_group: 'taxi', status: 1, online: false,
    device_id: 'dev-seed-001', lang: 1, city_id: 1, app: 32, v: 356,
    plan: { id: 'p1', title: '[紅] $1500', valid: 1 },
    doc_verified: true,
  };
  store.drivers.set(d.id, d);
  store.byTel.set(d.tel, d);

  // three seeded bookings around Tuen Mun (Mira Place) — statuses 11 (new)
  const seeds = [
    { bid: '900001', pickup_address: 'Mira Place, 屯門', lat: 22.3089203, lon: 114.157572,
      dropoff_address: '屯門市中心', dlat: 22.3822909, dlon: 113.9812064, ct: '1' },
    { bid: '900002', pickup_address: '屯門 V City', lat: 22.3160800, lon: 113.9735300,
      dropoff_address: '荃灣西', dlat: 22.3720800, dlon: 114.1140600, ct: '1' },
    { bid: '900003', pickup_address: '兆康站', lat: 22.4087000, lon: 113.9820000,
      dropoff_address: '青衣城', dlat: 22.2846800, dlon: 114.1096100, ct: '1' },
    { bid: '900004', pickup_address: '屯門市中心', lat: 22.3095000, lon: 114.1600000,
      dropoff_address: '良景邨', dlat: 22.3150000, dlon: 114.1500000, ct: '1' },
  ];
  for (const s of seeds) {
    store.orders.set(s.bid, {
      ...s, status: ORDER_STATUS.NEW, dcr: nowIso(), dup: nowIso(),
      dpu: nowIso(), distance: 0, driver_id: null, locked_until: 0,
      service: { at: 0, pm: '1' },
    });
  }
}

// ---------------------------------------------------------------- helpers
const ok = (data, message = '成功') => ({ status_code: '200', message, data });
const err = (code, message, data = null) => ({ status_code: String(code), message, data });
const httpStatusFor = (code) => ({ '200': 200, '101': 400, '105': 422, '2001': 401,
  '2002': 401, '2003': 401, '2004': 401, '2005': 401, '2401': 502, '2416': 422 }[String(code)] || 400);

const haversine = (aLat, aLon, bLat, bLon) => {
  const R = 6371000, toRad = (x) => (x * Math.PI) / 180;
  const dLat = toRad(bLat - aLat), dLon = toRad(bLon - aLon);
  const s = Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(aLat)) * Math.cos(toRad(bLat)) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(s));
};

export function estimateFare(ct, distanceM, extra = {}) {
  const t = TARIFF[ct] || TARIFF.red_taxi;
  const over = Math.max(0, distanceM - t.first_km * 1000);
  const steps = Math.ceil(over / 200);
  let amount = t.first + steps * t.per_200m;
  const toll = extra.tunnel ? TUNNEL_FEE_HKD : 0;
  amount += toll;
  const floor = CONFIG.company_price_floor[ct];
  return {
    amount_fare: Number(amount.toFixed(2)),
    amount_operation: 0,
    amount_company_price: Number(Math.max(floor, amount * 0.85).toFixed(2)),
    amount_driver: Number(amount.toFixed(2)),
    currency_id: '1', car_type: ct, distance: distanceM, tunnel: extra.tunnel ? 1 : 0,
    summary: [
      { title: '車資', charge: 'meter', amount_fare: Number((amount - toll).toFixed(2)) },
      ...(toll ? [{ title: '隧道費', charge: 'tunnel', amount_fare: toll }] : []),
    ],
  };
}

function bearer(req) {
  const h = req.headers.authorization || '';
  return h.startsWith('Bearer ') ? h.slice(7) : null;
}
function requireDriver(req, res) {
  const tok = bearer(req);
  const id = tok && store.tokens.get(tok);
  if (!id) { res.writeHead(401, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(err('2001', '數據驗證出現問題, 請重新登入'))); return null; }
  return store.drivers.get(id);
}
function body(req) {
  return new Promise((resolve) => {
    let raw = '';
    req.on('data', (c) => { raw += c; if (raw.length > 1e6) req.destroy(); });
    req.on('end', () => {
      if (!raw) return resolve({});
      const ct = req.headers['content-type'] || '';
      try {
        if (ct.includes('json')) return resolve(JSON.parse(raw));
        if (ct.includes('x-www-form-urlencoded')) return resolve(Object.fromEntries(new URLSearchParams(raw)));
      } catch { /* fall through */ }
      resolve({});
    });
  });
}
const need = (b, keys) => keys.filter((k) => b[k] === undefined || b[k] === '');

// ---------------------------------------------------------------- routes
const routes = [];
const route = (method, path, handler) => routes.push({ method, path, handler });

route('POST', '/api/v1/auth/login', async (req, res) => {
  const b = await body(req);
  const miss = need(b, ['tel', 'pwd']);
  if (miss.length) return send(res, 400, err('101', '參數不足夠', { missing: miss }));
  const d = store.byTel.get(String(b.tel));
  if (!d || d.pwd !== String(b.pwd)) return send(res, 401, err('2402', '電話號碼或密碼不正確'));
  const token = crypto.randomBytes(24).toString('hex');
  store.tokens.set(token, d.id);
  d.device_id = b.device_id || d.device_id;
  d.online = false;
  return send(res, 200, ok({
    id: d.id, name: d.name, tel: d.tel, car_number: d.car_number, car_type: d.car_type,
    car_group: d.car_group, status: d.status, plan: d.plan, doc_verified: d.doc_verified,
    access_token: token, login_key: crypto.randomBytes(16).toString('hex'),
    server_time: nowIso(), config_version: '1', app: d.app, v: d.v, lang: d.lang, city_id: d.city_id,
  }));
});

route('GET', '/api/v1/config', (req, res) => send(res, 200, ok(CONFIG)));

route('POST', '/api/v1/driver/online', async (req, res) => {
  const d = requireDriver(req, res); if (!d) return;
  const b = await body(req);
  d.online = b.online !== false && b.online !== 'false';
  return send(res, 200, ok({ id: d.id, online: d.online, server_time: nowIso() }));
});

route('POST', '/api/v1/driver/gps', async (req, res) => {
  const d = requireDriver(req, res); if (!d) return;
  const b = await body(req);
  const miss = need(b, ['lat', 'lng']); if (miss.length) return send(res, 400, err('101', '參數不足夠'));
  store.gps.set(d.id, { lat: Number(b.lat), lng: Number(b.lng), at: Date.now(), speed: Number(b.speed || 0) });
  const o = b.bid ? store.orders.get(String(b.bid)) : null;
  if (o && o.driver_id === d.id) o.last_gps = nowIso();
  return send(res, 200, ok({ server_time: nowIso(), accepted: true }));
});

route('POST', '/api/v1/orders/available', async (req, res) => {
  const d = requireDriver(req, res); if (!d) return;
  const b = await body(req);
  const miss = need(b, ['lat', 'lng']); if (miss.length) return send(res, 400, err('101', '參數不足夠'));
  const lat = Number(b.lat), lng = Number(b.lng);
  const out = [];
  for (const o of store.orders.values()) {
    if (o.status !== ORDER_STATUS.NEW) continue;
    const dist = haversine(lat, lng, o.lat, o.lon);
    if (dist > CONFIG.dispatch_radius_m) continue;
    out.push({
      id: o.bid, status: o.status, dcr: o.dcr, dpu: o.dpu,
      pickup_address: o.pickup_address, pickup_lat: o.lat, pickup_lon: o.lon,
      dropoff1_address: o.dropoff_address, dropoff1_lat: o.dlat, dropoff1_lon: o.dlon,
      ct: o.ct, distance: Math.round(dist), service: o.service,
    });
  }
  out.sort((a, b2) => a.distance - b2.distance);
  return send(res, 200, ok(out));
});

function transition(res, bid, from, to, extra = {}) {
  const o = store.orders.get(String(bid));
  if (!o) return send(res, 404, err('2401', '找不到訂單'));
  if (o.status !== from) {
    return send(res, 409, err('2403', `訂單狀態已變更 (current=${o.status})`, { status: o.status }));
  }
  o.status = to; o.dup = nowIso();
  Object.assign(o, extra);
  return send(res, 200, ok({ id: o.bid, status: o.status, status_label: STATUS_LABEL[o.status], dup: o.dup, ...extra }));
}

route('GET', '/api/v1/driver/risk', (req, res) => {
  const d = requireDriver(req, res); if (!d) return;
  return send(res, 200, ok(riskProfile(d.id)));
});

route('POST', '/api/v1/orders/report', async (req, res) => {
  const d = requireDriver(req, res); if (!d) return;
  const b = await body(req);
  const miss = need(b, ['bid', 'kind']); if (miss.length) return send(res, 400, err('101', '參數不足夠'));
  const o = store.orders.get(String(b.bid));
  if (!o || o.driver_id !== d.id) return send(res, 403, err('2406', '無權操作此訂單'));
  const kind = String(b.kind);
  recordRisk(d.id, kind);
  const map = { late: ORDER_STATUS.DRIVER_LATE, no_signal: ORDER_STATUS.DRIVER_NO_SIGNAL,
                rider_cancel: ORDER_STATUS.USER_CANCEL, no_show: ORDER_STATUS.USER_NO_SHOW,
                manual_complete: ORDER_STATUS.COMPLETE_MANUAL };
  if (map[kind] && o.status !== ORDER_STATUS.COMPLETE) {
    o.status = map[kind]; o.dup = nowIso();
  }
  return send(res, 200, ok({ id: o.bid, status: o.status, risk: riskProfile(d.id) }));
});

route('POST', '/api/v1/orders/accept', async (req, res) => {
  const d = requireDriver(req, res); if (!d) return;
  const b = await body(req);
  const miss = need(b, ['bid']); if (miss.length) return send(res, 400, err('101', '參數不足夠'));
  const risk = riskProfile(d.id);
  if (risk.restricted) {
    return send(res, 403, err('2407', '帳戶行為比例超出上限，已暫停接單', risk.breaches));
  }
  const o = store.orders.get(String(b.bid));
  if (!o) return send(res, 404, err('2401', '找不到訂單'));
  if (o.status !== ORDER_STATUS.NEW) {
    return send(res, 409, err('2404', '訂單已被接走', { status: o.status }));
  }
  const g = store.gps.get(d.id);
  if (g) {
    const dist = haversine(g.lat, g.lng, o.lat, o.lon);
    if (dist > CONFIG.arrive_pickup_max_radius_m) {
      return send(res, 422, err('2405', '距離接載點太遠', { distance: Math.round(dist) }));
    }
  }
  o.driver_id = d.id;
  o.did = d.id;
  recordRisk(d.id, 'accepted');
  o.locked_until = Date.now() + CONFIG.accept_lock_s * 1000;
  o.distance = o.distance || 0;
  return transition(res, o.bid, ORDER_STATUS.NEW, ORDER_STATUS.ACCEPTED,
    { driver_name: d.name, driver_car_number: d.car_number, driver_lat: g?.lat, driver_lng: g?.lng });
});

route('POST', '/api/v1/orders/arrived_pickup', async (req, res) => {
  const d = requireDriver(req, res); if (!d) return;
  const b = await body(req);
  const o = store.orders.get(String(b.bid));
  if (!o || o.driver_id !== d.id) return send(res, 403, err('2406', '無權操作此訂單'));
  return transition(res, b.bid, ORDER_STATUS.ACCEPTED, ORDER_STATUS.TO_PICKUP);
});

route('POST', '/api/v1/orders/start_ride', async (req, res) => {
  const d = requireDriver(req, res); if (!d) return;
  const b = await body(req);
  const o = store.orders.get(String(b.bid));
  if (!o || o.driver_id !== d.id) return send(res, 403, err('2406', '無權操作此訂單'));
  return transition(res, b.bid, ORDER_STATUS.TO_PICKUP, ORDER_STATUS.START_RIDE);
});

route('POST', '/api/v1/orders/arrived_dropoff', async (req, res) => {
  const d = requireDriver(req, res); if (!d) return;
  const b = await body(req);
  const o = store.orders.get(String(b.bid));
  if (!o || o.driver_id !== d.id) return send(res, 403, err('2406', '無權操作此訂單'));
  return transition(res, b.bid, ORDER_STATUS.START_RIDE, ORDER_STATUS.TO_DROPOFF);
});

route('POST', '/api/v1/orders/complete', async (req, res) => {
  const d = requireDriver(req, res); if (!d) return;
  const b = await body(req);
  const o = store.orders.get(String(b.bid));
  if (!o || o.driver_id !== d.id) return send(res, 403, err('2406', '無權操作此訂單'));
  const g = store.gps.get(d.id);
  const dist = g ? haversine(o.lat, o.lon, o.dlat, o.dlon) : (o.distance || 0);
  const fare = estimateFare(o.ct || '1', dist, { tunnel: !!b.tunnel });
  o.fare = fare; o.distance = Math.round(dist);
  recordRisk(d.id, 'completed');
  return transition(res, b.bid, ORDER_STATUS.TO_DROPOFF, ORDER_STATUS.COMPLETE, { fare });
});

route('POST', '/api/v1/orders/cancel', async (req, res) => {
  const d = requireDriver(req, res); if (!d) return;
  const b = await body(req);
  const o = store.orders.get(String(b.bid));
  if (!o || o.driver_id !== d.id) return send(res, 403, err('2406', '無權操作此訂單'));
  const to = ORDER_STATUS.DRIVER_CANCEL;
  recordRisk(d.id, 'cancel');
  return transition(res, b.bid, o.status, to);
});

route('GET', '/api/v1/orders/:bid', (req, res) => {
  const d = requireDriver(req, res); if (!d) return;
  const o = store.orders.get(req.params.bid);
  if (!o) return send(res, 404, err('2401', '找不到訂單'));
  return send(res, 200, ok({ ...o, status_label: STATUS_LABEL[o.status] }));
});

function send(res, httpCode, payload) {
  res.writeHead(httpCode, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify(payload));
}

// ---------------------------------------------------------------- server
export function createServer() {
  seed();
  return http.createServer(async (req, res) => {
    const url = new URL(req.url, 'http://x');
    let handler = null; const params = {};
    for (const r of routes) {
      if (r.method !== req.method) continue;
      if (!r.path.includes(':')) { if (r.path === url.pathname) { handler = r; break; } continue; }
      const names = [];
      const rx = new RegExp('^' + r.path.replace(/:([A-Za-z0-9_]+)/g, (_, n) => { names.push(n); return '([^/]+)'; }) + '$');
      const m = url.pathname.match(rx);
      if (m) { handler = r; names.forEach((n, i) => { params[n] = decodeURIComponent(m[i + 1]); }); break; }
    }
    if (!handler) return send(res, 404, err('404', 'Not Found'));
    req.params = params;
    try { await handler.handler(req, res); }
    catch (e) { send(res, 500, err('500', String(e && e.message || e))); }
  });
}

if (import.meta.url === `file://${process.argv[1]}`) {
  createServer().listen(PORT, () => console.log(`new-fly-taxi reference server on :${PORT}`));
}
