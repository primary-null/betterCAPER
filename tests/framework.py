"""
betterCAPER Test Framework
Core: Device abstraction, BettercapVerifier, TestSuite, helpers.
"""

import subprocess
import time
import os
import json
import xml.etree.ElementTree as ET
import re
from pathlib import Path
from datetime import datetime

TESTS_DIR  = Path(__file__).parent
ARTIFACTS  = TESTS_DIR / "artifacts"
CONFIG_PATH = TESTS_DIR / "config.json"
ARTIFACTS.mkdir(exist_ok=True)

APP_PACKAGE = "com.bettercaper"
APP_ACTIVITY = "com.bettercaper/.MainActivity"

# ---------------------------------------------------------------------------
# Config
# ---------------------------------------------------------------------------

def load_config() -> dict:
    if not CONFIG_PATH.exists():
        raise FileNotFoundError(
            "tests/config.json not found. "
            "Copy tests/config.json.template → tests/config.json and fill in credentials."
        )
    with open(CONFIG_PATH) as f:
        return json.load(f)


# ---------------------------------------------------------------------------
# Device
# ---------------------------------------------------------------------------

KEYMAP = {
    **{str(i): f"KEYCODE_{i}" for i in range(10)},
    ".": "KEYCODE_PERIOD", "-": "KEYCODE_MINUS", "_": "KEYCODE_UNDERSCORE",
    " ": "KEYCODE_SPACE",
    **{c: f"KEYCODE_{c.upper()}" for c in "abcdefghijklmnopqrstuvwxyz"},
}


class Device:
    """Wraps ADB for a single Android device, local or via SSH bridge."""

    def __init__(self, serial: str, name: str,
                 bridge_host: str = None, bridge_user: str = None,
                 adb_path: str = "~/Android/Sdk/platform-tools/adb"):
        self.serial = serial
        self.name   = name
        self.bridge_host = bridge_host
        self.bridge_user = bridge_user
        self.adb_path    = adb_path
        self.is_remote   = bridge_host is not None

    # --- raw ADB -------------------------------------------------------

    def _run(self, cmd: list, binary=False, timeout=30) -> subprocess.CompletedProcess:
        if binary:
            return subprocess.run(cmd, capture_output=True, timeout=timeout)
        return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)

    def adb(self, *args, binary=False, timeout=30):
        if self.is_remote:
            remote_cmd = f"{self.adb_path} -s {self.serial} " + " ".join(str(a) for a in args)
            cmd = ["ssh", f"{self.bridge_user}@{self.bridge_host}", remote_cmd]
        else:
            cmd = ["adb", "-s", self.serial] + [str(a) for a in args]
        return self._run(cmd, binary=binary, timeout=timeout)

    def shell(self, *args, timeout=30):
        return self.adb("shell", *args, timeout=timeout)

    # --- UI dump -------------------------------------------------------

    def dump_ui(self):
        """Return parsed XML root of current UI hierarchy."""
        if self.is_remote:
            remote_cmd = (
                f"{self.adb_path} -s {self.serial} shell uiautomator dump /sdcard/ui.xml "
                f"&& {self.adb_path} -s {self.serial} shell cat /sdcard/ui.xml"
            )
            result = self._run(["ssh", f"{self.bridge_user}@{self.bridge_host}", remote_cmd], timeout=20)
            xml_str = result.stdout
        else:
            self.adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
            xml_str = self.adb("shell", "cat", "/sdcard/ui.xml").stdout

        try:
            return ET.fromstring(xml_str)
        except ET.ParseError:
            return None

    # --- Element finders -----------------------------------------------

    @staticmethod
    def _parse_bounds(bounds_str):
        m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', bounds_str or "")
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            return {"cx": (x1+x2)//2, "cy": (y1+y2)//2, "x1": x1, "y1": y1, "x2": x2, "y2": y2}
        return None

    def _match(self, node, text=None, partial_text=None, desc=None, cls=None):
        if text         and node.get("text")         != text:          return False
        if partial_text and partial_text not in node.get("text", ""):  return False
        if desc         and node.get("content-desc") != desc:          return False
        if cls          and cls not in node.get("class", ""):          return False
        return True

    def find(self, root=None, **kw):
        """Find first matching element; returns dict with cx/cy or None."""
        if root is None:
            root = self.dump_ui()
        if root is None:
            return None
        for node in root.iter():
            if self._match(node, **kw):
                b = self._parse_bounds(node.get("bounds", ""))
                if b:
                    b["text"] = node.get("text", "")
                    b["desc"] = node.get("content-desc", "")
                    b["node"] = node
                    return b
        return None

    def find_all(self, root=None, **kw):
        """Find all matching elements."""
        if root is None:
            root = self.dump_ui()
        results = []
        if root is None:
            return results
        for node in root.iter():
            if self._match(node, **kw):
                b = self._parse_bounds(node.get("bounds", ""))
                if b:
                    b["text"] = node.get("text", "")
                    b["desc"] = node.get("content-desc", "")
                    b["node"] = node
                    results.append(b)
        return results

    def all_texts(self, root=None):
        if root is None:
            root = self.dump_ui()
        return [n.get("text","") for n in root.iter() if n.get("text","").strip()] if root else []

    # --- Interactions --------------------------------------------------

    def tap(self, x, y):
        self.shell("input", "tap", str(int(x)), str(int(y)))
        time.sleep(0.35)

    def tap_el(self, el):
        if el:
            self.tap(el["cx"], el["cy"])

    def keyevent(self, *keys):
        self.shell("input", "keyevent", *[str(k) for k in keys])
        time.sleep(0.15)

    def select_all_del(self):
        """Select all text in focused field and delete it."""
        self.shell("input", "keycombination", "113", "29")   # Ctrl+A
        time.sleep(0.1)
        self.keyevent("KEYCODE_DEL")

    def type_chars(self, text: str):
        """Type text char-by-char via keyevents to bypass autocorrect."""
        for ch in text:
            kc = KEYMAP.get(ch)
            if kc:
                self.keyevent(kc)
            else:
                self.shell("input", "text", ch)
        time.sleep(0.15)

    def input_text(self, text: str):
        """Raw input text (fast but may trigger autocorrect)."""
        self.shell("input", "text", text)
        time.sleep(0.15)

    def fill_field(self, el, text: str, use_keyevents=False):
        """Tap field, clear it, type text."""
        self.tap_el(el)
        time.sleep(0.2)
        self.select_all_del()
        if use_keyevents:
            self.type_chars(text)
        else:
            self.input_text(text)

    def dismiss_keyboard(self):
        self.keyevent("KEYCODE_BACK")
        time.sleep(0.3)

    def press_back(self):
        """Back navigation: edge swipe works on Android 14 predictive back."""
        self.shell("input", "swipe", "20", "900", "400", "900", "200")
        time.sleep(0.7)

    def swipe_up(self, x=540, y_start=2400, y_end=1400, duration=300):
        self.shell("input", "swipe", str(x), str(y_start), str(x), str(y_end), str(duration))
        time.sleep(0.5)

    def swipe_down(self, x=540, y_start=1400, y_end=2400, duration=300):
        self.shell("input", "swipe", str(x), str(y_start), str(x), str(y_end), str(duration))
        time.sleep(0.5)

    # --- Screenshot ----------------------------------------------------

    def screenshot(self, tag: str) -> str:
        ts = datetime.now().strftime("%H%M%S")
        fname = f"{self.name}_{tag}_{ts}.png"
        path = str(ARTIFACTS / fname)
        if self.is_remote:
            remote_cmd = f"{self.adb_path} -s {self.serial} exec-out screencap -p"
            result = self._run(
                ["ssh", f"{self.bridge_user}@{self.bridge_host}", remote_cmd],
                binary=True, timeout=15
            )
            with open(path, "wb") as f:
                f.write(result.stdout)
        else:
            with open(path, "wb") as f:
                subprocess.run(["adb", "-s", self.serial, "exec-out", "screencap", "-p"],
                               stdout=f, timeout=15)
        return path

    # --- App helpers ---------------------------------------------------

    def launch_app(self, stop_first=True):
        if stop_first:
            self.shell("am", "force-stop", APP_PACKAGE)
            time.sleep(0.5)
        self.shell("am", "start", "-n", APP_ACTIVITY)
        time.sleep(2)

    def wait_for(self, timeout=15.0, poll=1.0, **kw) -> dict:
        """Wait for element matching kw. Returns element or raises TimeoutError."""
        start = time.time()
        while time.time() - start < timeout:
            el = self.find(**kw)
            if el:
                return el
            time.sleep(poll)
        raise TimeoutError(f"[{self.name}] Timed out ({timeout}s) waiting for {kw}")

    def wait_gone(self, timeout=10.0, poll=0.5, **kw):
        """Wait until element disappears."""
        start = time.time()
        while time.time() - start < timeout:
            if not self.find(**kw):
                return True
            time.sleep(poll)
        return False


# ---------------------------------------------------------------------------
# BettercapVerifier — queries bettercap API directly via SSH chain
# ---------------------------------------------------------------------------

class BettercapVerifier:
    """Verify bettercap state server-side: dev-comp → kali → curl 127.0.0.1:8081"""

    def __init__(self, kali_cfg: dict, dev_comp_cfg: dict):
        self.kali     = kali_cfg
        self.dev_comp = dev_comp_cfg

    def _on_kali(self, cmd: str) -> str:
        host     = self.kali["host"]
        user     = self.kali["user"]
        password = self.kali["ssh_pass"]
        bridge   = f"{self.dev_comp['user']}@{self.dev_comp['host']}"
        inner    = f"sshpass -p '{password}' ssh -o StrictHostKeyChecking=no {user}@{host} '{cmd}'"
        result   = subprocess.run(["ssh", bridge, inner], capture_output=True, text=True, timeout=15)
        return result.stdout.strip()

    def _curl(self, path: str, method="GET", body: dict = None) -> dict | None:
        u    = self.kali.get("api_user", "user")
        p    = self.kali.get("api_pass", "user")
        port = self.kali.get("api_port", 8081)
        cmd  = f"curl -s -u {u}:{p} -X {method}"
        if body:
            escaped = json.dumps(body).replace("'", "'\\''")
            cmd += f" -H 'Content-Type: application/json' -d '{escaped}'"
        cmd += f" http://127.0.0.1:{port}{path}"
        raw = self._on_kali(cmd)
        try:
            return json.loads(raw)
        except json.JSONDecodeError:
            return None

    def get_session(self) -> dict | None:
        return self._curl("/api/session")

    def send_command(self, cmd: str) -> dict | None:
        return self._curl("/api/session", method="POST", body={"cmd": cmd})

    def is_module_active(self, module_name: str) -> bool:
        session = self.get_session()
        if not session:
            return False
        for m in session.get("modules", []):
            if m.get("name") == module_name and m.get("running"):
                return True
        return False

    def get_hosts(self) -> list:
        s = self.get_session()
        return (s or {}).get("lan", {}).get("hosts", [])

    def get_ble_devices(self) -> list:
        s = self.get_session()
        return (s or {}).get("ble", {}).get("devices", [])

    def get_wifi_aps(self) -> list:
        s = self.get_session()
        return (s or {}).get("wifi", {}).get("aps", [])


# ---------------------------------------------------------------------------
# TestSuite — collects results, prints as it goes
# ---------------------------------------------------------------------------

class TestResult:
    def __init__(self, name: str, passed: bool, msg: str, screenshot: str = None):
        self.name       = name
        self.passed     = passed
        self.msg        = msg
        self.screenshot = screenshot

    def __repr__(self):
        s = "PASS" if self.passed else "FAIL"
        return f"[{s}] {self.name} — {self.msg}"


class TestSuite:
    def __init__(self, name: str, device: Device, verifier: BettercapVerifier = None, cfg: dict = None):
        self.name     = name
        self.device   = device
        self.verifier = verifier
        self.cfg      = cfg or {}
        self.results  = []
        self._opts    = cfg.get("test_options", {}) if cfg else {}

    # --- assertion helpers -------------------------------------------

    def check(self, condition: bool, name: str, pass_msg="ok", fail_msg="FAILED",
              screenshot_tag: str = None) -> bool:
        sc = None
        if not condition and self._opts.get("screenshot_on_fail", True):
            tag = screenshot_tag or name.replace(" ", "_")[:30]
            try:
                sc = self.device.screenshot(f"FAIL_{tag}")
            except Exception:
                pass
        if condition and self._opts.get("screenshot_on_pass", False) and screenshot_tag:
            try:
                sc = self.device.screenshot(screenshot_tag)
            except Exception:
                pass

        result = TestResult(name, condition, pass_msg if condition else fail_msg, sc)
        self.results.append(result)
        icon = "✓" if condition else "✗"
        print(f"    {icon} {name}: {result.msg}")
        return condition

    def check_visible(self, text: str = None, desc: str = None,
                      partial: str = None, name: str = None) -> bool:
        root = self.device.dump_ui()
        if text:
            el = self.device.find(root, text=text)
            label = f'"{text}" visible'
        elif desc:
            el = self.device.find(root, desc=desc)
            label = f'desc="{desc}" visible'
        else:
            el = self.device.find(root, partial_text=partial)
            label = f'"{partial}" visible (partial)'
        return self.check(el is not None, name or label,
                          f"found: {(el or {}).get('text') or text or desc or partial}",
                          f"NOT found on screen")

    def check_api(self, condition: bool, name: str, pass_msg="API confirmed", fail_msg="API check failed") -> bool:
        return self.check(condition, f"[API] {name}", pass_msg, fail_msg)

    def send_cmd_and_check(self, cmd: str, expect_success=True) -> bool:
        result = self.verifier.send_command(cmd) if self.verifier else None
        ok = result is not None and result.get("success", False) == expect_success
        return self.check_api(ok, f"send '{cmd}'",
                              f"success={expect_success}", f"got {result}")

    def summary(self) -> str:
        total  = len(self.results)
        passed = sum(1 for r in self.results if r.passed)
        return f"{self.name}: {passed}/{total} passed"

    def failed(self) -> list:
        return [r for r in self.results if not r.passed]


# ---------------------------------------------------------------------------
# Connection helpers
# ---------------------------------------------------------------------------

def connect_app_to_kali(device: Device, kali: dict, opts: dict = None) -> bool:
    """
    Launch app, fill connect form, tap Connect, wait for dashboard.
    Returns True if dashboard reached.
    """
    opts     = opts or {}
    timeout  = opts.get("connect_timeout_sec", 20)

    device.launch_app()
    time.sleep(1)

    # dismiss leftover dialog from previous session
    root = device.dump_ui()
    for btn_text in ("OK", "Dismiss"):
        btn = device.find(root, text=btn_text)
        if btn:
            device.tap_el(btn)
            time.sleep(0.5)
            root = device.dump_ui()

    # collect all EditText fields
    fields = device.find_all(root, cls="android.widget.EditText")
    if len(fields) < 4:
        return False

    # field order: 0=Host, 1=SSHPort, 2=User, 3=Pass, 4=APIPort, 5=APIUser, 6=APIPass
    device.fill_field(fields[0], kali["host"])
    device.fill_field(fields[2], kali["user"],     use_keyevents=True)
    device.fill_field(fields[3], kali["ssh_pass"])

    if "api_user" in kali and len(fields) > 5:
        device.fill_field(fields[5], kali["api_user"])
    if "api_pass" in kali and len(fields) > 6:
        device.fill_field(fields[6], kali["api_pass"])

    device.dismiss_keyboard()
    time.sleep(0.5)

    # tap Connect
    root = device.dump_ui()
    connect_btn = device.find(root, text="Connect")
    if not connect_btn:
        return False
    device.tap_el(connect_btn)

    # wait for dashboard (CMD tab is a reliable indicator)
    try:
        device.wait_for(text="CMD", timeout=timeout)
    except TimeoutError:
        return False

    time.sleep(1)

    # dismiss interface selector if it appeared
    root = device.dump_ui()
    eth0 = device.find(root, partial_text="eth0")
    if eth0:
        device.tap_el(eth0)
        time.sleep(0.5)
    else:
        dismiss = device.find(root, text="Dismiss")
        if dismiss:
            device.tap_el(dismiss)
            time.sleep(0.5)

    return True


def navigate_to_tab(device: Device, tab: str) -> bool:
    """Tap a bottom nav tab by text."""
    root = device.dump_ui()
    el   = device.find(root, text=tab)
    if el:
        device.tap_el(el)
        time.sleep(1.2)
        return True
    return False


def navigate_to_screen(device: Device, screen: str) -> bool:
    """
    Navigate to a screen reachable from the dashboard.
    screen: tab name (Hosts/WiFi/BLE/Events/CMD) or menu item text.
    """
    return navigate_to_tab(device, screen)


def return_to_dashboard(device: Device, max_backs=6) -> bool:
    """Press back until CMD tab is visible (dashboard indicator)."""
    for _ in range(max_backs):
        root = device.dump_ui()
        if device.find(root, text="CMD"):
            return True
        device.press_back()
    return False


def send_command_via_ui(device: Device, cmd: str, response_timeout=5) -> str | None:
    """
    Type a command in the CMD screen input field, send it, wait for output.
    Returns output text or None. Assumes CMD screen is currently shown.
    """
    root  = device.dump_ui()
    field = device.find(root, cls="android.widget.EditText")
    if not field:
        return None

    device.tap_el(field)
    time.sleep(0.2)
    device.select_all_del()
    device.type_chars(cmd)
    time.sleep(0.2)

    # find send button (content-desc="Send")
    root     = device.dump_ui()
    send_btn = device.find(root, desc="Send")
    if not send_btn:
        return None
    device.tap_el(send_btn)
    time.sleep(response_timeout)

    # read output from TEXT views in the output card
    root  = device.dump_ui()
    texts = device.all_texts(root)
    # output is the first long monospace text after "OUTPUT"
    capturing = False
    for t in texts:
        if t == "OUTPUT":
            capturing = True
            continue
        if capturing and len(t) > 5 and t not in ("Ready...", "History"):
            return t
    return None


# ---------------------------------------------------------------------------
# Device factory from config
# ---------------------------------------------------------------------------

def make_devices(cfg: dict, only: list = None) -> dict:
    """
    Build {name: Device} from config.
    only: list of device names to include, e.g. ['pixel8']
    """
    dev_comp = cfg.get("dev_comp", {})
    devices  = {}
    for name, info in cfg["devices"].items():
        if only and name not in only:
            continue
        bridge = info.get("bridge")
        if bridge == "dev_comp":
            d = Device(
                serial=info["serial"], name=info["name"],
                bridge_host=dev_comp["host"], bridge_user=dev_comp["user"],
                adb_path=dev_comp.get("adb_path", "~/Android/Sdk/platform-tools/adb")
            )
        else:
            d = Device(serial=info["serial"], name=info["name"])
        devices[name] = d
    return devices


def make_verifier(cfg: dict, use_fallback=False) -> BettercapVerifier:
    kali_key = "fallback" if use_fallback else "primary"
    return BettercapVerifier(cfg["kali"][kali_key], cfg["dev_comp"])
