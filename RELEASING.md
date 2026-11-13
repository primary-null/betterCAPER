# Releasing betterCAPER

Official APKs are **built by GitHub, signed offline by the maintainer, and published as GitHub Release assets**. This file is the runbook. Users want [Download and install](README.md#download-and-install) instead.

## How a release flows

| # | Where | What | Result |
|---|-------|------|--------|
| 1 | GitHub | Run **Release build (unsigned)** on `main` with tag `v<versionName>` | Unit tests pass, release APK built **unsigned**, checked, build provenance attested, uploaded as artifact `unsigned-v<versionName>` |
| 2 | Maintainer's machine | Download it, verify the attestation, sign it with `tools/release/sign_release.py` | `betterCAPER-<versionName>.apk`, signed and re-verified |
| 3 | Maintainer's machine | Attach the signed APK to a **draft** release that targets the built commit | Draft only the maintainer can see |
| 4 | GitHub | Run **Publish release** (first as a dry run) | Re-verifies everything on the runner, then publishes the draft, attests the signed APK and checks the public download without credentials |

The signing key never reaches GitHub. Nothing is published unless the pinned signing certificate matches.

## Decisions and why

- **Releases, not Actions artifacts.** Workflow artifacts need a GitHub sign-in to download and expire (this project keeps CI artifacts 14 days). Release assets are public, permanent, and have stable links, including `releases/latest`. Only the newest non-draft, non-prerelease release is "latest"; drafts and prereleases never are. ([Linking to releases](https://docs.github.com/en/repositories/releasing-projects-on-github/linking-to-releases), [REST: releases](https://docs.github.com/en/rest/releases/releases))
- **One persistent signing key, kept offline.** Android installs an update over an app only if the signing certificate matches, and the only way out of a mismatch is an uninstall that erases the app's data. The signer therefore *is* the app's identity ([Sign your app](https://developer.android.com/studio/publish/app-signing)). A CI runner starts with no `debug.keystore` and makes a new one each run, so a CI-signed APK has a different signer every time. The old `beta-<run id>` debug prereleases were removed for that reason.
- **Sign with `apksigner`, after `zipalign`.** The Android guidance is: align first, then sign, because any change after signing invalidates the signature ([apksigner](https://developer.android.com/tools/apksigner), [command-line build](https://developer.android.com/build/building-cmdline)). The Gradle build already produces an aligned APK; the tools check it with `zipalign -c -P 16 4` and signing leaves the entries untouched.
- **Signed = built + signature.** `apk_check.py signed --compare-unsigned` fails if the signed file differs from the attested build in any entry, so the offline step cannot swap code in.
- **Checksums and attestations.** The release notes carry the SHA-256 of the APK and of the signing certificate, and GitHub shows each asset's digest. The unsigned build has a build-provenance attestation ([Artifact attestations](https://docs.github.com/en/actions/concepts/security/artifact-attestations)); the publish workflow adds an attestation for the signed APK that records its link to that build.
- **Least privilege.** Both release workflows start with `permissions: {}`. The build job gets `contents: read`, `id-token: write`, `attestations: write` and uses no cache and no secrets. Only the publish job can write contents. Inputs reach shell steps only through `env:`. Every action is pinned to a commit ([Automatic token authentication](https://docs.github.com/en/actions/how-tos/security-for-github-actions/security-guides/automatic-token-authentication)).
- **No separate "preview" package.** Prereleases (`1.1-beta.1`) use the same package and signer with a higher version code, so they update in place and testers never need to uninstall. A second package would split saved data and signing trust and would need its own developer-verification registration. Debug builds do get their own ID, `com.bettercaper.debug`, so a debug install can never block a release install.
- **Immutable releases.** Turn them on **before the first release**: repository **Settings**, section **Releases**, **Enable release immutability**. Published assets and the tag are then locked and GitHub attaches a release attestation (`gh release verify`). It only applies to releases published after it is switched on. ([Immutable releases](https://docs.github.com/en/code-security/concepts/supply-chain-security/immutable-releases))

## One-time setup

1. **Create the signing key** (RSA 4096, valid about 27 years; Android's guidance is at least 25):

   ```bash
   keytool -genkeypair -v -keystore betterCAPER-release.jks -alias betterCAPER \
     -keyalg RSA -keysize 4096 -validity 10000
   ```

   Keep the keystore **outside the repository** (`*.jks` and `*.keystore` are ignored anyway). Keep at least two offline copies in different places and store the passwords separately from the file. If the key is lost, no update can install over existing installs; users would have to uninstall and lose their saved connections.
2. **First release only:** run `sign_release.py` without `--cert-sha256`. It prints the certificate SHA-256 and says `NEW SIGNER`. Save that value as the repository **variable** `RELEASE_CERT_SHA256` (Settings → Secrets and variables → Actions → Variables). The publish workflow refuses to run while the variable is empty. A comma-separated list is accepted for a planned key rotation. Add the fingerprint to the README as well, so it is also pinned in the repository history.
3. Switch on **release immutability** (see above).
4. Optional hardening: create a `release` environment with required reviewers and move the variable there; restrict who may run workflows.

## Releasing a version

1. Raise `versionCode` and set `versionName` in `app/build.gradle.kts` (`versionCode` must exceed every published one; `versionName` is a plain version such as `1.0` or `1.1-beta.1`). Add a `## <versionName>` section to `CHANGELOG.md` (it becomes the release notes). Merge to `main` with CI green.
2. **Actions → Release build (unsigned) → Run workflow** (branch `main`, tag `v<versionName>`). Note the run ID.
3. Download and check the provenance:

   ```bash
   gh run download <run-id> -n unsigned-v1.0 -D release-work
   gh attestation verify release-work/betterCAPER-1.0-unsigned.apk -R primary-null/betterCAPER \
     --signer-workflow primary-null/betterCAPER/.github/workflows/release-build.yml
   ```
4. Sign. Passwords come from `BETTERCAPER_KS_PASS` / `BETTERCAPER_KEY_PASS` if set, otherwise `apksigner` prompts. They are never put on a command line.

   ```bash
   python tools/release/sign_release.py --unsigned release-work/betterCAPER-1.0-unsigned.apk \
     --keystore path/to/betterCAPER-release.jks --alias betterCAPER \
     --cert-sha256 <SHA-256 of the release certificate> --out release-work/signed
   ```

   It checks the unsigned file, signs with v2 + v3 (no v1, no v4), then verifies one signer, the pinned certificate (never the debug one), alignment and entry-for-entry identity with the build. On any failure it deletes the signed file.
5. Create the draft, targeting the commit from `release-work/release-metadata.json` (`commit`). Add `--prerelease` for a `-beta`/`-rc` version:

   ```bash
   gh release create v1.0 release-work/signed/betterCAPER-1.0.apk --draft \
     --target <commit> --title "betterCAPER 1.0"
   ```
6. **Actions → Publish release**: tag `v1.0`, the build run ID, **dry_run ticked**. It re-downloads the build and the draft asset, verifies attestation, signature, pin and version code, and changes nothing.
7. Run it again with **dry_run unticked**. It publishes the draft (prerelease flag and "latest" set from the version), attests the signed APK, downloads the public asset without credentials and compares the hash. Open the release in a private window and install it over the previous version on a device.

## Verifying a download

```bash
sha256sum betterCAPER-1.0.apk                      # compare with the release notes
apksigner verify --print-certs betterCAPER-1.0.apk # "certificate SHA-256 digest" must match every release
gh release verify-asset v1.0 betterCAPER-1.0.apk   # exact match with the immutable release asset
```

### Signed release attestation

The signed APK's attestation (predicate type `https://github.com/primary-null/betterCAPER/blob/main/RELEASING.md#signed-release-attestation`) states that the file has the same entries as an attested build plus one signature from the pinned certificate:

```bash
gh attestation verify betterCAPER-1.0.apk -R primary-null/betterCAPER \
  --signer-workflow primary-null/betterCAPER/.github/workflows/release-publish.yml \
  --predicate-type https://github.com/primary-null/betterCAPER/blob/main/RELEASING.md#signed-release-attestation
```

## Key rotation

Avoid it. Android 9 and later can carry a proof-of-rotation record (APK Signature Scheme v3, v3.1 on Android 13) created with `apksigner rotate` and `--lineage`, but rotation is not recommended for devices on Android 12 and earlier ([v3](https://source.android.com/docs/security/features/apksigning/v3), [v3.1](https://source.android.com/docs/security/features/apksigning/v3-1)). If one is ever needed, publish the new fingerprint in `RELEASE_CERT_SHA256` next to the old one, sign with the lineage by hand using `apksigner`, and test an in-place update from the previous release before publishing.

## Local builds

`./gradlew :app:assembleRelease` signs with `keystore.properties` if present (see `keystore.properties.example`), otherwise it produces an unsigned APK. A release you build yourself carries *your* signature and cannot update, or be updated by, the official release. The official APK is always the GitHub-built one, signed with `sign_release.py`.

## Android developer verification

Google is phasing in registration for apps installed outside Google Play on certified devices (enforcement from 30 September 2026 in Brazil, Indonesia, Singapore and Thailand, expanding globally from 2027). Registration links a package name to its signing keys, so the persistent key and the fixed package name `com.bettercaper` are prerequisites. Whether to register is a maintainer decision. See [Android developer verification](https://developer.android.com/developer-verification).

## Sources

- GitHub: [Managing releases](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository), [Linking to releases](https://docs.github.com/en/repositories/releasing-projects-on-github/linking-to-releases), [REST API for releases](https://docs.github.com/en/rest/releases/releases), [Immutable releases](https://docs.github.com/en/code-security/concepts/supply-chain-security/immutable-releases), [Verifying the integrity of a release](https://docs.github.com/en/code-security/how-tos/secure-your-supply-chain/secure-your-dependencies/verifying-the-integrity-of-a-release), [Artifact attestations](https://docs.github.com/en/actions/concepts/security/artifact-attestations), [Storing and sharing data from a workflow](https://docs.github.com/en/actions/how-tos/writing-workflows/choosing-what-your-workflow-does/storing-and-sharing-data-from-a-workflow).
- Android: [Sign your app](https://developer.android.com/studio/publish/app-signing), [apksigner](https://developer.android.com/tools/apksigner), [Build from the command line](https://developer.android.com/build/building-cmdline), [APK Signature Scheme v3](https://source.android.com/docs/security/features/apksigning/v3), [Android developer verification](https://developer.android.com/developer-verification).
