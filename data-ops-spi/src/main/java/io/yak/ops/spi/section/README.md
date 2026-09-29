# Section Contract → Product Contract

Scope: #12, Slice 1 Asset Understanding Loop, PD-001, F-001 and F-001-A.

| User question | Section contract | Truth owner |
|---|---|---|
| 这是什么、谁负责 | OVERVIEW / GOVERNANCE | Asset |
| 来自哪里 | TECHNICAL_METADATA / LINEAGE | Metadata / Lineage |
| 是否可信 | QUALITY and evidence | Quality |
| 是否敏感 | SECURITY with permission state | Security |
| 谁在使用 | USAGE, with source provenance | Asset activity / Lineage / consuming domains |
| 下一步去哪 | actions with source ID | Owning domain |

The five statuses follow F-001-A exactly. `EMPTY` requires a successful
read proving absence. `NOT_APPLICABLE` follows the approved type matrix.
Missing provider, timeout and source failure mean `UNAVAILABLE`; denial
means `PERMISSION_DENIED` without leaking summary or evidence.

`SectionSummary` is a typed read-only projection implemented by each
source domain. `SectionEvidence` links to verifiable source facts,
`SectionProvenance` records source identity and observation time, and
`SectionCapability` records applicability and read-side availability.
Actions carry the source identity and are offered only after authorization.
The Asset ledger must not persist source domain truth.

## Adding a section provider

1. Implement `SectionProvider` in the domain that owns the facts. Match only the
   asset identities that domain can resolve; do not make Asset query another
   domain's tables or internal services.
2. Return the owning `SectionType`, owner domain, `SectionSummary`, provenance,
   evidence, capability, and source-linked actions. A successful read proving no
   record is `EMPTY`; a missing provider or failed read is `UNAVAILABLE`.
3. Register the provider as an optional Spring bean and keep its dependency
   direction through this SPI. The Asset query endpoint selects the matching
   provider and isolates failures to that section.
4. Add provider tests for identity matching, owner/provenance and empty results,
   plus Asset query tests for permission denial and provider failure. Render the
   five states explicitly and verify the user's path into and back from the
   owning domain.

The Metadata MVP provider handles physical tables and reads table/column facts
through `MetadataQueryApi`. Usage stays federated: Asset page activity,
Lineage structural references, and consuming-domain business activity retain
separate owners and statuses. Do not report structural references as business
consumption.

This SPI is a contract, not the aggregation runtime or an HTTP endpoint.
Asset identity resolution, access checks, isolation and rendered E2E evidence
remain implementation acceptance for later slices.
