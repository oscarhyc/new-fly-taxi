#!/usr/bin/env bash
# new-fly-taxi — passive capture of ONE driver session, on YOUR machine.
#
# What it does:
#   1. starts mitmproxy with the redacting addon (captures keys/structure, masks PII)
#   2. prints the phone proxy settings to apply
#   3. waits for you to drive one trip manually in the app
#   4. writes captures/<timestamp>.jsonl (already masked) and keeps flows.mitm locally
#
# It does NOT create orders, does not accept orders, does not automate anything.
# You tap. The script only records what your own app session already sends.
#
# Prerequisites (pick ONE):
#   A) rooted phone  — CA must be in the SYSTEM store (the app trusts only src="system")
#      magisk module "MagiskTrustUserCerts" (or move the cert manually), then reboot
#   B) unrooted      — TLS bodies cannot be decrypted; skip this script and use
#      docs/manual-observation.md (screenshots + timings) instead
#
# Usage:  ./run-capture.sh
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
PORT="${CAPTURE_PORT:-8087}"
OUT="$ROOT/captures"
mkdir -p "$OUT"

command -v mitmdump >/dev/null 2>&1 || {
  echo "mitmproxy not found. Install it with:  pipx install mitmproxy  (or: pip install --user mitmproxy)"
  exit 1
}

TS="$(date +%Y%m%d-%H%M%S)"
STREAM="$OUT/flows-$TS.mitm"

cat <<EOF

=== new-fly-taxi capture ===

1) On your phone, set the Wi-Fi proxy to this computer:
       IP:   $(hostname -I 2>/dev/null | awk '{print $1}')
       PORT: $PORT
   (or: adb shell settings put global http_proxy $(hostname -I 2>/dev/null | awk '{print $1}'):$PORT)

2) The app only trusts SYSTEM certificates, so the mitmproxy CA must already be
   installed as a system CA. If the app shows a TLS error, stop here and either
   root the phone or use the manual observation checklist instead.

3) Open the driver app, log in with YOUR OWN account, and perform ONE trip manually
   (accept -> arrive -> start -> complete). Do not let this script drive anything.

4) Press Ctrl-C here when you are done.

Output: $OUT/capture-$TS.jsonl  (PII already masked)
EOF

mitmdump -s "$HERE/capture-addon.py" --listen-port "$PORT" \
         --save-stream-file "$STREAM" 2>&1 | tee "$OUT/mitm-$TS.log"

echo
echo "Done. Redacted capture: $OUT/capture-*.jsonl"
echo "Raw flow file (keep local, contains tokens): $STREAM"
