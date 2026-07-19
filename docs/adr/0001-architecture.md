# ADR 0001 — VerificationGovernor architecture, real engine fan-out instead of an LLM advisor

Status: accepted — 2026-07-19

## Decision

This actor's `:verify` node (`kyc-actor.verifier`) is a deterministic
fan-out to real verification engines (`kotoba-lang/mrz` via
`kotoba-lang/ekyc-native-provider` for `:document-ocr`,
`kotoba-lang/face-liveness` for `:liveness`, `kotoba-lang/face-match`'s
deliberate stub for `:face-match`, and `kotoba-lang/watchlist-screen` for
sanctions matching), NOT an LLM proposal node — a deliberate departure
from `cloud-itonami-isic-8291`'s Dossier-LLM ⊣ DisclosureGovernor
template, which every other actor in this fleet otherwise follows.

Every proposal `kyc-actor.verifier` produces is still censored by an
independent `VerificationGovernor` (`kyc-actor.policy`) before any case
disposition — the SEPARATION OF CONCERNS (intelligence node vs.
independent censor) is preserved even though the "intelligence" here is
deterministic matching math, not language generation. `VerificationGovernor`
has three HARD (un-overridable) gates and three SOFT (always-escalate)
gates, mirroring `DisclosureGovernor`'s exact 3+3 shape:

- HARD: `sanctions-hard-gate`, `liveness-required-gate`,
  `accreditation-claim-gate` (this actor's own honesty backstop — no
  sibling actor's Governor has an equivalent, because no other actor
  makes a self-built-vs-accredited-provider distinction that matters the
  way it does for a KYC/AML service).
- SOFT: `confidence-floor`, `pep-flag-gate`, `correction-request-always-escalates`.

## Rationale

An identity-verification VERDICT (does this document check out, did this
person actually blink on request, does this name match a sanctions
entry) is a checksum/geometry/string-matching problem with a real, testable,
deterministic answer — kotoba-lang/mrz's checksum algorithm, kotoba-lang/
face-liveness's EAR formula, and kotoba-lang/watchlist-screen's
Jaro-Winkler scoring are all real, cited, or otherwise verifiable math.
Routing that decision through an LLM (as `:advise` does for
`cloud-itonami-isic-8291`'s document-synthesis task) would introduce a
hallucination-shaped failure mode into a domain where it doesn't belong —
the exact concern this whole eKYC/AML effort (ADR-2607198200 through
ADR-2607198900 in `kotoba-lang/*`) was built around avoiding at every
prior phase.

## Consequences

- This actor's StateGraph node/edge structure is otherwise IDENTICAL to
  `cloud-itonami-isic-8291`'s (`intake→X→govern→decide→
  {commit|request-approval→commit|hold}`) — a maintainer familiar with
  that actor can read this one without relearning the control-flow shape,
  only the `X` node's substance differs.
- `kyc-actor.verifier/mock-verifier` fills the same demo/test role
  `dossier.llm/mock-advisor` does — dependency-light, deterministic,
  documented as NOT the real path.
- Every honest capability gap in the underlying engines (mrz's
  experimental optical step, face-liveness's missing detector,
  face-match's deliberate no-algorithm stance, watchlist-screen's missing
  EU source) passes through this actor unchanged — this ADR does not
  claim to close any of them, only to wire them together correctly and
  govern the result.
