(ns digitalprocurement.store
  "SSoT for the JPN-DIGITAL (Digital Agency) compliance actor, behind a
  `Store` protocol so the backend is a swap, not a rewrite -- the same
  seam every prior cloud-itonami actor in this fleet uses.

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store, using `langchain-store.core` for the
                        shared EDN-blob codec + event-log helpers
                        instead of a hand-rolled `enc`/`dec*`
                        (ADR-2607141600).

  Both implement the same protocol and pass the same contract
  (test/digitalprocurement/store_contract_test.clj).

  The primary entity here is an `engagement` -- one operator's
  compliance engagement carrying BOTH parallel tracks:

    :gbizid   -- 法人共通認証基盤（GビズID）registration (Digital
                 Agency + METI)
    :govcloud -- Digital Agency government-cloud (ガバメントクラウド)
                 service-catalog admission, ISMAP-registration-gated

  filing-draft and filing-submit actuation events apply per-TRACK to
  the SAME engagement record (draft first, submit later, independently
  for each track). Dedicated double-actuation-guard booleans per track
  (`:gbizid-drafted?`/`:gbizid-submitted?`/`:govcloud-drafted?`/
  `:govcloud-submitted?`, never a single `:status` value).

  The ledger stays append-only on every backend."
  (:require [digitalprocurement.registry :as registry]
            [langchain.db :as d]
            [langchain-store.core :as ls]))

(defprotocol Store
  (engagement [s id])
  (all-engagements [s])
  (assessment-of [s engagement-id track] "committed track assessment, or nil")
  (ledger [s])
  (draft-history [s] "the append-only filing-draft history")
  (submit-history [s] "the append-only filing-submit history")
  (next-draft-sequence [s track])
  (next-submit-sequence [s track])
  (engagement-track-drafted? [s engagement-id track])
  (engagement-track-submitted? [s engagement-id track])
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-engagements [s engagements] "replace/seed the engagement directory"))

;; ----------------------- track-scoped field mapping -----------------------
;; No dynamic keyword construction -- each track's drafted?/submitted?/
;; draft-number/submit-number fields are explicit, named keys (mirrors
;; the rest of this fleet's explicit-boolean-field style).

(def ^:private track-fields
  {:gbizid   {:drafted? :gbizid-drafted?   :draft-number :gbizid-draft-number
              :submitted? :gbizid-submitted? :submit-number :gbizid-submit-number}
   :govcloud {:drafted? :govcloud-drafted?   :draft-number :govcloud-draft-number
              :submitted? :govcloud-submitted? :submit-number :govcloud-submit-number}})

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained engagement set covering both actuation
  lifecycles (draft, submit) across both tracks, plus the governor's
  own dossier-grounded checks: a clean case (eng-1), an
  unregistered-track fabrication-defense case (eng-2), a fee-mismatch
  case (eng-3), a missing-gBizID-Prime-verification case (eng-4), a
  missing-ISMAP-registration case (eng-5), and a second clean case
  (eng-6) for double-draft/double-submit coverage."
  []
  {:engagements
   {"eng-1" {:id "eng-1" :operator "Kita Systems KK" :portal "gBizID / Gov-Cloud service catalog"
             :base-fee 600000 :monthly-rate 40000 :monitoring-months 12
             :claimed-fee 1080000.0
             :requires-gbizid-prime? true :gbizid-prime-verified? true
             :requires-ismap? true :ismap-verified? true
             :gbizid-drafted? false :gbizid-submitted? false
             :govcloud-drafted? false :govcloud-submitted? false
             :status :intake}
    "eng-2" {:id "eng-2" :operator "Atlantis Digital LLC" :portal "gBizID / Gov-Cloud service catalog"
             :base-fee 600000 :monthly-rate 40000 :monitoring-months 12
             :claimed-fee 1080000.0
             :requires-gbizid-prime? true :gbizid-prime-verified? true
             :requires-ismap? true :ismap-verified? true
             :gbizid-drafted? false :gbizid-submitted? false
             :govcloud-drafted? false :govcloud-submitted? false
             :status :intake}
    "eng-3" {:id "eng-3" :operator "Minami Systems KK" :portal "gBizID / Gov-Cloud service catalog"
             :base-fee 600000 :monthly-rate 40000 :monitoring-months 12
             :claimed-fee 1500000.0
             :requires-gbizid-prime? true :gbizid-prime-verified? true
             :requires-ismap? true :ismap-verified? true
             :gbizid-drafted? false :gbizid-submitted? false
             :govcloud-drafted? false :govcloud-submitted? false
             :status :intake}
    "eng-4" {:id "eng-4" :operator "Higashi CloudTech KK" :portal "gBizID / Gov-Cloud service catalog"
             :base-fee 600000 :monthly-rate 40000 :monitoring-months 12
             :claimed-fee 1080000.0
             :requires-gbizid-prime? true :gbizid-prime-verified? false
             :requires-ismap? true :ismap-verified? true
             :gbizid-drafted? false :gbizid-submitted? false
             :govcloud-drafted? false :govcloud-submitted? false
             :status :intake}
    "eng-5" {:id "eng-5" :operator "Nishi GovCloud KK" :portal "gBizID / Gov-Cloud service catalog"
             :base-fee 600000 :monthly-rate 40000 :monitoring-months 12
             :claimed-fee 1080000.0
             :requires-gbizid-prime? true :gbizid-prime-verified? true
             :requires-ismap? true :ismap-verified? false
             :gbizid-drafted? false :gbizid-submitted? false
             :govcloud-drafted? false :govcloud-submitted? false
             :status :intake}
    "eng-6" {:id "eng-6" :operator "Chuo Civic Tech KK" :portal "gBizID / Gov-Cloud service catalog"
             :base-fee 450000 :monthly-rate 30000 :monitoring-months 6
             :claimed-fee 630000.0
             :requires-gbizid-prime? true :gbizid-prime-verified? true
             :requires-ismap? true :ismap-verified? true
             :gbizid-drafted? false :gbizid-submitted? false
             :govcloud-drafted? false :govcloud-submitted? false
             :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- draft-filing!
  [s engagement-id track]
  (let [seq-n (next-draft-sequence s track)
        result (registry/register-draft engagement-id track seq-n)
        {:keys [drafted? draft-number]} (get track-fields track)]
    {:result result
     :engagement-patch {drafted? true
                        draft-number (get result "draft_number")}}))

(defn- submit-filing!
  [s engagement-id track]
  (let [seq-n (next-submit-sequence s track)
        result (registry/register-submit engagement-id track seq-n)
        {:keys [submitted? submit-number]} (get track-fields track)]
    {:result result
     :engagement-patch {submitted? true
                        submit-number (get result "submit_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (engagement [_ id] (get-in @a [:engagements id]))
  (all-engagements [_] (sort-by :id (vals (:engagements @a))))
  (assessment-of [_ engagement-id track] (get-in @a [:assessments engagement-id track]))
  (ledger [_] (:ledger @a))
  (draft-history [_] (:draft-records @a))
  (submit-history [_] (:submit-records @a))
  (next-draft-sequence [_ track] (get-in @a [:draft-sequences track] 0))
  (next-submit-sequence [_ track] (get-in @a [:submit-sequences track] 0))
  (engagement-track-drafted? [_ engagement-id track]
    (boolean (get-in @a [:engagements engagement-id (:drafted? (get track-fields track))])))
  (engagement-track-submitted? [_ engagement-id track]
    (boolean (get-in @a [:engagements engagement-id (:submitted? (get track-fields track))])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :engagement/upsert
      (swap! a update-in [:engagements (:id value)] merge value)

      :assessment/set
      (let [[engagement-id track] path]
        (swap! a assoc-in [:assessments engagement-id track] payload))

      :engagement/mark-drafted
      (let [[engagement-id track] path
            {:keys [result engagement-patch]} (draft-filing! s engagement-id track)]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:draft-sequences track] (fnil inc 0))
                       (update-in [:engagements engagement-id] merge engagement-patch)
                       (update :draft-records registry/append result))))
        result)

      :engagement/mark-submitted
      (let [[engagement-id track] path
            {:keys [result engagement-patch]} (submit-filing! s engagement-id track)]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:submit-sequences track] (fnil inc 0))
                       (update-in [:engagements engagement-id] merge engagement-patch)
                       (update :submit-records registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-engagements [s engagements] (when (seq engagements) (swap! a assoc :engagements engagements)) s))

(defn seed-db
  "A MemStore seeded with the demo engagement set."
  []
  (->MemStore (atom (assoc (demo-data)
                           :assessments {}
                           :ledger [] :draft-sequences {} :draft-records []
                           :submit-sequences {} :submit-records []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  {:engagement/id                   {:db/unique :db.unique/identity}
   :assessment/key                  {:db/unique :db.unique/identity}
   :ledger/seq                      {:db/unique :db.unique/identity}
   :draft-record/seq                {:db/unique :db.unique/identity}
   :submit-record/seq               {:db/unique :db.unique/identity}
   :draft-sequence/track            {:db/unique :db.unique/identity}
   :submit-sequence/track           {:db/unique :db.unique/identity}})

(defn- engagement->tx [{:keys [id operator portal base-fee monthly-rate monitoring-months claimed-fee
                               requires-gbizid-prime? gbizid-prime-verified?
                               requires-ismap? ismap-verified?
                               gbizid-drafted? gbizid-draft-number gbizid-submitted? gbizid-submit-number
                               govcloud-drafted? govcloud-draft-number govcloud-submitted? govcloud-submit-number
                               status]}]
  (cond-> {:engagement/id id}
    operator                              (assoc :engagement/operator operator)
    portal                                (assoc :engagement/portal portal)
    base-fee                              (assoc :engagement/base-fee base-fee)
    monthly-rate                          (assoc :engagement/monthly-rate monthly-rate)
    monitoring-months                     (assoc :engagement/monitoring-months monitoring-months)
    claimed-fee                           (assoc :engagement/claimed-fee claimed-fee)
    (some? requires-gbizid-prime?)  (assoc :engagement/requires-gbizid-prime? requires-gbizid-prime?)
    (some? gbizid-prime-verified?)  (assoc :engagement/gbizid-prime-verified? gbizid-prime-verified?)
    (some? requires-ismap?)         (assoc :engagement/requires-ismap? requires-ismap?)
    (some? ismap-verified?)         (assoc :engagement/ismap-verified? ismap-verified?)
    (some? gbizid-drafted?)               (assoc :engagement/gbizid-drafted? gbizid-drafted?)
    gbizid-draft-number                   (assoc :engagement/gbizid-draft-number gbizid-draft-number)
    (some? gbizid-submitted?)             (assoc :engagement/gbizid-submitted? gbizid-submitted?)
    gbizid-submit-number                  (assoc :engagement/gbizid-submit-number gbizid-submit-number)
    (some? govcloud-drafted?)             (assoc :engagement/govcloud-drafted? govcloud-drafted?)
    govcloud-draft-number                 (assoc :engagement/govcloud-draft-number govcloud-draft-number)
    (some? govcloud-submitted?)           (assoc :engagement/govcloud-submitted? govcloud-submitted?)
    govcloud-submit-number                (assoc :engagement/govcloud-submit-number govcloud-submit-number)
    status                                (assoc :engagement/status status)))

(def ^:private engagement-pull
  [:engagement/id :engagement/operator :engagement/portal :engagement/base-fee :engagement/monthly-rate
   :engagement/monitoring-months :engagement/claimed-fee
   :engagement/requires-gbizid-prime? :engagement/gbizid-prime-verified?
   :engagement/requires-ismap? :engagement/ismap-verified?
   :engagement/gbizid-drafted? :engagement/gbizid-draft-number
   :engagement/gbizid-submitted? :engagement/gbizid-submit-number
   :engagement/govcloud-drafted? :engagement/govcloud-draft-number
   :engagement/govcloud-submitted? :engagement/govcloud-submit-number
   :engagement/status])

(defn- pull->engagement [m]
  (when (:engagement/id m)
    {:id (:engagement/id m) :operator (:engagement/operator m) :portal (:engagement/portal m)
     :base-fee (:engagement/base-fee m) :monthly-rate (:engagement/monthly-rate m)
     :monitoring-months (:engagement/monitoring-months m) :claimed-fee (:engagement/claimed-fee m)
     :requires-gbizid-prime? (boolean (:engagement/requires-gbizid-prime? m))
     :gbizid-prime-verified? (boolean (:engagement/gbizid-prime-verified? m))
     :requires-ismap? (boolean (:engagement/requires-ismap? m))
     :ismap-verified? (boolean (:engagement/ismap-verified? m))
     :gbizid-drafted? (boolean (:engagement/gbizid-drafted? m))
     :gbizid-draft-number (:engagement/gbizid-draft-number m)
     :gbizid-submitted? (boolean (:engagement/gbizid-submitted? m))
     :gbizid-submit-number (:engagement/gbizid-submit-number m)
     :govcloud-drafted? (boolean (:engagement/govcloud-drafted? m))
     :govcloud-draft-number (:engagement/govcloud-draft-number m)
     :govcloud-submitted? (boolean (:engagement/govcloud-submitted? m))
     :govcloud-submit-number (:engagement/govcloud-submit-number m)
     :status (:engagement/status m)}))

(defn- assessment-key [engagement-id track] (str engagement-id "::" (name track)))

(defrecord DatomicStore [conn]
  Store
  (engagement [_ id]
    (pull->engagement (d/pull (d/db conn) engagement-pull [:engagement/id id])))
  (all-engagements [_]
    (->> (d/q '[:find [?id ...] :where [?e :engagement/id ?id]] (d/db conn))
         (map #(pull->engagement (d/pull (d/db conn) engagement-pull [:engagement/id %])))
         (sort-by :id)))
  (assessment-of [_ engagement-id track]
    (ls/dec* (d/q '[:find ?p . :in $ ?k
                   :where [?a :assessment/key ?k] [?a :assessment/payload ?p]]
                 (d/db conn) (assessment-key engagement-id track))))
  (ledger [_] (ls/read-stream conn :ledger/seq :ledger/fact))
  (draft-history [_] (ls/read-stream conn :draft-record/seq :draft-record/record))
  (submit-history [_] (ls/read-stream conn :submit-record/seq :submit-record/record))
  (next-draft-sequence [_ track]
    (or (d/q '[:find ?n . :in $ ?t
              :where [?e :draft-sequence/track ?t] [?e :draft-sequence/next ?n]]
            (d/db conn) track)
        0))
  (next-submit-sequence [_ track]
    (or (d/q '[:find ?n . :in $ ?t
              :where [?e :submit-sequence/track ?t] [?e :submit-sequence/next ?n]]
            (d/db conn) track)
        0))
  (engagement-track-drafted? [s engagement-id track]
    (boolean (get (engagement s engagement-id) (:drafted? (get track-fields track)))))
  (engagement-track-submitted? [s engagement-id track]
    (boolean (get (engagement s engagement-id) (:submitted? (get track-fields track)))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :engagement/upsert
      (d/transact! conn [(engagement->tx value)])

      :assessment/set
      (let [[engagement-id track] path]
        (d/transact! conn [{:assessment/key (assessment-key engagement-id track)
                            :assessment/payload (ls/enc payload)}]))

      :engagement/mark-drafted
      (let [[engagement-id track] path
            {:keys [result engagement-patch]} (draft-filing! s engagement-id track)
            next-n (inc (next-draft-sequence s track))]
        (d/transact! conn
                     [(engagement->tx (assoc engagement-patch :id engagement-id))
                      {:draft-sequence/track track :draft-sequence/next next-n}
                      {:draft-record/seq (count (draft-history s)) :draft-record/record (ls/enc (get result "record"))}])
        result)

      :engagement/mark-submitted
      (let [[engagement-id track] path
            {:keys [result engagement-patch]} (submit-filing! s engagement-id track)
            next-n (inc (next-submit-sequence s track))]
        (d/transact! conn
                     [(engagement->tx (assoc engagement-patch :id engagement-id))
                      {:submit-sequence/track track :submit-sequence/next next-n}
                      {:submit-record/seq (count (submit-history s)) :submit-record/record (ls/enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (ls/append-blob! conn :ledger/seq :ledger/fact (count (ledger s)) fact)
    fact)
  (with-engagements [s engagements]
    (when (seq engagements) (d/transact! conn (mapv engagement->tx (vals engagements)))) s))

(defn datomic-store
  ([] (datomic-store {}))
  ([{:keys [engagements]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-engagements s engagements))))

(defn datomic-seed-db
  []
  (datomic-store (demo-data)))
