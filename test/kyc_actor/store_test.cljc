(ns kyc-actor.store-test
  (:require [clojure.test :refer [deftest is]]
            [kyc-actor.store :as store]))

(deftest mem-store-case-lifecycle
  (let [s (store/mem-store)
        c (store/new-case "c1" {:subject/id "did:example:x"} {:created-at 100})]
    (is (nil? (store/case-by-id s "c1")))
    (store/create-case! s c)
    (is (= c (store/case-by-id s "c1")))
    (is (= [c] (store/all-cases s)))
    (let [updated (store/update-case! s "c1" #(assoc % :case/status :approved))]
      (is (= :approved (:case/status updated)))
      (is (= :approved (:case/status (store/case-by-id s "c1")))))))

(deftest mem-store-account-tier
  (let [s (store/mem-store)]
    (is (nil? (store/account-tier s "did:example:x")))
    (store/with-account-tier! s "did:example:x" :teen)
    (is (= :teen (store/account-tier s "did:example:x")))))

(deftest mem-store-ledger-append-only
  (let [s (store/mem-store)]
    (is (= [] (store/ledger s)))
    (store/append-ledger! s {:t :test-fact :n 1})
    (store/append-ledger! s {:t :test-fact :n 2})
    (is (= [{:t :test-fact :n 1} {:t :test-fact :n 2}] (store/ledger s)))))

(deftest demo-data-seeds-fictitious-cases
  (let [s (store/mem-store)]
    (store/demo-data! s 1000)
    (is (= 2 (count (store/all-cases s))))
    (is (every? #(re-find #"demo" (get-in % [:case/subject :subject/name])) (store/all-cases s)))))
