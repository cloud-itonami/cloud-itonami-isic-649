(ns kyc-actor.policy
  "VerificationGovernor — the independent compliance layer standing between
   a verification proposal (kyc-actor.verifier's real engine fan-out
   result) and any case disposition. Mirrors cloud-itonami-isic-8291's
   DisclosureGovernor shape exactly (six checks, three HARD/un-overridable,
   three SOFT/always-escalate), domain-adapted for identity verification.

   Three HARD violations (a human approver CANNOT override):
     1. sanctions-hard-gate      — a watchlist hit at :exact confidence
                                    NEVER auto-approves, regardless of who
                                    asks or how clean everything else looks.
     2. liveness-required-gate   — a case proposing to approve/publish-gate
                                    an identity without a completed,
                                    :verified liveness check is a HARD
                                    block, not merely low-confidence.
     3. accreditation-claim-gate — this actor's honesty backstop: any
                                    proposal/response text claiming
                                    regulatory accreditation ('bank-grade',
                                    legal-sense 'AML-compliant', 'licensed',
                                    'accredited', 'government approved') is
                                    blocked structurally, server-side — not
                                    left to documentation discipline alone.
                                    This actor is a self-built verification
                                    service with no such accreditation (see
                                    docs/legal/data-handling.md); a proposal
                                    claiming otherwise is a policy violation
                                    in itself, independent of the
                                    verification outcome.

   Three SOFT/always-escalate (a human decides):
     4. confidence-floor                   — engine confidence below
                                              threshold → escalate.
     5. pep-flag-gate                      — PEP/government-official
                                              subject → always escalate,
                                              even clean and high-confidence.
     6. correction-request-always-escalates — a data-subject dispute/
                                              re-verification request NEVER
                                              auto-resolves, at any
                                              confidence, any phase."
  (:require [kotoba.lang.text :as str]))

(def confidence-floor 0.6)

(def accreditation-claim-terms
  "Case-insensitive substrings that trip the accreditation-claim-gate. Kept
   as data, not hidden in the check logic, so it's auditable and
   extensible without touching `check`."
  ["bank-grade" "bank grade"
   "aml-compliant" "aml compliant"
   "licensed kyc" "licensed provider" "licensed identity"
   "regulatory accredited" "accredited provider" "government approved"
   "certified compliant" "fully compliant with"])

(defn- sanctions-violations [proposal]
  (when (= :exact (:watchlist-tier proposal))
    [{:rule :sanctions-hard-gate
      :detail "watchlist match at :exact confidence -- never auto-approved"}]))

(defn- liveness-violations [{:keys [op]} proposal]
  (when (and (= :case/approve op)
             (not= :verified (:liveness-status proposal)))
    [{:rule :liveness-required-gate
      :detail (str "case/approve without a completed :verified liveness check (status: "
                   (or (:liveness-status proposal) :missing) ")")}]))

(defn- accreditation-claim-violations [proposal]
  (let [text (str/lower (str (:summary proposal)))]
    (when (some #(str/includes? text %) accreditation-claim-terms)
      [{:rule :accreditation-claim-gate
        :detail "proposal text claims regulatory accreditation this actor does not hold"}])))

(defn check
  "Censors a verification `proposal` (kyc-actor.verifier's real engine
   fan-out result) against the policy tables. Returns
   {:ok? bool :violations [..] :confidence c :hard? bool :escalate? bool
    :pep? bool :correction? bool}.

   - :hard?     — at least one HARD violation. Forces HOLD; a human cannot
                  override.
   - :escalate? — soft: low confidence, PEP-flagged subject, OR a
                  correction request. A human decides.
   - :ok?       — clean AND not escalating: safe to auto-approve."
  [request proposal]
  (let [hard (into [] (concat (sanctions-violations proposal)
                               (liveness-violations request proposal)
                               (accreditation-claim-violations proposal)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        pep? (boolean (:pep? proposal))
        correction? (= :correction/request (:op request))
        hard? (boolean (seq hard))]
    {:ok? (and (not hard?) (not low?) (not pep?) (not correction?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? pep? correction?))
     :pep? pep?
     :correction? correction?}))

(defn hold-fact
  [request verdict]
  {:t :policy-hold
   :op (:op request)
   :case-id (:case-id request)
   :subject (:subject request)
   :disposition :hold
   :basis (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
