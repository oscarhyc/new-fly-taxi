# Quick start — capture one real driver session (before 26 Oct / 31 Dec)

The production service stops on **31 December** and driver accounts stop from **26 October**.
Everything static is already recovered, so the only thing still missing is **live evidence**:
real order payloads, real fare breakdowns, real dispatch timing, real push payloads.

This procedure records **your own** session. It creates nothing, accepts nothing, automates
nothing — you drive, the script only writes down what your app already sent.

---

## 0. Choose a path

| your phone | can you install a **system** CA? | then |
|---|---|---|
| rooted (Magisk) | yes | **full capture** (TLS decrypted) — path A |
| not rooted | no | **screenshots + timings** — path B |

The app's `network_security_config.xml` trusts `system` certificates only, so a user-installed
CA will not work. This is a hard wall, not a setting we can tweak.

---

## Path A — full capture (rooted phone)

### A1. On the computer that will run the proxy

```bash
# Linux / macOS
python3 -m pip install --user mitmproxy      # or: pipx install mitmproxy
export PATH="$HOME/.local/bin:$PATH"          # if pip put it in ~/.local/bin
mitmdump --version                            # expect >= 10

# Windows:  py -m pip install mitmproxy   (or download the installer from mitmproxy.org)
```

### A2. Install the mitmproxy CA as a SYSTEM certificate on the phone

1. Run mitmproxy once on the computer: `mitmdump` → it prints the certificate path
   (`~/.mitmproxy/mitmproxy-ca-cert.pem`).
2. Transfer that `.pem` to the phone.
3. Magisk users: install a module that moves user CAs into the system store
   (e.g. *MagiskTrustUserCerts*), put `mitmproxy-ca-cert.pem` in the module's folder,
   reboot — done.
   Or do it manually:
   ```bash
   # phone, with root shell (e.g. Magisk / adb shell su)
   HASH=$(openssl x509 -inform PEM -subject_hash_old -in mitmproxy-ca-cert.pem | head -1)
   cp mitmproxy-ca-cert.pem /data/local/tmp/$HASH.0
   su -c "mount -o rw,remount /system"
   su -c "cp /data/local/tmp/$HASH.0 /system/etc/security/cacerts/$HASH.0"
   su -c "chmod 644 /system/etc/security/cacerts/$HASH.0"
   # reboot
   ```
4. Verify: open `https://mitm.it` in the phone browser → you should see the mitmproxy page
   (that only proves the CA is trusted by the browser; the app may still refuse if it pins).

### A3. Start the capture

```bash
cd ~/new-fly-taxi/device-setup        # or wherever you cloned the repo
./run-capture.sh
```

It prints the phone-side proxy setting. On the phone: **Settings → Wi-Fi → your network →
Advanced → Proxy → Manual**, host = the computer's LAN IP, port = 8087.
Or over USB: `adb shell settings put global http_proxy <PC_IP>:8087`

### A4. Drive one trip

1. Open the driver app, log in with your account.
2. Wait for a booking to appear, accept it, go to the pickup, start the ride, complete it.
3. Come back to the terminal and press `Ctrl-C`.

Output:

```
captures/capture-<timestamp>.jsonl     # redacted: phone numbers, coordinates, tokens masked
captures/flows-<timestamp>.mitm        # raw — contains tokens, keep it local
docs/capture-report.md                 # generated: endpoints, keys, statuses + diff vs our contract
```

Copy **only the `.jsonl`** back to us. Keep the `.mitm` file local.

---

## Path B — no root: screenshots + timings

Use `docs/manual-observation.md`. The highest-value items, in order:

1. **Wrong OTP once** → the error dialog prints `status_code` + message → fills our error table.
2. **Fare breakdown screen** → maps to our fare model fields.
3. **Complaint type list** → maps to the config table.
4. **Accept countdown** and **nearby-driver refresh interval** → we cannot derive these statically.
5. **FCM notification text** → the push action vocabulary.
6. Driver home screen (counters) → maps to the driver-home snapshot fields.

---

## What happens to your data

* The addon masks phone numbers, coordinates, tokens, device ids, names, addresses and card data
  before anything is written to disk.
* `analysis` only ever sees keys, shapes and status values.
* If you want to be sure: after the capture, run
  `grep -c '<redacted>' captures/*.jsonl` — every sensitive field shows up as `<redacted>`.

## If the app refuses to connect

| symptom | cause | fix |
|---|---|---|
| browser works, app fails | CA not in system store | re-check step A2 |
| `SSL error` in app | pinning | the reference app does not declare pinning; if it still fails, use path B |
| proxy not used at all | app ignores the global proxy | the app may use its own socket; try a transparent redirect (iptables REDIRECT) or use path B |
