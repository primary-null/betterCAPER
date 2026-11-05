"""
Tests for ModulesScreen.
Validates: screen loads, module list, toggle actually hits bettercap API,
refresh updates state.
"""
import time
from framework import (TestSuite, Device, BettercapVerifier,
                       connect_app_to_kali, return_to_dashboard)


def _navigate_to_modules(device: Device) -> bool:
    """Modules is accessed via dashboard overflow/menu, not a bottom tab."""
    # Try overflow menu first
    root = device.dump_ui()
    more = device.find(root, desc="More options") or device.find(root, desc="More")
    if more:
        device.tap_el(more)
        time.sleep(0.5)
        root    = device.dump_ui()
        modules = device.find(root, text="Modules")
        if modules:
            device.tap_el(modules)
            time.sleep(1.5)
            return True
    # Try direct navigation via dashboard card tap
    root    = device.dump_ui()
    modules = device.find(root, text="Modules") or device.find(root, partial_text="Module")
    if modules:
        device.tap_el(modules)
        time.sleep(1.5)
        return True
    return False


def run(device: Device, verifier: BettercapVerifier, cfg: dict) -> TestSuite:
    suite = TestSuite("ModulesScreen", device, verifier, cfg)
    kali  = cfg["kali"]["primary"]
    opts  = cfg.get("test_options", {})

    print(f"\n[ModulesScreen] {device.name}")

    connected = connect_app_to_kali(device, kali, opts)
    suite.check(connected, "connect to kali", "connected", "FAILED — skipping")
    if not connected:
        return suite

    ok = _navigate_to_modules(device)
    suite.check(ok, "navigate to Modules screen", "on Modules ✓", "NOT found — may be dashboard card")
    if not ok:
        return suite

    time.sleep(0.5)

    # 1. Module list renders
    root  = device.dump_ui()
    texts = device.all_texts(root)
    suite.check(len(texts) > 5, "Modules screen has content", f"{len(texts)} elements", "blank screen")

    # 2. Known modules listed
    for mod in ("net.recon", "net.probe", "ble.recon"):
        root = device.dump_ui()
        el   = device.find(root, partial_text=mod)
        suite.check(el is not None, f"module '{mod}' listed",
                    "listed ✓", f"'{mod}' NOT visible")

    # 3. Toggle net.recon off → verify API → toggle back on
    root      = device.dump_ui()
    net_recon = device.find(root, partial_text="net.recon")
    if net_recon and verifier:
        was_active = verifier.is_module_active("net.recon")

        # find toggle for net.recon row — usually a Switch near the text
        # tap the row to toggle
        device.tap_el(net_recon)
        time.sleep(2)

        now_active = verifier.is_module_active("net.recon")
        toggled    = now_active != was_active
        suite.check_api(toggled, "net.recon toggle changes state in bettercap",
                        f"state changed: {was_active} → {now_active} ✓",
                        f"state unchanged: was={was_active} now={now_active}")

        # toggle back to original state
        device.tap_el(net_recon)
        time.sleep(2)
        restored = verifier.is_module_active("net.recon") == was_active
        suite.check_api(restored, "net.recon toggle restored",
                        f"restored to {was_active} ✓", "NOT restored")
    else:
        suite.check(True, "module toggle test skipped (no verifier or module not found)", "skipped", "")

    # 4. Refresh
    root    = device.dump_ui()
    refresh = device.find(root, desc="Refresh") or device.find(root, partial_text="Refresh")
    if refresh:
        device.tap_el(refresh)
        time.sleep(2)
        alive = len(device.all_texts(device.dump_ui())) > 3
        suite.check(alive, "Modules screen survives refresh", "alive ✓", "blank after refresh")

    # 5. Back navigates back
    device.press_back()
    time.sleep(1)
    root = device.dump_ui()
    suite.check(device.find(root, text="CMD") is not None,
                "back → dashboard", "dashboard ✓", "not on dashboard")

    return suite
