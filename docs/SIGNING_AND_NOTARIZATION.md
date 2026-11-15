# LaptopWatch signing, notarization and commercial release — BLOCKED

No production signing certificate, private key, notarization API key,
app-store publisher identity or approved commercial EULA/privacy text exists
in this repository. A proprietary root LICENSE covers original repository
source, but does not approve customer distribution or third-party assets. No signing or store actions were executed. The following is an
**owner-run release preparation guide**, not proof of signing.

## Windows desktop (Tauri)

1. Verify your organization's code-signing certificate and trusted hardware
   token/HSM. Do not commit/export any private key or PFX passphrase.
2. Build only after a clean reviewed dependency lock and verified native
   sidecar bundling. Review actual Windows installer signature, sidecar
   publisher, OS SmartScreen behavior and upgrade/rollback.
3. Sign the exact distributable with your authorized provider. Check its
   publisher identity and file digest using the system's signature tooling:

   ```powershell
   Get-AuthenticodeSignature .\\LaptopWatch-Setup.exe | Format-List *
   ```

   Do not publish if the signed file differs from the reviewed build.

## macOS desktop

1. Owner supplies Apple Developer Team ID, valid Developer ID Application
   distribution identity and notarytool keychain profile; use documented
   Tauri platform bundling with the approved entitlements.
2. Verify binaries, bundle and hardened runtime, notarize the signed artifact,
   staple the approved notary ticket and inspect Gatekeeper on a separate Mac.

   ```bash
   codesign --verify --strict --verbose=2 "LaptopWatch.app"
   xcrun notarytool submit "LaptopWatch.dmg" --keychain-profile "OWNER_PROFILE" --wait
   xcrun stapler staple "LaptopWatch.dmg"
   spctl -a -vv "LaptopWatch.app"
   ```

   These commands are examples requiring a real signed artifact and real
   Apple credentials; **none has been run here**.

## Android React Native

1. Generate and review the official native Android project, permissions,
   manifest, FGS category and API level before enabling the pinned native
   module, signing or distribution.
2. Configure a private upload signing keystore/Play App Signing identity
   through encrypted secret storage. Never store passwords in Gradle scripts,
   URLs, test logs or source.
3. Generate signed AAB and APK release artifacts from a reproducible locked
   build. On a verifier device compare installed package identity and
   signature, and confirm a mismatched TLS certificate fails closed.
4. Complete Play Store policy declarations, data safety, consent, retention
   and incident reporting before uploading anything.
5. iOS requires an independent native certificate-pin module and associated
   app-store policies. An Android-only OkHttp bridge is **not** an iOS client.

## Supply-chain and acceptance

For the exact release commit, run and retain `pip-audit`, `npm audit`,
`cargo audit`, `gitleaks`, `semgrep`, CycloneDX SBOM generation,
external security review, cold-start/idle resource benchmarks and 24-hour
reboot/Doze/physical-device testing. Redact all secrets and personal data in
reports. No claims of production readiness, full parity or commercial rights
until owner independently signs the acceptance matrix.

The proprietor has authorized proprietary source licensing and retired
legacy Kotlin `app/`. Commercial EULA, privacy/data-safety text, sales
jurisdictions, actual product distribution and independent runtime acceptance
still require separate review and approval.
