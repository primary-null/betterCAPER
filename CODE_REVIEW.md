# betterCAPER — Code Review

**Reviewed:** 2026-05-20  
**Resolution pass:** 2026-11-05  
**Codebase:** Kotlin/Jetpack Compose Android app, Hilt DI, sshj SSH tunnelling, bettercap REST + WebSocket API

The notes below are the original review. Each item is marked with what the 2026-11-05 pass did. The original line numbers are historical.

## Resolution

| Item | Result |
|------|--------|
| 1.1 WebSocket leak on reconnect | **Fixed.** The attempt closes the previous socket before opening another. Listener callbacks carry a generation, so a closed socket cannot start a new handshake. `SshTunnelManager` publishes a session only if its epoch is still current, and a stale attempt closes only its own epoch. |
| 1.2 Double-reconnect race | **Fixed.** `ReconnectPolicy` accepts one schedule for a burst of SSH and WebSocket failures, including concurrent callers. The permit stays held through the delay. A failed first login does not schedule a retry. Covered by `ReconnectPolicyTest`. |
| 2.1 `disconnect()` on the main thread in `onCleared` | **Fixed.** Teardown runs on a daemon thread. `viewModelScope` is not used there, because that scope is cancelled as the ViewModel clears. |
| 2.2 `resp.body!!` | **Already gone** by this pass. `getSession()` and `getEvents()` use the nullable body. |
| 2.3 Caplets dead code | **Already wired.** `AppNavigation` collects `capletsState` and calls `loadCaplets()`. |
| 2.4 Reconnect message as a blocking dialog | **Already split.** The reconnect text is `reconnectingMessage` on a card. `error` is cleared while reconnecting, so the dialog stays on real failures. |
| 3.1 Duplicate `"asus"` vendor match | **Already fixed.** `"asus"` is only in the router list. |
| 3.2 Singleton tunnel manager | **Left as-is.** One session is the product. A second owner would still be unsafe. Called out in the README. |
| 3.3 Switch `onCheckedChange` ignored the new value | **Already fixed** in `ModulesScreen`. |
| 3.4 Command history lost on navigation | **Fixed.** History is `AppUiState.commandHistory` (50 entries) for the ViewModel lifetime. Disconnect clears it. |
| 3.5 Silent plaintext fallback | **Fixed.** `SecureStorage.keystoreUnavailable` is true when the keystore cannot be opened, and the ViewModel shows a toast. |
| 3.6 `wifi.deauth` broadcast suggestion | **Fixed.** The suggestion is `wifi.deauth <BSSID>` and says `wifi.recon` must already be running. |
| Signing passwords in `app/build.gradle.kts` | **Fixed.** Not part of the May review. Passwords load from gitignored `keystore.properties`. An incomplete file warns and leaves release unsigned, so debug configuration still succeeds. |

Follow-up on the same pass: automatic reconnects stop after 3 attempts. Authentication and host-key failures do not retry. `onCleared` stops location updates. A stale reconnect delay cannot hold the single reconnect slot. A remote WebSocket close with code 1000 is unchanged, because this client also closes with 1000.

---

## Summary

The app is well-structured for its scope. The Hilt wiring is correct, the SSH tunnel is solid, and the UI layer cleanly separates concerns through ViewModel state flows. The May 2026 review found two real bugs (WebSocket leak on reconnect, double-reconnect race), several minor issues, and a few items that were already done or mis-filed. See the resolution table.

---

## 1. Critical Bugs

### 1.1 WebSocket is never closed before reconnect

**File:** `MainViewModel.kt:322-338` + `performConnection()` line 137

On an unexpected disconnect, `handleUnexpectedDisconnect()` calls `performConnection()` again. That function calls `connectEventStream(client)` which assigns a new `WebSocket` to `webSocket`. The **old** WebSocket is never closed first.

Every reconnect loop leaks an OkHttp WebSocket connection. After a few disconnects in a poor-signal environment the phone will have multiple open sockets, each firing `onFailure` and each triggering another reconnect.

**Fix:** Close the old socket at the top of `performConnection`, before starting anything else:

```kotlin
private fun performConnection(params: ConnectParams) {
    viewModelScope.launch {
        webSocket?.close(1000, null)   // ← add this
        webSocket = null
        _uiState.update { it.copy(connecting = true, error = null) }
        // ...
```

---

### 1.2 Double-reconnect race condition

**File:** `MainViewModel.kt:116`, `335-338`

Two triggers call `handleUnexpectedDisconnect()` on the same disconnect event:

1. SSH `DisconnectListener.notifyDisconnect()` (line 34–37 in `SshTunnelManager.kt`)
2. WebSocket `onFailure()` (line 335 in `MainViewModel.kt`)

Both fire nearly simultaneously when the connection drops. Both pass the `isIntentionalDisconnect` guard, launch a coroutine, wait 3 s, and then both call `performConnection()`. The result is two simultaneous SSH handshakes racing to the same server.

There's also a third path: on connection failure inside `performConnection`, the `catch` block calls `handleUnexpectedDisconnect()` directly (line 141), which can stack with the listener-triggered one.

**Fix:** Add a reconnect-in-progress flag that is set atomically before the delay:

```kotlin
private var isReconnecting = false

private fun handleUnexpectedDisconnect() {
    if (isIntentionalDisconnect || activeParams == null || isReconnecting) return
    isReconnecting = true
    viewModelScope.launch {
        _uiState.update { it.copy(connected = false, connecting = true, error = "Connection dropped. Reconnecting in 3s...") }
        delay(3000)
        isReconnecting = false
        if (!isIntentionalDisconnect && activeParams != null) {
            performConnection(activeParams!!)
        }
    }
}
```

Reset `isReconnecting = false` in `disconnect()` and at the top of `performConnection()` as well.

---

## 2. Moderate Issues

### 2.1 `SshTunnelManager.disconnect()` blocks the calling thread

**File:** `SshTunnelManager.kt:71-77`, `MainViewModel.kt:343-344`

`onCleared()` calls `tunnelManager.disconnect()` directly on the main thread (ViewModels are cleared on main). sshj's `disconnect()` sends a close packet and waits for the server ACK. On a slow or dropped connection this can hang for seconds and trigger an ANR.

**Fix:**
```kotlin
override fun onCleared() {
    super.onCleared()
    webSocket?.close(1000, null)
    viewModelScope.launch(Dispatchers.IO) { tunnelManager.disconnect() }
}
```

---

### 2.2 NPE risk in `BettercapApiClient`

**File:** `BettercapApiClient.kt:39`, `57`

`resp.body!!` uses the non-null assertion. OkHttp can return a null body for certain response codes (e.g. 204 No Content). A null body on `getSession()` or `getEvents()` will crash with an unhandled `NullPointerException` that propagates up to the ViewModel catch block — where it shows as a generic error. Not a crash in practice but misleading error messages.

**Fix:** Replace `!!` with safe alternatives:
```kotlin
// getSession
return gson.fromJson(resp.body?.charStream() ?: error("Empty response"), SessionResponse::class.java)

// getEvents
return gson.fromJson<List<BcEvent>>(resp.body?.charStream() ?: return emptyList(), type) ?: emptyList()
```

---

### 2.3 `capletsState` / `loadCaplets()` is dead code

**File:** `MainViewModel.kt:64-68`, `207-219`; `AppNavigation.kt`

`CapletsUiState`, `_capletsState`, `capletsState`, and `loadCaplets()` exist in the ViewModel and are exposed as a `StateFlow`, but no screen in `AppNavigation` ever calls `loadCaplets()` or reads `capletsState`. The caplets feature is half-finished.

**Options:** Either add a Caplets screen and wire it up, or remove these until the feature is ready.

---

### 2.4 Error dialog shown during reconnect state

**File:** `ConnectScreen.kt:51-67`, `MainViewModel.kt:149`

When `handleUnexpectedDisconnect()` fires, it sets `error = "Connection dropped. Reconnecting in 3s..."` alongside `connecting = true`. `ConnectScreen` shows an `AlertDialog` whenever `error != null`. So the reconnect message pops up as a dialog — the user has to dismiss it while the app is already reconnecting in the background. This is awkward UX.

The reconnect banner would be better as a `Snackbar` or a non-blocking status chip on the dashboard, not a blocking dialog.

---

## 3. Minor Issues

### 3.1 Ambiguous vendor match in `guessDeviceIcon()`

**File:** `DashboardScreen.kt:288-311`

`"asus"` appears in both the Router list and the Computer list (lines 292 and 297). Because Router is checked first, an Asus laptop vendor string will show a Router icon. In practice `vendor` strings from bettercap's OUI database are specific enough (`"ASUSTeK COMPUTER INC."`) that both patterns would match the same prefix, but it is still a logic error.

**Fix:** Remove `"asus"` from the Computer vendor list — it's already covered by the Router block, and Asus laptops are uncommon enough on typical pentest targets that the default `Devices` icon is fine.

---

### 3.2 `SshTunnelManager` is `@Singleton` with mutable state

**File:** `DiModule.kt:31-33`, `SshTunnelManager.kt`

`SshTunnelManager` holds `sshClient`, `serverSocket`, and `tunnelThread` as mutable instance fields. As a singleton it is shared across the process lifetime. This is fine while there is only one ViewModel, but if a second screen were ever to hold a reference to the tunnel manager (e.g. a future background service), these fields could be stomped. Not a current bug, just a fragile pattern for a singleton.

---

### 3.3 `ArpSpoofCard` toggle logic is inverted

**File:** `ModulesScreen.kt:152-158`

```kotlin
Switch(
    checked = running,
    onCheckedChange = { onToggle(!running) },   // ← ignores the lambda param
```

The `onCheckedChange` lambda receives the desired new state as its parameter (the `it` value), but the code ignores `it` and uses `!running` instead. These are equivalent in practice but it's an anti-pattern — if Compose ever debounces or batches state changes, the `running` captured in the closure could be stale.

**Fix:**
```kotlin
onCheckedChange = { newValue -> onToggle(newValue) }
```

The same pattern appears in `ModuleCard` at line 290.

---

### 3.4 `CommandScreen` history is not persisted across navigation

**File:** `CommandScreen.kt:85`

```kotlin
val history = remember { mutableStateListOf<String>() }
```

`remember` with no key means the history is scoped to the composable's lifetime in the back-stack. Navigate away and back and the history is gone. For a tool aimed at power users, persistent history (even just in the ViewModel as a `List<String>` in `AppUiState`) would be valuable.

---

### 3.5 `SecureStorage` fallback is silent to the user

**File:** `SecureStorage.kt:18-22`

If the Android Keystore is corrupted, the code silently falls back to plaintext `SharedPreferences`. The log message is only visible in logcat. SSH passwords stored in the fallback path are unencrypted on the device filesystem. Consider surfacing this to the user as a one-time warning via a Toast or a persistent error state.

---

### 3.6 `wifi.deauth` suggestion lacks context

**File:** `CommandScreen.kt:54`

```kotlin
CmdSuggestion("wifi.deauth ff:ff:ff:ff:ff:ff", "Deauth all clients on an AP"),
```

`wifi.deauth` requires WiFi recon to be running and a target AP to be selected (via `wifi.recon.channel` or similar). Sending the broadcast MAC with no context will silently fail or deauth every AP on the current channel. The description is misleading — consider adding `wifi.recon on` as a prerequisite note, or changing the suggestion to a BSSID placeholder.

---

## 4. Security Observations

| Area | Status | Notes |
|------|--------|-------|
| SSH host key pinning | Good | TOFU with SHA-256 fingerprint, stored in `EncryptedSharedPreferences`. Correct rejection on key mismatch with clear error. |
| Credential storage | Good | Uses `EncryptedSharedPreferences` with AES-256-GCM. Fallback to plaintext logged but not surfaced. |
| BouncyCastle provider replacement | Correct | `Security.removeProvider("BC")` then `insertProviderAt(BouncyCastleProvider(), 1)` at `Application.onCreate()` — before any SSH activity. Needed for x25519/ed25519 support on Android. |
| API credentials in memory | Acceptable | Credentials live in `ConnectParams` and `BettercapApiClient` as plain `String` fields. No in-memory encryption. Acceptable for a local-use tool. |
| Airgeddon plugin deployment | Review | Plugin is base64-encoded and piped through `echo ... \| base64 -d > file`. If `pluginBase64()` were ever to return a string with embedded shell metacharacters this would be a remote code execution on the user's own server. The current base64 content is safe. |
| SSH password in any source file | Not present | Confirmed: no credentials appear in any tracked source file. |
| ProGuard rules | Good | Proper `-keep` rules for sshj, BouncyCastle, Gson, and data models. |

---

## 5. Architecture & DI

The Hilt setup is correct:

- `@HiltAndroidApp` on `BetterCaperApp` — `Application` subclass correctly annotated
- `@AndroidEntryPoint` on `MainActivity`
- `@HiltViewModel` + `@Inject constructor` on `MainViewModel`
- `hiltViewModel()` in `AppNavigation`
- `@Module @InstallIn(SingletonComponent::class)` in `DiModule` providing `SecureStorage`, `ConnectionRepository`, and `SshTunnelManager` as `@Singleton`

All three dependencies of `MainViewModel` are properly provided. The DI graph is shallow and clear.

One note: `ConnectionRepository` takes both a `SecureStorage` and an `Application` context. Since `SecureStorage` already wraps the context, passing `Application` separately to `ConnectionRepository` (just to build the legacy file path) creates an implicit coupling. The legacy migration code could be moved into `SecureStorage` itself, removing the context parameter from `ConnectionRepository`.

---

## 6. Dependency Versions

| Library | Current | Note |
|---------|---------|------|
| `security-crypto` | 1.0.0 | Has known issues on Android 6.0 Keystore. Current stable is 1.1.0-alpha06. Consider upgrading if you support API 23. |
| `sshj` | 0.38.0 | Up to date. |
| `bcprov-jdk18on` | 1.78.1 | Up to date. |
| `hilt` | 2.51.1 | Up to date. |
| `compose-bom` | 2024.12.01 | December 2024 BOM — one BOM release behind as of review date, but fine. |

---

## 7. Recommended Fixes (Priority Order)

These were the May priorities. The resolution table at the top is the current state. The suggested `isReconnecting` snippet was not copied as-is: clearing that flag before `performConnection()` still let a second handshake start. `ReconnectPolicy` holds the single-flight permit until the delay is consumed.

1. **[Bug]** Close old WebSocket before reconnect — fixed, with a generation check
2. **[Bug]** One reconnect for SSH and WebSocket loss — fixed in `ReconnectPolicy`
3. **[Crash-risk]** Nullable `resp.body` — already fixed before this pass
4. **[ANR-risk]** `disconnect()` off the main thread in `onCleared()` — fixed
5. **[UX]** Non-blocking reconnect status — already present
6. **[Cleanup]** Caplets screen — already wired
7. **[Minor]** Switch `onCheckedChange` — already fixed
8. **[Minor]** Duplicate `"asus"` entry — already fixed
