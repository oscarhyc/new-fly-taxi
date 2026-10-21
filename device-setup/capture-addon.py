"""mitmproxy addon: contract-focused, PII-redacting capture for FlyTaxi.

Usage:
  mitmdump -s capture-addon.py --listen-port 8080 --save-stream-file flows.mitm

Writes ~/rea-apk/captures/<timestamp>.jsonl with one line per request:
  {ts, host, method, path, status, req_keys, req_sample, resp_keys, resp_shape, notes}

Never stores full bodies. Coordinates, phone numbers, tokens and names are replaced
by <redacted>. Add path-specific overrides in PATHS if a field must be kept.
"""

import json
import os
import re
import time
from mitmproxy import http, ctx

OUT_DIR = os.path.expanduser("~/rea-apk/captures")
# Hosts to capture. Override with REA_CAPTURE_HOSTS (regex), or REA_CAPTURE_ALL=1 to
# capture every host (useful when testing against a local server).
HOSTS = os.environ.get("REA_CAPTURE_HOSTS", r"(flytaxiapp\.com|cargrid-hk-release-config\.s3\.ap-east-1\.amazonaws\.com)$")
INTERESTING = re.compile(os.environ.get("REA_CAPTURE_ALL") == "1" and r".*" or HOSTS)
REDACT_KEYS = {
    "tel", "customer_tel", "driver_tel", "mobile", "phone", "pwd", "password",
    "access_token", "login_key", "server_jwt_token", "login_integrity_id",
    "token", "idToken", "sessionInfo", "sso_token", "fcm_id", "fcmToken",
    "imei", "device_id", "did", "hashed_driver_id", "mertAccRef",
    "lat", "lng", "lon", "driver_lat", "driver_lng", "pickup_lat", "pickup_lon",
    "dropoff1_lat", "dropoff1_lon", "dropoff2_lat", "dropoff2_lon",
    "name", "driver_name", "driver_email", "email", "address_name",
    "pickup_address", "dropoff1_address", "dropoff2_addr", "remark",
    "complain_desc", "card_id", "card_ext", "id_number", "doc_number",
}

os.makedirs(OUT_DIR, exist_ok=True)
OUT = os.path.join(OUT_DIR, time.strftime("%Y%m%d-%H%M%S") + ".jsonl")


def shape(v, depth=0):
    """value-free structural summary"""
    if isinstance(v, dict):
        if depth >= 3:
            return "{...}"
        return {k: shape(x, depth + 1) for k, x in v.items()}
    if isinstance(v, list):
        return [shape(v[0], depth + 1)] if v else []
    if isinstance(v, str):
        return "<str:%d>" % len(v)
    if isinstance(v, bool):
        return "<bool>"
    if isinstance(v, (int, float)):
        return "<num>"
    return "<null>"


def scrub(obj):
    if isinstance(obj, dict):
        return {k: ("<redacted>" if k in REDACT_KEYS else scrub(v)) for k, v in obj.items()}
    if isinstance(obj, list):
        return [scrub(x) for x in obj]
    return obj


def parse_body(msg):
    body = msg.get_text(strict=False)
    if not body:
        return None, body
    s = body.strip()
    if s.startswith("{") or s.startswith("["):
        try:
            return json.loads(s), "json"
        except Exception:
            return None, "json-unparsed"
    if "=" in s and "&" in s:  # form-urlencoded
        from urllib.parse import parse_qsl
        return dict(parse_qsl(s)), "form"
    return None, "other"


def write(rec):
    with open(OUT, "a", encoding="utf-8") as fh:
        fh.write(json.dumps(rec, ensure_ascii=False) + "\n")


def request(flow: http.HTTPFlow):
    host = flow.request.pretty_host
    if not INTERESTING.search(host):
        return
    parsed, kind = parse_body(flow.request)
    req_keys = list(parsed.keys()) if isinstance(parsed, dict) else None
    rec = {
        "ts": time.strftime("%Y-%m-%dT%H:%M:%S"),
        "host": host,
        "method": flow.request.method,
        "path": flow.request.path.split("?")[0],
        "req_encoding": kind,
        "req_keys": req_keys,
        "req_sample": scrub(parsed) if isinstance(parsed, (dict, list)) else None,
        "dpop": bool(flow.request.headers.get("dpop")),
        "auth": "bearer" if flow.request.headers.get("authorization") else None,
        "content_type": flow.request.headers.get("content-type"),
    }
    flow.metadata["rea_rec"] = rec


def response(flow: http.HTTPFlow):
    rec = flow.metadata.get("rea_rec")
    if not rec:
        return
    parsed, kind = parse_body(flow.response)
    rec["status"] = flow.response.status_code
    rec["resp_encoding"] = kind
    if isinstance(parsed, dict):
        rec["resp_keys"] = sorted(parsed.keys())
        rec["resp_shape"] = shape(parsed)
        # keep only status-like fields verbatim: they encode the state machine
        keep = {}
        for k, v in parsed.items():
            if re.search(r"(^|_)(status|code|err|msg|message|version)$", k):
                keep[k] = v
        rec["status_fields"] = keep
    write(rec)
    ctx.log.info("captured %s %s -> %s" % (rec["method"], rec["path"], rec["status"]))
