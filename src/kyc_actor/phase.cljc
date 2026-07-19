(ns kyc-actor.phase
  "Phase 0→3 staged rollout — mirrors cloud-itonami-isic-8291's
   dossier.phase exactly (same structure, domain-adapted ops). Where the
   VerificationGovernor answers 'is this allowed?', the phase answers 'how
   much autonomy does the actor have *yet*?'. It can only ever make the
   actor MORE conservative than the governor: it downgrades a
   governor-clean approval to human-approval-required or hold, never the
   reverse.

     Phase 0  read-only       — :case/status-check only (still governed).
     Phase 1  assisted-approve — :case/approve allowed, every approval
                                 needs human sign-off.
     Phase 2  + correction    — adds :correction/request (still
                                 approval-only — see below, never auto).
     Phase 3  supervised-auto — governor-clean, high-confidence
                                 :case/approve may auto-commit.

   `:correction/request` is deliberately NEVER a member of any phase's
   `:auto` set, at any phase — a data-subject dispute/re-verification
   request always reaches a human, independent of the
   VerificationGovernor's own always-escalate check on the same op.")

(def read-ops #{:case/status-check})
(def write-ops #{:case/approve :correction/request})

(def phases
  {0 {:label "read-only" :writes #{} :auto #{}}
   1 {:label "assisted-approve" :writes #{:case/approve} :auto #{}}
   2 {:label "assisted-correction" :writes #{:case/approve :correction/request} :auto #{}}
   3 {:label "supervised-auto" :writes #{:case/approve :correction/request} :auto #{:case/approve}}})

(def default-phase 3)

(defn gate
  "Adjust a governor disposition for the rollout phase. Returns
   {:disposition kw :reason kw|nil}.
     - reads (:case/status-check) pass through unchanged.
     - a governor HOLD always stays HOLD (compliance wins).
     - a write op not yet enabled in this phase → HOLD (:phase-disabled).
     - a write op enabled but not auto-eligible → ESCALATE
       (:phase-approval), even if the governor was clean.
       :correction/request is never auto-eligible at any phase, so it
       always lands here once phase ≥ 2."
  [phase {:keys [op]} governor-disposition]
  (let [{:keys [writes auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold governor-disposition) {:disposition :hold :reason nil}
      (contains? read-ops op) {:disposition governor-disposition :reason nil}
      (not (contains? writes op)) {:disposition :hold :reason :phase-disabled}
      (and (= :commit governor-disposition) (not (contains? auto op)))
      {:disposition :escalate :reason :phase-approval}
      :else {:disposition governor-disposition :reason nil})))

(defn verdict->disposition
  [verdict]
  (cond (:hard? verdict) :hold
        (:escalate? verdict) :escalate
        :else :commit))
