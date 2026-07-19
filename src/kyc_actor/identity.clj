(ns kyc-actor.identity
  "CACAO/did:key self-mint identity for this actor — minted and held in the
   actor's OWN runtime (CLAUDE.md's kotoba-server section: an actor
   authenticates with its own key, never a shared operator secret), so
   this actor can authenticate to kotobase.net as itself. Persists the raw
   Ed25519 seed at `.kyc-actor/identity.edn` (gitignored, never
   committed).

   Streamlined port of gftdcojp/cloud-itonami's `cloud_itonami/identity.clj`
   (ADR-2607141700) — same architecture (own seed, atomic
   create-if-absent persistence, CACAO mint scoped to a kotobase graph),
   simplified for this actor's narrower need (no multi-target CLI dispatch,
   no itonami.cloud-specific audience — this actor only needs to
   authenticate to kotobase.net, not the itonami.cloud ops plane the
   original module also serves). JVM-only (java.security.SecureRandom /
   java.time / java.nio.file), not .cljc — a local operator tool, not
   edge/browser code, matching the original's own scoping."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [cacao.core :as cacao]
            [ed25519.core :as ed]
            [ipns.core :as ipns])
  (:import (java.security SecureRandom)
           (java.nio.file Files StandardOpenOption FileAlreadyExistsException)
           (java.time Instant ZoneOffset)
           (java.time.format DateTimeFormatter)))

(def default-kotobase-url "https://kotobase.net")
(def default-kotobase-aud "did:web:kotobase.net")

(def ^:private identity-path (io/file ".kyc-actor" "identity.edn"))

(defn- random-seed ^bytes []
  (let [b (byte-array 32)]
    (.nextBytes (SecureRandom.) b)
    b))

(def ^:private iso-formatter (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ss'Z'"))

(defn- iso
  "Instant -> the strict whole-second `YYYY-MM-DDTHH:MM:SSZ` CACAO wire
   format cacao.core/mint expects (truncated to seconds — fractional
   seconds are rejected downstream)."
  [^Instant instant]
  (.format iso-formatter (.atZone instant ZoneOffset/UTC)))

(defn- read-identity [^java.io.File path]
  (let [content (slurp path)]
    (when-not (str/blank? content)
      (edn/read-string content))))

(defn- graph-name
  "This actor's own graph: the key-derived IPNS name of its Ed25519
   public key. Pure function of `seed-hex`, never persisted (recomputed on
   every load so it can never diverge from the seed actually on disk)."
  [seed-hex]
  (ipns/pubkey->name (ed/pubkey-from-seed (ed/unhex seed-hex))))

(defn load-or-create-identity!
  "Load this actor's persisted identity, or generate + persist a new one.
   Returns {:seed-hex :did :graph}. :seed-hex is the private signing key —
   `.kyc-actor/` MUST stay gitignored (see .gitignore).

   First-time bootstrap is a check-then-act race handled the same way the
   original module documents: `Files/write`'s CREATE_NEW option fails
   atomically (OS-level) if another process already created the file
   between this process's .exists check and its own write — the loser
   catches that and re-reads the winner's identity instead of both
   processes ending up with a different seed than what's actually
   persisted. `path` defaults to `.kyc-actor/identity.edn`; tests pass a
   temp path instead of touching the real gitignored one."
  ([] (load-or-create-identity! identity-path))
  ([^java.io.File path]
   (let [identity (if (.exists path)
                    (read-identity path)
                    (let [seed (random-seed)
                          seed-hex (ed/hexify seed)
                          did (ed/did-key-from-seed seed)
                          identity {:seed-hex seed-hex :did did}]
                      (io/make-parents path)
                      (try
                        (Files/write (.toPath path)
                                     (.getBytes (pr-str identity) "UTF-8")
                                     (into-array StandardOpenOption [StandardOpenOption/CREATE_NEW]))
                        identity
                        (catch FileAlreadyExistsException _
                          (read-identity path)))))]
     (assoc identity :graph (graph-name (:seed-hex identity))))))

(defn mint-kotobase-session
  "Mint a kotobase.net CACAO from this actor's own seed, scoped to
   `resources` (e.g. this actor's own graph's read/write ops), valid for
   `ttl-seconds` (default 24h). `aud` defaults to kotobase.net's own did
   (the pod enforces aud == its configured node did and rejects a
   mismatch)."
  ([identity resources] (mint-kotobase-session identity resources {}))
  ([{:keys [seed-hex]} resources {:keys [aud ttl-seconds] :or {aud default-kotobase-aud ttl-seconds (* 24 3600)}}]
   (let [now (Instant/now)
         exp (.plusSeconds now ttl-seconds)]
     (:cacao-b64
      (cacao/mint {:seed (ed/unhex seed-hex)
                   :aud aud
                   :iat (iso now)
                   :exp (iso exp)
                   :nonce (str (random-uuid))
                   :resources resources})))))

(defn -main [& _]
  (let [identity (load-or-create-identity!)]
    (println "did:  " (:did identity))
    (println "graph:" (:graph identity))
    (println "(private key stays in .kyc-actor/identity.edn, gitignored -- never printed here)")))
