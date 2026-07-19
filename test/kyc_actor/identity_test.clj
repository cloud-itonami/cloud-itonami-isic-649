(ns kyc-actor.identity-test
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [kyc-actor.identity :as identity]))

(defn- temp-path []
  (io/file (System/getProperty "java.io.tmpdir")
           (str "kyc-actor-identity-test-" (System/nanoTime))
           "identity.edn"))

(deftest first-load-generates-and-persists-a-real-did-key
  (let [path (temp-path)
        id (identity/load-or-create-identity! path)]
    (is (.exists path))
    (is (string? (:seed-hex id)))
    (is (= 64 (count (:seed-hex id))) "32-byte seed, hex-encoded")
    (is (re-matches #"did:key:z.+" (:did id)))
    (is (string? (:graph id)))))

(deftest second-load-returns-the-same-persisted-identity-not-a-new-one
  (let [path (temp-path)
        id1 (identity/load-or-create-identity! path)
        id2 (identity/load-or-create-identity! path)]
    (is (= (:seed-hex id1) (:seed-hex id2)))
    (is (= (:did id1) (:did id2)))
    (is (= (:graph id1) (:graph id2)))))

(deftest graph-is-recomputed-not-persisted
  ;; the identity.edn file on disk should NOT contain a stale :graph key --
  ;; it's always recomputed from :seed-hex on load, per this ns's own doc.
  (let [path (temp-path)
        _ (identity/load-or-create-identity! path)
        raw (edn/read-string (slurp path))]
    (is (nil? (:graph raw)))
    (is (contains? raw :seed-hex))
    (is (contains? raw :did))))

(deftest mint-kotobase-session-produces-a-non-empty-cacao-string
  (let [id (identity/load-or-create-identity! (temp-path))
        cacao-b64 (identity/mint-kotobase-session id ["kotoba://op/datom:read"])]
    (is (string? cacao-b64))
    (is (pos? (count cacao-b64)))))
