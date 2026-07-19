(ns kyc-actor.operation-test
  (:require [clojure.test :refer [deftest is]]
            [langgraph.graph :as g]
            [kyc-actor.operation :as op]
            [kyc-actor.store :as store]
            [kyc-actor.verifier :as verifier]))

(defn- fresh-actor [] (op/build (store/mem-store) {:verifier (verifier/mock-verifier)}))

(defn- clean-request [case-id]
  {:op :case/approve :case-id case-id
   :subject {:subject/id "did:example:clean" :subject/name "Clean Subject"}
   :evidence {:refs {} :content-by-ref {}}})

(deftest phase-3-clean-case-auto-commits
  (let [actor (fresh-actor)
        res (g/run* actor {:request (clean-request "case-clean-1") :context {:phase 3}} {:thread-id "t1"})]
    (is (not= :interrupted (:status res)))
    (is (= :commit (get-in res [:state :disposition])))))

(deftest sanctioned-case-hard-holds-regardless-of-phase
  (let [actor (fresh-actor)
        request (assoc (clean-request "case-sanctioned-1") :subject {:subject/id "did:example:bad"})
        res (g/run* actor {:request request :context {:phase 3}} {:thread-id "t2"})]
    (is (not= :interrupted (:status res)) "a HARD violation never reaches human approval")
    (is (= :hold (get-in res [:state :disposition])))
    (is (= :sanctions-hard-gate (get-in res [:state :verdict :violations 0 :rule])))))

(deftest sanctioned-case-cannot-be-overridden-by-approval-since-it-never-escalates
  (let [actor (fresh-actor)
        request (assoc (clean-request "case-sanctioned-2") :subject {:subject/id "did:example:bad"})
        res (g/run* actor {:request request :context {:phase 3}} {:thread-id "t3"})]
    ;; even attempting to resume with an approval on a HARD-held thread
    ;; does nothing useful -- there was no interrupt to resume from.
    (is (= :hold (get-in res [:state :disposition])))))

(deftest correction-request-always-escalates-then-can-be-approved
  (let [actor (fresh-actor)
        request {:op :correction/request :case-id "case-clean-1"
                  :subject {:subject/id "did:example:clean"}
                  :evidence {:refs {} :content-by-ref {}}}
        res (g/run* actor {:request request :context {:phase 3}} {:thread-id "t4"})]
    (is (= :interrupted (:status res)))
    (is (= :data-subject-dispute (-> res :state :audit last :reason)))
    (let [res2 (g/run* actor {:approval {:status :approved :by "reviewer-1"}} {:thread-id "t4" :resume? true})]
      (is (= :commit (get-in res2 [:state :disposition]))))))

(deftest correction-request-rejected-approval-holds
  (let [actor (fresh-actor)
        request {:op :correction/request :case-id "case-clean-1"
                  :subject {:subject/id "did:example:clean"}
                  :evidence {:refs {} :content-by-ref {}}}
        res (g/run* actor {:request request :context {:phase 3}} {:thread-id "t5"})]
    (is (= :interrupted (:status res)))
    (let [res2 (g/run* actor {:approval {:status :rejected :by "reviewer-1"}} {:thread-id "t5" :resume? true})]
      (is (= :hold (get-in res2 [:state :disposition]))))))

(deftest phase-0-read-only-holds-every-approve
  (let [actor (fresh-actor)
        res (g/run* actor {:request (clean-request "case-clean-1") :context {:phase 0}} {:thread-id "t6"})]
    (is (= :hold (get-in res [:state :disposition])))))

(deftest phase-1-clean-case-escalates-not-auto-commits
  (let [actor (fresh-actor)
        res (g/run* actor {:request (clean-request "case-clean-1") :context {:phase 1}} {:thread-id "t7"})]
    (is (= :interrupted (:status res)))
    (is (= :phase-approval (-> res :state :audit last :reason)))))

(deftest commit-writes-case-status-and-ledger
  (let [db (store/mem-store)
        _ (store/create-case! db (store/new-case "case-clean-1" {:subject/id "x"} {:created-at 0}))
        actor (op/build db {:verifier (verifier/mock-verifier)})
        _ (g/run* actor {:request (clean-request "case-clean-1") :context {:phase 3}} {:thread-id "t8"})]
    (is (= :approved (:case/status (store/case-by-id db "case-clean-1"))))
    (is (= :committed (:t (last (store/ledger db)))))))

(deftest hold-writes-case-status-and-ledger
  (let [db (store/mem-store)
        _ (store/create-case! db (store/new-case "case-sanctioned-1" {:subject/id "x"} {:created-at 0}))
        actor (op/build db {:verifier (verifier/mock-verifier)})
        request (assoc (clean-request "case-sanctioned-1") :subject {:subject/id "did:example:bad"})
        _ (g/run* actor {:request request :context {:phase 3}} {:thread-id "t9"})]
    (is (= :held (:case/status (store/case-by-id db "case-sanctioned-1"))))
    (is (= :policy-hold (:t (last (store/ledger db)))))))
