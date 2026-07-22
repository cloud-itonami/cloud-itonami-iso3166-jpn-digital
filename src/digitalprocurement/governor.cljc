(ns digitalprocurement.governor
  "Digital Procurement Compliance Governor -- the independent
  compliance layer that earns the DigitalProcurement-LLM the right to
  commit. The LLM has no notion of what gBizID or Digital Agency
  actually require, whether a gBizID Prime identity verification is
  actually on file, whether ISMAP registration -- a documented
  PREREQUISITE for Gov-Cloud service-catalog admission -- is actually
  verified, whether a claimed engagement fee actually equals base +
  months x rate, or when a draft stops being a draft and becomes a
  real-world gBizID/Gov-Cloud submission, so this MUST be a separate
  system able to *reject* a proposal and fall back to HOLD.

  `:itonami.blueprint/governor` is `:digital-procurement-compliance-governor`
  (blueprint.edn).

  This blueprint's own text (docs/business-model.md Trust Controls:
  'any actual filing, registration, or compliance-program submission
  requires Digital Procurement Compliance Governor clearance and
  always escalates to human sign-off'; 'a false or fabricated
  regulatory-requirement claim is a HARD hold that cannot be
  overridden by human approval alone') names exactly the checks below.

  Seven checks, in priority order, ALL HARD violations: a human
  approver CANNOT override them. The confidence/actuation gate is
  SOFT: it asks a human to look (low confidence / actuation), and the
  human may approve -- but see `digitalprocurement.phase`: for `:stake
  :actuation/draft-filing`/`:actuation/submit-filing` NO phase ever
  allows auto-commit either. Two independent layers agree that
  actuation is always a human call.

    1. Spec-basis                  -- did the compliance-track
                                       proposal cite an OFFICIAL
                                       source (`digitalprocurement.facts`),
                                       or invent one?
    2. Evidence incomplete         -- for `:filing/draft`/
                                       `:filing/submit`, has the
                                       track actually been assessed
                                       with a full evidence checklist
                                       on file?
    3. gBizID Prime missing        -- for `:filing/submit` on the
                                       `:gbizid` track, when the
                                       engagement declares
                                       `:requires-gbizid-prime? true`,
                                       INDEPENDENTLY verify
                                       `:gbizid-prime-verified?` is
                                       true. Grounded in gBizID's
                                       documented identity-verification
                                       requirement for Prime-tier
                                       accounts (gbiz-id.go.jp).
    4. ISMAP unverified            -- for `:filing/submit` on the
                                       `:govcloud` track, when the
                                       engagement declares
                                       `:requires-ismap? true`,
                                       INDEPENDENTLY verify
                                       `:ismap-verified?` is true.
                                       ISMAP registration is a
                                       documented PREREQUISITE for
                                       Gov-Cloud service-catalog
                                       admission (cyber.go.jp) --
                                       submitting a Gov-Cloud
                                       application claim without it is
                                       structurally invalid, not
                                       merely risky.
    5. Engagement fee mismatch     -- for `:filing/submit`,
                                       INDEPENDENTLY recompute whether
                                       the engagement's own `:claimed-
                                       fee` equals `base-fee +
                                       monthly-rate x monitoring-
                                       months` -- honest reapplication
                                       of the ground-truth-recompute
                                       discipline sibling actors use,
                                       matched against this repo's own
                                       revenue lines (per-engagement
                                       compliance-review fee +
                                       recurring monitoring
                                       subscription).
    6. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:filing/draft`/
                                       `:filing/submit` (REAL acts)
                                       -> escalate.

  Two more guards, double-draft/double-submit prevention, are enforced
  off dedicated per-track `:gbizid-drafted?`/`:gbizid-submitted?`/
  `:govcloud-drafted?`/`:govcloud-submitted?` facts (never a `:status`
  value)."
  (:require [digitalprocurement.facts :as facts]
            [digitalprocurement.registry :as registry]
            [digitalprocurement.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Drafting a real gBizID/Gov-Cloud filing package and submitting a
  real gBizID application / Gov-Cloud catalog application are the two
  real-world actuation events this actor performs."
  #{:actuation/draft-filing :actuation/submit-filing})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:compliance/assess` (or `:filing/draft`/`:filing/submit`)
  proposal with no spec-basis citation is a HARD violation -- never
  invent gBizID's or Digital Agency's requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:compliance/assess :filing/draft :filing/submit} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案はコンプライアンス要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:filing/draft`/`:filing/submit`, the track's required
  evidence checklist must actually be satisfied."
  [{:keys [op subject track]} st]
  (when (contains? #{:filing/draft :filing/submit} op)
    (let [assessment (store/assessment-of st subject track)]
      (when-not (and assessment
                     (facts/required-evidence-satisfied?
                      track (:checklist assessment)))
        [{:rule :evidence-incomplete
          :detail (str subject "/" (name track) " の必要書類が充足していない状態での提案")}]))))

(defn- gbizid-prime-missing-violations
  "For `:filing/submit` on the `:gbizid` track, when the engagement
  declares `:requires-gbizid-prime? true`, INDEPENDENTLY verify
  `:gbizid-prime-verified?` is true -- the flagship genuinely new
  check this vertical adds. CONDITIONAL on the engagement's own
  `:requires-gbizid-prime?` ground truth."
  [{:keys [op subject track]} st]
  (when (and (= op :filing/submit) (= track :gbizid))
    (let [e (store/engagement st subject)]
      (when (and (true? (:requires-gbizid-prime? e))
                 (not (true? (:gbizid-prime-verified? e))))
        [{:rule :gbizid-prime-missing
          :detail (str subject " はgBizIDプライムの本人確認を要するが未確認 -- 提出提案は進められない")}]))))

(defn- ismap-unverified-violations
  "For `:filing/submit` on the `:govcloud` track, when the engagement
  declares `:requires-ismap? true`, INDEPENDENTLY verify
  `:ismap-verified?` is true. ISMAP registration is a documented
  PREREQUISITE for Gov-Cloud service-catalog admission -- submitting a
  Gov-Cloud filing without it is structurally invalid."
  [{:keys [op subject track]} st]
  (when (and (= op :filing/submit) (= track :govcloud))
    (let [e (store/engagement st subject)]
      (when (and (true? (:requires-ismap? e))
                 (not (true? (:ismap-verified? e))))
        [{:rule :ismap-unverified
          :detail (str subject " はISMAP登録(ガバメントクラウド申請の前提条件)が未確認 -- 提出提案は進められない")}]))))

(defn- engagement-fee-mismatch-violations
  "For `:filing/submit`, INDEPENDENTLY recompute whether the
  engagement's own claimed fee equals base + months x rate."
  [{:keys [op subject]} st]
  (when (= op :filing/submit)
    (let [e (store/engagement st subject)]
      (when-not (registry/engagement-fee-matches-claim? e)
        [{:rule :engagement-fee-mismatch
          :detail (str subject " の申告手数料(" (:claimed-fee e)
                      ")が独立再計算値(" (registry/compute-engagement-fee e) ")と一致しない")}]))))

(defn- already-drafted-violations
  "For `:filing/draft`, refuses to draft the SAME engagement/track
  twice."
  [{:keys [op subject track]} st]
  (when (= op :filing/draft)
    (when (store/engagement-track-drafted? st subject track)
      [{:rule :already-drafted
        :detail (str subject "/" (name track) " は既にドラフト済み")}])))

(defn- already-submitted-violations
  "For `:filing/submit`, refuses to submit the SAME engagement/track
  twice."
  [{:keys [op subject track]} st]
  (when (= op :filing/submit)
    (when (store/engagement-track-submitted? st subject track)
      [{:rule :already-submitted
        :detail (str subject "/" (name track) " は既に提出済み")}])))

(defn check
  "Censors a DigitalProcurement-LLM proposal against the governor
  rules. Returns {:ok? bool :violations [..] :confidence c
  :escalate? bool :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (gbizid-prime-missing-violations request st)
                           (ismap-unverified-violations request st)
                           (engagement-fee-mismatch-violations request st)
                           (already-drafted-violations request st)
                           (already-submitted-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :track      (:track request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
