# Phase 1 Asset Governance Hub Review Remediation

Scope: F-001 Asset Governance Hub Phase 1 on `main` at `f10ab0a1ffeb806cce0340c35c3e22042ac654d9`.

The review is limited to feature completeness and implementation correctness. Browser verification is out of scope. Findings below are tracked to closure in the remediation branch.

## Findings

| ID | Priority | Finding | Remediation | Status |
| --- | --- | --- | --- | --- |
| F1 | P1 | Asset Detail waits for the legacy aggregate endpoint, which performs synchronous cross-domain fan-out before the independently fetched sections can start. A slow source can block initial rendering. | Detail now reads only Asset-owned facts; source attributes and all six Sections load in parallel through independent requests. | Fixed |
| F2 | P1 | Security table classification lookup omits datasource identity and does not restrict results to active classifications. | Security reads use project + datasource + database + table and only `ACTIVE` rows; exact-key reads also reject non-active rows and expose the classification status. | Fixed |
| F3 | P1 | Metric Usage reads Metric-owned data without enforcing the Metric `READ` permission used by the public usage controller. | Metric assets require `MetricPermissionCode.READ`; denied requests return `PERMISSION_DENIED` before provider lookup. | Fixed |
| F4 | P1 | Metadata field lookup resolves a table with the full asset key but drops schema when listing fields; duplicate names in different schemas can return fields from the wrong table. | The Metadata query contract now carries schema and queries the exact datasource/database/schema/table tuple, including null/blank Schema handling. | Fixed |
| F5 | P2 | Metadata and Security actions preserve the return Asset id but do not select the actual source object in their specialist pages. | Metadata opens the exact entity detail drawer by entity id; Security opens the classification tab with the table identity filter; both retain the return Asset id. | Fixed |
| F6 | P2 | Usage is reported `OK` even when page activity, lineage references, and business consumption are all unavailable. | Usage now returns `UNAVAILABLE` when every child fact is unavailable and preserves typed per-source states for partial results. | Fixed |
| F7 | P2 | Provider diagnostics report registration and reconcile state but omit which Sections each source type supports. | Provider SPI declares source coverage; reconcile diagnostics and the inventory UI expose each source type's supported Sections. | Fixed |
| F8 | P2 | The legacy `fields` block always reports `UNAVAILABLE` for physical tables although the Technical Metadata Section returns fields and the UI separately renders them. | The stale fields block was removed; physical table columns render from Technical Metadata only. | Fixed |
| F9 | P2 | Fixed Section contracts use primitive status strings and nested `Map<String, Object>` payloads despite the typed Section contract and repository style rules. | `SectionView` uses `SectionStatus`; Overview, Governance, and Usage summaries are typed records/enums; provider-owned summaries stay typed across the Asset API. | Fixed |
| F10 | P2 | Lifecycle now contributes a public SectionProvider SPI, but its dependency contract still says the module has no outgoing SPI and is a leaf. | Lifecycle `DEPENDENCIES.md` now documents the SPI export and Asset consumer while preserving Lifecycle as the TTL truth owner. | Fixed |
| F11 | P2 | AssetDiscoverService duplicates Metadata context construction and has a separate provider dispatch/logging path for Usage, making permission and diagnostics behavior diverge. | Metadata context resolution and Section provider selection/invocation/completion logging are centralized. | Fixed |

## Closure Record

| ID | Result / evidence | Status |
| --- | --- | --- |
| F1 | `AssetDiscoverService.detail`, independent `/source-attributes`, parallel Section requests in Asset Detail UI. | Closed |
| F2 | `findActiveByTable`, exact datasource/database/table lookup, `ACTIVE` status in query and response. | Closed |
| F3 | Metric Usage permission guard uses `MetricPermissionCode.READ`. | Closed |
| F4 | `MetadataQueryApi.listPhysicalColumns` and Catalog query now carry schema. | Closed |
| F5 | Metadata entity drawer and Security classification tab receive target identity plus `returnAssetId`. | Closed |
| F6 | Typed Usage child facts determine parent status. | Closed |
| F7 | `supportedSections` API response and inventory display. | Closed |
| F8 | Legacy detail/UI fields block removed. | Closed |
| F9 | Typed summaries and enum states retain the existing Section JSON contract. | Closed |
| F10 | Lifecycle dependency contract synchronized with its implemented SPI. | Closed |
| F11 | Shared metadata resolver and provider dispatch path. | Closed |
