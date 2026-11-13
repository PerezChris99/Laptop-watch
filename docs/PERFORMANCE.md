# LaptopWatch — performance and soak evidence

## Measurement policy

No synthetic/demo value may be entered as a real measurement. Source inspection provides no camera latency, mic jitter, codec success, ICE recovery, device battery cost or 24h soak numbers. All values below are **NOT MEASURED** as of 2026-11-13.

| Metric | Target | Measured | Device / setup |
|---|---:|---|---|
| Camera glass-to-glass LAN p50 | ≤100ms | NOT MEASURED | Pending 120fps capture |
| Camera glass-to-glass LAN p95 | ≤150ms | NOT MEASURED | Pending |
| Camera glass-to-glass WAN p50 | ≤250ms | NOT MEASURED | Pending |
| Camera glass-to-glass WAN p95 | ≤400ms | NOT MEASURED | Pending |
| Phone → laptop mic LAN | ≤150ms | NOT MEASURED | Pending |
| Laptop → phone mic LAN | ≤150ms | NOT MEASURED | Pending |
| Phone ↔ laptop mic WAN each direction | ≤300ms | NOT MEASURED | Pending |
| Lock LAN | ≤150ms | NOT MEASURED | Pending |
| Lock WAN | ≤400ms | NOT MEASURED | Pending |
| Siren onset LAN | ≤200ms | NOT MEASURED | Pending |
| ICE restart recovery | ≤2s | NOT MEASURED | Pending |
| Cold camera frame | ≤2s | NOT MEASURED | Pending |
| Warm camera reconnect | ≤200ms | NOT MEASURED | Pending |
| 24h dropped frames | <0.1% | NOT MEASURED | Pending |
| 24h memory growth | <5% | NOT MEASURED | Pending |
| 1h Android monitoring battery cost | report | NOT MEASURED | Pending |

## Protocol

- Use a millisecond clock on target scene, film display and handset at 120fps (or hardware camera), align clock, count display frames. Report clock skew, samples (minimum 100), p50 and p95 for LAN and WAN separately.
- For lock, log monotonic event at handset tap and desktop OS call completion with calibrated offset, repeat 100 times.
- For audio, send timestamped impulse/chirp and measure speaker playback via independent recording; test both directions and echo.
- For ICE, switch Wi-Fi to cellular mid-stream, mark last visible good frame and first recovered frame. Record selected candidate pair and relay use.
- For 24h test, collect frame sequence IDs, observed loss, RSS or heap time series, CPU and battery draw; note sleep/restart events.
- Document OS, CPU/GPU, camera backend, codec, resolution, network RTT/bandwidth, device Android version, test build SHA and date.
- Attach raw logs to an authorized evidence location (remove personal video/audio before publishing), not fabricated summary values.

Release gate stays RED until physical measurements satisfy budgets or are explicitly documented as misses.
