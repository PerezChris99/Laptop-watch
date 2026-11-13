# LaptopWatch — automated and physical acceptance matrix

| Gate | Environment | Test case | Pass condition | Evidence status |
|---|---|---|---|---|
| A1 | Android SDK/Gradle | clean build + unit + lint | all pass from wrapper | UNRUN |
| A2 | Pixel Android current | pair/stream/lock/alarms | no crash or permission error | UNRUN |
| A3 | Samsung One UI | background service/notifications | policy-compliant, actionable alert | UNRUN |
| A4 | Android 12 3GB | UI 60fps, battery/soak | targets measured | UNRUN |
| D1 | Windows 11 | install/launch/tray/capture/lock | works under ordinary user | UNRUN |
| D2 | macOS Intel and ARM | install/notarize/tray/capture/lock | works and Gatekeeper accepted | UNRUN |
| D3 | Ubuntu 24.04 X11/Wayland | WebRTC preview and daemon | both sessions work; fallback documented | UNRUN |
| D4 | Fedora Linux | OS-lock/session/unit behavior | commands and systemd work | UNRUN |
| N1 | same Wi-Fi 2.4/5GHz | mDNS then ICE LAN | no IP typing, meets latency | UNRUN |
| N2 | mobile CGNAT | STUN then TURN if needed | encrypted connectivity | UNRUN |
| N3 | symmetric NAT at both ends | opt-in coturn | relay established and measured | UNRUN |
| N4 | Wi-Fi ↔ cellular | ICE restart and label | ≤2s measured or honest degraded banner | UNRUN |
| CV1 | empty office | no person alert | zero false alert | UNRUN |
| CV2 | person enters | detect class person | sensitivity/recall measured | UNRUN |
| CV3 | moving curtain | motion logged, no person alert | zero person false alert | UNRUN |
| CV4 | television flicker | motion logged, no person alert | zero person false alert | UNRUN |
| CV5 | pet | motion logged, no person alert | zero person false alert | UNRUN |
| CV6 | lighting change | motion logged, no person alert | zero person false alert | UNRUN |
| CV7 | IR/night | documented limitations | measurable detection + constraints | UNRUN |
| S1 | hostile LAN | wrong key, MITM, replay, downgrade, brute force | all rejected; no action | UNRUN |
| S2 | local host | audit tamper/reorder + key revocation | verifier rejects | UNRUN |
| S3 | backup/restore | key/data exposure | secrets unavailable after restore | UNRUN |
| I1 | clean Windows/macOS/Linux | uninstall | no daemon, camera or startup orphaned | UNRUN |
| I2 | fresh installed user | 3-step QR onboarding | no README needed | UNRUN |

Test report must contain build commit, environment, time, log/video evidence and reason for any failure. Do not mark test pass based solely on compilation, design notes or a demo-mode run.
