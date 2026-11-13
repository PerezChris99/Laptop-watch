# LaptopWatch — Data Safety declaration worksheet

**NOT A VERIFIED PLAY CONSOLE DECLARATION.** Never claim "no data collected" solely because the app is designed for local use. Finish instrumentation and provider audit.

| Data category | Prototype source | Planned storage and destination | Required audit |
|---|---|---|---|
| Camera images/video | Local camera capture, snapshots | Desktop, Android; opt-in encrypted relay packets | Confirm file paths, retention and upload network traces |
| Audio | Android RECORD_AUDIO, warning playback | Only on user-initiated intercom/recording | Permission timing, streams, retained recordings |
| Device identifiers | Configuration, future pairing | Local keystore/Room and optional signaling | Identity creation, revocation and logging |
| Precise location | Android permission and UI; mock server data removed on perez | Only real, consented sensor fix if feature launches | Remove unnecessary permission or clearly disclose lawful collection |
| Person/face data | Prototype synthetic profiles | No verified live biometric feature | Remove simulation from live disclosure and assess future biometric scope |
| Diagnostics/crashes | No default analytics claim may be assumed | Only opt-in if provider integrated | Search dependencies, logs and network |
| Purchase/subscription | Not integrated | Merchant of Record + signed entitlement | Processor identity, exports, webhook retention |
| Push tokens | Future design only | FCM if implemented | Firebase SDK/Google Play services declaration |

## Questions for release owner

1. Does data leave the device at any point via relay, telemetry, crash reporting, sign-in, app store SDK or support?
2. Is data collection optional, user-controlled, encrypted in transit and deletable?
3. Can bystander images, audio, precise location or inferred identity be classified as sensitive?
4. What account/deletion requests can the company actually fulfill?
5. Are any providers collecting device/IP diagnostics even if the app does not upload footage?

## Technical verification

Inspect release APK/AAB dependencies and manifest. Test network traffic with and without all opt-in toggles, including cold startup, pairing, stream, push, payment and failure flows. Inspect Android Auto Backup and account deletion behavior. Compare declarations, shipped privacy policy and actual network tracing. Record reviewer/signoff date.

There is **no verified production network/data-flow audit** as of the source baseline.
