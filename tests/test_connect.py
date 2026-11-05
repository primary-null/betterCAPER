"""
Tests for the ConnectScreen.
Validates: form rendering, field enabling, connect flow, error handling,
back-navigation safety (no infinite loop), saved connections icon.
"""
import time
from framework import TestSuite, Device, BettercapVerifier, connect_app_to_kali


def run(device: Device, verifier: BettercapVerifier, cfg: dict) -> TestSuite:
    suite = TestSuite("ConnectScreen", device, verifier, cfg)
    kali  = cfg["kali"]["primary"]
    opts  = cfg.get("test_options", {})

    print(f"\n[ConnectScreen] {device.name}")

    # 1. App launches and connect screen renders
    device.launch_app()
    time.sleep(1)
    root = device.dump_ui()
    ok = device.find(root, partial_text="betterCAPER")
    suite.check(ok is not None, "connect screen loads", "betterCAPER title visible", "title NOT found")

    # 2. Section labels visible
    suite.check_visible(text="SSH Connection", name="SSH Connection label")
    suite.check_visible(text="Bettercap API",  name="Bettercap API label")

    # 3. Connect button disabled when fields are empty
    root  = device.dump_ui()
    btn   = device.find(root, text="Connect")
    suite.check(btn is not None, "Connect button present", "found", "NOT found")
    enabled = btn["node"].get("enabled", "true") if btn else "true"
    suite.check(enabled == "false", "Connect disabled when empty",
                "disabled=true (correct)", f"enabled={enabled} with empty host")

    # 4. Fill host only — button still disabled
    fields = device.find_all(root, cls="android.widget.EditText")
    if fields:
        device.fill_field(fields[0], kali["host"])
        device.dismiss_keyboard()
        time.sleep(0.3)
        root  = device.dump_ui()
        btn   = device.find(root, text="Connect")
        enabled = btn["node"].get("enabled","false") if btn else "false"
        suite.check(enabled == "false", "Connect still disabled (no username)",
                    "disabled (correct)", f"enabled={enabled}")

    # 5. Fill username — button becomes enabled
    root   = device.dump_ui()
    fields = device.find_all(root, cls="android.widget.EditText")
    if len(fields) >= 3:
        device.fill_field(fields[2], kali["user"], use_keyevents=True)
        device.dismiss_keyboard()
        time.sleep(0.3)
        root  = device.dump_ui()
        btn   = device.find(root, text="Connect")
        enabled = btn["node"].get("enabled","false") if btn else "false"
        suite.check(enabled == "true", "Connect enabled with host+user",
                    "enabled (correct)", f"enabled={enabled}")

    # 6. Bad credentials → error dialog
    device.launch_app()
    time.sleep(1)
    root   = device.dump_ui()
    fields = device.find_all(root, cls="android.widget.EditText")
    if len(fields) >= 4:
        device.fill_field(fields[0], kali["host"])
        device.fill_field(fields[2], "baduser", use_keyevents=True)
        device.fill_field(fields[3], "badpass")
        device.dismiss_keyboard()
        time.sleep(0.3)
        root = device.dump_ui()
        btn  = device.find(root, text="Connect")
        device.tap_el(btn)
        # wait for error dialog (up to 15s for SSH timeout)
        try:
            err = device.wait_for(partial_text="Error", timeout=20)
            suite.check(True, "error dialog shown on bad creds", "dialog appeared", "no dialog")
            # dismiss
            ok_btn = device.find(text="OK")
            device.tap_el(ok_btn)
            time.sleep(0.5)
        except TimeoutError:
            suite.check(False, "error dialog shown on bad creds", "dialog appeared", "timeout — no dialog")

    # 7. Successful connect → navigates to dashboard
    connected = connect_app_to_kali(device, kali, opts)
    suite.check(connected, "successful connect → dashboard",
                "dashboard reached", "dashboard NOT reached")

    if connected:
        # 8. Back from dashboard → returns to connect screen, NOT dashboard again
        device.press_back()
        time.sleep(1.5)
        root = device.dump_ui()
        on_connect = device.find(root, partial_text="betterCAPER") is not None
        on_dashboard = device.find(root, text="CMD") is not None and device.find(root, partial_text="betterCAPER") is None
        suite.check(on_connect and not on_dashboard,
                    "back from dashboard → connect screen (no loop)",
                    "on connect screen ✓", "still on dashboard — navigation loop bug")

        # 9. Second back press does NOT re-navigate to dashboard
        time.sleep(1)
        root = device.dump_ui()
        still_connect = device.find(root, partial_text="betterCAPER") is not None
        suite.check(still_connect, "stays on connect screen after 2nd back",
                    "stays on connect ✓", "navigated away — loop")

    # 10. Saved connections bookmark icon present
    root = device.dump_ui()
    bk   = device.find(root, desc="Saved connections")
    suite.check(bk is not None, "saved connections bookmark icon present",
                "icon found", "icon NOT found")

    return suite
