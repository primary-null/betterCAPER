# Changelog

## 1.0

Work in progress. Version name `1.0`, version code `1`.

- Release signing passwords are no longer in the Gradle file. They load from a gitignored `keystore.properties`. Debug builds do not need it. A missing file leaves release builds unsigned.
- Reconnect is single-flight. A dropped SSH session and a failed WebSocket cannot each start a handshake. The previous WebSocket is closed, and callbacks from that socket are ignored. A failed first login is shown once and does not loop. Authentication failures and host-key changes never retry. Other drops retry at most 3 times.
- Closing the screen stops location updates and no longer waits on the SSH teardown on the main thread.
- Command history stays in the ViewModel while the session is connected.
- If the Android keystore cannot be opened, the app tells you that saved secrets are not encrypted.
- The `wifi.deauth` suggestion asks for one BSSID and notes that `wifi.recon` has to be running.
