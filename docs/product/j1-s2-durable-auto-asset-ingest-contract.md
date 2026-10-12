# J1 / S2 · 自动及时资产纳管：Metadata → Asset 领域合同

Status: **IMPLEMENTING / one concentrated Draft PR / NOT ACCEPTED**. Approved direction: [#538](https://github.com/gitfortian/data-ops/issues/538) D1=A/D4=A. S1 [PR #540](https://github.com/gitfortian/data-ops/pull/540) is **MERGED**. This document freezes the cross-owner boundary before the S2 feature is enabled.

## Non-negotiable owner boundary

| Fact or action | Authority | Exact contract |
| --- | --- | --- |
| Project, datasource, physical namespace and committed table row | Metadata / DataSource | Metadata per-chunk table-row commit supplies `projectId + runId + metadataEntityId`; dry-run and non-table entities never emit |
| Durable pending delivery | Metadata outbox | Event INSERT occurs in the SAME `yakBusinessTransactionManager` transaction as the TABLE upsert; idempotency key `(project_id, run_id, entity_id)` |
| Single table existence | Metadata provider | `refreshInProject(projectId, entityId)` must verify project, physical TABLE, source type and `gone_at IS NULL`; legacy `refresh(sourceId)` is not authorized for automatic writes |
| Asset PENDING / changes / restoration | Asset Owner | `ingestCommittedMetadataTable` calls existing Asset upsert within Asset transaction. Never publishes, grants access, runs whole Provider sweep or calls `markGoneAssets` |
| Retry / redelivery | Metadata delivery worker | Claim-token database CAS + 5-minute lease, exponential capped backoff, max 8 attempts → DEAD; expired lease recoverable. After Asset commit but before ACK, safe idempotent replay |
| User-facing receipt | Metadata event status + Asset source lookup | Event `SUCCEEDED` means a scoped Asset Owner receipt has been acknowledged; it **does not** mean asset PUBLISHED or imply a batch's columns were completely enumerated |
| Manual/periodic reconciliation | Asset Owner existing system | Remains independently available; its project-wide GONE logic MUST NOT be called from per-table auto evidence |

## Partial failure and absence semantics

- Metadata Run is not a whole-run atomic transaction. Each successfully committed table chunk emits its event even if a later table/column/namespace fails and the Run is marked FAILED/PARTIAL/SUSPECT.
- A missing table in a source event is never absence evidence for any other table, datasource or project. `SOURCE_GONE` is outside S2's automatic positive-evidence worker.
- Re-reading an event only queries the originally authorized project and exact Metadata table entity ID. Current provider `gone_at` or permission revocation prevents stale writes; events remain RETRY/DEAD, never silently accepted.
- Repeating `(project,run,entity)` is constrained by a DB unique key; subsequent distinct Run tickets can safely upsert the same Asset key. Duplicate Asset creation uses existing unique-key collision handling.
- Events are inserted on completed Metadata table writes, **not** on start or on a `RunStatus.SUCCESS` callback. Browser timeouts or JVM crashes cannot erase a previously committed ticket.
- System dispatch uses scoped service authority, not the originating user's stale session; users with only Modeling rights never gain Asset write access through a public endpoint.
- Default `yak.metadata.auto-ingest.enabled=false` while S2 is under review; enabling requires both MySQL/PostgreSQL migrations and operational checks, not merely deploy of the PR. Disabled dispatch leaves pending evidence durable.

## Progress & required gates

| Task | Status | Verification |
| --- | --- | --- |
| S2-T00 cross-domain scope and partial commit contract | IN REVIEW | This contract + Owner tests |
| S2-T01 same-transaction outbox | IMPLEMENTED, CI pending | Data row + ticket rollback + no dry-run/REGISTERED test |
| S2-T02 project-scoped single-table ingest, no GONE | IMPLEMENTED, CI pending | Provider exact project SQL + Asset no-global-sweep tests |
| S2-T03 lease CAS, replay, retry, dead-letter | IMPLEMENTED, CI pending | Retry/lost ACK/multi-worker/project tests |
| S2-T04 admin read receipt/retry + UI provenance | API IMPLEMENTED; UI PENDING | Tenant read+write RBAC, Asset lookup separation |
| S2-T05 full regression, migrations, release evidence | PENDING | Full CI, Product Guard, database integration and manual E2E |

### Release / final acceptance

The S2 PR stays Draft until the whole S2-T00–T05 passes product review, full CI, migrations, negative test matrix and acceptance. Real MySQL `trade_db.trade_order` / `trade_order_detail` automatic ingest → Asset PENDING → Asset source lookup/replay remains **DEFERRED_MANUAL_TEST**, not an asserted pass.

No S2 change adds automatic publishing, SQL DDL execution, other source global sweeps, new homepage navigation or a mandatory Asset PENDING gate for ODS.
