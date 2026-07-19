# Maturity

**Level: R0 local-store (real graph, real engine wiring, no live deployment)**

Implemented:
- `kyc-actor.store` — `Store` protocol + `MemStore`. Case lifecycle
  (intake/pending/escalated/approved/held), account-tier tracking,
  append-only ledger. Only MemStore ships (a `DatomicStore` backend, per
  `cloud-itonami-isic-8291`'s own precedent, is a documented follow-up).
- `kyc-actor.verifier` — `real-verifier` genuinely calls
  `kotoba-lang/ekyc-native-provider` (itself calling `mrz`/`face-liveness`/
  `face-match`) and `kotoba-lang/watchlist-screen`, tested end-to-end
  against real fixture data (the same ICAO Doc 9303 TD3 worked example
  `kotoba-lang/mrz` uses, a real blink-frame sequence). `mock-verifier` for
  dependency-light demos, mirroring `dossier.llm/mock-advisor`'s role.
- `kyc-actor.policy` — `VerificationGovernor`, 3 HARD + 3 soft-escalate
  gates (see README.md), full positive/negative test coverage per gate.
- `kyc-actor.phase` — 0→3 staged rollout, `:correction/request` never
  auto-eligible at any phase.
- `kyc-actor.operation` — real `langgraph-clj` `StateGraph`
  (`intake→verify→govern→decide→{commit|request-approval→commit|hold}`),
  `interrupt-before` human-in-the-loop tested through actual pause/resume.
- `kyc-actor.api` — logical API surface (create/submit-evidence/
  request-verification/status/audit/dispute/resume-approval), every
  response carrying an enforced `disclosures` block denying regulatory
  accreditation.
- `kyc-actor.identity` — real CACAO/did:key self-mint (ported from
  `gftdcojp/cloud-itonami`'s `identity.clj`, ADR-2607141700), tested:
  real Ed25519 key generation, real `did:key` derivation, real CACAO
  minting, atomic create-if-absent persistence.
- `docs/legal/data-handling.md` — explicit `[CONFIRM: ...]` markers for
  every unresolved legal/compliance question, mirroring `network-isekai`'s
  `legal/terms.md` convention.
- 38 tests, 90 assertions, 0 failures. `clj-kondo`: 0 errors, 0 warnings.
  `clojure -M:dev:run` demo walks clean-auto-commit, sanctioned-hard-hold,
  and correction-request-escalate-then-approve scenarios successfully.

Not yet R0→R1 (i.e., explicitly absent, not a rounding-down):
- **Not deployed anywhere.** No kotobase.net pod binding exercised end-to-
  end (the CACAO identity module is real and tested standalone; actually
  authenticating a live session against a running kotobase.net node is
  untested).
- **No `DatomicStore` backend** — `MemStore` only. A restart loses all
  case/ledger state.
- **No PEP data source.** `kyc-actor.verifier/real-verifier` always
  reports `:pep? false` — OFAC SDN / UN Consolidated are sanctions lists,
  not a distinct PEP list this actor has access to. The `pep-flag-gate`
  itself is real and tested (via directly-constructed proposals in
  `policy_test.cljc`); the real verifier just never trips it today.
- **No real deployment's `watchlist.ports/IWatchlistIndex`** wired by
  default — `real-verifier` needs one injected (e.g.
  `watchlist.adapters.edn-index` pointed at real OFAC/UN data, shipped in
  `kotoba-lang/watchlist-screen` itself).
- **Inherits every upstream engine's own honest gaps unchanged**: `mrz`'s
  optical capture is R1-experimental with no bundled OCR-B templates,
  `face-liveness` has no bundled `ILandmarkTracker`, `face-match` has no
  comparison algorithm (R0, by design), `watchlist-screen` has no EU
  Consolidated List source. This actor correctly wires these gaps through;
  it does not close any of them.
- **No HTTP transport** — `kyc-actor.api` is a logical function surface, not
  a wired HTTP server. A real deployment adds that layer.
- **No billing / API-key issuance / customer dashboard / DPA** — explicit
  follow-ups, not attempted here.
- **No real-world verification-accuracy or false-accept/false-reject data**
  of any kind for the combined pipeline — this actor is real, tested
  *software*, not a validated compliance product. See
  `docs/legal/data-handling.md`.

## Registry note

Promoted from ISIC group code `649` ("Other financial service activities,
except insurance and pension funding activities"), whose natural 4-digit
children (6491/6492/6493/6499) are all already claimed by unrelated
`cloud-itonami` businesses and every other plausible identity-verification-
adjacent code checked during this session (6619, 6399, 8010, 8020, 8030,
8219, 8220, 8291, 8299) was also already claimed. This is an imperfect
semantic fit, flagged here for registry maintainers — same pattern
`cloud-itonami-isic-8292`'s own ADR documents for a similar situation.
