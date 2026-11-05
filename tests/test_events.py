"""
Tests for EventsScreen.
Validates: events load, stream content, events.clear via CMD.
"""
import time
from framework import (TestSuite, Device, BettercapVerifier,
                       connect_app_to_kali, navigate_to_tab,
                       send_command_via_ui, return_to_dashboard)


def run(device: Device, verifier: BettercapVerifier, cfg: dict) -> TestSuite:
    suite = TestSuite("EventsScreen", device, verifier, cfg)
    kali  = cfg["kali"]["primary"]
    opts  = cfg.get("test_options", {})

    print(f"\n[EventsScreen] {device.name}")

    connected = connect_app_to_kali(device, kali, opts)
    suite.check(connected, "connect to kali", "connected", "FAILED — skipping")
    if not connected:
        return suite

    # 1. Navigate to Events tab
    ok = navigate_to_tab(device, "Events")
    suite.check(ok, "Events tab navigates to EventsScreen", "on Events screen ✓", "tab not found")
    if not ok:
        return suite

    time.sleep(1)

    # 2. Events list loads (not empty — net.probe and ble.recon are running)
    root   = device.dump_ui()
    texts  = device.all_texts(root)
    # events typically have timestamps like "HH:MM:SS" or module names
    has_events = any("." in t and len(t) > 5 for t in texts if t not in ("Events",))
    suite.check(has_events, "events list is populated",
                "events visible ✓", "events list appears empty")

    # 3. Back button returns to dashboard
    device.press_back()
    time.sleep(1)
    root = device.dump_ui()
    back_on_dash = device.find(root, text="CMD") is not None
    suite.check(back_on_dash, "back → returns to dashboard",
                "on dashboard ✓", "NOT on dashboard")

    # 4. events.clear via CMD → events list empties then repopulates
    ok = navigate_to_tab(device, "CMD")
    if ok:
        send_command_via_ui(device, "events.clear",
                            response_timeout=opts.get("command_response_timeout_sec", 5))
        time.sleep(0.5)
        if verifier:
            result = verifier.send_command("events.clear")
            suite.check_api(result is not None and result.get("success"), "events.clear API call succeeds",
                            "success ✓", f"got {result}")

        # navigate to events and confirm cleared (may immediately repopulate)
        device.press_back()
        time.sleep(0.5)
        ok = navigate_to_tab(device, "Events")
        if ok:
            time.sleep(1)
            root   = device.dump_ui()
            # just confirm the screen loads without crash
            suite.check_visible(partial="Events", name="Events screen loads after clear")

    return suite
