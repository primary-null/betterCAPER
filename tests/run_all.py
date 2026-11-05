#!/usr/bin/env python3
"""
betterCAPER automated test runner.

Usage:
  python3 tests/run_all.py                         # all devices, all suites
  python3 tests/run_all.py --device pixel8         # one device
  python3 tests/run_all.py --suite cmd,ble         # specific suites
  python3 tests/run_all.py --device pixel8 --suite connect,dashboard,cmd
  python3 tests/run_all.py --kali fallback         # use kali fallback box
  python3 tests/run_all.py --list                  # list available suites
"""

import sys
import argparse
import time
import json
import traceback
from pathlib import Path
from datetime import datetime

# ensure tests/ is on path
sys.path.insert(0, str(Path(__file__).parent))

from framework import load_config, make_devices, make_verifier, connect_app_to_kali, ARTIFACTS

import test_connect
import test_dashboard
import test_cmd
import test_events
import test_wifi
import test_ble
import test_modules
import test_spoofing
import test_proxies
import test_webui
import test_caplets
import test_pcaps

SUITES = {
    "connect":   test_connect.run,
    "dashboard": test_dashboard.run,
    "cmd":       test_cmd.run,
    "events":    test_events.run,
    "wifi":      test_wifi.run,
    "ble":       test_ble.run,
    "modules":   test_modules.run,
    "spoofing":  test_spoofing.run,
    "proxies":   test_proxies.run,
    "webui":     test_webui.run,
    "caplets":   test_caplets.run,
    "pcaps":     test_pcaps.run,
}

# suites that must run sequentially (stateful: leave app on dashboard)
ORDERED_SUITES = [
    "connect", "dashboard", "cmd", "events",
    "wifi", "ble", "modules", "spoofing",
    "proxies", "webui", "caplets", "pcaps",
]


def run_suite(name: str, device, verifier, cfg: dict):
    fn = SUITES[name]
    try:
        return fn(device, verifier, cfg)
    except Exception as e:
        print(f"  !! EXCEPTION in {name}: {e}")
        traceback.print_exc()
        from framework import TestSuite
        suite = TestSuite(name, device, verifier, cfg)
        suite.check(False, f"suite raised exception", "ok", str(e))
        return suite


def print_banner(text: str):
    w = 70
    print("\n" + "=" * w)
    print(f"  {text}")
    print("=" * w)


def main():
    parser = argparse.ArgumentParser(description="betterCAPER test runner")
    parser.add_argument("--device",  help="comma-separated device names (e.g. pixel8,samsung)")
    parser.add_argument("--suite",   help="comma-separated suite names (e.g. cmd,ble)")
    parser.add_argument("--kali",    choices=["primary","fallback"], default="primary")
    parser.add_argument("--list",    action="store_true", help="list suites and devices")
    args = parser.parse_args()

    cfg = load_config()

    if args.list:
        print("Suites:", ", ".join(ORDERED_SUITES))
        print("Devices:", ", ".join(cfg["devices"].keys()))
        return

    # resolve devices
    device_filter = [d.strip() for d in args.device.split(",")] if args.device else None
    devices = make_devices(cfg, only=device_filter)
    if not devices:
        print("No matching devices. Check --device or config.json.")
        sys.exit(1)

    # resolve suites
    suite_filter = [s.strip() for s in args.suite.split(",")] if args.suite else None
    suites_to_run = [s for s in ORDERED_SUITES if suite_filter is None or s in suite_filter]
    if not suites_to_run:
        print(f"No matching suites. Available: {', '.join(ORDERED_SUITES)}")
        sys.exit(1)

    # build verifier
    use_fallback = (args.kali == "fallback")
    verifier = make_verifier(cfg, use_fallback=use_fallback)

    start_time = time.time()
    timestamp  = datetime.now().strftime("%Y%m%d_%H%M%S")
    all_results = {}  # device_name → {suite_name → TestSuite}

    print_banner(f"betterCAPER test run — {timestamp}")
    print(f"  Devices : {', '.join(devices.keys())}")
    print(f"  Suites  : {', '.join(suites_to_run)}")
    print(f"  Kali    : {cfg['kali'][args.kali]['host']} ({args.kali})")

    for dev_name, device in devices.items():
        print_banner(f"Device: {device.name} [{device.serial}]")
        all_results[dev_name] = {}

        for suite_name in suites_to_run:
            print(f"\n  → {suite_name}")
            suite = run_suite(suite_name, device, verifier, cfg)
            all_results[dev_name][suite_name] = suite
            print(f"  {suite.summary()}")
            if suite.failed():
                for r in suite.failed():
                    print(f"      ✗ {r.name}: {r.msg}")
                    if r.screenshot:
                        print(f"        screenshot: {r.screenshot}")

    # --- Final report ---
    print_banner("RESULTS")
    total_pass = total_fail = 0

    for dev_name, suites in all_results.items():
        print(f"\n{dev_name}:")
        for suite_name, suite in suites.items():
            p = sum(1 for r in suite.results if r.passed)
            f = sum(1 for r in suite.results if not r.passed)
            total_pass += p
            total_fail += f
            status = "✓" if f == 0 else "✗"
            print(f"  {status} {suite_name:15s} {p}/{p+f}")

    elapsed = time.time() - start_time
    print(f"\nTotal: {total_pass} passed, {total_fail} failed  ({elapsed:.1f}s)")
    print(f"Artifacts: {ARTIFACTS}/")

    # save JSON report
    report_path = ARTIFACTS / f"report_{timestamp}.json"
    report = {
        "timestamp": timestamp,
        "elapsed_sec": round(elapsed, 1),
        "kali": cfg["kali"][args.kali]["host"],
        "results": {}
    }
    for dev_name, suites in all_results.items():
        report["results"][dev_name] = {}
        for suite_name, suite in suites.items():
            report["results"][dev_name][suite_name] = [
                {"name": r.name, "passed": r.passed, "msg": r.msg,
                 "screenshot": r.screenshot}
                for r in suite.results
            ]
    with open(report_path, "w") as f:
        json.dump(report, f, indent=2)
    print(f"JSON report: {report_path}")

    sys.exit(0 if total_fail == 0 else 1)


if __name__ == "__main__":
    main()
