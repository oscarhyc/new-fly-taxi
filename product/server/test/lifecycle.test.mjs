// Contract conformance test: drives the full driver lifecycle against the
// reference server and asserts the state progression and the error envelope.
//
//   node product/server/test/lifecycle.test.mjs
//
// Expected order status progression (see product/spec/state-machine.md):
//   11 New -> 14 Confirmed -> 21 Taxi is coming -> 31 On the way
//        -> 41 To drop-off -> 51 Completed

import assert from 'node:assert/strict';
import { createServer } from '../src/server.mjs';

const server = createServer();
await new Promise((r) => server.listen(0, r));
const base = `http://127.0.0.1:${server.address().port}`;

let token = null;
const call = async (path, body, opts = {}) => {
  const res = await fetch(base + path, {
    method: opts.method || 'POST',
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(opts.headers || {}) },
    body: opts.method === 'GET' ? undefined : JSON.stringify(body || {}),
  });
  const j = await res.json();
  return { http: res.status, ...j };
};

const steps = [];
const step = (name, fn) => steps.push([name, fn]);

step('login rejects a wrong password', async () => {
  const r = await call('/api/v1/auth/login', { tel: '57170655', pwd: 'nope' });
  assert.equal(r.http, 401);
  assert.equal(r.status_code, '2402');
  assert.ok(r.message && r.message.length > 0, 'error message must be present');
});

step('login rejects missing params (101)', async () => {
  const r = await call('/api/v1/auth/login', { tel: '57170655' });
  assert.equal(r.http, 400);
  assert.equal(r.status_code, '101');
});

step('login succeeds and returns a token + driver profile', async () => {
  const r = await call('/api/v1/auth/login', { tel: '57170655', pwd: 'test', device_id: 'dev-test' });
  assert.equal(r.http, 200);
  assert.equal(r.status_code, '200');
  assert.equal(r.data.car_number, 'WN4881');
  assert.ok(r.data.access_token && r.data.server_time);
  token = r.data.access_token;
});

step('unauthenticated call is rejected (2001)', async () => {
  const saved = token; token = null;
  const r = await call('/api/v1/orders/available', { lat: 22.3089, lng: 114.1576 });
  assert.equal(r.http, 401);
  assert.equal(r.status_code, '2001');
  token = saved;
});

step('config exposes the tunables', async () => {
  const r = await call('/api/v1/config', null, { method: 'GET' });
  assert.equal(r.status_code, '200');
  assert.equal(r.data.dispatch_radius_m, 1500);
  assert.equal(r.data.cancel_fee_hkd, 6);
});

step('gps update requires lat/lng (101)', async () => {
  const r = await call('/api/v1/driver/gps', { lat: 22.3089 });
  assert.equal(r.status_code, '101');
});

step('gps heartbeat is accepted', async () => {
  const r = await call('/api/v1/driver/gps', { lat: 22.3089203, lng: 114.157572, speed: 0 });
  assert.equal(r.status_code, '200');
  assert.equal(r.data.accepted, true);
});

step('dispatch list returns nearby new orders only', async () => {
  const r = await call('/api/v1/orders/available', { lat: 22.3089203, lng: 114.157572 });
  assert.equal(r.status_code, '200');
  assert.ok(Array.isArray(r.data) && r.data.length > 0, 'expected at least one nearby order');
  assert.ok(r.data.every((o) => o.status === '11'));
  assert.ok(r.data[0].distance <= 1500, 'dispatch radius honoured');
  globalThis.__bid = r.data[0].id;
});

step('accept out of radius is refused', async () => {
  const far = await call('/api/v1/orders/available', { lat: 22.28468, lng: 114.10961 });
  const r = await call('/api/v1/orders/accept', { bid: far.data[0]?.id || '900003' });
  assert.ok(r.http === 422 || r.status_code === '2404', `expected radius/availability refusal, got ${r.status_code}`);
});

step('lifecycle: accept -> arrived_pickup -> start_ride -> arrived_dropoff -> complete', async () => {
  const bid = globalThis.__bid;
  let r = await call('/api/v1/orders/accept', { bid });
  assert.equal(r.status_code, '200');
  assert.equal(r.data.status, '14', 'accepted');
  assert.equal(r.data.status_label, 'Confirmed');

  r = await call('/api/v1/orders/arrived_pickup', { bid });
  assert.equal(r.data.status, '21', 'taxi is coming');

  r = await call('/api/v1/orders/start_ride', { bid });
  assert.equal(r.data.status, '31', 'on the way');

  r = await call('/api/v1/orders/arrived_dropoff', { bid });
  assert.equal(r.data.status, '41', 'to drop-off');

  r = await call('/api/v1/orders/complete', { bid });
  assert.equal(r.data.status, '51', 'completed');
  assert.ok(r.data.fare.amount_fare > 0, 'fare computed');
  assert.ok(Array.isArray(r.data.fare.summary), 'fare breakdown present');
});

step('double accept is refused with a state conflict', async () => {
  const r = await call('/api/v1/orders/accept', { bid: globalThis.__bid });
  assert.equal(r.http, 409);
  assert.equal(r.status_code, '2404');
});

step('order detail exposes the same status + label', async () => {
  const r = await call(`/api/v1/orders/${globalThis.__bid}`, null, { method: 'GET' });
  assert.equal(r.data.status, '51');
  assert.equal(r.data.status_label, 'Completed');
});

let failed = 0;
for (const [name, fn] of steps) {
  try { await fn(); console.log(`  PASS  ${name}`); }
  catch (e) { failed++; console.log(`  FAIL  ${name}\n        ${e.message}`); }
}
server.close();
console.log(failed ? `\n${failed}/${steps.length} steps FAILED` : `\nall ${steps.length} steps passed`);
process.exit(failed ? 1 : 0);
