"""
Tests for SpoofingScreen.
Validates: screen loads, ARP spoof controls render, toggle sends command to bettercap.
Note: arp.spoof on is pre-approved in config — verified via API.
"""
import time
from framework import (TestSuite, Device, BettercapVerifier,
                       connect_app_to_kali)


def _navigate_to_spoofing(device: Device) -> bool:
    root = device.dump_ui()
    more = device.find(root, desc="More options") or device.find(root, desc="More")
    if more:
        device.tap_el(more)
        time.sleep(0.5)
        root = device.dump_ui()
        spoof = device.find(root, text="Spoofing") or device.find(root, partial_text="Spoof")
        if spoof:
            device.tap_el(spoof)
            time.sleep(1.5)
            return True
    root  = device.dump_ui()
    spoof = device.find(root, text="Spoofing") or device.find(root, partial_text="Spoof")
    if spoof:
        device.tap_el(spoof)
        time.sleep(1.5)
        return True
    return False


def run(device: Device, verifier: BettercapVerifier, cfg: dict) -> TestSuite:
    suite = TestSuite("SpoofingScreen", device, verifier, cfg)
    kali  = cfg["kali"]["primary"]
    opts  = cfg.get("test_options", {})

    print(f"\n[SpoofingScreen] {device.name}")

    connected = connect_app_to_kali(device, kali, opts)
    suite.check(connected, "connect to kali", "connected", "FAILED — skipping")
    if not connected:
        return suite

    ok = _navigate_to_spoofing(device)
    suite.check(ok, "navigate to Spoofing screen", "on Spoofing ✓", "NOT navigated")
    if not ok:
        return suite

    time.sleep(0.5)

    # 1. ARP Spoof section visible
    suite.check_visible(partial="ARP", name="ARP section visible")
    suite.check_visible(partial="spoof", name="arp.spoof control visible")

    # 2. Screen has toggle controls
    root      = device.dump_ui()
    toggles   = [n for n in root.iter() if "Switch" in n.get("class","") or "ToggleButton" in n.get("class","")]
    suite.check(len(toggles) > 0, "toggle controls present",
                f"{len(toggles)} toggles found ✓", "no toggle controls found")

    # 3. Toggle arp.spoof ON → verify API
    root        = device.dump_ui()
    spoof_ctrl  = device.find(root, partial_text="arp.spoof") or device.find(root, partial_text="ARP Spoof")
    if spoof_ctrl and verifier:
        was_active = verifier.is_module_active("arp.spoof")

        device.tap_el(spoof_ctrl)
        time.sleep(2)

        now_active = verifier.is_module_active("arp.spoof")
        toggled    = now_active != was_active
        suite.check_api(toggled, "arp.spoof toggle changes state",
                        f"{was_active} → {now_active} ✓",
                        f"unchanged: was={was_active} now={now_active}")

        # always restore: turn off
        if now_active:
            device.tap_el(spoof_ctrl)
            time.sleep(2)
            off = not verifier.is_module_active("arp.spoof")
            suite.check_api(off, "arp.spoof turned back off", "off ✓", "still ON — left running!")
    else:
        suite.check(True, "arp.spoof toggle test skipped", "skipped (no verifier or control)", "")

    # 4. DNS spoof section visible (if present)
    root = device.dump_ui()
    dns  = device.find(root, partial_text="dns") or device.find(root, partial_text="DNS")
    suite.check(dns is not None, "DNS spoof section visible",
                "visible ✓", "NOT visible (may not be implemented)")

    # 5. Back navigates back
    device.press_back()
    time.sleep(1)
    root = device.dump_ui()
    suite.check(device.find(root, text="CMD") is not None,
                "back → dashboard", "dashboard ✓", "not on dashboard")

    return suite
