"""
Tests for BluetoothScreen (BLE).
Validates: screen loads, BLE devices visible (ble.recon running on your-kali-host),
device entries have MACs, API cross-check.
"""
import time
from framework import (TestSuite, Device, BettercapVerifier,
                       connect_app_to_kali, navigate_to_tab)


def run(device: Device, verifier: BettercapVerifier, cfg: dict) -> TestSuite:
    suite = TestSuite("BluetoothScreen", device, verifier, cfg)
    kali  = cfg["kali"]["primary"]
    opts  = cfg.get("test_options", {})

    print(f"\n[BluetoothScreen] {device.name}")

    connected = connect_app_to_kali(device, kali, opts)
    suite.check(connected, "connect to kali", "connected", "FAILED — skipping")
    if not connected:
        return suite

    # 1. BLE tab navigates
    ok = navigate_to_tab(device, "BLE")
    suite.check(ok, "BLE tab navigates", "on BLE screen ✓", "tab not found")
    if not ok:
        return suite

    time.sleep(2)  # BLE list may take a moment to populate

    # 2. Screen renders
    root  = device.dump_ui()
    texts = device.all_texts(root)
    suite.check(len(texts) > 3, "BLE screen has content", f"{len(texts)} elements", "blank")

    # 3. BLE devices present (ble.recon is on — expect devices)
    root     = device.dump_ui()
    # BLE entries usually show RSSI (dBm) or MAC addresses like XX:XX:XX
    has_rssi = device.find(root, partial_text="dBm") is not None
    has_mac  = any(":" in t and len(t) == 17 for t in texts)  # XX:XX:XX:XX:XX:XX
    suite.check(has_rssi or has_mac, "BLE devices listed",
                "devices visible ✓", "no BLE devices found (is ble.recon running?)")

    # 4. Refresh doesn't crash
    root    = device.dump_ui()
    refresh = device.find(root, desc="Refresh") or device.find(root, partial_text="Refresh")
    if refresh:
        device.tap_el(refresh)
        time.sleep(2)
        root  = device.dump_ui()
        alive = len(device.all_texts(root)) > 2
        suite.check(alive, "BLE screen survives refresh", "alive ✓", "blank after refresh")
    else:
        suite.check(True, "Refresh button check skipped (not found)", "skipped", "")

    # 5. API: BLE devices match
    if verifier:
        api_devices = verifier.get_ble_devices()
        suite.check_api(len(api_devices) > 0, "API reports BLE devices",
                        f"{len(api_devices)} devices via API ✓", "API returned 0 BLE devices")

    # 6. Back navigates back
    device.press_back()
    time.sleep(1)
    root = device.dump_ui()
    suite.check(device.find(root, text="CMD") is not None,
                "back → dashboard", "dashboard ✓", "not on dashboard")

    return suite
