# Owner-run physical Android acceptance (Maestro)

**BLOCKED / NOT EXECUTED.** No installable React Native Android app, Android
toolchain, signed foreground service or physical test devices are present at
this checkpoint. The configured `appId` must match the reviewed native Android
package after the official JavaScript-only RN template is generated.

Required setup: pair the laptop and Android phone on a private LAN, display a
fresh QR, grant only appropriate runtime permissions, connect an actual webcam,
start the signed sidecar and monitor backend/hardware logs with secrets redacted.

```bash
cd mobile
# After native project, dependency lock and physical setup are verified:
PAIRING_QR_URI='laptopwatch://pair?...fresh-and-redacted...' maestro test e2e/01-pair-and-controls.yaml
maestro test e2e/02-armed-media.yaml
```

The example URI is NOT an executable invitation: use a real live QR value.
Do not store a signed QR (which exposes addresses, fingerprint, challenge)
in CI logs or committed fixtures. The owner must approve the pending pairing
**during the run**. The tests must be expanded to verify native certificate
mismatch and revocation, permission denial, offline disabled controls,
background/Doze, GNSS last-seen and after-reboot behavior.

Passing Maestro on an emulator alone is insufficient. Record handset, API
level, laptop OS, sidecar version, timestamps, real command response and
whether each recording was consented before claiming hardware acceptance.
