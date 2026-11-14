# LaptopWatch private location relay — optional self-hosted service

This code implements a **single-owner, TLS-terminated, signed last-seen receipt
service**, not a publicly hosted LaptopWatch cloud. It handles metadata and
actual GPS coordinates: deploying it creates a separate personal-data flow.
Do not claim a zero-cloud deployment if you enable it.

## Authenticated roles

Provision an owner's laptop Ed25519 **desktop** key and each paired phone's
**owner_phone** key on the relay **locally**, not over the Internet. Device UUID
and 32-byte public key must match the owner's trusted pairing records.

- Desktop key signs `POST /api/location/report`; each report includes
  `event_id`, `latitude`, `longitude`, `accuracy_meters`,
  `observed_at_ms`, `provider`.
- A paired phone key signs `GET /api/location/latest` and
  `POST /api/location/clear`. Phone cannot publish GPS as desktop.
- Each report is idempotent by desktop ID + event ID. Conflicting ID/body is
  denied. Successful response includes event ID and SHA256 of signed body as
  a receipt; desktop must match before acknowledging its local offline queue.
- A stored fix is **never** claimed as live. The server records both source
  measurement timestamp and delivery receipt time.

The shared auth transcript is
`LW1\nMETHOD\nPATH\nSHA256(body).hex\nUTC_SECONDS\nNONCE\nDEVICE_UUID\n`.
All requests require an Ed25519 signature, nonce and finite timestamp window.
No URL passwords or shared PINs.

## Deployment

Requires a **real public domain**, DNS, Docker, a host that receives inbound
HTTP/HTTPS for ACME, disk encryption/backups and firewall hygiene. Launch
one deployment per owner (this service is not a multi-tenant account backend):

```sh
cd services/location-relay
cp .env.example .env   # set real RELAY_DOMAIN and DNS first
docker compose build
docker compose run --rm relay python -m laptopwatch_relay.cli \
  enroll --id DESKTOP_UUID --key-b64url DESKTOP_PUBKEY --role desktop
docker compose run --rm relay python -m laptopwatch_relay.cli \
  enroll --id PHONE_UUID --key-b64url PHONE_PUBKEY --role owner_phone
docker compose up -d
```

Use only public keys, never paste secret/private Ed25519 keys into Docker
commands. Revoke devices locally with `revoke --id UUID`. Do not publish
port 8080; Caddy must terminate real certificate-validated HTTPS.

**This service is development-grade until** a full security review, encrypted
server storage, signed receipt tests, owner identity verification, retention
and deletion policies, real DNS/TLS/uptime telemetry and external backups pass.
If a phone is lost, revoke its relay key and its desktop pairing.

## Actual current feature boundary

Android owner phone query and desktop signed reconnect-uploader exist in
source; live deployments and field tests have NOT been completed. An offline
stolen laptop cannot deliver a new GPS fix until it is awake, running and
connected long enough for an authenticated upload.

GPS coordinates are AES-256-GCM encrypted in SQLite; the companion `.location-key` file must be preserved securely and backed up separately, otherwise history cannot be decrypted. The relay does not support video/audio, cloud evidence or GPS sampling.
The computer must run with a real sensor to produce any fix, and Python
cannot run during system sleep or power loss.


## Signed incident reporting and optional background FCM hints

The owner desktop's optional verified-person monitor writes a private local
incident first. Only after receiving a signed HTTPS report with an idempotent
SHA-256 receipt does it advance its durable upload cursor. The relay accepts
only enrolled desktop keys for incident reporting and only enrolled owner
phone keys for reading history or registering notification delivery tokens.

New role-restricted, signed endpoints:

- Desktop: POST /api/incidents/report
- Phone: POST /api/incidents/query, POST /api/incidents/clear
- Phone: POST /api/push/register, POST /api/push/unregister, GET /api/push/status

The relay encrypts incident metadata and each phone FCM token with AES-GCM
using a separate per-deployment private key file. Its push delivery scheduler
persists pending events and retries failed provider submissions with exponential
backoff. Only event ID, desktop UUID and generic event kind are sent in an FCM
message. No images, raw recordings, location coordinates or private Ed25519
keys are forwarded to Firebase.

**Firebase is OFF by default.** For opt-in background alerts, provision a
Firebase project, Android app and google-services configuration outside Git.
Give the relay Application Default Credentials / workload identity with only
the Firebase messaging send permission. Set
LAPTOPWATCH_FIREBASE_PROJECT_ID in its Compose environment. Prefer
short-lived workload identities; if using a service account JSON key, mount it
read-only using an owner-controlled compose.override.yaml not checked into
the repository. Do not place that key in this README or any .env file.

The Android owner must explicitly opt into notifications; WorkManager registers
its token with the signed relay. A push is **only a hint**. On opening the app,
signed history is queried and verified before it becomes a forensic event.

**Known limitations:** FCM delivery cannot be guaranteed on every offline
phone/OEM. No relay/FCM service has been deployed or verified using actual
owner accounts. Real wake/retry tests and owner consent still remain.
