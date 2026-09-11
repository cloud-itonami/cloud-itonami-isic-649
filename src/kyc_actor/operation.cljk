(ns kyc-actor.operation
  "OperationActor — one identity-verification-case operation = one
   supervised actor run, expressed as a langgraph-clj StateGraph. Mirrors
   cloud-itonami-isic-8291's dossier.operation shape exactly (same node
   sequence, same interrupt-before human-in-the-loop pattern), with the
   Dossier-LLM's `:advise` node replaced by `:verify` — a fan-out to real
   verification engines (kyc-actor.verifier), not an LLM proposal. Its
   result is ALWAYS routed through the VerificationGovernor (`:govern`)
   and the rollout phase gate (`:decide`) before anything commits.

   Everything the actor depends on is injected, so each is a swap, not a
   rewrite:
     - the Store    (MemStore, v1)                — `store` arg
     - the Verifier (mock | real engine fan-out)   — :verifier opt
     - the Phase    (0→3 rollout)                  — :phase in context

   One graph run = one case operation (intake → verify → govern → decide →
   commit | hold | approval). Human-in-the-loop = real approval workflow:
   `interrupt-before #{:request-approval}` pauses the actor and hands the
   decision to a human reviewer (compliance officer). The reviewer resumes
   with `{:approval {:status :approved}}` (or `:rejected`)."
  (:require [langgraph.graph :as g]
            [langgraph.checkpoint :as cp]
            [kyc-actor.verifier :as verifier]
            [kyc-actor.policy :as policy]
            [kyc-actor.phase :as phase]
            [kyc-actor.store :as store]))

(defn- commit-fact [request proposal]
  {:t :committed
   :op (:op request)
   :case-id (:case-id request)
   :subject (:subject request)
   :disposition :commit
   :cites (:cites proposal)
   :summary (:summary proposal)})

(defn build
  "Compiles an OperationActor graph bound to `store` (any
   `kyc-actor.store/Store`). opts:
     :verifier     — a `kyc-actor.verifier/IVerifier` (default: mock-verifier)
     :checkpointer — langgraph checkpointer (default: in-mem)"
  [store & [{:keys [verifier checkpointer]
             :or {verifier (verifier/mock-verifier)
                  checkpointer (cp/mem-checkpointer)}}]]
  (-> (g/state-graph
       {:channels
        {:request {:default nil}
         :context {:default nil} ; injected phase / reviewer identity
         :proposal {:default nil}
         :verdict {:default nil}
         :disposition {:default nil} ; :commit | :hold | :escalate
         :approval {:default nil}
         :audit {:reducer into :default []}}})

      (g/add-node :intake
        (fn [{:keys [request]}]
          (store/update-case! store (:case-id request) #(assoc % :case/status :pending))
          {}))

      ;; Real engine fan-out (kyc-actor.verifier) — proposal only, never
      ;; commits or discloses anything itself.
      (g/add-node :verify
        (fn [{:keys [request]}]
          (let [p (verifier/-verify verifier request)]
            {:proposal p
             :audit [{:t :verify-proposal :case-id (:case-id request) :summary (:summary p)}]})))

      ;; VerificationGovernor — independent censor (separate system than
      ;; the verifier's engine fan-out).
      (g/add-node :govern
        (fn [{:keys [request proposal]}]
          {:verdict (policy/check request proposal)}))

      ;; Decide: governor disposition, then the rollout-phase gate (which
      ;; can only add caution). HARD governor violations → HOLD, no override.
      (g/add-node :decide
        (fn [{:keys [request context proposal verdict]}]
          (let [base (phase/verdict->disposition verdict)
                ph (:phase context phase/default-phase)
                {:keys [disposition reason]} (phase/gate ph request base)]
            (case disposition
              :hold
              {:disposition :hold
               :audit [(cond-> (policy/hold-fact request verdict)
                         reason (assoc :phase-reason reason :phase ph))]}

              :escalate
              {:disposition :escalate
               :audit [{:t :approval-requested
                        :op (:op request) :case-id (:case-id request)
                        :reason (or reason
                                    (cond (:correction? verdict) :data-subject-dispute
                                          (:pep? verdict) :pep-flag
                                          :else :low-confidence))
                        :phase ph :confidence (:confidence verdict)}]}

              :commit
              {:disposition :commit :proposal proposal}))))

      ;; Approval handoff — paused by interrupt-before; a human reviewer
      ;; (compliance officer) resumes with :approval.
      (g/add-node :request-approval
        (fn [{:keys [request approval verdict]}]
          (if (= :approved (:status approval))
            {:disposition :commit
             :audit [{:t :approval-granted :op (:op request) :case-id (:case-id request)
                      :by (:by approval)}]}
            {:disposition :hold
             :audit [(merge (policy/hold-fact request (assoc verdict :violations
                                                              [{:rule :approver-rejected}]))
                            {:t :approval-rejected})]})))

      ;; Commit — the ONLY node that writes the case status + audit ledger.
      (g/add-node :commit
        (fn [{:keys [request proposal]}]
          (store/update-case! store (:case-id request)
            #(assoc % :case/status :approved :case/disposition :commit :case/proposal proposal))
          (let [f (commit-fact request proposal)]
            (store/append-ledger! store f)
            {:audit [f]})))

      ;; Hold — write the rejection to the ledger; no case approval.
      (g/add-node :hold
        (fn [{:keys [request audit]}]
          (store/update-case! store (:case-id request)
            #(assoc % :case/status :held :case/disposition :hold))
          (when-let [hf (last (filter #(#{:policy-hold :approval-rejected} (:t %)) audit))]
            (store/append-ledger! store (assoc hf :disposition :hold)))
          {}))

      (g/set-entry-point :intake)
      (g/add-edge :intake :verify)
      (g/add-edge :verify :govern)
      (g/add-edge :govern :decide)

      (g/add-conditional-edges :decide
        (fn [{:keys [disposition]}]
          (case disposition
            :commit :commit
            :escalate :request-approval
            :hold)))

      (g/add-conditional-edges :request-approval
        (fn [{:keys [disposition]}]
          (if (= :commit disposition) :commit :hold)))

      (g/set-finish-point :commit)
      (g/set-finish-point :hold)

      (g/compile-graph
       {:checkpointer checkpointer
        :interrupt-before #{:request-approval}})))
