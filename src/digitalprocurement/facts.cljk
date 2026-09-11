(ns digitalprocurement.facts
  "Japan Digital Agency (デジタル庁) gBizID + Gov-Cloud/ISMAP compliance
  catalog -- the ONLY source of regulatory-requirement facts this actor
  is allowed to cite (`digitalprocurement.governor`'s spec-basis check
  enforces that every proposal touching `:compliance/assess`,
  `:filing/draft`, or `:filing/submit` cites this catalog and nothing
  invented).

  Every fact below was verified via web search against `digital.go.jp`,
  `meti.go.jp`, `gbiz-id.go.jp`, and `cyber.go.jp` government domains
  during this repo's research pass (2026-07-22). Two tracks, each with
  its own owner authority and legal basis -- do NOT merge them into one
  undifferentiated 'Digital Agency requirement':

    :gbizid    -- 法人共通認証基盤（GビズID）, jointly operated by Digital
                  Agency (デジタル庁) and METI (経済産業省). Common
                  corporate authentication needed to access government
                  e-procurement / digital-service portals.
    :govcloud  -- Digital Agency government-cloud (ガバメントクラウド)
                  service-catalog admission. ISMAP registration is a
                  documented PREREQUISITE, not the whole story: Digital
                  Agency runs a SEPARATE technical/service-requirements
                  review on top of it.

  What this catalog deliberately does NOT claim (see README/dossier):
    - no ISMAP registration fee, timeline, or renewal period (not
      verified in any source consulted);
    - no specific content of the two named technical-requirement
      appendices beyond their names/categories (their content was never
      read, only that they exist and both must be satisfied);
    - no gBizID registration fee figure (not specifically stated in any
      source consulted, so this catalog only models it as a
      registration/verification workflow, no price commentary).")

(def catalog
  {:gbizid
   {:name "GビズID（GビズID） -- 法人共通認証基盤"
    :name-en "gBizID -- Common Corporate Authentication Infrastructure"
    :owner-authority "デジタル庁 / 経済産業省 (Digital Agency + METI, jointly operated)"
    :legal-basis "法人共通認証基盤（GビズID）-- 行政サービス共通の法人・個人事業主向け認証基盤"
    :official-portal "https://gbiz-id.go.jp/top/"
    :public-overview "https://pr.gbiz-id.go.jp/"
    :provenance "https://gbiz-id.go.jp/top/"
    :account-tiers
    {:entry  {:name-local "gBizIDエントリー"
              :description "basic tier, no identity verification"}
     :prime  {:name-local "gBizIDプライム"
              :description "corporate representative / sole-proprietor tier, requires identity verification -- needed for e.g. subsidy applications and most substantive government digital services"}
     :member {:name-local "gBizIDメンバー"
              :description "issued to an organization's employees under a Prime account"}}
    :prime-issuance-process
    ["traditional: mail an inkan certificate (印鑑証明書) plus a document stamped with the registered seal to the gBizID operation center -- typically 2-3 weeks"
     "from FY2024 (令和6年度): an online application using a My Number card became additionally available"]
    :prime-issuance-provenance
    ["https://note.com/pubcome_jp/n/n29dde9d96389"
     "https://www.digital.go.jp/assets/contents/node/basic_page/field_ref_resources/b5181b0c-6424-4977-b415-b1cbb3301bc8/51df186a/20240618_policies_assessment_outline_04.pdf"
     "https://www.meti.go.jp/policy/mono_info_service/digital_architecture/ouranos/ouranos_trust/250131/siryo8.pdf"]
    :validity-period "gBizIDプライム / gBizIDメンバー: 発行日から2年3ヶ月 (2 years 3 months from issuance date), renewal required after"
    :required-evidence
    ["gBizIDプライム identity-verification record (印鑑証明書郵送 or マイナンバーカードオンライン申請)"
     "gBizIDプライム account issuance confirmation"
     "gBizIDプライム有効期間（2年3ヶ月）管理記録"]}

   :govcloud
   {:name "ガバメントクラウド サービスカタログ登録 -- Digital Agency government-cloud service-catalog admission"
    :owner-authority "デジタル庁 (Digital Agency)"
    :legal-basis
    (str "ISMAP登録 (政府情報システムのためのセキュリティ評価制度 / "
         "Information system Security Management and Assessment Program) "
         "がガバメントクラウドサービスカタログ申請の前提条件（PREREQUISITE）。"
         "ISMAP登録のみではガバメントクラウド採用を保証しない -- デジタル庁による"
         "別途の技術・サービス要件審査 (SEPARATE review) が必要。")
    :ismap-is-prerequisite? true
    :ismap-provenance "https://www.cyber.go.jp/policy/group/general/ismap.html"
    :current-procurement-cycle
    "令和８年度募集 (FY2026 recruitment) -- デジタル庁におけるガバメントクラウド等の整備のためのクラウドサービスの提供 調達仕様書"
    :procurement-spec-url
    "https://www.digital.go.jp/assets/contents/node/basic_page/field_ref_resources/5107093b-7c29-4ffe-808b-6e39b5e0e5be/546bc008/20251226_procurement_public_notice_specification_02.pdf"
    :procurement-listing-url
    "https://www.digital.go.jp/en/procurement/5107093b-7c29-4ffe-808b-6e39b5e0e5be"
    :technical-requirement-appendices
    ;; Named categories only -- their internal content was never read
    ;; and this catalog does not invent it (see docstring).
    ["別紙１技術要件詳細「基本事項」" "別紙２技術要件詳細「サービス要件（基本）」"]
    :legal-framework
    (str "会計法 (Public Accounting Act) を含む共通の政府調達ルール + "
         "デジタル社会推進標準ガイドライン (Digital Government Promotion "
         "Standard Guidelines)。デジタル庁は外部弁護士・公認会計士で構成される"
         "コンプライアンス委員会を持ち、自庁の調達ルール・行動規範を策定する。"
         "指名停止対象者は指名から除外され、この制限は100,000 SDR以上の"
         "調達案件に適用される。")
    :legal-framework-provenance
    ["https://www.digital.go.jp/en/news/2ZYjJ7wg"
     "https://www.digital.go.jp/en/procurement"]
    :procurement-portal "https://www.digital.go.jp/en/procurement"
    :provenance "https://www.cyber.go.jp/policy/group/general/ismap.html"
    :required-evidence
    ["ISMAP cloud-service-list registration record (prerequisite)"
     "別紙１技術要件詳細「基本事項」チェックリスト充足記録"
     "別紙２技術要件詳細「サービス要件（基本）」チェックリスト充足記録"
     "Digital Agency procurement-portal filing record"]}})

(def valid-tracks (set (keys catalog)))

(defn spec-basis [track] (get catalog track))

(defn coverage
  ([] (coverage (keys catalog)))
  ([tracks]
   (let [have (filter catalog tracks) missing (remove catalog tracks)]
     {:requested (count tracks) :covered (count have)
      :covered-tracks (vec (sort (map name have)))
      :missing-tracks (vec (sort (map name missing)))
      :note "R0 catalog seed -- gBizID + Gov-Cloud/ISMAP only, JPN-DIGITAL agency scope"})))

(defn required-evidence-satisfied? [track submitted]
  (when-let [{:keys [required-evidence]} (spec-basis track)]
    (= (count required-evidence) (count (filter (set submitted) required-evidence)))))

(defn evidence-checklist [track] (:required-evidence (spec-basis track) []))

(defn ismap-prerequisite-track?
  "Is `track` one whose spec-basis names ISMAP as a documented
  prerequisite? (Only `:govcloud` today -- gBizID has no ISMAP
  dependency.)"
  [track]
  (boolean (:ismap-is-prerequisite? (spec-basis track))))
