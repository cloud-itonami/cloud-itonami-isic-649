(ns kyc-actor.policy-test
  (:require [clojure.test :refer [deftest is]]
            [kyc-actor.policy :as policy]))

(def clean-approve-request {:op :case/approve :case-id "c1" :subject {:subject/id "x"}})
(def clean-proposal {:confidence 0.95 :watchlist-tier nil :liveness-status :verified
                      :pep? false :summary "verification checks passed"})

(deftest clean-high-confidence-proposal-is-ok
  (let [v (policy/check clean-approve-request clean-proposal)]
    (is (true? (:ok? v)))
    (is (false? (:hard? v)))
    (is (false? (:escalate? v)))))

(deftest sanctions-hard-gate-never-overridable
  (let [v (policy/check clean-approve-request (assoc clean-proposal :watchlist-tier :exact))]
    (is (true? (:hard? v)))
    (is (false? (:ok? v)))
    (is (= [:sanctions-hard-gate] (mapv :rule (:violations v))))))

(deftest liveness-required-gate-blocks-approve-without-verified-liveness
  (let [v (policy/check clean-approve-request (assoc clean-proposal :liveness-status :review))]
    (is (true? (:hard? v)))
    (is (= [:liveness-required-gate] (mapv :rule (:violations v)))))
  (let [v (policy/check clean-approve-request (dissoc clean-proposal :liveness-status))]
    (is (true? (:hard? v)) "missing liveness-status entirely is also a hard block")))

(deftest liveness-required-gate-does-not-apply-to-non-approve-ops
  (let [v (policy/check {:op :case/status-check :case-id "c1" :subject {}}
                         (assoc clean-proposal :liveness-status nil))]
    (is (false? (:hard? v)))))

(deftest accreditation-claim-gate-blocks-regulatory-claims
  (doseq [phrase ["This service is bank-grade and AML-compliant."
                  "We are a licensed KYC provider."
                  "Fully compliant with all regulations."]]
    (let [v (policy/check clean-approve-request (assoc clean-proposal :summary phrase))]
      (is (true? (:hard? v)) (str "should block: " phrase))
      (is (= [:accreditation-claim-gate] (mapv :rule (:violations v)))))))

(deftest confidence-floor-escalates-not-holds
  (let [v (policy/check clean-approve-request (assoc clean-proposal :confidence 0.4))]
    (is (false? (:hard? v)))
    (is (true? (:escalate? v)))
    (is (false? (:ok? v)))))

(deftest pep-flag-always-escalates-even-clean-and-high-confidence
  (let [v (policy/check clean-approve-request (assoc clean-proposal :pep? true))]
    (is (false? (:hard? v)))
    (is (true? (:escalate? v)))))

(deftest correction-request-always-escalates-regardless-of-confidence
  (let [v (policy/check {:op :correction/request :case-id "c1" :subject {}}
                         (assoc clean-proposal :confidence 0.99))]
    (is (true? (:escalate? v)))
    (is (true? (:correction? v)))))

(deftest multiple-hard-violations-all-reported
  (let [v (policy/check clean-approve-request
                         (assoc clean-proposal :watchlist-tier :exact :liveness-status :review))]
    (is (= 2 (count (:violations v))))))
