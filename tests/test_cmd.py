"""
Tests for CommandScreen.
Validates: collapse/expand, suggestions, command send/receive, history,
auto-expand, clear, API verification of state-changing commands.
"""
import time
from framework import (TestSuite, Device, BettercapVerifier,
                       connect_app_to_kali, navigate_to_tab,
                       send_command_via_ui, return_to_dashboard)


def _go_cmd(device: Device) -> bool:
    return navigate_to_tab(device, "CMD")


def run(device: Device, verifier: BettercapVerifier, cfg: dict) -> TestSuite:
    suite = TestSuite("CommandScreen", device, verifier, cfg)
    kali  = cfg["kali"]["primary"]
    opts  = cfg.get("test_options", {})
    resp_t = opts.get("command_response_timeout_sec", 5)

    print(f"\n[CommandScreen] {device.name}")

    connected = connect_app_to_kali(device, kali, opts)
    suite.check(connected, "connect to kali", "connected", "FAILED — skipping suite")
    if not connected:
        return suite

    # navigate to CMD
    ok = _go_cmd(device)
    suite.check(ok, "navigate to CMD tab", "on CMD screen", "CMD tab not found")
    if not ok:
        return suite

    time.sleep(0.5)

    # 1. OUTPUT card visible
    suite.check_visible(text="OUTPUT", name="OUTPUT card present")

    # 2. Collapse button present
    root = device.dump_ui()
    col  = device.find(root, desc="Collapse")
    suite.check(col is not None, "collapse button present", "found ✓", "NOT found")

    # 3. Collapse works — suggestion list fills screen
    if col:
        device.tap_el(col)
        time.sleep(0.6)
        root = device.dump_ui()
        col_gone = device.find(root, desc="Collapse") is None
        exp_btn  = device.find(root, desc="Expand")
        suite.check(exp_btn is not None, "collapsed → Expand button appears",
                    "Expand visible ✓", "Expand NOT visible after collapse")

        # suggestions visible when collapsed
        suite.check_visible(text="Network",  name="Network group visible when collapsed")
        suite.check_visible(text="WiFi",     name="WiFi group visible when collapsed")
        suite.check_visible(text="BLE",      name="BLE group visible when collapsed")

        # expand again
        device.tap_el(exp_btn)
        time.sleep(0.5)

    # 4. All command groups present (may require scrolling)
    for group in ("Network", "ARP Spoof", "WiFi", "BLE", "Sniffing", "General"):
        root = device.dump_ui()
        el   = device.find(root, text=group)
        if not el:
            # scroll suggestion list down to find it
            device.swipe_up(y_start=2600, y_end=2200)
            time.sleep(0.3)
            root = device.dump_ui()
            el   = device.find(root, text=group)
        suite.check(el is not None, f"command group '{group}' visible",
                    "visible ✓", f"NOT found (may be off-screen)")

    # 5. Collapse, tap suggestion → fills input
    root = device.dump_ui()
    col  = device.find(root, desc="Collapse")
    if col:
        device.tap_el(col)
        time.sleep(0.5)

    root    = device.dump_ui()
    ns_row  = device.find(root, text="net.show")
    if ns_row:
        device.tap_el(ns_row)
        time.sleep(0.3)
        root  = device.dump_ui()
        field = device.find(root, cls="android.widget.EditText")
        val   = field["node"].get("text", "") if field else ""
        suite.check(val == "net.show", "tap suggestion fills input",
                    f"input = '{val}' ✓", f"input = '{val}' (expected 'net.show')")
        # clear it
        device.select_all_del()

    # --- run commands and verify output ---

    def run_cmd_ui(cmd, expect_in_output=None):
        """Helper: type cmd, send, wait, check output contains text."""
        # collapse first so input is reachable
        root = device.dump_ui()
        exp  = device.find(root, desc="Expand")
        if exp:
            device.tap_el(exp)
            time.sleep(0.4)

        output = send_command_via_ui(device, cmd, response_timeout=resp_t)
        ok = output is not None
        suite.check(ok, f"'{cmd}' returns output",
                    f"got response ✓", "no output received")

        if expect_in_output and output:
            has_it = expect_in_output.lower() in output.lower()
            suite.check(has_it, f"'{cmd}' output contains '{expect_in_output}'",
                        f"found ✓", f"not in: {output[:80]}")

        # auto-expand: output box should now be expanded
        root = device.dump_ui()
        col  = device.find(root, desc="Collapse")
        suite.check(col is not None, f"output auto-expanded after '{cmd}'",
                    "expanded ✓", "still collapsed — auto-expand not triggered")
        return output

    # 6. net.show
    run_cmd_ui("net.show", expect_in_output="success")

    # 7. active
    run_cmd_ui("active", expect_in_output="api.rest")

    # 8. ble.show
    run_cmd_ui("ble.show", expect_in_output="success")

    # 9. wifi.show — expect error (no wlan0mon on your-kali-host)
    output = send_command_via_ui(device, "wifi.show", response_timeout=resp_t)
    suite.check(output is not None, "'wifi.show' returns something (error ok)",
                "got response ✓", "no response")

    # 10. net.sniff on — verify via API
    run_cmd_ui("net.sniff on")
    time.sleep(1)
    if verifier:
        active = verifier.is_module_active("net.sniff")
        suite.check_api(active, "net.sniff running after 'net.sniff on'",
                        "API: running ✓", "API: NOT running")
    # turn it off
    run_cmd_ui("net.sniff off")
    time.sleep(1)
    if verifier:
        active = verifier.is_module_active("net.sniff")
        suite.check_api(not active, "net.sniff stopped after 'net.sniff off'",
                        "API: stopped ✓", "API: still running")

    # 11. History tracks commands
    root    = device.dump_ui()
    history = device.find(root, text="History")
    suite.check(history is not None, "History section visible", "found ✓", "NOT found")

    hist_items = device.find_all(root, partial_text="net.")
    suite.check(len(hist_items) >= 2, "History has multiple entries",
                f"{len(hist_items)} entries ✓", "history empty or too short")

    # 12. Clear output
    root     = device.dump_ui()
    clear_btn = device.find(root, desc="Clear")
    if clear_btn:
        device.tap_el(clear_btn)
        time.sleep(0.5)
        root  = device.dump_ui()
        ready = device.find(root, text="Ready...")
        suite.check(ready is not None, "Clear resets output to 'Ready...'",
                    "Ready... visible ✓", "output not cleared")

    # 13. Invalid command shows error
    output = send_command_via_ui(device, "thisisnotacmd", response_timeout=resp_t)
    suite.check(output is not None, "invalid command returns error",
                "got error response ✓", "no response for invalid cmd")

    return suite
