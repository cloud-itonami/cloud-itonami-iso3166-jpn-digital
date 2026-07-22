(ns digitalprocurement.registry-test
  (:require [clojure.test :refer [deftest is testing]]
            [digitalprocurement.registry :as registry]))

(deftest engagement-fee-recompute
  (let [e {:base-fee 600000 :monthly-rate 40000 :monitoring-months 12 :claimed-fee 1080000.0}]
    (is (== 1080000.0 (registry/compute-engagement-fee e)))
    (is (true? (registry/engagement-fee-matches-claim? e))))
  (let [bad {:base-fee 600000 :monthly-rate 40000 :monitoring-months 12 :claimed-fee 1500000.0}]
    (is (false? (registry/engagement-fee-matches-claim? bad)))))

(deftest register-draft-and-submit
  (let [d (registry/register-draft "eng-1" :gbizid 0)
        s (registry/register-submit "eng-1" :govcloud 0)]
    (is (= "JPN-GBIZID-DFT-000000" (get d "draft_number")))
    (is (= "JPN-GOVCLOUD-SUB-000000" (get s "submit_number")))
    (is (nil? (get-in d ["certificate" "proof"])))
    (is (= "draft-unsigned" (get-in s ["certificate" "status"])))))

(deftest register-requires-ids
  (is (thrown? Exception (registry/register-draft "" :gbizid 0)))
  (is (thrown? Exception (registry/register-submit "eng-1" "" 0))))
