# Changelog

## 1.0

Version name `1.0`, version code `1`.

- Distribution: signed APKs are published as GitHub Releases. GitHub builds the unsigned release APK and attests it, the maintainer signs it offline with a persistent key, and a second workflow checks the signed file against the build before publishing. The debug-signed `beta-<run id>` prerelease workflow is removed. See `RELEASING.md`.
- Debug builds now install as `com.bettercaper.debug` ("betterCAPER Debug"), so a debug install can no longer block an in-place update to the signed release. Data in an earlier debug install (package `com.bettercaper`) is not carried over to it.
- Release signing passwords are no longer in the Gradle file. They load from a gitignored `keystore.properties`. Debug builds do not need it. A missing file leaves release builds unsigned.
- Reconnect is single-flight. A dropped SSH session and a failed WebSocket cannot each start a handshake. The previous WebSocket is closed, and callbacks from that socket are ignored. A failed first login is shown once and does not loop. Authentication failures and host-key changes never retry. Other drops retry at most 3 times.
- Closing the screen stops location updates and no longer waits on the SSH teardown on the main thread.
- Command history stays in the ViewModel while the session is connected.
- If the Android keystore cannot be opened, the app tells you that saved secrets are not encrypted.
- The `wifi.deauth` suggestion asks for one BSSID and notes that `wifi.recon` has to be running.
- Screens use a near-black surface, readable secondary text, and a status line with the SSH host, interface, and reconnect text. The dashboard overflow is grouped, and Modules and Caplets sit above the host list.
