"""Tests for the release tooling and for the guard rails around it.

Run from the repository root:  python3 -m unittest discover -s tools/release -v
They need no Android SDK: APK facts come from captured tool output and small synthetic zip files.
"""
from __future__ import annotations

import os
import re
import struct
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import apk_check  # noqa: E402

REPO = HERE.parent.parent
WORKFLOWS = REPO / ".github" / "workflows"

RELEASE_MANIFEST = """\
N: android=http://schemas.android.com/apk/res/android (line=2)
  E: manifest (line=2)
    A: http://schemas.android.com/apk/res/android:versionCode(0x0101021b)=1
    A: http://schemas.android.com/apk/res/android:versionName(0x0101021c)="1.0" (Raw: "1.0")
    A: http://schemas.android.com/apk/res/android:compileSdkVersion(0x01010572)=35
    A: package="com.bettercaper" (Raw: "com.bettercaper")
      E: uses-sdk (line=7)
        A: http://schemas.android.com/apk/res/android:minSdkVersion(0x0101020c)=29
        A: http://schemas.android.com/apk/res/android:targetSdkVersion(0x01010270)=35
      E: application (line=17)
        A: http://schemas.android.com/apk/res/android:allowBackup(0x01010280)=true
"""

DEBUG_MANIFEST = RELEASE_MANIFEST.replace(
    "        A: http://schemas.android.com/apk/res/android:allowBackup(0x01010280)=true",
    "        A: http://schemas.android.com/apk/res/android:debuggable(0x0101000f)=true\n"
    "        A: http://schemas.android.com/apk/res/android:testOnly(0x01010272)=true",
)

VERIFY_OK = """\
Verifies
Verified using v1 scheme (JAR signing): false
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true
Verified using v3.1 scheme (APK Signature Scheme v3.1): false
Verified using v4 scheme (APK Signature Scheme v4): false
Number of signers: 1
Signer #1 certificate DN: CN=betterCAPER release, O=Example
Signer #1 certificate SHA-256 digest: AABBCCDDEEFF00112233445566778899aabbccddeeff00112233445566778899
Signer #1 key algorithm: RSA
Signer #1 key size (bits): 4096
"""


def write_zip(path: Path, entries: dict[str, bytes]) -> None:
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, data in entries.items():
            archive.writestr(name, data)


class ManifestParsing(unittest.TestCase):
    def test_release_manifest(self):
        info = apk_check.parse_manifest(RELEASE_MANIFEST)
        self.assertEqual(info["package"], "com.bettercaper")
        self.assertEqual(info["versionCode"], 1)
        self.assertEqual(info["versionName"], "1.0")
        self.assertEqual((info["minSdk"], info["targetSdk"]), (29, 35))
        self.assertFalse(info["debuggable"])
        self.assertFalse(info["testOnly"])

    def test_debuggable_and_test_only_are_detected(self):
        info = apk_check.parse_manifest(DEBUG_MANIFEST)
        self.assertTrue(info["debuggable"])
        self.assertTrue(info["testOnly"])


class ManifestProblems(unittest.TestCase):
    def info(self, **changes):
        info = apk_check.parse_manifest(RELEASE_MANIFEST)
        info.update(changes)
        return info

    def test_clean_release_has_no_problems(self):
        self.assertEqual(apk_check.manifest_problems(self.info(), "com.bettercaper", "v1.0"), [])

    def test_tag_must_match_version_name(self):
        self.assertTrue(apk_check.manifest_problems(self.info(), "com.bettercaper", "v1.1"))
        self.assertTrue(apk_check.manifest_problems(self.info(), "com.bettercaper", "1.0"))

    def test_wrong_package_including_debug_suffix(self):
        self.assertTrue(apk_check.manifest_problems(self.info(package="com.bettercaper.debug"), "com.bettercaper", None))

    def test_debuggable_and_test_only_are_rejected(self):
        self.assertTrue(apk_check.manifest_problems(self.info(debuggable=True), "com.bettercaper", None))
        self.assertTrue(apk_check.manifest_problems(self.info(testOnly=True), "com.bettercaper", None))

    def test_debug_version_suffix_is_rejected(self):
        self.assertTrue(apk_check.manifest_problems(self.info(versionName="1.0-debug"), "com.bettercaper", None))

    def test_version_name_character_set(self):
        for good in ("1.0", "1.1-beta.1", "2.0.0-rc1", "10"):
            self.assertTrue(apk_check.VERSION_NAME_RE.match(good), good)
        for bad in ("", "v1.0", "1..0", "1.0 ", "1.0/../x", "1.0;rm", "-1", "1.0-"):
            self.assertFalse(apk_check.VERSION_NAME_RE.match(bad), repr(bad))

    def test_prerelease_follows_the_version_name(self):
        self.assertFalse(apk_check.prerelease_version("1.0"))
        self.assertTrue(apk_check.prerelease_version("1.1-beta.1"))


class VerifyParsing(unittest.TestCase):
    def test_build_tools_37_signer_labels(self):
        current = VERIFY_OK.replace("Signer #1", "V3.0 Signer:")
        duplicate = VERIFY_OK[VERIFY_OK.index("Signer #1 certificate DN:"):].replace("Signer #1", "V3.1 Signer:")
        stamp = VERIFY_OK[VERIFY_OK.index("Signer #1 certificate DN:"):].replace("Signer #1", "Source Stamp Signer:")
        self.assertEqual(apk_check.parse_verify(VERIFY_OK), apk_check.parse_verify(current + duplicate + stamp))

    def test_different_sdk_range_certificates_remain_distinct(self):
        first = VERIFY_OK.replace("Signer #1", "Signer (minSdkVersion=28, maxSdkVersion=32)")
        second = VERIFY_OK[VERIFY_OK.index("Signer #1 certificate DN:"):].replace(
            "Signer #1", "Signer (minSdkVersion=33, maxSdkVersion=2147483647)").replace(
                "AABBCCDDEEFF00112233445566778899aabbccddeeff00112233445566778899", "cd" * 32)
        self.assertEqual(2, len(apk_check.parse_verify(first + second)["signers"]))

    def test_signer_details(self):
        result = apk_check.parse_verify(VERIFY_OK)
        self.assertTrue(result["verifies"])
        self.assertEqual(result["signerCount"], 1)
        self.assertTrue(result["schemes"]["v2"] and result["schemes"]["v3"])
        self.assertFalse(result["schemes"]["v1"])
        signer = result["signers"][1]
        self.assertEqual(signer["sha256"], "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899")
        self.assertEqual((signer["algorithm"], signer["bits"]), ("RSA", "4096"))

    def test_failure_output_does_not_verify(self):
        self.assertFalse(apk_check.parse_verify("DOES NOT VERIFY\nERROR: Missing META-INF/MANIFEST.MF\n")["verifies"])

    def test_fingerprint_forms(self):
        plain = "ab" * 32
        self.assertEqual(apk_check.normalize_fingerprint(plain.upper()), plain)
        self.assertEqual(apk_check.normalize_fingerprint(":".join(plain[i:i + 2] for i in range(0, 64, 2))), plain)
        for bad in ("", "abc", "zz" * 32, "ab" * 31):
            with self.assertRaises(apk_check.CheckError):
                apk_check.normalize_fingerprint(bad)


class ZipComparison(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.dir = Path(self.tmp.name)
        self.base = {"classes.dex": b"dex" * 100, "AndroidManifest.xml": b"manifest", "res/a.png": b"png"}
        self.unsigned = self.dir / "unsigned.apk"
        write_zip(self.unsigned, self.base)

    def table(self, entries):
        path = self.dir / "other.apk"
        write_zip(path, entries)
        return apk_check.entry_table(path)

    def test_identical_entries_pass(self):
        self.assertEqual(apk_check.compare_entries(apk_check.entry_table(self.unsigned), self.table(self.base)), [])

    def test_changed_entry_is_reported(self):
        changed = dict(self.base, **{"classes.dex": b"evil" * 100})
        problems = apk_check.compare_entries(apk_check.entry_table(self.unsigned), self.table(changed))
        self.assertEqual(len(problems), 1)
        self.assertIn("classes.dex", problems[0])

    def test_added_and_missing_entries_are_reported(self):
        extra = dict(self.base, **{"assets/payload.bin": b"x"})
        self.assertTrue(apk_check.compare_entries(apk_check.entry_table(self.unsigned), self.table(extra)))
        missing = {k: v for k, v in self.base.items() if k != "res/a.png"}
        self.assertTrue(apk_check.compare_entries(apk_check.entry_table(self.unsigned), self.table(missing)))

    def test_v1_signature_files_are_the_only_allowed_additions(self):
        signed = dict(self.base, **{"META-INF/MANIFEST.MF": b"m", "META-INF/CERT.SF": b"s", "META-INF/CERT.RSA": b"r"})
        self.assertEqual(apk_check.compare_entries(apk_check.entry_table(self.unsigned), self.table(signed)), [])
        sneaky = dict(self.base, **{"META-INF/services/x": b"s"})
        self.assertTrue(apk_check.compare_entries(apk_check.entry_table(self.unsigned), self.table(sneaky)))

    def test_v1_entries_are_listed(self):
        signed = self.dir / "signed.apk"
        write_zip(signed, dict(self.base, **{"META-INF/CERT.RSA": b"r"}))
        self.assertEqual(apk_check.v1_signature_entries(signed), ["META-INF/CERT.RSA"])
        self.assertEqual(apk_check.v1_signature_entries(self.unsigned), [])

    def test_signing_block_detection(self):
        self.assertFalse(apk_check.has_signing_block(self.unsigned))
        data = bytearray(self.unsigned.read_bytes())
        eocd = data.rfind(b"PK\x05\x06")
        directory = struct.unpack("<I", data[eocd + 16:eocd + 20])[0]
        block = b"\x00" * 8 + apk_check.SIGNING_BLOCK_MAGIC
        data[eocd + 16:eocd + 20] = struct.pack("<I", directory + len(block))
        data[directory:directory] = block
        blocked = self.dir / "blocked.apk"
        blocked.write_bytes(bytes(data))
        self.assertTrue(apk_check.has_signing_block(blocked))

    def test_not_a_zip_is_a_tooling_error(self):
        junk = self.dir / "junk.apk"
        junk.write_bytes(b"not a zip")
        with self.assertRaises(apk_check.CheckError):
            apk_check.has_signing_block(junk)


class BuildToolsDiscovery(unittest.TestCase):
    def test_picks_the_newest_directory_that_has_every_tool(self):
        with tempfile.TemporaryDirectory() as tmp:
            sdk = Path(tmp)
            suffix = apk_check.EXE_SUFFIXES[0]
            for version, tools in (("34.0.0", ("aapt2", "apksigner", "zipalign")),
                                   ("35.0.0", ("aapt2", "apksigner", "zipalign")),
                                   ("36.0.0", ("aapt2",))):
                directory = sdk / "build-tools" / version
                directory.mkdir(parents=True)
                for tool in tools:
                    (directory / (tool + suffix)).write_bytes(b"")
            old = {k: os.environ.pop(k, None) for k in ("ANDROID_BUILD_TOOLS", "ANDROID_HOME", "ANDROID_SDK_ROOT")}
            os.environ["ANDROID_HOME"] = str(sdk)
            try:
                self.assertEqual(apk_check.find_build_tools().name, "35.0.0")
            finally:
                for key in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
                    os.environ.pop(key, None)
                for key, value in old.items():
                    if value is not None:
                        os.environ[key] = value

    def test_missing_build_tools_is_a_tooling_error(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(apk_check.CheckError):
                apk_check.find_build_tools(tmp)


def run_blocks(text: str):
    """Yield the lines of every `run:` step so injection patterns can be searched for."""
    lines = text.splitlines()
    index = 0
    while index < len(lines):
        match = re.match(r"^(\s*)(?:- )?run:\s*(.*)$", lines[index])
        if not match:
            index += 1
            continue
        indent = len(match.group(1)) + (2 if lines[index].lstrip().startswith("- ") else 0)
        rest = match.group(2)
        yield lines[index]
        index += 1
        if rest.strip() in ("|", ">", "|-", ">-", "|+", ">+"):
            while index < len(lines) and (not lines[index].strip() or len(lines[index]) - len(lines[index].lstrip()) > indent):
                yield lines[index]
                index += 1


class WorkflowGuardRails(unittest.TestCase):
    def workflows(self):
        files = sorted(WORKFLOWS.glob("*.yml"))
        self.assertTrue(files)
        return {f.name: f.read_text(encoding="utf-8") for f in files}

    def test_actions_are_pinned_to_full_commit_ids(self):
        for name, text in self.workflows().items():
            for use in re.findall(r"^\s*(?:- )?uses:\s*(\S+)", text, re.M):
                self.assertRegex(use, r"@[0-9a-f]{40}$", f"{name}: {use}")

    def test_every_workflow_declares_permissions_and_none_use_pull_request_target(self):
        for name, text in self.workflows().items():
            self.assertRegex(text, r"(?m)^permissions:", name)
            self.assertNotIn("pull_request_target", text, name)

    def test_release_workflows_start_with_no_permissions_and_use_no_secrets(self):
        for name in ("release-build.yml", "release-publish.yml"):
            text = self.workflows()[name]
            self.assertRegex(text, r"(?m)^permissions: \{\}$", name)
            self.assertNotIn("secrets.", text, name)
            self.assertRegex(text, r"(?m)^on:\n  workflow_dispatch:", f"{name} must be manual only")

    def test_inputs_never_reach_a_shell_directly(self):
        for name, text in self.workflows().items():
            for line in run_blocks(text):
                self.assertNotRegex(line, r"\$\{\{\s*(inputs|github\.event)\.", f"{name}: {line.strip()}")

    def test_only_the_publish_workflow_can_write_contents(self):
        for name, text in self.workflows().items():
            if name != "release-publish.yml":
                self.assertNotIn("contents: write", text, name)

    def test_release_build_has_no_cache_and_stays_unsigned(self):
        text = self.workflows()["release-build.yml"]
        self.assertNotIn("cache:", text)
        self.assertNotIn("apksigner sign", text)
        self.assertNotIn("assembleDebug", text)

    def test_publish_is_a_dry_run_by_default_and_fails_closed(self):
        text = self.workflows()["release-publish.yml"]
        self.assertRegex(text, r"dry_run:\n(?:.*\n)*?\s+default: true")
        self.assertIn("RELEASE_CERT_SHA256", text)
        self.assertIn("is not set", text)

    def test_the_debug_certificate_escape_hatch_is_never_used(self):
        for name, text in self.workflows().items():
            self.assertNotIn("--allow-debug-cert", text, name)
        for doc in ("README.md", "RELEASING.md"):
            self.assertNotIn("--allow-debug-cert", (REPO / doc).read_text(encoding="utf-8"), doc)

    def test_the_debug_signed_beta_workflow_is_gone(self):
        self.assertFalse((WORKFLOWS / "beta-release.yml").exists())


class GradleAndRepositoryConfiguration(unittest.TestCase):
    def setUp(self):
        self.gradle = (REPO / "app" / "build.gradle.kts").read_text(encoding="utf-8")

    def test_release_package_is_the_expected_one(self):
        self.assertIn(f'applicationId = "{apk_check.EXPECTED_PACKAGE}"', self.gradle)

    def test_debug_builds_install_next_to_the_release(self):
        self.assertRegex(self.gradle, r'debug \{[^}]*applicationIdSuffix = "\.debug"')

    def test_release_is_never_debuggable(self):
        self.assertRegex(self.gradle, r"release \{\s*(?://[^\n]*\n\s*)*isDebuggable = false")
        self.assertNotIn("isDebuggable = true", self.gradle)

    def test_release_does_not_fall_back_to_the_debug_key(self):
        self.assertNotIn('signingConfigs.getByName("debug")', self.gradle)
        self.assertNotIn("signingConfigs.debug", self.gradle)

    def test_key_material_is_ignored_by_git(self):
        ignore = (REPO / ".gitignore").read_text(encoding="utf-8")
        for pattern in ("keystore.properties", "*.jks", "*.keystore"):
            self.assertIn(pattern, ignore.splitlines())


if __name__ == "__main__":
    unittest.main()
