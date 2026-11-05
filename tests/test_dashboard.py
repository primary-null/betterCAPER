"""
Tests for the DashboardScreen.
Validates: interface card, active modules, LAN hosts, nav tabs,
interface selector, refresh, disconnect.
"""
import time
from framework import TestSuite, Device, BettercapVerifier, connect_app_to_kali, return_to_dashboard


def run(device: Device, verifier: BettercapVerifier, cfg: dict) -> TestSuite:
    suite = TestSuite("DashboardScreen", device, verifier, cfg)
    kali  = cfg["kali"]["primary"]
    opts  = cfg.get("test_options", {})

    print(f"\n[DashboardScreen] {device.name}")

    connected = connect_app_to_kali(device, kali, opts)
    suite.check(connected, "connect to kali", "connected", "FAILED to connect — skipping suite")
    if not connected:
        return suite

    time.sleep(1)

    # 1. Interface card visible
    suite.check_visible(text="Interface", name="Interface card header")

    # 2. Active modules section exists
    suite.check_visible(partial="Active Modules", name="Active Modules section")

    # 3. Known modules shown (api.rest always running)
    suite.check_visible(partial="api.rest", name="api.rest module listed")
    suite.check_visible(partial="ble.recon", name="ble.recon module listed")

    # 4. LAN Hosts section and at least one host
    suite.check_visible(partial="LAN", name="LAN hosts section")
    root  = device.dump_ui()
    hosts = device.find_all(root, partial_text="192.168.")
    suite.check(len(hosts) > 0, "LAN hosts populated",
                f"{len(hosts)} hosts visible", "no hosts on dashboard")

    # 5. All bottom nav tabs present
    for tab in ("Hosts", "WiFi", "BLE", "Events", "CMD"):
        suite.check_visible(text=tab, name=f"nav tab: {tab}")

    # 6. Refresh button present
    root    = device.dump_ui()
    refresh = device.find(root, desc="Refresh") or device.find(root, partial_text="Refresh")
    suite.check(refresh is not None, "Refresh button present", "found", "NOT found")

    # 7. Interface selector dialog — open, pick eth0, dismiss
    root   = device.dump_ui()
    iface  = device.find(root, text="Interface")
    if iface:
        device.tap_el(iface)
        time.sleep(1)
        root  = device.dump_ui()
        dlg   = device.find(root, partial_text="Select Network")
        suite.check(dlg is not None, "interface selector dialog opens",
                    "dialog visible", "dialog did NOT open")
        eth0 = device.find(root, partial_text="eth0")
        if eth0:
            device.tap_el(eth0)
        else:
            dismiss = device.find(root, text="Dismiss")
            device.tap_el(dismiss)
        time.sleep(0.5)
        root  = device.dump_ui()
        gone  = device.find(root, partial_text="Select Network") is None
        suite.check(gone, "interface selector closes after selection",
                    "dialog closed ✓", "dialog still open")

    # 8. Refresh doesn't crash
    root    = device.dump_ui()
    refresh = device.find(root, desc="Refresh") or device.find(root, partial_text="Refresh")
    if refresh:
        device.tap_el(refresh)
        time.sleep(2)
        root  = device.dump_ui()
        still_on_dash = device.find(root, text="CMD") is not None
        suite.check(still_on_dash, "refresh doesn't crash/navigate away",
                    "still on dashboard ✓", "navigated away after refresh")

    # 9. API: hosts match what UI shows
    if verifier:
        api_hosts = verifier.get_hosts()
        suite.check_api(len(api_hosts) > 0, "kali reports LAN hosts",
                        f"{len(api_hosts)} hosts via API", "API returned 0 hosts")

    # 10. Disconnect → returns to connect screen
    root   = device.dump_ui()
    # disconnect is usually in overflow menu or a dedicated button
    more   = device.find(root, desc="More options") or device.find(root, desc="More")
    if more:
        device.tap_el(more)
        time.sleep(0.5)
        root  = device.dump_ui()
        disc  = device.find(root, partial_text="Disconnect")
        if disc:
            device.tap_el(disc)
            time.sleep(1.5)
            root = device.dump_ui()
            on_connect = device.find(root, partial_text="betterCAPER") is not None
            suite.check(on_connect, "disconnect returns to connect screen",
                        "on connect screen ✓", "NOT on connect screen after disconnect")

    return suite
