# Operator guide

## Running the demo

```bash
kbb -M:dev:run
```

Walks three scenarios through `kyc-actor.operation`'s compiled graph with
`kyc-actor.verifier/mock-verifier`: a clean auto-commit, a sanctioned
hard-hold, and a correction-request that escalates then gets approved.

## Wiring a real verifier

```clojure
(require '[kyc-actor.verifier :as verifier]
         '[kyc-actor.operation :as op]
         '[watchlist.adapters.edn-index :as edn-index])

(def watchlist-index (edn-index/edn-index "path/to/kotoba-lang/watchlist-screen/resources/watchlist/lists"))
(def actor (op/build store {:verifier (verifier/real-verifier watchlist-index)}))
```

You must also supply an `ekyc-native-provider.ports/IEvidenceFetcher` (see
that repo's README — no implementation ships anywhere in this org today)
if you want `:document-ocr`/`:liveness` checks to resolve real content
rather than `:review`-by-default.

## Rollout phase

`context {:phase N}` passed into `g/run*`'s initial state controls
autonomy (`kyc-actor.phase/phases`). Start new deployments at phase 0 or 1
(read-only / assisted-approve-with-human-signoff) and only move to phase 3
(supervised-auto) once you trust the wiring in your own environment —
this is a per-deployment operational decision, not something this repo
picks for you (`phase/default-phase` is 3, matching
`cloud-itonami-isic-8291`'s own precedent, but that default is a
convenience for this repo's own demo/tests, not a recommendation to run a
new real deployment at full autonomy from day one).

## CACAO identity (kotobase.net auth)

```bash
kbb -M:identity
```

Mints/loads this actor's own Ed25519 identity at
`.kyc-actor/identity.edn` (gitignored — never commit this file). See
`kyc-actor.identity`'s module doc for `mint-kotobase-session`.

## Human review

Any case that escalates (`:disposition :escalate`) pauses at
`:request-approval` (`interrupt-before`). Resume with:

```clojure
(kyc-actor.api/resume-approval! actor case-id true "reviewer-id")   ; approve
(kyc-actor.api/resume-approval! actor case-id false "reviewer-id")  ; reject
```

A case that HARD-holds (`:hold` reached directly from `:decide`, e.g. a
`:sanctions-hard-gate` violation) never reaches `:request-approval` at
all — there is nothing to resume; the audit ledger (`kyc-actor.api/case-audit`)
records why.
