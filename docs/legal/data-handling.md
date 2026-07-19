# Data handling — unresolved items (owner + counsel territory)

This document exists so this actor never silently pretends a legal/
compliance question was answered by engineering. Every `[CONFIRM: ...]`
marker below is an open question this repo does NOT resolve — the code
does not assume an answer either way, and `kyc-actor.api`'s `disclosures`
block (`kyc-actor/api.cljc`) tells every API caller this explicitly.
Mirrors `network-isekai`'s `legal/terms.md`/`legal/privacy.md` convention
for the same reason (ADR-2607198200's Phase A research found that pattern
already established in this workspace for exactly this situation).

## What this actor is

A self-built identity-verification / sanctions-screening service
(`kotoba-lang/mrz` + `kotoba-lang/face-liveness` + `kotoba-lang/face-match`
+ `kotoba-lang/watchlist-screen`, composed via
`kotoba-lang/ekyc-native-provider`), operated as a `cloud-itonami`
business actor. It is explicitly **not**:

- A licensed or accredited KYC/AML provider in any jurisdiction.
- A substitute for real identity/age verification where the law requires
  one (COPPA, APPI, GDPR Art. 8, or any local equivalent) — see
  `kotoba-lang/mrz`'s and `kotoba-lang/face-liveness`'s own MATURITY.md
  files for the underlying engines' real, honestly-scoped capability
  limits, which this actor inherits unchanged.
- A source of legal advice about whether any of this satisfies a
  regulator, a partner's compliance requirement, or a contract's
  representations.

`kyc-actor.policy`'s `accreditation-claim-gate` (see `src/kyc_actor/
policy.cljc`) is a technical control blocking any *proposal* text from
claiming otherwise — it does not itself constitute a compliance program.

## Open items

- `[CONFIRM: does this actor's self-declared account-tier mechanism (no
  identity/age verification — see network-isekai's ADR-2607198500 for the
  identical honesty stance applied to a different consumer) satisfy any
  applicable minor-protection law for the jurisdictions this actor will
  actually operate in? If not, what verification is legally required
  before this actor may process a minor's data at all?]`
- `[CONFIRM: what data retention period applies to case records, evidence
  references, and the audit ledger (`kyc-actor.store`)? GDPR/APPI both
  require a defined, minimal retention period, not an indefinite one.]`
- `[CONFIRM: what is the legal basis for processing (consent? contract?
  legitimate interest?) for each data category this actor's case schema
  touches — subject identifiers, document evidence references, biometric
  liveness evidence references, sanctions-screening results?]`
- `[CONFIRM: does screening a name against OFAC/UN sanctions data
  (kotoba-lang/watchlist-screen) trigger any jurisdiction-specific
  notice/consent requirement toward the screened subject?]`
- `[CONFIRM: who is the legally responsible entity for a `:hold`
  disposition's consequences (denied service, held funds, etc) — this
  actor's `store`/`ledger` records the decision and its basis, but this
  repo does not itself define legal liability or an appeals process
  beyond the `:correction/request` technical escalation path.]`
- `[CONFIRM: a real Privacy Policy / Terms of Service covering this
  actor's UGC-adjacent data flows (evidence submission, case disputes) —
  none exists yet; drafting one is explicitly owner+counsel work, not
  engineering, per ADR-2607198200's original framing of Phase 0 items 4-5
  in the network-isekai context this whole effort traces back to.]`

## What IS engineering-resolved (for contrast, not a compliance claim)

- Raw government-ID document bytes and biometric templates never enter
  this actor's own `Store` — only opaque evidence references do (see
  `kyc-actor.store`'s module doc). Durable custody is a deployment
  concern (`ekyc.adapters.kagi-custody` is the intended, existing seam —
  see `kotoba-lang/ekyc-native-provider`'s own README).
- The append-only ledger (`kyc-actor.store/ledger`) records who decided
  what, on what basis, at what confidence — a real audit trail exists to
  hand to counsel/a regulator, even though this document does not resolve
  what retention/access-control policy should govern it.
