# betterCAPER

Android remote for [bettercap](https://www.bettercap.org/), written in Kotlin and Jetpack Compose. The phone opens an SSH tunnel and talks to bettercap's API on `127.0.0.1` through that tunnel.

**Version 1.0.** Under active development.

Use this app only on networks you own or are explicitly authorised to test. Scanning, deauthentication, ARP spoofing, and the other bettercap modules can disrupt devices. You are responsible for staying inside the law and your rules of engagement.

## Download and install

**No signed APK has been published yet.** The instructions below apply once the first release is published.

1. Open the **[Releases page](https://github.com/primary-null/betterCAPER/releases)** and download `betterCAPER-<version>.apk` from **Assets**. No GitHub account is needed.
2. On a phone running Android 10 or later, allow installs from the app you open the file with (Settings → Apps → Special app access → Install unknown apps; the wording varies by phone).
3. Open the APK and tap **Install**.

The release notes list the APK's SHA-256 and the SHA-256 of the signing certificate. Releases are built by GitHub Actions from this repository and signed with the project's signing key, which never leaves the maintainer's machine. See [RELEASING.md](RELEASING.md).

### Upgrade

Install the newer release over the old one. Do not uninstall first: Android keeps your saved connections because the signing certificate is the same in every release. Android will not install an older version over a newer one. Releases marked **Pre-release** (a version such as `1.1-beta.1`) are the same app and update in place, but they are never offered as the latest release.

Debug builds from CI or Android Studio are a different app (`betterCAPER Debug`, package `com.bettercaper.debug`) and install next to the release. A debug build made before that change used the release's package name with a throwaway key, so Android refuses to update it with the signed release. Uninstall that one old debug build once (this erases its saved connections), then install the release.

### Check your download

```bash
sha256sum betterCAPER-1.0.apk                       # must equal the SHA-256 in the release notes
apksigner verify --print-certs betterCAPER-1.0.apk  # certificate SHA-256 must equal the release notes, every release
```

`apksigner` is in the Android SDK build-tools. [RELEASING.md](RELEASING.md#verifying-a-download) also shows how to verify the build attestations with the GitHub CLI.

Google is phasing in developer verification for apps installed outside Google Play on certified devices. If your phone asks for extra confirmation when you install this APK, see [Android developer verification](https://developer.android.com/developer-verification) for how it works in your region.

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
- **Dark theme** — Near-black surfaces, matrix green primary, and readable secondary text
- **Reconnect** — After a session has come up, an unexpected drop waits 3 seconds and tries one handshake at a time, up to 3 times. A failed first login, a bad password, or a changed host key stops instead of looping
- **Interface-down retry** — If a command fails because an interface looks down, the client tries `ip link set <iface> up` once and sends the command again

## Status and limitations

- Unit tests cover bettercap session parsing and the reconnect policy.
- One SSH session at a time; `SshTunnelManager` is a process-wide singleton.
- Official releases are built by GitHub Actions and signed with the project's signing key ([RELEASING.md](RELEASING.md)). A release you build yourself is unsigned unless you add `keystore.properties` (see below), and it cannot update or be updated by an official release.
- `android_gpsd.py` is an optional gpsd emulator for the host: it receives the phone's GPS position and serves it to bettercap's `gps` module. It listens on `127.0.0.1` only.

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

From the repository root (Windows or a Unix shell):

```bat
gradlew.bat :app:assembleDebug
```

```bash
./gradlew :app:assembleDebug
```

Debug builds use the Android debug keystore. They do not read `keystore.properties`. They install as `com.bettercaper.debug` ("betterCAPER Debug"), next to the release.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

Tests and lint:

```bat
gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The release tooling has its own tests (Python 3, no SDK needed): `python -m unittest discover -s tools/release -v`.

### Your own signed build

This is for running your own build. The official APK is the one on the [releases page](https://github.com/primary-null/betterCAPER/releases) ([RELEASING.md](RELEASING.md)).

1. Create a keystore outside git, or keep the file in the repo root. `*.jks` and `*.keystore` are gitignored.
2. Copy `keystore.properties.example` to `keystore.properties` and set `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`. `storeFile` is relative to the repo root.
3. `gradlew.bat :app:assembleRelease`

If `keystore.properties` is missing, configuration still succeeds and the release APK is unsigned (`app-release-unsigned.apk`, not installable). If the file is present but incomplete, or the keystore file is missing, Gradle prints which entry or file name is the problem and the release APK stays unsigned. A release build never falls back to the debug key.

Release APK, when signed: `app/build/outputs/apk/release/app-release.apk`. Unsigned: `app/build/outputs/apk/release/app-release-unsigned.apk`.

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
