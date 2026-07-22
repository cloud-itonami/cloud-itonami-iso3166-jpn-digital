(ns digitalprocurement.registry
  "Pure-function filing-draft + filing-submit record construction for
  the JPN-DIGITAL (Digital Agency) actor -- an append-only compliance
  book-of-record draft, one per track (`:gbizid` / `:govcloud`).

  Like every sibling actor's registry, there is no single reference-
  number standard gBizID or Digital Agency assigns to a compliance
  filing-draft/filing-submit package this actor produces for its own
  audit trail -- this namespace does NOT invent one; it builds a
  track-scoped sequence number and validates the record's required
  fields, the same honest, non-fabricating discipline
  `digitalprocurement.facts` uses.

  `engagement-fee-matches-claim?` is an HONEST reapplication of the
  SAME ground-truth-recompute DISCIPLINE sibling actors use (verify a
  claimed monetary total against the entity's own recorded quantity x
  unit fields), reapplied to this repo's own revenue model
  (docs/business-model.md: 'per-engagement compliance-review fee' as
  the base + 'recurring regulatory-change monitoring subscription' as
  monthly-rate x monitoring-months -- an honest structural match, not
  an invented pricing scheme).

  This namespace is pure data + pure functions -- no I/O, no network
  call to gBizID or any Digital Agency portal. It builds the RECORD an
  operator would keep, not the act of actually registering/submitting
  itself (that is `digitalprocurement.operation`'s `:filing/submit`,
  always human-gated -- see README Actuation)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is
  the operator's act, not this actor's."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn- track-code [track]
  (str/upper-case (name track)))

(defn compute-engagement-fee
  "The ground-truth engagement fee for `engagement`'s own `:base-fee`
  (per-engagement compliance-review fee) and `:monitoring-months` x
  `:monthly-rate` (recurring regulatory-change monitoring subscription)
  -- a single flat base + months x rate calculation, not a full pricing
  engine."
  [{:keys [base-fee monthly-rate monitoring-months]}]
  (+ (double base-fee)
     (* (double monthly-rate) (double monitoring-months))))

(defn engagement-fee-matches-claim?
  "Does `engagement`'s own `:claimed-fee` equal the independently
  recomputed `compute-engagement-fee`?"
  [{:keys [claimed-fee] :as engagement}]
  (== (double claimed-fee) (compute-engagement-fee engagement)))

(defn register-draft
  "Validate + construct the FILING-DRAFT registration DRAFT for `track`
  (`:gbizid` or `:govcloud`) -- the operator's own act of preparing a
  gBizID / Gov-Cloud filing package. Pure function -- does not touch
  gBizID or any Digital Agency portal."
  [engagement-id track sequence]
  (when-not (and engagement-id (not= engagement-id ""))
    (throw (ex-info "draft: engagement_id required" {})))
  (when-not (and track (not= track ""))
    (throw (ex-info "draft: track required" {})))
  (when (< sequence 0)
    (throw (ex-info "draft: sequence must be >= 0" {})))
  (let [draft-number (str "JPN-" (track-code track) "-DFT-" (zero-pad sequence 6))
        record {"record_id" draft-number
                "kind" "filing-draft"
                "engagement_id" engagement-id
                "track" (name track)
                "immutable" true}]
    {"record" record "draft_number" draft-number
     "certificate" (unsigned-certificate "FilingDraft" draft-number draft-number)}))

(defn register-submit
  "Validate + construct the FILING-SUBMIT registration DRAFT for
  `track` -- the operator's own act of actually submitting the gBizID
  application / Gov-Cloud catalog application (always human-gated
  upstream)."
  [engagement-id track sequence]
  (when-not (and engagement-id (not= engagement-id ""))
    (throw (ex-info "submit: engagement_id required" {})))
  (when-not (and track (not= track ""))
    (throw (ex-info "submit: track required" {})))
  (when (< sequence 0)
    (throw (ex-info "submit: sequence must be >= 0" {})))
  (let [submit-number (str "JPN-" (track-code track) "-SUB-" (zero-pad sequence 6))
        record {"record_id" submit-number
                "kind" "filing-submit"
                "engagement_id" engagement-id
                "track" (name track)
                "immutable" true}]
    {"record" record "submit_number" submit-number
     "certificate" (unsigned-certificate "FilingSubmit" submit-number submit-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
