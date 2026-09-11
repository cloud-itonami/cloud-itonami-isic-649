(ns kyc-actor.store
  "SSoT for the digital identity verification / eKYC-AML actor, behind a
   `Store` protocol so the backend is a swap, not a rewrite — mirrors
   cloud-itonami-isic-8291's dossier.store shape (Store protocol +
   MemStore, append-only ledger, contract test proving the protocol
   surface).

   Only MemStore ships in v1 (a DatomicStore backend, per 8291's own
   precedent using langchain.db, is a documented follow-up — see
   MATURITY.md; not built here for real because this session's effort
   went into the actual verification engines (kotoba-lang/mrz,
   face-liveness, watchlist-screen) rather than a second storage backend
   with no incremental honesty payoff).

   Case entities are deliberately narrow: an applicant reference
   (pseudonymous — :subject/id, optionally :subject/name for name
   screening; never raw government-ID document bytes or biometric
   templates, which live in ekyc.adapters.kagi-custody per
   kotoba-lang/ekyc-native-provider's own design, referenced here only by
   opaque evidence-ref), a per-check evidence map, a disposition, and an
   append-only ledger of every governor/phase decision. There is no field
   anywhere in this schema for raw document/biometric bytes — the scope
   boundary matches kotoba-lang/ekyc's own custody-vs-ledger split.")

(defprotocol Store
  (case-by-id [s id])
  (all-cases [s])
  (create-case! [s case])
  (update-case! [s id f] "apply `f` to the stored case, returns the updated case")
  (account-tier [s subject-id])
  (with-account-tier! [s subject-id tier])
  (ledger [s])
  (append-ledger! [s fact] "append one immutable decision fact"))

(def case-statuses #{:intake :pending :escalated :approved :held})

(defn new-case
  [id subject opts]
  {:case/id id
   :case/subject subject ; {:subject/id "did:..." :subject/name "..." (optional, self-declared)}
   :case/status :intake
   :case/evidence {} ; check-kind -> {:evidence-ref ...}, opaque refs only, never raw bytes
   :case/disposition nil
   :case/created-at (:created-at opts)
   :case/updated-at (:created-at opts)})

;; ───────────────────────── MemStore ─────────────────────────

(defrecord MemStore [state]
  Store
  (case-by-id [_ id] (get-in @state [:cases id]))
  (all-cases [_] (vals (:cases @state)))
  (create-case! [_ case]
    (swap! state assoc-in [:cases (:case/id case)] case)
    case)
  (update-case! [_ id f]
    (get-in (swap! state update-in [:cases id] f) [:cases id]))
  (account-tier [_ subject-id] (get-in @state [:account-tiers subject-id]))
  (with-account-tier! [_ subject-id tier]
    (swap! state assoc-in [:account-tiers subject-id] tier)
    tier)
  (ledger [_] (:ledger @state))
  (append-ledger! [_ fact]
    (swap! state update :ledger (fnil conj []) fact)
    fact))

(defn mem-store [] (->MemStore (atom {:cases {} :account-tiers {} :ledger []})))

;; ───────────────────────── demo data (fictitious, non-real subjects) ─────

(defn demo-data!
  "Seeds `store` with entirely fictitious cases/account-tiers so the actor +
   sim run offline with no real applicant ever named in this repository —
   same discipline as cloud-itonami-isic-8291's dossier.store/demo-data."
  [store now-ms]
  (with-account-tier! store "did:example:demo-clean" :teen)
  (with-account-tier! store "did:example:demo-unset" nil)
  (create-case! store (new-case "case-demo-clean"
                                 {:subject/id "did:example:demo-clean" :subject/name "Example Clean Person (demo)"}
                                 {:created-at now-ms}))
  (create-case! store (new-case "case-demo-sanctioned"
                                 {:subject/id "did:example:demo-sanctioned" :subject/name "Example Sanctioned Person (demo)"}
                                 {:created-at now-ms})))
