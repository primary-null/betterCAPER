#!/usr/bin/env python3
"""Sign the GitHub-built unsigned APK with the project signing key, on the maintainer's own machine.

    sign_release.py --unsigned DIR/betterCAPER-1.0-unsigned.apk --keystore KEY.jks --alias ALIAS \\
                    [--cert-sha256 HEX] [--out DIR] [--build-tools DIR]

The key never goes to GitHub. Passwords are not accepted on the command line: set
BETTERCAPER_KS_PASS and BETTERCAPER_KEY_PASS, or let apksigner prompt for them.

Steps: check the unsigned APK (no signature, right package, not debuggable or testOnly),
sign it with apksigner (v2 and v3 only, no v4 .idsig file), then verify the result: one signer,
the pinned certificate, not the debug certificate, alignment intact, and entries identical to the
unsigned APK. Without --cert-sha256 the run establishes a NEW signer and says so; that is only right
for the very first release. Every later release must pass the fingerprint of the first one.
"""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import apk_check  # noqa: E402


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--unsigned", required=True, type=Path, help="unsigned APK from the release-build artifact")
    parser.add_argument("--keystore", required=True, type=Path)
    parser.add_argument("--alias", required=True, help="key alias inside the keystore")
    parser.add_argument("--cert-sha256", help="pinned certificate SHA-256; omit only for the first release")
    parser.add_argument("--out", type=Path, default=Path("signed"), help="output directory (default: ./signed)")
    parser.add_argument("--build-tools", help="build-tools directory (default: newest under ANDROID_HOME)")
    args = parser.parse_args(argv)

    try:
        for path, label in ((args.unsigned, "unsigned APK"), (args.keystore, "keystore")):
            if not path.is_file():
                raise apk_check.CheckError(f"{label} {path} does not exist")
        tools = apk_check.find_build_tools(args.build_tools)
        metadata_path = args.unsigned.with_name("release-metadata.json")
        metadata = json.loads(metadata_path.read_text(encoding="utf-8")) if metadata_path.is_file() else {}
        tag = metadata.get("tag")
        pins = [apk_check.normalize_fingerprint(p) for p in args.cert_sha256.split(",")] if args.cert_sha256 else []

        problems, info = apk_check.check_unsigned(args.unsigned, tools, package=apk_check.EXPECTED_PACKAGE, tag=tag)
        if metadata and metadata.get("unsignedSha256") != apk_check.sha256_file(args.unsigned):
            problems.append("unsigned APK does not match the sha256 in release-metadata.json")
        if problems:
            for problem in problems:
                print(f"FAIL: {problem}", file=sys.stderr)
            return 1
        version = info["versionName"]
        target = args.out / f"betterCAPER-{version}.apk"
        if target.exists():
            raise apk_check.CheckError(f"{target} already exists; refusing to overwrite a signed release")
        args.out.mkdir(parents=True, exist_ok=True)

        sign = [str(apk_check._tool(tools, "apksigner")), "sign",
                "--ks", str(args.keystore), "--ks-key-alias", args.alias,
                "--min-sdk-version", str(info["minSdk"]),
                "--v1-signing-enabled", "false", "--v2-signing-enabled", "true", "--v3-signing-enabled", "true",
                "--v4-signing-enabled", "false", "--out", str(target)]
        if os.environ.get("BETTERCAPER_KS_PASS"):
            sign += ["--ks-pass", "env:BETTERCAPER_KS_PASS"]
        if os.environ.get("BETTERCAPER_KEY_PASS"):
            sign += ["--key-pass", "env:BETTERCAPER_KEY_PASS"]
        sign.append(str(args.unsigned))
        # stdin, stdout and stderr stay attached so apksigner can prompt for passwords it was not given.
        if subprocess.run(sign).returncode != 0:
            target.unlink(missing_ok=True)
            print("FAIL: apksigner could not sign the APK", file=sys.stderr)
            return 1

        problems, _info, verify = apk_check.check_signed(
            target, tools, package=apk_check.EXPECTED_PACKAGE, tag=tag, pins=pins,
            unsigned=args.unsigned, allow_debug_cert=False)
        signer = next(iter(verify["signers"].values()), {})
        fingerprint = signer.get("sha256")
        if problems:
            for problem in problems:
                print(f"FAIL: {problem}", file=sys.stderr)
            target.unlink(missing_ok=True)
            print("The signed file was removed. Nothing was published.", file=sys.stderr)
            return 1
    except apk_check.CheckError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 2

    print(f"signed:   {target}")
    print(f"sha256:   {apk_check.sha256_file(target)}")
    print(f"signer:   {fingerprint}")
    print(f"schemes:  {', '.join(sorted(k for k, v in verify['schemes'].items() if v))} (verified as an Android 10+ device does)")
    if not pins:
        print()
        print("NEW SIGNER. No --cert-sha256 was given, so this run established the signer above.")
        print("Back the keystore up now, then store the fingerprint as the repository variable")
        print("RELEASE_CERT_SHA256 and pass it as --cert-sha256 for every later release.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
