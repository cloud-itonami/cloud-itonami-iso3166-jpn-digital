# Business Model: Independent Digital-Agency-Regulated GovTech Procurement Compliance Service — Japan (Digital Agency)

## Classification

- Repository: `cloud-itonami-iso3166-jpn-digital`
- ISO 3166 (agency-level): `JPN-DIGITAL`, parent `JPN`
- Ooyake cross-reference: `gov.jpn.digital` (Digital Agency / デジタル庁)
- Activity: gBizID (デジタル庁 / 経済産業省 の行政サービス共通の法人・個人事業主向け認証) registration, and Digital Agency government-cloud (Gov-Cloud) service-catalog and technical/security baseline standards for digital-government procurement
- Social impact: [:govtech-market-access :digital-standard-compliance :public-spend-transparency]

## Customer

- a SaaS or IT operator bidding on a Digital-Agency-governed government-cloud contract
- an operator needing gBizID registration to access a Japanese government e-procurement or digital-service portal
- a foreign GovTech vendor navigating Japan's digital-government technical and security baseline for the first time

## Offer

- gBizID registration walkthrough
- Gov-Cloud service-catalog and technical/security-baseline compliance checklist
- digital-government procurement portal navigation
- compliance-audit export package for the operator's own records

## Revenue

- per-engagement compliance-review fee
- recurring regulatory-change monitoring subscription
- compliance-audit export package

## Trust Controls

- any actual filing, registration, or compliance-program submission
  requires Digital Procurement Compliance Governor clearance and always escalates to human
  sign-off (`:filing/submit` is never automated at any phase)
- a false or fabricated regulatory-requirement claim is a HARD hold that
  cannot be overridden by human approval alone — it must be corrected
  against a cited Digital Agency source first
- this service does **not** provide legal or tax advice; characterization
  and filing on the client's behalf beyond checklist/draft assistance
  routes to Japan-licensed counsel or a registered agent
- every requirement cites the official Digital Agency source or
  regulation, never invented

## Boundary with adjacent actors (read before forking)

- **`cloud-itonami-iso3166-jpn`**: the COUNTRY-level coordinator (general
  Japan public-sector market entry). This repo is a narrower, deeper
  AGENCY-level leaf — most operators need the country-level blueprint plus
  only the agency-level blueprints that actually apply to their contract.
- **`com-etzhayyim-ooyake`** (etzhayyim/root): read-only civic-wayfinding
  mirror of government structure, non-commercial, barred from acting as or
  for the government (G3 impersonation ban). This blueprint is commercial
  and never claims to be Digital Agency or an official channel.
- **`matsurigoto`** (etzhayyim/root): sovereign e-government statecraft —
  literally the government. This blueprint is an independent operator that
  engages with Digital Agency under its public rules — never the
  agency itself.
- **`com-etzhayyim-toritsugi`** (etzhayyim/root): guides a consenting
  INDIVIDUAL citizen through their OWN procedure, non-profit,
  donation-only. This blueprint's client is a business operator, not an
  individual citizen, and it is commercial.
- **`cloud-itonami-M6910`**: helps a client BECOME a legal entity
  (incorporation, ISIC 6910) — a prior, different regulatory phase (company
  law). This blueprint assumes incorporation is already done and handles
  Digital Agency-specific compliance (a different regulatory domain).
