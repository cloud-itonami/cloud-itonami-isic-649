(ns kyc-actor.verifier-test
  (:require [clojure.test :refer [deftest is]]
            [face-liveness.model :as liveness-model]
            [kyc-actor.verifier :as verifier]
            [watchlist.model :as watchlist-model]
            [watchlist.ports :as watchlist-ports]))

(def now 1700000000000)
(defn- clock [] now)

;; Real ICAO Doc 9303 TD3 worked example -- same fixture kotoba-lang/mrz's
;; own conformance test uses.
(def valid-mrz-lines
  ["P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<"
   "L898902C36UTO7408122F1204159ZE184226B<<<<<10"])

(def open-eye {:p1 [0 0] :p4 [10 0] :p2 [3 2] :p6 [3 -2] :p3 [7 2] :p5 [7 -2]})
(def closed-eye {:p1 [0 0] :p4 [10 0] :p2 [3 0.2] :p6 [3 -0.2] :p3 [7 0.2] :p5 [7 -0.2]})
(defn- blink-frames []
  (map-indexed (fn [i eye] (liveness-model/frame i {:left-eye eye}))
               [open-eye open-eye closed-eye open-eye]))
(defn- blink-challenge [] (liveness-model/challenge "live-1" :blink {:issued-at now :ttl-ms 3600000}))

(def sanctioned-entity
  (watchlist-model/entity "ofac-sdn:1" :ofac-sdn
                           {:type :individual :primary-name "Example Sanctioned Person"}))
(def fresh-manifest (watchlist-model/list-manifest :ofac-sdn {:fetched-at (- now 86400000) :entity-count 1}))

(defn- fixed-watchlist-index [entities manifests]
  (reify watchlist-ports/IWatchlistIndex
    (entities [_] entities)
    (manifests [_] manifests)))

(deftest real-verifier-clean-case-produces-verified-proposal
  (let [index (fixed-watchlist-index [sanctioned-entity] [fresh-manifest])
        v (verifier/real-verifier index clock)
        request {:case-id "case-1"
                  :subject {:subject/id "did:example:clean" :subject/name "Totally Unrelated Name"}
                  :evidence {:refs {:document-ocr "doc-ref" :liveness "live-ref"}
                             :content-by-ref {"doc-ref" {:mrz-lines valid-mrz-lines}
                                               "live-ref" {:challenge (blink-challenge) :frames (blink-frames)}}}}
        proposal (verifier/-verify v request)]
    (is (= :verified (:ekyc-status proposal)))
    (is (= :verified (:liveness-status proposal)))
    (is (nil? (:watchlist-tier proposal)))
    (is (false? (:watchlist-stale? proposal)))
    (is (= 1.0 (:confidence proposal)))))

(deftest real-verifier-sanctioned-subject-surfaces-exact-watchlist-tier
  (let [index (fixed-watchlist-index [sanctioned-entity] [fresh-manifest])
        v (verifier/real-verifier index clock)
        request {:case-id "case-2"
                  :subject {:subject/id "did:example:bad" :subject/name "Example Sanctioned Person"}
                  :evidence {:refs {} :content-by-ref {}}}
        proposal (verifier/-verify v request)]
    (is (= :exact (:watchlist-tier proposal)))))

(deftest real-verifier-stale-watchlist-index-flags-stale
  (let [stale-manifest (watchlist-model/list-manifest :ofac-sdn {:fetched-at (- now (* 30 86400000)) :entity-count 1})
        index (fixed-watchlist-index [sanctioned-entity] [stale-manifest])
        v (verifier/real-verifier index clock)
        request {:case-id "case-3"
                  :subject {:subject/id "did:example:x" :subject/name "Anyone"}
                  :evidence {:refs {} :content-by-ref {}}}
        proposal (verifier/-verify v request)]
    (is (true? (:watchlist-stale? proposal)))))

(deftest real-verifier-tampered-document-yields-rejected-ekyc-status
  (let [tampered ["P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<"
                  "L898902C46UTO7408122F1204159ZE184226B<<<<<10"]
        index (fixed-watchlist-index [] [fresh-manifest])
        v (verifier/real-verifier index clock)
        request {:case-id "case-4"
                  :subject {:subject/id "did:example:x"}
                  :evidence {:refs {:document-ocr "doc-ref"} :content-by-ref {"doc-ref" {:mrz-lines tampered}}}}
        proposal (verifier/-verify v request)]
    (is (= :rejected (:ekyc-status proposal)))))

(deftest mock-verifier-scenario-selection
  (let [v (verifier/mock-verifier)]
    (is (= :exact (:watchlist-tier (verifier/-verify v {:case-id "case-sanctioned-1"}))))
    (is (nil? (:watchlist-tier (verifier/-verify v {:case-id "case-clean-1"}))))))
