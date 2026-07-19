# Design

## Node-by-node

| Node | Reads | Writes | Notes |
|---|---|---|---|
| `:intake` | `request` | case status → `:pending` | |
| `:verify` | `request` | `proposal` + audit | `kyc-actor.verifier/-verify` — real engine fan-out, never itself decides |
| `:govern` | `request`, `proposal` | `verdict` | `kyc-actor.policy/check` — independent of the verifier |
| `:decide` | `request`, `context`, `proposal`, `verdict` | `disposition` + audit | governor verdict → base disposition → phase gate (can only add caution) |
| `:request-approval` | `request`, `approval`, `verdict` | `disposition` + audit | `interrupt-before`-paused; human resumes with `:approval` |
| `:commit` | `request`, `proposal` | case status → `:approved`, ledger | the only node that writes an approval |
| `:hold` | `request`, `audit` | case status → `:held`, ledger | no approval, no side effects beyond the ledger fact |

## Why `:verify` is not `:advise`

`cloud-itonami-isic-8291`'s Dossier-LLM (`:advise`) proposes a fact backed
by an LLM's reading of registry documents — appropriate where the
underlying task is document *synthesis/summarization*. This actor's task
is document/biometric/sanctions-list *matching* — a checksum either
passes or it doesn't, a challenge-response either shows the right motion
or it doesn't, a name either scores above a match threshold or it
doesn't. Routing that through an LLM would add a hallucination-shaped
failure mode to a domain where the actual math is already deterministic
and testable. `kyc-actor.verifier/real-verifier` calls the real engines
directly; `VerificationGovernor` still independently censors the result,
same separation of concerns as every other actor in this fleet.

## Data flow for a single case

```
1. api/create-case!            → Store: new case, :case/status :intake
2. api/submit-evidence! (×N)   → Store: case/evidence[check] = {evidence-ref, content}
3. api/request-verification!   → StateGraph run:
     intake  → case/status :pending
     verify  → real engines score each submitted evidence check
     govern  → VerificationGovernor censors the combined proposal
     decide  → phase gate; :commit | :escalate | :hold
     [commit | request-approval→commit|hold]
   → Store: case/status :approved|:held, ledger append
4. api/case-status / api/case-audit  → read-only
```

## Extending

- New check kind: add a `case` branch in `kyc-actor.verifier/real-verifier`
  (or a new engine repo entirely, following the `kotoba-lang/mrz` /
  `face-liveness` / `face-match` pattern — real algorithm where one
  honestly exists, a documented stub where one doesn't).
- New governor gate: add a `-violations` fn in `kyc-actor.policy`, wire it
  into `check`'s `hard`/`escalate` computation, add both positive and
  negative test coverage in `policy_test.cljc`.
- New phase behavior: edit `kyc-actor.phase/phases` — `:correction/request`
  must never enter any phase's `:auto` set (see `phase.cljc`'s own
  invariant).
