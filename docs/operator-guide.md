# Operator Guide

Implementation: `src/digitalprocurement/` (see README.md's
Implementation section for the module map). "the advisor" below is
`digitalprocurement.digitalprocurementllm`; "the Digital Procurement
Compliance Governor" is `digitalprocurement.governor`; gBizID and
Gov-Cloud/ISMAP are separate `:track`s (`:gbizid` / `:govcloud`) on the
same client engagement, assessed and filed independently.

## First Deployment

1. Confirm the client already uses (or has completed the equivalent of)
   `cloud-itonami-iso3166-jpn` for general Japan market-entry; this repo is
   an agency-specific supplement, not a substitute.
2. Register the client's intake: business type, the specific
   Digital Agency-regulated activity involved, prior filing/compliance
   history in Japan if any.
3. Run the advisor in read-only mode against Digital Agency's
   (デジタル庁) published guidance.
4. Compare the checklist against the client's current documentation.
5. Enable gated filing/compliance-draft assistance once the
   Digital Procurement Compliance Governor contract is trusted; actual submission always
   requires human sign-off.

## Minimum Production Controls

- client-owned data store for compliance documents
- clear provenance (official Digital Agency source citation) for every
  requirement surfaced
- approval workflow for any filing, registration, or compliance-program
  submission
- named referral relationship with Japan-licensed counsel or a registered
  agent for anything beyond checklist/draft assistance
- monthly audit export

## Certification

Certified operators must prove data provenance, audit traceability, that
automated actions cannot bypass the Digital Procurement Compliance Governor, and a working
referral relationship with Japan-licensed counsel or a registered agent for
whatever licensed representation Japanese law requires for actual
Digital Agency filings.
