"""
Tests for WebViewScreen.
Validates: screen navigable, WebView renders or shows graceful error,
no crash when ui caplet not running.
"""
import time
from framework import (TestSuite, Device, BettercapVerifier,
                       connect_app_to_kali)


def _navigate_to_webui(device: Device) -> bool:
    root = device.dump_ui()
    more = device.find(root, desc="More options") or device.find(root, desc="More")
    if more:
        device.tap_el(more)
        time.sleep(0.5)
        root  = device.dump_ui()
        webui = device.find(root, text="Web UI") or device.find(root, partial_text="WebUI") \
              or device.find(root, partial_text="Web")
        if webui:
            device.tap_el(webui)
            time.sleep(2)
            return True
    return False


def run(device: Device, verifier: BettercapVerifier, cfg: dict) -> TestSuite:
    suite = TestSuite("WebViewScreen", device, verifier, cfg)
    kali  = cfg["kali"]["primary"]
    opts  = cfg.get("test_options", {})

    print(f"\n[WebViewScreen] {device.name}")

    connected = connect_app_to_kali(device, kali, opts)
    suite.check(connected, "connect to kali", "connected", "FAILED — skipping")
    if not connected:
        return suite

    ok = _navigate_to_webui(device)
    suite.check(ok, "navigate to Web UI screen", "navigated ✓", "NOT found in menu")
    if not ok:
        return suite

    time.sleep(3)  # WebView needs time to load

    # 1. Screen title shows
    suite.check_visible(partial="Web", name="Web UI title in app bar")

    # 2. Either WebView content OR graceful error (not crash)
    root      = device.dump_ui()
    texts     = device.all_texts(root)
    has_webv  = any("bettercap" in t.lower() or "http" in t.lower() for t in texts)
    has_error = any("error" in t.lower() or "caplet" in t.lower() or
                    "not initialized" in t.lower() or "port" in t.lower() for t in texts)
    has_retry = device.find(root, text="Retry") is not None
    suite.check(has_webv or has_error or has_retry,
                "shows web content or graceful error (not crash)",
                "content or error ✓", "blank/crashed — no content or error")

    # 3. Refresh button present
    root    = device.dump_ui()
    refresh = device.find(root, desc="Refresh")
    suite.check(refresh is not None, "Refresh button present", "found ✓", "NOT found")

    # 4. Refresh doesn't crash
    if refresh:
        device.tap_el(refresh)
        time.sleep(2)
        root  = device.dump_ui()
        alive = len(device.all_texts(root)) > 2
        suite.check(alive, "refresh doesn't crash", "alive ✓", "blank after refresh")

    # 5. Back navigates back
    device.press_back()
    time.sleep(1)
    root = device.dump_ui()
    suite.check(device.find(root, text="CMD") is not None,
                "back → dashboard", "dashboard ✓", "not on dashboard")

    return suite
