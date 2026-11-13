# Google Play listing and declaration checklist — DRAFT

**Do not submit until the signed Android app and claimed features have been verified.**

**Title:** LaptopWatch  
**Suggested short description (requires character validation):** Protect your laptop with remote monitoring and quick security actions.  
**Category:** Tools / security utility — confirm Play policy classification before submission.

## Draft long description — FUTURE CAPABILITIES, NOT TODAY'S APP

LaptopWatch connects your Android phone with a companion running on your own computer. After a secure QR pairing, you can monitor your workstation camera, remotely lock your screen, sound an alert and review security events. Smart person detection helps reduce alerts caused by everyday movement. Camera and microphone use are visible, controlled by the user and subject to local laws.

Designed for your own devices, not hidden surveillance. Continuous use depends on OS background limits, battery and connectivity. Remote monitoring may require an optional encrypted relay.

## Required listing artifacts

- [ ] Real phone screenshots for supported device sizes and locales, from a release-signed build.
- [ ] Real desktop companion screenshot with security/consent indicator.
- [ ] Feature graphic using accurate brand, not a mock feature that does not exist.
- [ ] Short/long copy reconciled with actual app features.
- [ ] Play Console Data Safety disclosure substantiated by SDK/data-flow audit.
- [ ] App access instructions for reviewers, if secure pairing requires a test device.
- [ ] Permission declarations, foreground service usage video/justification when requested.
- [ ] Privacy URL live and accessible without sign-in.
- [ ] Tested AAB and meaningful versionCode/versionName with release notes.
- [ ] Support email, entity, content rating, targeted markets and age suitability confirmed.

**Planned foreground service justification** (subject to current policy review): foreground monitoring of the customer's own computer after explicit pairing and arming, with persistent visible notification. **However:** a long-running `dataSync` service is subject to Android 15+ 6h limits; a push/event architecture must be finished and policy-correct before reuse of this text.
