(ns kyc-actor.verifier
  "The verification 'proposal' node's real intelligence -- NOT an LLM.
   Deliberate deviation from cloud-itonami-isic-8291's Dossier-LLM
   template: this repo's own ADR explains why an identity-verification
   VERDICT should come from deterministic engines, not language
   generation (an LLM hallucinating in an identity/AML decision path is
   exactly the wrong shape of risk for this domain).

   `real-verifier` genuinely calls kotoba-lang/ekyc-native-provider (which
   itself calls kotoba-lang/mrz + face-liveness + face-match) and
   kotoba-lang/watchlist-screen, combining both into ONE proposal
   kyc-actor.policy/check then censors. `mock-verifier` is a deterministic
   canned-response stand-in for dependency-light demos (kyc-actor.sim),
   mirroring cloud-itonami-isic-8291's dossier.llm/mock-advisor's role
   exactly -- a real actor consumer injects real-verifier via
   kyc-actor.operation/build's :verifier opt."
  (:require [ekyc.adapters.provider :as ekyc-provider]
            [ekyc-native-provider.core :as native]
            [ekyc-native-provider.ports :as fetch-ports]
            [watchlist.core :as watchlist-core]))

(defprotocol IVerifier
  (-verify [verifier request]
    "`request`: {:case-id :subject {:subject/id :subject/name}
                  :evidence {:refs {check evidence-ref}
                             :content-by-ref {evidence-ref content}}}
     (`content` per check kind matches
     ekyc-native-provider.ports/IEvidenceFetcher's documented shape.)
     Returns a proposal: {:confidence :ekyc-status :liveness-status
     :watchlist-tier :watchlist-stale? :pep? :summary :cites} --
     kyc-actor.policy/check's input shape."))

(defn- now-ms [] #?(:clj (System/currentTimeMillis) :cljs (.getTime (js/Date.))))

(defn- in-memory-fetcher
  "The simplest possible ekyc-native-provider.ports/IEvidenceFetcher:
   content is pre-attached to the request itself (see this ns's
   IVerifier doc) rather than resolved from a durable store -- adequate
   for this actor's own case-submission flow, where evidence arrives
   alongside the verification request. A deployment wanting durable
   custody (ekyc.adapters.kagi-custody) would inject a different fetcher
   here, not change this ns."
  [content-by-ref]
  (reify fetch-ports/IEvidenceFetcher
    (fetch-evidence [_ _check evidence-ref] (get content-by-ref evidence-ref))))

(defn real-verifier
  "`watchlist-index` is a watchlist.ports/IWatchlistIndex (e.g.
   watchlist.adapters.edn-index/edn-index pointed at real OFAC/UN data, or
   an in-memory demo index -- see kyc-actor.sim). `now-ms-fn` defaults to
   the system clock; pass a fixed fn in tests."
  ([watchlist-index] (real-verifier watchlist-index #(now-ms)))
  ([watchlist-index now-ms-fn]
   (reify IVerifier
     (-verify [_ {:keys [case-id subject evidence]}]
       (let [now (now-ms-fn)
             fetcher (in-memory-fetcher (:content-by-ref evidence))
             client (native/provider-client fetcher now-ms-fn)]
         (ekyc-provider/create-session! client {:id case-id :expires-at (+ now 3600000)} {})
         (doseq [[check evidence-ref] (:refs evidence)]
           (ekyc-provider/upload-evidence!
            client {:session-id case-id :check check :evidence-ref evidence-ref} {}))
         (let [ekyc-result (ekyc-provider/retrieve-result! client {:session-id case-id} {})
               liveness-item (first (filter #(= :liveness (:check %)) (:evidence ekyc-result)))
               watchlist-result (when-let [n (:subject/name subject)]
                                  (watchlist-core/screen watchlist-index n now))
               top (when watchlist-result (watchlist-core/highest-confidence-candidate watchlist-result))
               confidences (remove nil? [(:confidence liveness-item) (:confidence top)])]
           ;; Conservative combination: any weak signal drags overall
           ;; confidence down (min, not average) -- documented, not a
           ;; fabricated blended score.
           {:confidence (if (seq confidences) (apply min confidences) 0.0)
            :ekyc-status (:status ekyc-result)
            :liveness-status (:status liveness-item)
            :watchlist-tier (:tier top)
            :watchlist-stale? (:watchlist/stale? watchlist-result)
            ;; No PEP data source is wired in v1 -- OFAC SDN / UN Consolidated
            ;; are sanctions lists, not a distinct PEP list (a different data
            ;; source this actor doesn't have). Always false, honestly, not
            ;; guessed. See MATURITY.md.
            :pep? false
            :summary (str "self-built verification: ekyc=" (name (or (:status ekyc-result) :review))
                          ", watchlist=" (if top (name (:tier top)) "clear"))
            :cites [{:source :ekyc-native-provider :session case-id}
                    {:source :watchlist-screen :stale? (:watchlist/stale? watchlist-result)}]}))))))

(defn mock-verifier
  "Deterministic canned proposals, keyed off request :case-id containing
   'sanctioned' -- for dependency-light demo/sim use only. Mirrors
   dossier.llm/mock-advisor's exact role: the default kyc-actor.operation/
   build wires unless a real verifier is injected."
  []
  (reify IVerifier
    (-verify [_ {:keys [case-id]}]
      (if (re-find #"sanctioned" (str case-id))
        {:confidence 0.95 :ekyc-status :verified :liveness-status :verified
         :watchlist-tier :exact :watchlist-stale? false :pep? false
         :summary "self-built verification: mock sanctioned-subject scenario"
         :cites [{:source :mock-verifier}]}
        {:confidence 0.9 :ekyc-status :verified :liveness-status :verified
         :watchlist-tier nil :watchlist-stale? false :pep? false
         :summary "self-built verification: mock clean scenario"
         :cites [{:source :mock-verifier}]}))))
