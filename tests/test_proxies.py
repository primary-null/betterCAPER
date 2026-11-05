"""
Tests for ProxyScreen.
Validates: screen loads, HTTP/HTTPS/TCP proxy sections, toggle hits API.
"""
import time
from framework import (TestSuite, Device, BettercapVerifier,
                       connect_app_to_kali)


def _navigate_to_proxies(device: Device) -> bool:
    root = device.dump_ui()
    more = device.find(root, desc="More options") or device.find(root, desc="More")
    if more:
        device.tap_el(more)
        time.sleep(0.5)
        root = device.dump_ui()
        prx  = device.find(root, text="Proxies") or device.find(root, partial_text="Proxy")
        if prx:
            device.tap_el(prx)
            time.sleep(1.5)
            return True
    root = device.dump_ui()
    prx  = device.find(root, text="Proxies") or device.find(root, partial_text="Proxy")
    if prx:
        device.tap_el(prx)
        time.sleep(1.5)
        return True
    return False


def run(device: Device, verifier: BettercapVerifier, cfg: dict) -> TestSuite:
    suite = TestSuite("ProxyScreen", device, verifier, cfg)
    kali  = cfg["kali"]["primary"]
    opts  = cfg.get("test_options", {})

    print(f"\n[ProxyScreen] {device.name}")

    connected = connect_app_to_kali(device, kali, opts)
    suite.check(connected, "connect to kali", "connected", "FAILED — skipping")
    if not connected:
        return suite

    ok = _navigate_to_proxies(device)
    suite.check(ok, "navigate to Proxies screen", "on Proxies ✓", "NOT navigated")
    if not ok:
        return suite

    time.sleep(0.5)

    # 1. Proxy sections visible
    suite.check_visible(partial="HTTP",  name="HTTP proxy section")
    suite.check_visible(partial="HTTPS", name="HTTPS proxy section")
    suite.check_visible(partial="TCP",   name="TCP proxy section")

    # 2. HTTP proxy toggle → verify API
    root     = device.dump_ui()
    http_ctrl = device.find(root, partial_text="http.proxy") or device.find(root, partial_text="HTTP Proxy")
    if http_ctrl and verifier:
        was_active = verifier.is_module_active("http.proxy")

        device.tap_el(http_ctrl)
        time.sleep(2)

        now_active = verifier.is_module_active("http.proxy")
        toggled    = now_active != was_active
        suite.check_api(toggled, "http.proxy toggle changes state",
                        f"{was_active} → {now_active} ✓",
                        f"unchanged: was={was_active}")

        # restore
        if now_active:
            device.tap_el(http_ctrl)
            time.sleep(2)
            suite.check_api(not verifier.is_module_active("http.proxy"),
                            "http.proxy turned back off", "off ✓", "still running")
    else:
        suite.check(True, "http.proxy toggle skipped", "skipped", "")

    # 3. Port fields present (config fields for proxy ports)
    root     = device.dump_ui()
    port_fld = device.find(root, partial_text="Port") or device.find(root, partial_text="port")
    suite.check(port_fld is not None, "proxy port field present",
                "field found ✓", "no port field")

    # 4. Back navigates back
    device.press_back()
    time.sleep(1)
    root = device.dump_ui()
    suite.check(device.find(root, text="CMD") is not None,
                "back → dashboard", "dashboard ✓", "not on dashboard")

    return suite
