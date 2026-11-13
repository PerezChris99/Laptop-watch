# LaptopWatch — privacy policy draft

**DRAFT — NOT A PUBLISHED PRIVACY POLICY.** Publication requires confirmed company name, address, support email, actual deployed data flows, retention controls, and legal review. This document is future policy language, not a claim that the unreleased prototype satisfies it.

**Effective date:** To be assigned at launch.  
**Provider/controller:** [Legal entity or sole proprietor name — required].  
**Contact:** [Support/privacy email and address — required].  
**Jurisdictions:** [Confirm countries served and legal bases before launch].

## Product and users

LaptopWatch is intended to help a device owner monitor their own laptop and surroundings with an Android controller and a local desktop application. Users must only monitor places, devices, audio and people they are legally entitled to monitor. Audio and video recording laws can require notice or consent from others; app use is not proof of legal permission.

## Data that may be processed

- **Camera/video:** webcam frames and user-enabled recordings, snapshots and alerts. May depict people and their surroundings.
- **Audio:** microphone and intercom data when the user activates a permitted intercom or recording function.
- **Security events:** event type, timestamps, device status, user-selected action, and linked snapshot identifiers.
- **Device and network:** device identifiers, pairing public keys, certificate fingerprints, IP/network metadata and diagnostics necessary to establish connections.
- **Account/billing (if commercial features launch):** entitlement, plan/device allocation and payment provider transaction references; payment card numbers must stay with the Merchant of Record.
- **Location (only if real location feature is later built):** source and accuracy of location, subject to explicit consent. A laptop does not inherently provide a meter-level GPS fix.
- **Biometric recognition:** not offered as a verified live feature in the current prototype. Any future face identification requires an additional documented legal basis, opt-in and risk assessment.

## Where data flows

The v1 **design target** is local video/evidence storage on the user's devices. WebRTC media will be encrypted in transit. If the user enables WAN operation, optional signaling and TURN relay providers may process service metadata and relay encrypted traffic; they can observe IP addresses and connection times even without decrypting media. Push notification and license/payment integrations may process service tokens and metadata where enabled. A final production inventory must enumerate every provider and what it receives. Do not claim a zero-third-party flow until those integrations are audited.

## Purpose and lawful basis

To provide an explicitly enabled security function, prevent unauthorized control, pair devices, maintain reliable service, support lawful requests and deliver purchased features. Applicable legal basis depends on jurisdiction and provider role; obtain legal review for legitimate interests vs consent vs contract and any surveillance/high-risk special categories.

## Visibility and choice

Camera and microphone activity must be visibly indicated on desktop/phone. Users can arm/disarm, stop a stream, revoke phone pairing and delete local logs. Cloud services and telemetry are disabled by default unless a separately disclosed and opt-in function is deployed. Never bypass a camera's hardware activity indicator.

## Retention and deletion

**Proposed, not implemented:** Free 7 days; Pro 90 days; Business 1 year, configurable to shorter where required. Real deletion/export/retention scheduler must exist and be tested before these promises appear on a public site. Local recordings and exports remain in user-chosen directories until removed. Account deletion and the downstream processor deletion process must be defined before cloud/licensing features launch.

## Security safeguards

Pair-specific public-key authentication, encrypted media, local key protection, replay protection, access limits, tamper-evident audit and signed updates are implementation **requirements**, not currently verified product claims. Users should enable OS disk encryption, login passwords and device updates.

## Sharing and international transfers

No sale of personal content is intended. Any Merchant of Record, signaling, relay, push, support or infrastructure vendor must be disclosed with a concrete purpose, geography, retention, and contractual transfer mechanism where required. A final processor list and transfers table must be added.

## Individual rights and contact

Provide a real channel for access, correction, export, deletion, objections and complaints, subject to law and third-party privacy rights. The controller must document response deadlines and any national representative/authority information required for each launch market.

## Children and bystanders

Not designed for covert surveillance, workplaces without lawful authorization or recording children without required protections. Device owners must notify bystanders when the law requires.

## Changes

Show the effective date, policy version and user notice before deploying material changes.

**Publication gate:** replace placeholders, verify every technical data-flow claim against shipping binaries and network traces, reconcile Android Data Safety, then approve with legal counsel.
