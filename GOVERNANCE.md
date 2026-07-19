# Governance

`cloud-itonami-isic-649` is an OSS open-business blueprint. Governance
covers both code and the operator model.

## Maintainers

Maintainers may merge changes that preserve these invariants:

- The verifier (`kyc-actor.verifier`) cannot directly commit, approve, or
  resolve a correction request — it only produces a proposal.
- `VerificationGovernor` (`kyc-actor.policy`) remains independent of the
  verifier.
- Hard governor violations (`sanctions-hard-gate`, `liveness-required-gate`,
  `accreditation-claim-gate`) cannot be overridden by human approval.
- A `:correction/request` never auto-resolves, at any rollout phase.
- Every commit, hold, and approval event is auditable
  (`kyc-actor.store/ledger`).
- No proposal or API response may claim regulatory accreditation this
  actor does not hold — enforced at two independent layers
  (`kyc-actor.policy`'s `accreditation-claim-gate` and `kyc-actor.api`'s
  `disclosures` block); a change that removes either without removing the
  other requires explicit sign-off, not a routine merge.
- No engine's own honestly-scoped limitations (see `kotoba-lang/mrz`,
  `face-liveness`, `face-match`, `watchlist-screen`'s own `MATURITY.md`
  files) may be silently upgraded to a stronger claim in THIS repo's own
  docs without the underlying engine actually changing.

## Scope changes

Adding a new check type, a new governor gate, or a new phase requires
updating `docs/legal/data-handling.md` if it touches a new data category
or a new open legal question — not deferring that to a later PR.
