(ns digitalprocurement.facts-test
  (:require [clojure.test :refer [deftest is testing]]
            [digitalprocurement.facts :as facts]))

(deftest gbizid-has-spec-basis
  (let [sb (facts/spec-basis :gbizid)]
    (is (some? sb))
    (is (string? (:provenance sb)))
    (is (seq (:required-evidence sb)))
    (is (string? (:validity-period sb)))))

(deftest govcloud-has-spec-basis
  (let [sb (facts/spec-basis :govcloud)]
    (is (some? sb))
    (is (string? (:provenance sb)))
    (is (seq (:required-evidence sb)))
    (is (true? (:ismap-is-prerequisite? sb)))
    (is (= 2 (count (:technical-requirement-appendices sb)))
        "the two named appendix categories, content not invented")))

(deftest unknown-track-has-no-spec-basis
  (is (nil? (facts/spec-basis :unknown-track)))
  (is (nil? (facts/spec-basis :zzz))))

(deftest required-evidence-satisfied
  (let [sb (facts/spec-basis :gbizid)
        all (:required-evidence sb)]
    (is (true? (facts/required-evidence-satisfied? :gbizid all)))
    (is (not (facts/required-evidence-satisfied? :gbizid (take 1 all))))
    (is (nil? (facts/required-evidence-satisfied? :unknown-track all)))))

(deftest coverage-is-honest
  (let [c (facts/coverage [:gbizid :govcloud :unknown-track])]
    (is (= 3 (:requested c)))
    (is (= 2 (:covered c)))
    (is (= ["unknown-track"] (:missing-tracks c)))))

(deftest ismap-prerequisite-track-is-govcloud-only
  (is (true? (facts/ismap-prerequisite-track? :govcloud)))
  (is (false? (facts/ismap-prerequisite-track? :gbizid))))
