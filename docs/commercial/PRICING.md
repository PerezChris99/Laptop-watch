# LaptopWatch — proposed commercial pricing and entitlements

**Proposal only — no billing, license server or purchased entitlement is currently active.**

| Plan | Suggested price | Devices | Media/network | Evidence |
|---|---|---|---|---|
| Free | $0 | 1 laptop | 720p, local LAN | 7-day retention; export watermark |
| Pro | $4.99/mo or $39/year | 3 laptops | 1080p, WAN + fair-use relay, push alert | 90-day retention, watermark-free |
| Business | $14.99/mo or $129/year | Proposed unlimited devices subject to contract/quotas | Priority relay, SSO-ready | 1-year retention, priority support / invoices |

**Do not publish these as live promises.** Every tier needs entitlement enforcement, retention controls, payment plans, support processes and quota tests. Business "unlimited" plus relay has significant unbounded abuse/cost risk; establish fair-use concurrent streams/GB, SSO capability and SLA before activating that tier.

## Checkout architecture (decision target)

Use an approved Merchant of Record such as Paddle, Lemon Squeezy or Polar after verifying merchant eligibility, tax coverage, refund terms, payout availability in the operating country and app-store policy. Store only signed license entitlements/metadata; card data stays with the merchant. Graceful offline-verifiable signed license lease; revalidation on renewal and device change. One entitlement should unlock Android and desktop for an authorized device allocation. No hand-built card processor or unbounded TURN relay service.

## Pricing operational checks

Measure actual relay egress GB/user/month; calculate monthly gross margin after TURN hosting, storage, merchant fees, refunds and taxes. Confirm budget for free users (LAN-only). Validate refund/suspension and account deletion workflows, receipts, billing contact, chargeback procedures, country pricing and mobile-store linking rules with current policies.

**Business approval required:** legal seller, currency, regional taxes, support capacity, device limits and exact prices.
