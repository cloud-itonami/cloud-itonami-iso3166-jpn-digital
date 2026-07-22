# cloud-itonami-iso3166-jpn-digital

Open ISO 3166 Agency Blueprint for **JPN-DIGITAL**: Digital Agency
(デジタル庁, Digital Agency) — a Japan-agency-level LEAF under
the `cloud-itonami-iso3166-jpn` country-level coordinator.

This repository designs a forkable OSS business for an independent
compliance consultant: an already-incorporated operator (typically one
already using `cloud-itonami-iso3166-jpn` for general Japan market entry)
gets a Compliance Advisor + independent **Digital Procurement Compliance Governor** to
navigate gBizID (デジタル庁 / 経済産業省 の行政サービス共通の法人・個人事業主向け認証) registration, and Digital Agency government-cloud (Gov-Cloud) service-catalog and technical/security baseline standards for digital-government procurement.

## No robotics premise — digital/data service exemption

Agency-specific compliance navigation is a pure data/software service with
no physical-domain work — the same exemption class as `cloud-itonami-6310`
and `cloud-itonami-gtin-*`. `blueprint.edn` sets
`:itonami.blueprint/robotics false` and `:required-technologies` lists only
real capabilities (`:identity`, `:forms`, `:dmn`, `:bpmn`, `:audit-ledger`),
no `:robotics`.

## Core Contract

```text
operator intake + prior filing/compliance history
        |
        v
Compliance Advisor -> Digital Procurement Compliance Governor -> compliance draft, or human sign-off
        |
        v
gated filing / registration / compliance-program submission + audit ledger
```

No automated proposal can submit a filing or registration the governor
refuses, suppress a compliance record, or claim a legal conclusion the
governor has not cleared. `:filing/submit` is never in any phase's `:auto`
set — it always requires human sign-off (mirrors `cloud-itonami-M6910`'s
`filing-submit-never-auto-at-any-phase` invariant).

## Implementation

`src/digitalprocurement/` — a langgraph-clj StateGraph actor, same
containment shape as `cloud-itonami-iso3166-ago`'s
`marketentry.*` (advisor sealed to proposals-only, independent
governor, append-only ledger, `Store` protocol swap, phase gate):

- `facts.cljc` — the gBizID + Gov-Cloud/ISMAP catalog, the ONLY source
  of regulatory-requirement facts the actor may cite. Two tracks,
  `:gbizid` and `:govcloud`, each with its own owner authority and
  legal basis.
- `governor.cljc` — the Digital Procurement Compliance Governor: a
  spec-basis/no-fabrication HARD check, an evidence-incomplete check, a
  **gBizID Prime identity-verification** HARD check (`:gbizid`
  track, `:filing/submit`), an **ISMAP-prerequisite-unverified** HARD
  check (`:govcloud` track, `:filing/submit` — ISMAP registration is a
  documented prerequisite for Gov-Cloud catalog admission, not
  optional), an independently-recomputed engagement-fee-mismatch
  check, a confidence-floor/actuation gate, and double-draft/
  double-submit guards, per track.
- `store.cljc` — `MemStore`/`DatomicStore` (via
  `kotoba-lang/langchain-store`, not a hand-rolled `enc`/`dec*`) for
  the `engagement` entity, which tracks BOTH the `:gbizid` and
  `:govcloud` tracks' state independently on the same record.
- `registry.cljc` — pure-function filing-draft/filing-submit record
  construction, one sequence per track.
- `digitalprocurementllm.cljc` — the Compliance Advisor (mock LLM,
  proposals only).
- `operation.cljc` — the StateGraph: intake → advise → govern → decide
  → [request-approval →] commit/hold, `interrupt-before` on human
  approval.
- `phase.cljc` — phase 0→3 rollout; `:filing/draft`/`:filing/submit`
  are permanently absent from every phase's `:auto` set.

Ops: `:engagement/intake`, `:compliance/assess` (per-track evidence
checklist), `:filing/draft`, `:filing/submit` — the latter three all
take a `:track` (`:gbizid` or `:govcloud`) in the request, since one
engagement runs both tracks independently.

## What this is NOT

- **Not Digital Agency (デジタル庁) itself, and not the
  government of Japan.** See [`docs/business-model.md`](docs/business-model.md)
  for the boundary with `com-etzhayyim-ooyake`, `matsurigoto`,
  `com-etzhayyim-toritsugi`, `legal-entity.etzhayyim.com`,
  `cloud-itonami-M6910`, and the country-level `cloud-itonami-iso3166-jpn`.
- **Not legal or tax advice.** Every regulatory claim must cite the
  official Digital Agency source and route final filings to
  Japan-licensed counsel or a registered agent where the law requires
  licensed representation.

## Capability layer

Resolves via [`kotoba-lang/iso3166`](https://github.com/kotoba-lang/iso3166)
(code `JPN-DIGITAL`, `:parent "JPN"`, cross-referenced to ooyake's
`gov.jpn.digital`). Required capabilities:

- :identity
- :forms
- :dmn
- :bpmn
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
