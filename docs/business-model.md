# Business model (open blueprint, not a running business)

This is an open-source business *blueprint* — the code a `cloud-itonami`
operator would run, not a claim that this specific instance is operating
as a business today. See `docs/legal/data-handling.md` for what remains
unresolved before this could actually onboard a real customer.

## Value proposition

Identity verification (document check + liveness challenge + sanctions
screening) as a self-hosted, auditable, governed service — real engines
you can read and test (`kotoba-lang/mrz`, `face-liveness`,
`watchlist-screen`), not a black-box SaaS API. The differentiator against
a commercial KYC vendor (Onfido, Jumio, Sumsub) is transparency and
self-hosting, explicitly NOT claimed accuracy or regulatory accreditation
— see README.md's "Why no LLM" and MATURITY.md's honest gap list.

## Who this fits

An operator who:
- Wants full visibility into what a verification check actually does
  (every engine here is real, tested, open-source code, not an opaque API
  response).
- Can accept this actor's current, real limitations (no bundled face
  detector, no face-match algorithm, no PEP data source, no EU sanctions
  list, self-declared-only account tiers elsewhere in this workspace) —
  or can supply the missing pieces themselves (a real
  `face-liveness.ports/ILandmarkTracker`, a real
  `face-match.ports/IFaceMatcher`, an EU FSD API token).
- Has, or is obtaining, the legal/compliance sign-off this repo explicitly
  does not provide (`docs/legal/data-handling.md`).

## Who this does NOT fit (yet)

A regulated financial institution or anyone needing a licensed/accredited
KYC-AML provider today — this actor makes no such claim and
`kyc-actor.policy`'s `accreditation-claim-gate` actively blocks any
proposal that would.

## Revenue shape (not implemented)

A per-case or per-seat fee for hosting/operating this actor for a
customer — billing, metering, and API-key issuance are explicit
MATURITY.md follow-ups, not built here.
