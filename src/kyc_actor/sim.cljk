(ns kyc-actor.sim
  "Demo runner: push three representative verification cases through one
   OperationActor and watch the VerificationGovernor + approval workflow
   earn the (mock) verifier the right to approve a case.

     case1  clean subject, high confidence            → auto-commit (phase 3)
     case2  watchlist :exact hit (sanctioned subject)  → HARD hold, no override
     case3  correction/request (data-subject dispute)  → always escalate → approve → commit

   Uses kyc-actor.verifier/mock-verifier (dependency-light demo) — a real
   deployment injects kyc-actor.verifier/real-verifier instead (see
   kyc-actor.operation/build's :verifier opt and this repo's README.md).

   Run: clojure -M:dev:run"
  (:require [langgraph.graph :as g]
            [kyc-actor.store :as store]
            [kyc-actor.operation :as op]))

(defn- line [& xs] (println (apply str xs)))

(defn- run-case!
  [actor thread-id request context approve?]
  (let [res (g/run* actor {:request request :context context} {:thread-id thread-id})]
    (if (= :interrupted (:status res))
      (do (line "   ⏸  human review pending (reason: "
                (-> res :state :audit last :reason) ")")
          (let [res2 (g/run* actor
                             {:approval {:status (if approve? :approved :rejected) :by "compliance-1"}}
                             {:thread-id thread-id :resume? true})]
            (line "   ▶  " (if approve? "approved → " "rejected → ")
                  "disposition = " (get-in res2 [:state :disposition]))
            res2))
      (do (line "   → disposition = " (get-in res [:state :disposition])
                "  (confidence " (get-in res [:state :verdict :confidence]) ")")
          res))))

(defn -main [& _]
  (let [db (store/mem-store)
        _ (store/demo-data! db #?(:clj (System/currentTimeMillis) :cljs (.getTime (js/Date.))))
        actor (op/build db)]

    (line "── OperationActor (real engines when wired via :verifier; VerificationGovernor active) ──")

    (line "\ncase1  clean subject, high confidence")
    (run-case! actor "case1"
               {:op :case/approve :case-id "case-demo-clean"
                :subject {:subject/id "did:example:demo-clean" :subject/name "Example Clean Person (demo)"}
                :evidence {:refs {} :content-by-ref {}}}
               {:phase 3} true)

    (line "\ncase2  watchlist :exact hit (mock sanctioned-subject scenario)")
    (run-case! actor "case2-sanctioned"
               {:op :case/approve :case-id "case-demo-sanctioned"
                :subject {:subject/id "did:example:demo-sanctioned" :subject/name "Example Sanctioned Person (demo)"}
                :evidence {:refs {} :content-by-ref {}}}
               {:phase 3} true)

    (line "\ncase3  correction/request — data-subject disputes a prior verdict (always human review)")
    (run-case! actor "case3"
               {:op :correction/request :case-id "case-demo-clean"
                :subject {:subject/id "did:example:demo-clean" :subject/name "Example Clean Person (demo)"}
                :evidence {:refs {} :content-by-ref {}}}
               {:phase 3} true)

    (line "\n── audit ledger (append-only) ──")
    (doseq [f (store/ledger db)]
      (line "  " (pr-str f)))

    (line "\ndone.")))
