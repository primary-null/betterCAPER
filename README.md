# betterCAPER

Android remote for [bettercap](https://www.bettercap.org/), written in Kotlin and Jetpack Compose. The phone opens an SSH tunnel and talks to bettercap's API on `127.0.0.1` through that tunnel.

**Version 1.0** (`versionName` 1.0, `versionCode` 1 in `app/build.gradle.kts`). This is work in progress. It is not a finished product.

Use this app only on networks you own or are explicitly authorised to test. Scanning, deauthentication, ARP spoofing, and the other bettercap modules can disrupt devices. You are responsible for staying inside the law and your rules of engagement.

## Features

- **Dashboard** — LAN hosts, active modules, interface and gateway, with a best-effort device icon
- **WiFi** — Access points with RSSI, channel, encryption, and clients. Choosing an interface runs `wifi.recon on`
- **Bluetooth/BLE** — Devices already found by `ble.recon`, with RSSI, vendor, and last-seen time. BLE does not start by itself
- **Modules** — Module list with on/off toggles and ARP spoof controls
- **Events** — Live WebSocket event stream, coloured by type
- **Command** — bettercap commands, a suggestion list, and history kept for the life of the screen's ViewModel
- **Caplets** — List, edit, and run caplets on the remote host
- **WiFi & BLE hook** — On demand, deploy a caplet that starts `wifi.recon` and `ble.recon` together
- **Saved connections** — Bookmarks with add, edit, and delete
- **Airgeddon hook** — Deploy or remove a plugin on the remote host and show the log it writes
- **Dark theme** — Black background with blue highlights
- **Reconnect** — After a session has come up, an unexpected drop waits 3 seconds and tries one handshake at a time, up to 3 times. A failed first login, a bad password, or a changed host key stops instead of looping
- **Interface-down retry** — If a command fails because an interface looks down, the client tries `ip link set <iface> up` once and sends the command again

## Status

Honest limits as of version 1.0:

- Debug unit tests cover session JSON parsing and the reconnect decision policy. They do not talk to a device or a bettercap host.
- The Python runner in `tests/` drives a real phone and a lab host. It needs a local `tests/config.json` copied from `tests/config.json.template`. That file is gitignored. This pass did not run it.
- One SSH session at a time. `SshTunnelManager` is a process-wide singleton.
- Release signing is local. There is no keystore in the tree. Without `keystore.properties`, or with an incomplete one, debug builds still work and release builds are unsigned.
- `android_gpsd.py` is a small original helper for this project, not vendored third-party code. It listens on `127.0.0.1` only. The `bettercap/` directory is empty and is not a bundled copy of bettercap.
- Not published. Build from this checkout.

## Security

- All traffic tunnelled over SSH — bettercap API is never exposed to the network
- bettercap REST API bound to `127.0.0.1` only — unreachable from the LAN even if port 8081 is probed
- Trust-on-first-use (TOFU) SSH host key pinning — MITM attempts are blocked with a clear warning
- Credentials stored encrypted at rest using Android Keystore (AES-256-GCM via `EncryptedSharedPreferences`)

## Requirements

- Android 10+ (minSdk 29)
- A Linux host running bettercap with the REST API enabled (We strongly recommend using **Kali Linux** or **Parrot OS** for the best compatibility)
- SSH access to that host

---

## Kali Linux Setup

### 1. Install bettercap

```bash
sudo apt update
sudo apt install bettercap -y
```

### 2. Enable the bettercap REST API

Test it works manually first:

```bash
sudo bettercap -eval "set api.rest.address 127.0.0.1; set api.rest.username user; set api.rest.password user; set api.rest.websocket true; api.rest on"
```

You should see `api.rest` listening on `127.0.0.1:8081`. Press `Ctrl+C` to stop.

> **Tip:** Change `user` / `user` to stronger credentials and match them in the app's API fields.
> **Security:** `api.rest.address 127.0.0.1` binds the API to localhost only — it is unreachable from the LAN and only accessible through the SSH tunnel the app creates.

### 3. Run bettercap as a systemd service (auto-start on boot)

Create the service file:

```bash
sudo nano /etc/systemd/system/bettercap.service
```

Paste the following (replace credentials if you changed them):

```ini
[Unit]
Description=bettercap api.rest service
Wants=network.target
After=network.target

[Service]
Type=simple
ExecStart=/usr/bin/bettercap -no-colors -eval "set api.rest.address 127.0.0.1; set api.rest.username user; set api.rest.password user; set api.rest.websocket true; set events.stream.output /var/log/bettercap.log; api.rest on"
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

Enable and start it:

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now bettercap
sudo systemctl status bettercap
```

### 4. Enable persistent monitor mode for WiFi scanning

`wifi.recon` requires the wireless adapter in monitor mode. NetworkManager interferes by putting the adapter back into managed mode — the cleanest fix is a systemd service that runs `airmon-ng` before bettercap on every boot.

Create the monitor mode service:

```bash
sudo nano /etc/systemd/system/wlan0mon.service
```

```ini
[Unit]
Description=Enable wlan0 monitor mode (wlan0mon)
After=network.target
Before=bettercap.service

[Service]
Type=oneshot
RemainAfterExit=yes
ExecStart=/bin/bash -c "iw dev wlan0mon info &>/dev/null || airmon-ng start wlan0; exit 0"
ExecStop=/bin/bash -c "airmon-ng stop wlan0mon; systemctl restart NetworkManager; exit 0"

[Install]
WantedBy=multi-user.target
```

Enable it and update the bettercap service to depend on it:

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now wlan0mon
```

Update `/etc/systemd/system/bettercap.service` to use `eth0` for network ops and `wlan0mon` for WiFi:

```ini
[Unit]
Description=bettercap api.rest service
Wants=network.target wlan0mon.service
After=network.target wlan0mon.service

[Service]
Type=simple
ExecStart=/usr/bin/bettercap -no-colors -iface eth0 -eval "set api.rest.address 127.0.0.1; set api.rest.username user; set api.rest.password user; set api.rest.websocket true; set wifi.interface wlan0mon; set events.stream.output /var/log/bettercap.log; api.rest on; net.probe on; ble.recon on; wifi.recon on"
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl restart bettercap
```

On every boot `wlan0mon` is ready before bettercap starts — no manual steps needed. WiFi scanning, LAN host discovery, and BLE scanning all start automatically.

> **Note:** `airmon-ng` kills NetworkManager and wpa_supplicant temporarily. Your WiFi connection will drop, but if the host is on ethernet (`eth0`) the SSH tunnel stays up.

### 5. Enable SSH (required for the app to connect)

```bash
sudo systemctl enable --now ssh
```

Verify SSH is running:

```bash
sudo systemctl status ssh
```

### 6. Find your Kali IP address

```bash
ip addr show
```

Look for the `inet` address on your active interface (e.g. `eth0` or `wlan0`). That is the IP you enter in the app.

---

## Connect from the App

| Field | Value |
|-------|-------|
| SSH Host | IP address of your Kali box |
| SSH Port | `22` |
| SSH Username | Your Kali username |
| SSH Password | Your Kali password |
| API Port | `8081` (bettercap default) |
| API User | `user` |
| API Password | `user` |

The API fields match the defaults used in the service file above — change both if you set custom credentials.

After connecting, the app will prompt you to select a network interface. Once selected, `wifi.recon` starts automatically and the WiFi screen populates without any further steps.

---

## Building

From this checkout (Windows or a Unix shell):

```bat
gradlew.bat :app:assembleDebug
```

```bash
./gradlew :app:assembleDebug
```

Debug builds use the Android debug keystore. They do not read `keystore.properties`.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

Checks used for this tree:

```bat
gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

### Signed release

1. Create a keystore outside git, or keep the file in the repo root. `*.jks` and `*.keystore` are gitignored.
2. Copy `keystore.properties.example` to `keystore.properties` and set `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`. `storeFile` is relative to the repo root.
3. `gradlew.bat :app:assembleRelease`

If `keystore.properties` is missing, configuration still succeeds and the release APK is unsigned. If the file is present but incomplete, or the keystore file is missing, configuration fails with an error that names the missing entry or file name.

Release APK, when the task succeeds: `app/build/outputs/apk/release/app-release.apk`

---

## WiFi & BLE Hook

The WiFi & BLE Hook screen deploys a caplet to `~/.bettercap/bettercaper-local.cap` on the remote host. **Start Scan** loads that caplet, which starts `wifi.recon` and `ble.recon` together. **Stop Scan** stops both. That hook does not run on its own.

Separately, when you pick a network interface in the app, the app sends `wifi.recon on` for that interface. BLE stays off until you start it from the command screen or this hook. A host systemd unit, if you install the example below, can also start modules on the machine itself. That is host configuration, not the app.

---

## Airgeddon Hook Plugin

The Airgeddon screen deploys a bettercap plugin to `~/.airgeddon/plugins/bettercaper.sh` on the remote host via SSH. The plugin hooks into airgeddon's callback system and logs events (handshake captures, evil twin, deauth, scans, etc.) to `/tmp/.bettercaper_ag.log`, which the app reads and displays.

Restart airgeddon after deploying the plugin for it to take effect.

---

## Stack

- Kotlin + Jetpack Compose
- Hilt — dependency injection
- [sshj](https://github.com/hierynomus/sshj) — SSH tunnel and remote exec
- OkHttp — REST API + WebSocket event stream
- Gson — JSON parsing
- AndroidX Security Crypto — encrypted credential storage
- BouncyCastle — full JCA crypto provider for x25519/ed25519 SSH key exchange
