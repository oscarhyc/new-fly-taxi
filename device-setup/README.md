# Runtime capture setup (passive only — no order creation)

Goal: observe FlyTaxi traffic to confirm/correct `docs/api-contract.md`.
Scope (agreed): config sync, login, integrity chain, nearby drivers, autocomplete, directions,
fare estimate, driver home snapshot, driver dispatch list. **No** create/accept/lock/start/complete
/ cancel / payment calls.

---

## 0. Why this cannot run on the analysis box

```
systemd-detect-virt -> vmware          (this box is itself a VM)
/dev/kvm            -> missing         (no nested virtualization; cpuinfo has no vmx/svm)
RAM                 -> 3.8 GB total, 2.7 GB free
DISPLAY             -> empty (headless)
```

The Android emulator's x86_64 images require KVM. There is none here, so an Android VM would not
boot in any practical sense. Everything below runs on **your own machine** or **a real phone**.

## 1. The real blocker: Play Integrity (read this before doing anything)

Both apps gate on Play Integrity (`/v6/device/integrity_challenge_test` → `/v6/integrity/verify` →
`/v6/device/jwt_authorization`), and both ship an `IntegrityFailActivity`.

Emulators and most VMs cannot return `MEETS_DEVICE_INTEGRITY`: the verdict is backed by
hardware key attestation and a Google-certified device fingerprint. So the emulator path may stop
at login, no matter how well the proxy is configured. Test that cheaply **before** investing time:

1. Boot the emulator, install the APK, open it, log in.
2. If `IntegrityFailActivity` appears (or login loops), the emulator path is dead for that app —
   fall back to a rooted real device or accept static-only evidence.

## 2. Option A — rooted real phone (highest success rate)

```bash
# phone: enable developer options + USB debugging, then:
adb devices
adb shell getprop ro.build.version.release
adb shell dumpsys package com.flytaxi | grep -E 'versionName|versionCode'
```

Root with Magisk, then:

```bash
# keep integrity passing with a root-hiding/spoofing module
#   Magisk + Zygisk + PlayIntegrityFix (+ Shamiko optional)
# install the mitmproxy CA as a SYSTEM cert (the app trusts only src="system")
pipx install mitmproxy
mitmproxy                                   # first run creates ~/.mitmproxy/mitmproxy-ca-cert.pem
HASH=$(openssl x509 -inform PEM -subject_hash_old -in ~/.mitmproxy/mitmproxy-ca-cert.pem | head -1)
cp ~/.mitmproxy/mitmproxy-ca-cert.pem "$HASH.0"
# easiest: a Magisk module that mounts user certs into /system/etc/security/cacerts
#   (MagiskTrustUserCerts / "Move Certificates"), then reboot
adb push "$HASH.0" /sdcard/
```

Then proxy the device at the mitmproxy host:

```bash
adb shell settings put global http_proxy <PC_IP>:8080
# verify: open any https site in the phone browser -> mitmproxy shows it
# some apps ignore the global proxy; if so use a transparent redirect / PCAPdroid+mitm addon
```

Run the addon (redacts PII, keeps keys/structure/status):

```bash
mitmdump -s ~/rea-apk/device-setup/capture-addon.py --listen-port 8080 \
         --save-stream-file ~/rea-apk/captures/flows.mitm
```

## 3. Option B — emulator on your own PC (Linux easiest)

Requirements: KVM (`ls /dev/kvm`), 16 GB RAM recommended, ~10 GB disk.

```bash
# 1. command-line tools
mkdir -p ~/android-sdk && cd ~/android-sdk
curl -LO https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q commandlinetools-linux-*.zip
export ANDROID_HOME=$HOME/android-sdk
export PATH=$ANDROID_HOME/cmdline-tools/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH

# 2. packages (Play Store image so Play services + Integrity exist)
yes | sdkmanager --sdk_root=$ANDROID_HOME --licenses
sdkmanager --sdk_root=$ANDROID_HOME "platform-tools" "emulator" \
  "system-images;android-33;google_apis_playstore;x86_64"

# 3. AVD, writable system so we can install the CA into /system
avdmanager create avd -n flytaxi -k "system-images;android-33;google_apis_playstore;x86_64" -d pixel_5
emulator -avd flytaxi -writable-system -no-snapshot -gpu swiftshader_indirect &

# 4. system CA install (no Magisk needed on a writable-system image)
adb root && adb remount
HASH=$(openssl x509 -inform PEM -subject_hash_old -in ~/.mitmproxy/mitmproxy-ca-cert.pem | head -1)
adb push "$HASH.0" /system/etc/security/cacerts/
adb shell chmod 644 /system/etc/security/cacerts/$HASH.0
adb reboot
```

Play Store images are locked to a real Google account for Play services; you can still `adb install`
the APK directly with your account's OTP login inside the app.

**Expect** `MEETS_DEVICE_INTEGRITY` to fail on the emulator. If the app still lets you log in, you
get everything you need. If it blocks, stop — do not burn hours on fingerprint spoofing.

## 4. What to do in the app (passive sequence)

| step | action | expected endpoints |
|---|---|---|
| 1 | cold start, no login | `/v6/api/get_config_sqlite` |
| 2 | login (SMS OTP) | `/v6/member/create_login_token`, integrity chain |
| 3 | stay on map ~60 s | `/v6/gps/get_user_nearby_drivers` (cadence!) |
| 4 | type an address | `/v6/book/get_book_autocomplete` |
| 5 | set pickup + dropoff | `/v6/book/get_book_direction` |
| 6 | open the fare screen (do **not** confirm) | `/v6/api/fare2` |
| 7 | driver app: log in, go online, stay idle | `/v6/driver/batch_info2`, `/v6/book/list`, `/v6/gps/update_driver` |
| 8 | stop | — |

Record for each step: wall-clock time, what you tapped, and the `captures/*.jsonl` lines produced.

**Never** tap 確認叫車 / 接單 / 取消. Repeating `book/list` polling for a few minutes is fine and is
the single most valuable passive artifact (it shows the dispatch payload).

## 5. Deliver back

```
captures/<ts>.jsonl        # redacted, one line per request
captures/flows.mitm        # optional raw; do not share (contains tokens/PII)
```

The `.jsonl` alone is enough for me to: confirm request encodings, fill per-endpoint response keys,
enumerate `status` values, and correct the contract doc. Keep `flows.mitm` local.

## 6. Troubleshooting

| symptom | cause | fix |
|---|---|---|
| TLS errors, app shows network fail | CA not in **system** store | re-check `cacerts` hash name + remount; app trusts only `src="system"` |
| browser through proxy OK, app not | app ignores global proxy or uses raw sockets | transparent redirect (iptables/REDIRECT) or emulator `-http-proxy` |
| `IntegrityFailActivity` | verdict failed | real certified device; emulator will not fix this |
| app detects root | Magisk visible | Zygisk + denylist + Shamiko; do not patch the APK (breaks signature) |
| empty response bodies | HTTP/2 + gzip | mitmproxy handles both; check `resp_encoding` in the jsonl |
