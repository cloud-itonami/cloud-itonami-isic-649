# cloud-itonami-isic-649 — Digital Identity Verification (eKYC/AML) Actor

A governed, langgraph-clj `StateGraph` actor that fans out to REAL
verification engines (`kotoba-lang/mrz`, `kotoba-lang/face-liveness`,
`kotoba-lang/face-match` via `kotoba-lang/ekyc-native-provider`, and
`kotoba-lang/watchlist-screen`), censors every result through an
independent `VerificationGovernor`, and requires human approval for
anything the governor can't clear on its own. Phase F of the eKYC/AML
vendor plan approved this session (Phases A–E: `kotoba-lang/mrz`,
`kotoba-lang/watchlist-screen`, `kotoba-lang/face-liveness`,
`kotoba-lang/face-match`, `kotoba-lang/ekyc-native-provider`).

**This is a self-built verification service, not a licensed/accredited
KYC-AML provider.** Read `docs/legal/data-handling.md` before assuming
this satisfies any real compliance requirement — it explicitly does not
resolve several open legal questions (owner+counsel territory).

## Architecture

```
kyc-actor.verifier (real fan-out, NOT an LLM)
  ├─ ekyc-native-provider.core/provider-client
  │    ├─ mrz.core            (:document-ocr — real ICAO 9303 decode/checksum)
  │    ├─ face-liveness.core  (:liveness — real EAR/MAR/yaw challenge scoring)
  │    └─ face-match.core     (:face-match — deliberate stub, always :review)
  └─ watchlist.core/screen
       └─ real OFAC SDN + UN Consolidated List data (kotoba-lang/watchlist-screen)
                    │
                    ▼
kyc-actor.policy (VerificationGovernor — 3 HARD + 3 soft-escalate gates)
                    │
                    ▼
kyc-actor.phase (0→3 rollout — can only make the actor MORE conservative)
                    │
                    ▼
kyc-actor.operation (langgraph-clj StateGraph: intake→verify→govern→decide→
                      {commit | request-approval(human-in-the-loop)→commit|hold})
                    │
                    ▼
kyc-actor.store (MemStore, append-only ledger) ←→ kyc-actor.api (logical API surface)
```

## Why no LLM (deliberate deviation from the cloud-itonami actor template)

Every other `cloud-itonami-isic-*` actor in this fleet seals an LLM
advisor behind a Governor (Dossier-LLM ⊣ DisclosureGovernor,
`cloud-itonami-isic-8291`). This actor's `:verify` node is deliberately
**not** an LLM proposal — it's a deterministic fan-out to the real
verification engines above. An identity-verification VERDICT should come
from checksum/geometry/data-matching math, not language generation; an
LLM hallucinating in an AML decision path is exactly the wrong shape of
risk for this domain. See this repo's ADR for the full reasoning.

## VerificationGovernor — three HARD gates, three always-escalate

1. **sanctions-hard-gate** — a watchlist hit at `:exact` confidence never
   auto-approves, regardless of who asks.
2. **liveness-required-gate** — a case proposing `:case/approve` without a
   completed, `:verified` liveness check is a HARD block.
3. **accreditation-claim-gate** — this actor's honesty backstop: any
   proposal/response text claiming regulatory accreditation is blocked
   structurally, server-side (`kyc-actor.api`'s `disclosures` block
   enforces the same thing at the API layer).
4. **confidence-floor** — low engine confidence → escalate to a human.
5. **pep-flag-gate** — PEP/government-official subject → always escalate
   (no PEP data source is wired in v1 — always `false` today, honestly;
   see MATURITY.md).
6. **correction-request-always-escalates** — a data-subject dispute never
   auto-resolves, at any confidence, any phase.

## Usage

```clojure
(require '[kyc-actor.store :as store]
         '[kyc-actor.operation :as op]
         '[kyc-actor.verifier :as verifier]
         '[kyc-actor.api :as api])

(def db (store/mem-store))
;; mock-verifier for dependency-light demos; inject verifier/real-verifier
;; (with a real watchlist.ports/IWatchlistIndex) for genuine verification.
(def actor (op/build db {:verifier (verifier/mock-verifier)}))

(api/create-case! db "case-1" {:subject/id "did:..." :subject/name "..."} (System/currentTimeMillis))
(api/request-verification! actor db "case-1" {:subject/id "did:..." :subject/name "..."})
```

Demo: `kbb -M:dev:run` (`kyc-actor.sim`).

See `MATURITY.md`, `docs/legal/data-handling.md`, and
`90-docs/adr/*-cloud-itonami-isic-649.edn` (in the `com-junkawasaki/root`
superproject) for full detail.
