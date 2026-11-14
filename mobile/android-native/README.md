# Mandatory pinned Android networking adapter — integration pending

`LaptopWatchPinnedTransport.kt` and `LaptopWatchPinnedPackage.kt` are the
**thin native security boundary** for the JavaScript React Native app. The
bridge validates the exact QR-pinned leaf certificate DER SHA-256 and rejects
non-private hosts and HTTP. Redirects are disabled. Requests are asynchronous;
failure contains no secret-bearing diagnostic messages.

**SOURCE ONLY — NOT REGISTERED OR COMPILED.** This repository does not yet
contain the generated React Native 0.88 `android/` and `ios/` project
templates. Do not load this Kotlin file in the existing legacy `app/`
reference. After generating and reviewing the official JS React Native
template, copy/register this package in the new native Android project, add
OkHttp, make `INTERNET` permission explicit, disable cleartext networking,
run unit/instrumented tests and verify MITM/wrong-cert rejection on hardware.

The JS client **fails closed** if `NativeModules.LaptopWatchPinnedTransport`
is absent. Do not replace it with plain `fetch` or disable TLS verification.
The bridge's current base64 response assembly holds evidence in memory; large
MP4 transfers require a reviewed direct-to-private-file streaming adapter
before mobile production use. Do not call this a hardened implementation until
it builds, passes security tests and is actually registered.

No recording or background collection is initiated by this native module.
