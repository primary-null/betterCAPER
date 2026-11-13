#!/usr/bin/env python3
"""Check a betterCAPER APK before it is signed or published.

Standard library only. Needs the Android SDK build-tools (aapt2, apksigner, zipalign)
and, for apksigner on Linux or macOS, a Java runtime on PATH.

    apk_check.py unsigned APK [--tag v1.0] [--json OUT] [--commit SHA] [--run-id ID]
    apk_check.py signed   APK --cert-sha256 HEX [--compare-unsigned APK] [--tag v1.0] [--json OUT]

`unsigned` is what the GitHub build job runs: the APK must carry no signature of any kind.
`signed` is what the maintainer and the publish workflow run: exactly one signer, the pinned
certificate, never the Android debug certificate, and (with --compare-unsigned) the same
entries as the unsigned APK that GitHub built and attested.

Exit status: 0 all checks passed, 1 a check failed, 2 the tooling is missing or misused.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import struct
import subprocess
import sys
import zipfile
from pathlib import Path

EXPECTED_PACKAGE = "com.bettercaper"
MIN_RSA_BITS = 2048
SIGNING_BLOCK_MAGIC = b"APK Sig Block 42"
# versionName becomes a tag and a file name, so keep it to a conservative character set.
VERSION_NAME_RE = re.compile(r"^[0-9]+(\.[0-9]+)*(-[0-9A-Za-z]+(\.[0-9A-Za-z]+)*)?$")
V1_SIGNATURE_ENTRY_RE = re.compile(r"^META-INF/([^/]+\.(SF|RSA|DSA|EC)|MANIFEST\.MF)$")
EXE_SUFFIXES = (".exe", ".bat") if os.name == "nt" else ("",)


class CheckError(Exception):
    """The tooling is missing or was used wrongly (exit status 2)."""


# ---------------------------------------------------------------- build-tools


def _version_key(path: Path):
    return [int(p) if p.isdigit() else 0 for p in re.split(r"[.\-]", path.name)]


def _tool(directory: Path, name: str):
    for suffix in EXE_SUFFIXES:
        candidate = directory / (name + suffix)
        if candidate.is_file():
            return candidate
    return None


def find_build_tools(explicit: str | None = None) -> Path:
    """Return a build-tools directory that holds aapt2, apksigner and zipalign for this OS."""
    explicit = explicit or os.environ.get("ANDROID_BUILD_TOOLS")
    if explicit:
        candidates = [Path(explicit)]
    else:
        sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
        root = Path(sdk) / "build-tools" if sdk else None
        candidates = sorted((p for p in root.iterdir() if p.is_dir()), key=_version_key, reverse=True) if root and root.is_dir() else []
    for directory in candidates:
        if all(_tool(directory, t) for t in ("aapt2", "apksigner", "zipalign")):
            return directory
    raise CheckError(
        "No usable Android SDK build-tools directory (aapt2, apksigner, zipalign). "
        "Set ANDROID_HOME or pass --build-tools."
    )


def _run(command: list[str], *, timeout: int = 300) -> subprocess.CompletedProcess:
    return subprocess.run(
        command, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout
    )


def _run_tool(directory: Path, name: str, *args: str) -> subprocess.CompletedProcess:
    path = _tool(directory, name)
    if path is None:
        raise CheckError(f"{name} not found in {directory}")
    return _run([str(path), *args])


# ------------------------------------------------------------------- parsing


_ATTR_RE = re.compile(r"A: http://schemas\.android\.com/apk/res/android:(\w+)\(0x[0-9a-fA-F]+\)=(.*)$")
_RAW_RE = re.compile(r'\(Raw: "(.*)"\)\s*$')


def _attr_value(rest: str):
    raw = _RAW_RE.search(rest)
    if raw:
        return raw.group(1)
    rest = rest.strip()
    if rest == "true":
        return True
    if rest == "false":
        return False
    try:
        return int(rest, 0)
    except ValueError:
        return rest.strip('"')


def parse_manifest(text: str) -> dict:
    """Read the facts a release depends on from `aapt2 dump xmltree --file AndroidManifest.xml`."""
    info = {"package": None, "versionCode": None, "versionName": None, "minSdk": None,
            "targetSdk": None, "debuggable": False, "testOnly": False}
    names = {"versionCode": "versionCode", "versionName": "versionName", "minSdkVersion": "minSdk",
             "targetSdkVersion": "targetSdk", "debuggable": "debuggable", "testOnly": "testOnly"}
    for line in text.splitlines():
        line = line.strip()
        package = re.match(r'A: package="([^"]*)"', line)
        if package:
            info["package"] = package.group(1)
            continue
        attr = _ATTR_RE.match(line)
        if attr and attr.group(1) in names:
            info[names[attr.group(1)]] = _attr_value(attr.group(2))
    return info


def parse_verify(text: str) -> dict:
    """Read `apksigner verify --verbose --print-certs` output."""
    result = {"verifies": False, "schemes": {}, "signerCount": None, "signers": {}}
    for line in text.splitlines():
        line = line.strip()
        if line == "Verifies":
            result["verifies"] = True
            continue
        scheme = re.match(r"Verified using (v\d(?:\.\d)?) scheme .*: (true|false)$", line)
        if scheme:
            result["schemes"][scheme.group(1)] = scheme.group(2) == "true"
            continue
        count = re.match(r"Number of signers: (\d+)", line)
        if count:
            result["signerCount"] = int(count.group(1))
            continue
        labels = r"(?:Signer (?:#\d+|\(minSdkVersion=[^\r\n]+\))|V\d+(?:\.\d+)? Signer(?: #\d+)?):?"
        signer = re.match(rf"({labels}) (certificate DN|certificate SHA-256 digest|key algorithm|key size \(bits\)): (.*)$", line)
        if signer:
            fields = {"certificate DN": "dn", "certificate SHA-256 digest": "sha256",
                      "key algorithm": "algorithm", "key size (bits)": "bits"}
            entry = result["signers"].setdefault(signer.group(1), {})
            value = signer.group(3).strip()
            entry[fields[signer.group(2)]] = value.lower() if signer.group(2) == "certificate SHA-256 digest" else value
    # The same certificate can be reported for v2, v3 and v3.1 separately.
    unique = []
    for signer in result["signers"].values():
        if signer not in unique:
            unique.append(signer)
    result["signers"] = dict(enumerate(unique, start=1))
    return result


def normalize_fingerprint(value: str) -> str:
    cleaned = re.sub(r"[^0-9a-fA-F]", "", value).lower()
    if len(cleaned) != 64:
        raise CheckError(f"Not a SHA-256 fingerprint (need 64 hex digits): {value!r}")
    return cleaned


# --------------------------------------------------------------- zip content


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def has_signing_block(path: Path) -> bool:
    """True when an APK Signature Scheme v2/v3 block sits in front of the central directory."""
    with open(path, "rb") as handle:
        handle.seek(0, os.SEEK_END)
        size = handle.tell()
        tail = min(size, 65535 + 22)
        handle.seek(size - tail)
        data = handle.read(tail)
        eocd = data.rfind(b"PK\x05\x06")
        if eocd < 0 or eocd + 20 > len(data):
            raise CheckError(f"{path.name} is not a zip file")
        directory_offset = struct.unpack("<I", data[eocd + 16:eocd + 20])[0]
        if directory_offset < 16:
            return False
        handle.seek(directory_offset - 16)
        return handle.read(16) == SIGNING_BLOCK_MAGIC


def v1_signature_entries(path: Path) -> list[str]:
    with zipfile.ZipFile(path) as archive:
        return sorted(n for n in archive.namelist() if V1_SIGNATURE_ENTRY_RE.match(n))


def entry_table(path: Path) -> dict:
    with zipfile.ZipFile(path) as archive:
        infos = archive.infolist()
        names = [i.filename for i in infos]
        if len(names) != len(set(names)):
            raise CheckError(f"{path.name} has duplicate zip entries")
        return {i.filename: (i.CRC, i.file_size, i.compress_size, i.compress_type) for i in infos}


def compare_entries(unsigned: dict, signed: dict) -> list[str]:
    """The signed APK must be the unsigned one plus a signature, nothing else."""
    problems = []
    for name in sorted(unsigned.keys() - signed.keys()):
        problems.append(f"signed APK lacks entry {name}")
    for name in sorted(signed.keys() - unsigned.keys()):
        if not V1_SIGNATURE_ENTRY_RE.match(name):
            problems.append(f"signed APK has extra entry {name}")
    for name in sorted(unsigned.keys() & signed.keys()):
        if unsigned[name] != signed[name]:
            problems.append(f"entry {name} differs from the unsigned build")
    return problems


# -------------------------------------------------------------------- checks


def inspect_manifest(apk: Path, tools: Path) -> dict:
    proc = _run_tool(tools, "aapt2", "dump", "xmltree", "--file", "AndroidManifest.xml", str(apk))
    if proc.returncode != 0:
        raise CheckError(f"aapt2 could not read {apk.name}: {proc.stderr.strip()[:300]}")
    return parse_manifest(proc.stdout)


def check_alignment(apk: Path, tools: Path) -> str | None:
    """Return a problem description, or None when zipalign accepts 4-byte and 16 KB alignment."""
    proc = _run_tool(tools, "zipalign", "-c", "-P", "16", "4", str(apk))
    if proc.returncode != 0:
        return f"zipalign -c -P 16 4 failed: {(proc.stdout + proc.stderr).strip()[-300:]}"
    return None


def manifest_problems(info: dict, expected_package: str, tag: str | None) -> list[str]:
    problems = []
    if info["package"] != expected_package:
        problems.append(f"package is {info['package']!r}, expected {expected_package!r}")
    if not isinstance(info["versionCode"], int) or info["versionCode"] < 1:
        problems.append(f"versionCode {info['versionCode']!r} is not a positive integer")
    name = info["versionName"]
    if not isinstance(name, str) or not VERSION_NAME_RE.match(name):
        problems.append(f"versionName {name!r} is not a plain version such as 1.0 or 1.1-beta.1")
    elif tag is not None and tag != f"v{name}":
        problems.append(f"tag {tag!r} does not match versionName {name!r} (expected v{name})")
    if info["debuggable"]:
        problems.append("application is debuggable")
    if info["testOnly"]:
        problems.append("application is marked testOnly")
    if isinstance(name, str) and name.endswith("-debug"):
        problems.append("versionName carries the debug suffix")
    return problems


def check_unsigned(apk: Path, tools: Path, *, package: str, tag: str | None) -> tuple[list[str], dict]:
    info = inspect_manifest(apk, tools)
    problems = manifest_problems(info, package, tag)
    if has_signing_block(apk):
        problems.append("APK already carries a v2/v3 signature block")
    entries = v1_signature_entries(apk)
    if entries:
        problems.append("APK already carries v1 signature entries: " + ", ".join(entries))
    alignment = check_alignment(apk, tools)
    if alignment:
        problems.append(alignment)
    return problems, info


def check_signed(apk: Path, tools: Path, *, package: str, tag: str | None, pins: list[str],
                 unsigned: Path | None, allow_debug_cert: bool) -> tuple[list[str], dict, dict]:
    info = inspect_manifest(apk, tools)
    problems = manifest_problems(info, package, tag)
    min_sdk = str(info["minSdk"] or 29)
    proc = _run_tool(tools, "apksigner", "verify", "--verbose", "--print-certs", "--min-sdk-version", min_sdk, str(apk))
    verify = parse_verify(proc.stdout)
    if proc.returncode != 0 or not verify["verifies"]:
        problems.append("apksigner verify failed: " + (proc.stdout + proc.stderr).strip().splitlines()[-1][:200]
                        if (proc.stdout + proc.stderr).strip() else "apksigner verify failed")
        return problems, info, verify
    if not (verify["schemes"].get("v2") or verify["schemes"].get("v3")):
        problems.append("no APK Signature Scheme v2 or v3 signature verified")
    if verify["signerCount"] != 1 or len(verify["signers"]) != 1:
        problems.append(f"expected exactly one signer, found {verify['signerCount']}")
    for index, signer in sorted(verify["signers"].items()):
        fingerprint = signer.get("sha256", "")
        if not allow_debug_cert and "CN=Android Debug" in signer.get("dn", ""):
            problems.append(f"signer #{index} is the Android debug certificate")
        if signer.get("algorithm") == "RSA" and int(signer.get("bits", "0") or 0) < MIN_RSA_BITS:
            problems.append(f"signer #{index} RSA key is smaller than {MIN_RSA_BITS} bits")
        if pins and fingerprint not in pins:
            problems.append(f"signer #{index} certificate SHA-256 {fingerprint} is not the pinned release certificate")
    alignment = check_alignment(apk, tools)
    if alignment:
        problems.append(alignment)
    if unsigned is not None:
        unsigned_info = inspect_manifest(unsigned, tools)
        for key in ("package", "versionCode", "versionName", "minSdk", "targetSdk"):
            if unsigned_info[key] != info[key]:
                problems.append(f"{key} differs from the unsigned build: {unsigned_info[key]!r} vs {info[key]!r}")
        problems.extend(compare_entries(entry_table(unsigned), entry_table(apk)))
    return problems, info, verify


def prerelease_version(version_name: str) -> bool:
    return "-" in version_name


# ----------------------------------------------------------------------- CLI


def _report(label: str, apk: Path, info: dict, problems: list[str], extra: dict) -> dict:
    summary = {
        "file": apk.name, "sha256": sha256_file(apk), "bytes": apk.stat().st_size,
        "package": info["package"], "versionName": info["versionName"], "versionCode": info["versionCode"],
        "minSdk": info["minSdk"], "targetSdk": info["targetSdk"], "debuggable": info["debuggable"],
        "testOnly": info["testOnly"],
        "prerelease": prerelease_version(info["versionName"]) if isinstance(info["versionName"], str) else None,
        **extra,
    }
    print(f"{label}: {apk.name}")
    for key in ("package", "versionName", "versionCode", "minSdk", "targetSdk", "debuggable", "testOnly", "bytes", "sha256"):
        print(f"  {key}: {summary[key]}")
    for key, value in extra.items():
        print(f"  {key}: {value}")
    if problems:
        for problem in problems:
            print(f"FAIL: {problem}")
    else:
        print("OK: all checks passed")
    return summary


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="mode", required=True)
    for mode in ("unsigned", "signed"):
        p = sub.add_parser(mode)
        p.add_argument("apk", type=Path)
        p.add_argument("--package", default=EXPECTED_PACKAGE)
        p.add_argument("--tag", help="release tag; must equal v<versionName>")
        p.add_argument("--build-tools", help="build-tools directory (default: newest under ANDROID_HOME)")
        p.add_argument("--json", type=Path, help="write the result summary to this file")
        p.add_argument("--commit", help="commit SHA to record in the JSON summary")
        p.add_argument("--run-id", help="workflow run id to record in the JSON summary")
    signed = sub.choices["signed"]
    signed.add_argument("--cert-sha256", required=True,
                        help="pinned release certificate SHA-256 (comma separated for a key rotation)")
    signed.add_argument("--compare-unsigned", type=Path, help="the unsigned APK this one must be built from")
    signed.add_argument("--allow-debug-cert", action="store_true",
                        help="tooling self-test only; the release workflows never pass this")
    args = parser.parse_args(argv)

    try:
        if not args.apk.is_file():
            raise CheckError(f"{args.apk} does not exist")
        tools = find_build_tools(args.build_tools)
        if args.mode == "unsigned":
            problems, info = check_unsigned(args.apk, tools, package=args.package, tag=args.tag)
            extra: dict = {}
        else:
            pins = [normalize_fingerprint(p) for p in args.cert_sha256.split(",") if p.strip()]
            if not pins:
                raise CheckError("--cert-sha256 must name at least one fingerprint")
            if args.compare_unsigned is not None and not args.compare_unsigned.is_file():
                raise CheckError(f"{args.compare_unsigned} does not exist")
            problems, info, verify = check_signed(
                args.apk, tools, package=args.package, tag=args.tag, pins=pins,
                unsigned=args.compare_unsigned, allow_debug_cert=args.allow_debug_cert)
            signer = next(iter(verify["signers"].values()), {})
            extra = {"signerSha256": signer.get("sha256"), "signatureSchemes": sorted(k for k, v in verify["schemes"].items() if v)}
            if args.compare_unsigned is not None:
                extra["unsignedSha256"] = sha256_file(args.compare_unsigned)
    except CheckError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 2

    summary = _report(args.mode, args.apk, info, problems, extra)
    summary.update({"schema": 1, "mode": args.mode, "ok": not problems, "problems": problems})
    if args.mode == "unsigned":
        # This file doubles as release-metadata.json for the signing and publishing steps.
        summary.update({"unsignedApk": args.apk.name, "unsignedSha256": summary["sha256"]})
    if args.commit:
        summary["commit"] = args.commit
    if args.run_id:
        summary["runId"] = args.run_id
    if args.tag:
        summary["tag"] = args.tag
    if args.json:
        args.json.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
