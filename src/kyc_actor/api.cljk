(ns kyc-actor.api
  "The logical API surface a customer/integrator calls — plain functions,
   NOT HTTP handlers. Mirrors ekyc/aml's own 'network I/O stays with the
   host' convention: this repo models the operations
   (create/submit-evidence/request-verification/status/audit/dispute), a
   real deployment wires actual HTTP/transport on top (same separation
   kotoba-lang/composer's ADR-2607031510 documents for its own domain).

   Every response carries a fixed `disclosures` block, enforced HERE,
   server-side — not left to documentation discipline alone. This is the
   API-layer half of this actor's honesty backstop; kyc-actor.policy's
   accreditation-claim-gate is the OTHER half, blocking any *proposal*
   text making the same claims this block already denies. Both exist
   because a single point of enforcement is one bug away from a silent
   regression."
  (:require [langgraph.graph :as g]
            [kyc-actor.store :as store]))

(def disclosures
  {:non-adjudicating true
   :regulatory-accreditation :none
   :provider :self-built
   :jurisdiction-scope :unscoped
   :notice (str "This is a self-built verification service. It is NOT a "
                "licensed or accredited KYC/AML provider. Self-declared "
                "identity attributes are not independently verified. "
                "See docs/legal/data-handling.md for what remains "
                "unresolved (owner/counsel territory).")})

(defn- respond [body] (assoc body :disclosures disclosures))

(defn create-case!
  "POST /v1/cases equivalent. `subject` is {:subject/id ... :subject/name
   ... (optional, self-declared)}."
  [store id subject now-ms]
  (let [case (store/new-case id subject {:created-at now-ms})]
    (store/create-case! store case)
    (respond {:ok true :case-id id :status (:case/status case)})))

(defn submit-evidence!
  "POST /v1/cases/{id}/evidence equivalent. Attaches an evidence reference
   for `check` to the case (kyc-actor.verifier's :evidence request shape,
   built up here across calls) — does NOT itself run verification;
   verification runs when the case is submitted via `request-verification!`.
   `evidence-ref`/`content` mirror ekyc-native-provider.ports/
   IEvidenceFetcher's per-check content shape."
  [store id check evidence-ref content now-ms]
  (let [updated (store/update-case! store id
                  (fn [c]
                    (when c
                      (-> c
                          (assoc-in [:case/evidence check] {:evidence-ref evidence-ref :content content})
                          (assoc :case/updated-at now-ms)))))]
    (respond {:ok (some? updated) :case-id id :check check})))

(defn- case-evidence-request [store id]
  (let [c (store/case-by-id store id)
        evidence (:case/evidence c)]
    {:refs (into {} (map (fn [[k v]] [k (:evidence-ref v)])) evidence)
     :content-by-ref (into {} (map (fn [[_ v]] [(:evidence-ref v) (:content v)])) evidence)}))

(defn- run-op!
  [actor store case-id op subject]
  (let [request {:op op :case-id case-id :subject subject :evidence (case-evidence-request store case-id)}
        res (g/run* actor {:request request :context {}} {:thread-id case-id})]
    (respond {:ok true :case-id case-id
              :status (if (= :interrupted (:status res)) :escalated (get-in res [:state :disposition]))
              :pending-approval? (= :interrupted (:status res))})))

(defn request-verification!
  "POST /v1/cases/{id}/verify equivalent — runs the case through the actor
   (kyc-actor.operation's compiled graph): verify → govern → decide →
   commit | hold | human-approval-pending."
  [actor store case-id subject]
  (run-op! actor store case-id :case/approve subject))

(defn dispute!
  "POST /v1/cases/{id}/dispute equivalent — files a correction/request,
   which always escalates to human review (kyc-actor.phase never puts
   :correction/request in any phase's :auto set, at any phase)."
  [actor store case-id subject]
  (run-op! actor store case-id :correction/request subject))

(defn resume-approval!
  "The human-reviewer resume half of request-verification!/dispute!'s
   interrupt-before pause."
  [actor case-id approved? reviewer]
  (let [res (g/run* actor {:approval {:status (if approved? :approved :rejected) :by reviewer}}
                     {:thread-id case-id :resume? true})]
    (respond {:ok true :case-id case-id :status (get-in res [:state :disposition])})))

(defn case-status
  "GET /v1/cases/{id} equivalent."
  [store id]
  (if-let [c (store/case-by-id store id)]
    (respond {:ok true :case-id id :status (:case/status c) :disposition (:case/disposition c)})
    (respond {:ok false :error :case-not-found})))

(defn case-audit
  "GET /v1/cases/{id}/audit equivalent — a signed-nothing-yet excerpt of
   the ledger for this case (no signing wired in v1, see MATURITY.md)."
  [store id]
  (respond {:ok true :case-id id
            :audit (filterv #(= id (:case-id %)) (store/ledger store))}))
