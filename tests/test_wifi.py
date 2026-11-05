"""
Tests for WifiScreen.
Validates: screen loads, AP list or graceful error, refresh.
Note: your-kali-host uses wlan0mon which may not be present — error handling tested.
"""
import time
from framework import (TestSuite, Device, BettercapVerifier,
                       connect_app_to_kali, navigate_to_tab, return_to_dashboard)


def run(device: Device, verifier: BettercapVerifier, cfg: dict) -> TestSuite:
    suite = TestSuite("WifiScreen", device, verifier, cfg)
    kali  = cfg["kali"]["primary"]
    opts  = cfg.get("test_options", {})

    print(f"\n[WifiScreen] {device.name}")

    connected = connect_app_to_kali(device, kali, opts)
    suite.check(connected, "connect to kali", "connected", "FAILED — skipping")
    if not connected:
        return suite

    # 1. WiFi tab navigates to WifiScreen
    ok = navigate_to_tab(device, "WiFi")
    suite.check(ok, "WiFi tab navigates", "on WiFi screen ✓", "tab not found")
    if not ok:
        return suite

    time.sleep(1.5)

    # 2. Screen renders (not blank, not crashed)
    root  = device.dump_ui()
    texts = device.all_texts(root)
    suite.check(len(texts) > 3, "WiFi screen has content",
                f"{len(texts)} elements ✓", "screen appears blank")

    # 3. Either AP list OR graceful error message — not a crash
    root     = device.dump_ui()
    has_aps  = device.find(root, partial_text="BSSID") is not None or \
               device.find(root, partial_text="SSID")  is not None or \
               device.find(root, partial_text="::")     is not None   # MAC
    has_err  = device.find(root, partial_text="error")  is not None or \
               device.find(root, partial_text="wlan")   is not None or \
               device.find(root, partial_text="No WiFi") is not None
    suite.check(has_aps or has_err, "shows APs or graceful error",
                "APs shown ✓" if has_aps else "error shown gracefully ✓",
                "blank screen — possibly crashed")

    # 4. Refresh button present
    root    = device.dump_ui()
    refresh = device.find(root, desc="Refresh") or device.find(root, partial_text="Refresh")
    suite.check(refresh is not None, "Refresh button on WiFi screen", "found ✓", "NOT found")

    # 5. Refresh doesn't crash
    if refresh:
        device.tap_el(refresh)
        time.sleep(2)
        root  = device.dump_ui()
        alive = len(device.all_texts(root)) > 2
        suite.check(alive, "WiFi screen survives refresh", "alive ✓", "appears blank after refresh")

    # 6. Back navigates back
    device.press_back()
    time.sleep(1)
    root = device.dump_ui()
    suite.check(device.find(root, text="CMD") is not None,
                "back → dashboard", "dashboard ✓", "not on dashboard")

    # 7. API: check wifi state
    if verifier:
        aps = verifier.get_wifi_aps()
        # may be 0 if no adapter — just check API responds
        session = verifier.get_session()
        suite.check_api(session is not None, "bettercap session reachable", "reachable ✓", "API unreachable")

    return suite
