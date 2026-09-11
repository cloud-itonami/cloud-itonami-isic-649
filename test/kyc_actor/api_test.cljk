(ns kyc-actor.api-test
  (:require [clojure.test :refer [deftest is]]
            [kyc-actor.api :as api]
            [kyc-actor.operation :as op]
            [kyc-actor.store :as store]
            [kyc-actor.verifier :as verifier]))

(defn- fresh [] (let [s (store/mem-store)] [s (op/build s {:verifier (verifier/mock-verifier)})]))

(deftest every-response-carries-the-disclosures-block
  (let [[s _actor] (fresh)
        r (api/create-case! s "c1" {:subject/id "x"} 0)]
    (is (= api/disclosures (:disclosures r)))
    (is (= :none (get-in r [:disclosures :regulatory-accreditation])))))

(deftest create-and-status-round-trip
  (let [[s _actor] (fresh)]
    (api/create-case! s "c1" {:subject/id "x"} 0)
    (let [status (api/case-status s "c1")]
      (is (true? (:ok status)))
      (is (= :intake (:status status))))))

(deftest status-of-unknown-case-is-honest-not-found
  (let [[s _actor] (fresh)]
    (is (false? (:ok (api/case-status s "nope"))))
    (is (= :case-not-found (:error (api/case-status s "nope"))))))

(deftest submit-evidence-then-request-verification-clean-case-commits
  (let [[s actor] (fresh)]
    (api/create-case! s "case-clean-1" {:subject/id "x" :subject/name "Clean"} 0)
    (api/submit-evidence! s "case-clean-1" :document-ocr "ref-1" {:mrz-lines []} 0)
    (let [result (api/request-verification! actor s "case-clean-1" {:subject/id "x" :subject/name "Clean"})]
      (is (= :commit (:status result))))
    (is (= :approved (:status (api/case-status s "case-clean-1"))))))

(deftest request-verification-sanctioned-case-holds
  (let [[s actor] (fresh)]
    (api/create-case! s "case-sanctioned-1" {:subject/id "bad"} 0)
    (let [result (api/request-verification! actor s "case-sanctioned-1" {:subject/id "bad"})]
      (is (= :hold (:status result))))))

(deftest dispute-escalates-then-resume-approval-commits
  (let [[s actor] (fresh)]
    (api/create-case! s "case-clean-1" {:subject/id "x"} 0)
    (let [result (api/dispute! actor s "case-clean-1" {:subject/id "x"})]
      (is (true? (:pending-approval? result)))
      (is (= :escalated (:status result))))
    (let [resumed (api/resume-approval! actor "case-clean-1" true "reviewer-1")]
      (is (= :commit (:status resumed))))))

(deftest case-audit-only-returns-this-cases-facts
  (let [[s actor] (fresh)]
    (api/create-case! s "case-clean-1" {:subject/id "x"} 0)
    (api/create-case! s "case-sanctioned-1" {:subject/id "bad"} 0)
    (api/request-verification! actor s "case-clean-1" {:subject/id "x"})
    (api/request-verification! actor s "case-sanctioned-1" {:subject/id "bad"})
    (let [audit (api/case-audit s "case-clean-1")]
      (is (every? #(= "case-clean-1" (:case-id %)) (:audit audit)))
      (is (seq (:audit audit))))))
