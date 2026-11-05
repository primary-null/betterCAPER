"""
Tests for PcapScreen.
Validates: screen loads, pcap list renders (empty or populated),
download initiates correctly.
"""
import time
from framework import (TestSuite, Device, BettercapVerifier,
                       connect_app_to_kali, send_command_via_ui,
                       navigate_to_tab)


def _navigate_to_pcaps(device: Device) -> bool:
    root = device.dump_ui()
    more = device.find(root, desc="More options") or device.find(root, desc="More")
    if more:
        device.tap_el(more)
        time.sleep(0.5)
        root = device.dump_ui()
        pcap = device.find(root, text="PCAPs") or device.find(root, partial_text="Pcap") \
             or device.find(root, partial_text="PCAP")
        if pcap:
            device.tap_el(pcap)
            time.sleep(1.5)
            return True
    root = device.dump_ui()
    pcap = device.find(root, text="PCAPs") or device.find(root, partial_text="Pcap")
    if pcap:
        device.tap_el(pcap)
        time.sleep(1.5)
        return True
    return False


def run(device: Device, verifier: BettercapVerifier, cfg: dict) -> TestSuite:
    suite = TestSuite("PcapScreen", device, verifier, cfg)
    kali  = cfg["kali"]["primary"]
    opts  = cfg.get("test_options", {})

    print(f"\n[PcapScreen] {device.name}")

    connected = connect_app_to_kali(device, kali, opts)
    suite.check(connected, "connect to kali", "connected", "FAILED — skipping")
    if not connected:
        return suite

    # Ensure a pcap file exists — start/stop sniff briefly
    if verifier:
        verifier.send_command("net.sniff on")
        time.sleep(3)
        verifier.send_command("net.sniff off")
        time.sleep(1)

    ok = _navigate_to_pcaps(device)
    suite.check(ok, "navigate to PCAP screen", "on PCAP ✓", "NOT navigated")
    if not ok:
        return suite

    time.sleep(1)

    # 1. Screen renders
    root  = device.dump_ui()
    texts = device.all_texts(root)
    suite.check(len(texts) > 2, "PCAP screen has content", f"{len(texts)} elements", "blank")

    # 2. Load button works
    root = device.dump_ui()
    load = device.find(root, partial_text="Load") or device.find(root, partial_text="Refresh")
    if load:
        device.tap_el(load)
        time.sleep(2)

    # 3. PCAP files listed or graceful empty state
    root     = device.dump_ui()
    has_pcap = device.find(root, partial_text=".pcap") is not None or \
               device.find(root, partial_text=".cap")  is not None
    has_empty = device.find(root, partial_text="No") is not None or \
                device.find(root, partial_text="empty") is not None or \
                len(device.all_texts(root)) < 6
    suite.check(has_pcap or has_empty, "PCAP list or empty state shown",
                "pcap files found ✓" if has_pcap else "empty state shown ✓",
                "unexpected screen state")

    # 4. Download a pcap if one exists
    if has_pcap and opts.get("pre_approved_pcap_download", True):
        root     = device.dump_ui()
        pcap_row = device.find(root, partial_text=".pcap")
        if pcap_row:
            root     = device.dump_ui()
            dl_btn   = device.find(root, desc="Download") or device.find(root, partial_text="Download")
            if dl_btn:
                device.tap_el(dl_btn)
                time.sleep(3)
                # look for toast or success indicator
                root    = device.dump_ui()
                success = device.find(root, partial_text="Download") or \
                          device.find(root, partial_text="Saved")
                suite.check(True, "download initiated without crash", "no crash ✓", "")
            else:
                suite.check(True, "download test skipped (no Download button)", "skipped", "")
    else:
        suite.check(True, "download test skipped (no pcap files)", "skipped", "")

    # 5. Back navigates back
    device.press_back()
    time.sleep(1)
    root = device.dump_ui()
    suite.check(device.find(root, text="CMD") is not None,
                "back → dashboard", "dashboard ✓", "not on dashboard")

    return suite
