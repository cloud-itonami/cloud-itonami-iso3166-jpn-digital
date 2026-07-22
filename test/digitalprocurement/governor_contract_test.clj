(ns digitalprocurement.governor-contract-test
  "The governor contract as executable tests -- this vertical's own
  Trust Controls implemented faithfully, and the integration test
  running the compiled StateGraph end-to-end. The single invariant
  under test:

    DigitalProcurement-LLM never drafts or submits a filing the
    Digital Procurement Compliance Governor would reject,
    `:filing/draft`/`:filing/submit` NEVER auto-commit at any phase,
    `:engagement/intake` MAY auto-commit when clean, and every
    decision (commit OR hold) leaves exactly one ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [digitalprocurement.store :as store]
            [digitalprocurement.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :digital-procurement-operator :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- assess!
  [actor tid-prefix subject track]
  (exec-op actor (str tid-prefix "-assess") {:op :compliance/assess :subject subject :track track} operator)
  (approve! actor (str tid-prefix "-assess")))

(defn- draft!
  [actor tid-prefix subject track]
  (exec-op actor (str tid-prefix "-draft") {:op :filing/draft :subject subject :track track} operator)
  (approve! actor (str tid-prefix "-draft")))

(deftest clean-intake-auto-commits
  (testing "integration: engagement/intake at phase 3 auto-commits through the full compiled graph"
    (let [[db actor] (fresh)
          res (exec-op actor "t1"
                    {:op :engagement/intake :subject "eng-1"
                     :patch {:id "eng-1" :operator "Kita Systems KK"}} operator)]
      (is (= :commit (get-in res [:state :disposition])))
      (is (= "Kita Systems KK" (:operator (store/engagement db "eng-1"))) "SSoT actually updated")
      (is (= 1 (count (store/ledger db)))))))

(deftest compliance-assess-always-needs-approval
  (testing "assess is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :compliance/assess :subject "eng-1" :track :gbizid} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/assessment-of db "eng-1" :gbizid)))))))

(deftest fabricated-track-is-held
  (testing "a compliance/assess proposal with no official spec-basis -> HOLD"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :compliance/assess :subject "eng-1" :track :gbizid :no-spec? true} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/assessment-of db "eng-1" :gbizid)) "no assessment written"))))

(deftest draft-without-assessment-is-held
  (testing "filing/draft before any compliance assessment -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :filing/draft :subject "eng-1" :track :gbizid} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest gbizid-prime-missing-is-held-and-unoverridable
  (testing "missing gBizID Prime identity verification -> HARD hold (flagship check)"
    (let [[db actor] (fresh)
          _ (assess! actor "t5pre" "eng-4" :gbizid)
          _ (draft! actor "t5pre" "eng-4" :gbizid)
          res (exec-op actor "t5" {:op :filing/submit :subject "eng-4" :track :gbizid} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:gbizid-prime-missing} (-> (store/ledger db) last :basis)))
      (is (empty? (store/submit-history db))))))

(deftest ismap-unverified-is-held-and-unoverridable
  (testing "missing ISMAP registration (Gov-Cloud prerequisite) -> HARD hold"
    (let [[db actor] (fresh)
          _ (assess! actor "t6pre" "eng-5" :govcloud)
          _ (draft! actor "t6pre" "eng-5" :govcloud)
          res (exec-op actor "t6" {:op :filing/submit :subject "eng-5" :track :govcloud} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:ismap-unverified} (-> (store/ledger db) last :basis)))
      (is (empty? (store/submit-history db))))))

(deftest engagement-fee-mismatch-is-held
  (testing "claimed fee that doesn't equal base + months x rate -> HOLD"
    (let [[db actor] (fresh)
          _ (assess! actor "t7pre" "eng-3" :gbizid)
          _ (draft! actor "t7pre" "eng-3" :gbizid)
          res (exec-op actor "t7" {:op :filing/submit :subject "eng-3" :track :gbizid} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:engagement-fee-mismatch} (-> (store/ledger db) last :basis)))
      (is (empty? (store/submit-history db))))))

(deftest submit-always-escalates-then-human-decides
  (testing "integration: a clean fully-assessed submit still ALWAYS interrupts for human approval"
    (let [[db actor] (fresh)
          _ (assess! actor "t8pre" "eng-1" :gbizid)
          _ (draft! actor "t8pre" "eng-1" :gbizid)
          r1 (exec-op actor "t8" {:op :filing/submit :subject "eng-1" :track :gbizid} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, submit record drafted"
        (let [r2 (approve! actor "t8")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:gbizid-submitted? (store/engagement db "eng-1"))))
          (is (= 1 (count (store/submit-history db))) "one draft submit record"))))))

(deftest draft-always-escalates-then-human-decides
  (testing "a clean fully-assessed draft still ALWAYS interrupts for human approval"
    (let [[db actor] (fresh)
          _ (assess! actor "t9pre" "eng-1" :gbizid)
          r1 (exec-op actor "t9" {:op :filing/draft :subject "eng-1" :track :gbizid} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, draft record drafted"
        (let [r2 (approve! actor "t9")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:gbizid-drafted? (store/engagement db "eng-1"))))
          (is (= 1 (count (store/draft-history db))) "one draft record"))))))

(deftest engagement-double-draft-is-held
  (testing "drafting the same engagement/track twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (assess! actor "t10pre" "eng-1" :gbizid)
          _ (draft! actor "t10pre" "eng-1" :gbizid)
          res (exec-op actor "t10" {:op :filing/draft :subject "eng-1" :track :gbizid} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-drafted} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/draft-history db))) "still only the one earlier draft"))))

(deftest engagement-double-submit-is-held
  (testing "submitting the same engagement/track twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (assess! actor "t11pre" "eng-1" :gbizid)
          _ (draft! actor "t11pre" "eng-1" :gbizid)
          _ (exec-op actor "t11a" {:op :filing/submit :subject "eng-1" :track :gbizid} operator)
          _ (approve! actor "t11a")
          res (exec-op actor "t11" {:op :filing/submit :subject "eng-1" :track :gbizid} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-submitted} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/submit-history db))) "still only the one earlier submit"))))

(deftest two-tracks-are-independent
  (testing "drafting/submitting the gbizid track does not mark the govcloud track drafted/submitted"
    (let [[db actor] (fresh)
          _ (assess! actor "t12pre" "eng-1" :gbizid)
          _ (draft! actor "t12pre" "eng-1" :gbizid)
          _ (exec-op actor "t12a" {:op :filing/submit :subject "eng-1" :track :gbizid} operator)
          _ (approve! actor "t12a")
          e (store/engagement db "eng-1")]
      (is (true? (:gbizid-drafted? e)))
      (is (true? (:gbizid-submitted? e)))
      (is (false? (:govcloud-drafted? e)) "govcloud track untouched by gbizid actuation")
      (is (false? (:govcloud-submitted? e))))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :engagement/intake :subject "eng-1"
                          :patch {:id "eng-1" :operator "Kita Systems KK"}} operator)
      (exec-op actor "b" {:op :compliance/assess :subject "eng-1" :track :gbizid :no-spec? true} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
