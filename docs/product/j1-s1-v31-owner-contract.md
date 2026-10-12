# J1 / S1 · V3.1 Owner Contract and Phase Acceptance Ledger

Status: **IN_PROGRESS / one S1 draft PR**. Design: [#539](https://github.com/gitfortian/data-ops/issues/539) UX-GATE-A/B APPROVED (2026-10-12). Engineering: [#538](https://github.com/gitfortian/data-ops/issues/538). **UX-D6=B: no Home modifications.**

## S1 cross-owner implementation verification ledger (2026-10-12, pending final CI)

> This section supersedes the earlier *pre-implementation gap list* below. It does **not** claim runtime deployment E2E or S1-GATE approval. All changes remain in one Draft PR #540.

| S1 task | Implemented engineering contract (review with actual PR diff) | Still requires gate evidence |
|---|---|---|
| T00/T01 | Existing UX #539 frozen. DataSource source-ID links; project-scoped DataSource listing validates hints; Catalog entityId deep links check source/database on return and ignore stale project responses | Latest Architecture/Guard + negative tests |
| T02 | Metadata `JobQueryDTO` and `CollectJobAdminService.page` filter exact `dataSourceId/databaseName/schemaName` **inside Project ID** (existing query overload retained). UI prioritizes saved-job reuse; editing dirty leave protection and active-schedule impact confirmation | Actual API contract tests and UI |
| T03 | Run frozen `scopeSnapshot` and counts remain separate from current table catalog. Timeout does **not** prove a run ID or completion: consult persisted Job/Run history and do not auto-resubmit | Cross-role/manual unknown recovery E2E deferred |
| T04 | Existing AssetExplorer and Metadata entity owner remain sole physical-detail UI. Table → exact ODS main action, plus independent optional Asset manual governance / Semantic process inspection. Entity-return link verified to source | Table/column, RBAC and project-change regression |
| T05 | Asset project-wide Provider reconcile remains explicit/manual, only "accepted but unverified" from submission; lastRun status is historical with unknown/failed cases, Source Lookup remains separately queried | Server receipt correlation **not available** in S1: downgrade is intentional, not a success claim |
| T06 | Modeling source key: active Project + datasourceId + database + schema + table, with live exact DataSource table existence check; 0/1/N candidates, legacy schema ambiguity `UNVERIFIED`, not code/name matching. MySQL/PostgreSQL V3 persist source schema on model **and source column mapping**, including database lookup index | No automatic claim that missing legacy source schema uniquely identifies an existing model |
| T07 | S1 ODS UI validates Metadata entity/source, previews schema-aware live columns, manually maps and saves resumable ODS Draft ID, then atomic optimistic structure/mapping/source save. Live source columns are compared against preview fields/types/width/nullability/PK at commit. Reopen model ID retains source schema, return source catalog after save; dirty navigation prompts; late project write responses do not claim success | Browser refresh/Project/RBAC/full integration tests plus manual dataset |
| T08 | Added backend unit tests for 0/1/N, DB/schema collisions, ambiguous legacy, absent physical source, live drift; UI Jest for saved ODS reuse, ambiguous source, denied project source; Metadata exact paginated query test. | **Only actual CI successes and independent manual E2E can close this line** |

### Outstanding evidence / bounded limitations

- **Real MySQL `trade_db.trade_order` and `trade_order_detail` run → Modeling save/reopen** requires an integrated deployed environment, tracked as `DEFERRED_MANUAL_TEST`, not simulated PASS.
- No correlated Asset asynchronous reconcile receipt ID in current Owner service. S1 intentionally reports ACCEPTED/UNKNOWN and historical Provider evidence, never per-click SUCCESS.
- Database has an exact-source lookup index, **not a uniqueness constraint**; 0/1/N candidate UI supports historical duplicates. Transactional save rechecks existing candidates but does not claim a cross-transaction distributed uniqueness guarantee.
- Old ODS with non-null source ID/database/table but missing schema may require manual provenance repair. S1 fails closed when a named schema cannot be proven.
- S1 creates/updates **draft models only** and never auto-executes SQL, publishes, auto-reconciles Asset, or requires Asset UPDATE permission for Modeling.
- S1-GATE must remain unchecked until latest final PR HEAD has all required checks complete and a reviewer accepts the evidence.

## Frozen user journey

1. DataSource: connection saved, connection test and physical Metadata harvest are separate facts.
2. Save a source-scoped Metadata job; preview is recommended, not a prerequisite for explicit manual run. Existing cron stays untouched unless the user edits/saves it.
3. On successful RunView, show frozen scope and entity counts. The table directory is a **separate current-state query**, never an inferred complete run-member list. Unknown timeout results do not cause automatic retries.
4. Inspect physical table/columns. Offer ODS as the main modeling continuation and Asset reconciliation as an independent governance action.
5. Look up saved ODS using **project + dataSourceId + database + schema + table**, with 0/1/N/unverifiable branches, and open the original model/mappings or confirm new draft. Save remains manual; no automatic DDL/execution.
6. After save, primary **return to the originating table**, secondary **open the saved ODS model**. Reopening and browser refresh must read Modeling Owner persisted records. Asset PENDING or Asset UPDATE permission is *not* a Modeling prerequisite.

## Facts verified against existing owners

| Owner | Current capability (verified in repository) | Missing/unsafe assumption | S1 rule |
|---|---|---|---|
| DataSource | Project-scoped list and source connection/test, including database/schema lists | Query-string source ID could be stale/unauthorized | Source ID is an untrusted hint; re-read Owner list, and let backend enforce project/RBAC before write. Never put credentials in links |
| Metadata Job | `/collect-jobs/page`, `/collect-jobs/:id`, create/update, dry-run/manual run, history by job | Existing query supports keyword/provider/enabled, **no proven exact dataSource/database/schema bounded search** | Page is explicitly all saved jobs; never conclude none belongs to a source from one page; only run a persisted job |
| Metadata Run | `RunView` includes `runId`, counts, `scopeSnapshot` (dataSourceId/databaseName/schemaName/tablePattern/collectColumns) | Synchronous HTTP timeout does **not** provide trustworthy request-to-run correlation; counts include tables and columns; partial failure may persist some entities | Read frozen snapshot; show UNKNOWN + separate history review, never associate by close timestamps or auto retry |
| Metadata Catalog | Metadata search supports `queryFilter.datasourceId`, `databaseName`, `providerType` and entity ID detail | Search results are **current directory**, not necessarily run membership | Verify source project scope, filter table entities; failed/forbidden source read is not zero tables |
| Asset | `reconcile(METADATA)` submits a **project-wide Provider scope** job; `/reconcile/status` only returns historical `lastRun` per Provider | Submit returns text, no run/request id; empty map was truthy and incorrectly reported SUCCESS; a timed refresh is not a receipt | Require explicit project-scope confirmation. Receipt UI only says ACCEPTED/UNKNOWN. Last-run counts, timestamps, errors are historical; missing evidence stays UNKNOWN; separate Source Lookup required for PENDING |
| Modeling | Reverse import API selects by datasource/keyword and previews columns using datasource/database/table, existing model/save/mapping version features | Import list/preview does **not** prove exact project+database+schema+table 0/1/N saved ODS candidate or concurrent mapping/version safety; schema omitted in column-preview request | **Block claims of exact match or saved model reopening** until Modeling Owner provides/verifies bounded exact source key, source drift, persisted model version/mapping and save race contract |
| RBAC / project | Per-domain read/create/update and project context | Asset write permission is not Modeling write permission; old async responses can outlive project change | Each Owner revalidates; no URL-based grant, no cross-domain proxy write |

### Reconciliation evidence classification

- `lastRun === null`: never ran.
- Empty/partial `lastRun`, no timestamp or missing count field: historical result unknown; **do not substitute zero**.
- Error field present: historical failure, regardless of any remaining counts.
- Timestamp plus four numeric counts and no error: a historical Provider success **only**; it does not prove the most recent manual action completed.
- If submit succeeds: show a local *accepted but unverified* notice, never a new provider success, and require explicit subsequent status/Source Lookup read. The current server provides no correlated receipt; do not poll at three seconds or claim current-run completion.

## One-PR acceptance / blockers (work in progress)

- [ ] T00 freeze precise Owner DTO/query/save contracts, including Schema semantics; confirm full RBAC/Project tests.
- [ ] T01 DataSource / Metadata / Catalog source handoff and source validation with real API.
- [ ] T02 exact saved-job discovery/reuse, dirty protection, old/new scope and Scheduler isolation.
- [ ] T03 credible timeout correlation **or** explicit evidence-level downgrade, job history recovery and partial-failure cases.
- [ ] T04 one verified table identity/deep link, actual columns, independent Asset lookups, ODS continuation.
- [ ] T05 manual Asset provider reconciliation accepted vs historical state + Source Lookup + revoke/project switch.
- [ ] T06 exact ODS source candidate query; 0/1/N/error; schema/db collision; persistent mapping and drift review.
- [ ] T07 save/version/dirty/refresh/back/return original table after persisted ODS.
- [ ] T08 scoped frontend/backend unit+integration tests, CI/Product Guard; real deployment E2E = **DEFERRED_MANUAL_TEST**.
- [ ] S1-GATE: full phase complete; review before merge. This document and isolated UI commits do NOT satisfy the gate.

## Negative golden stories

1. Source not in active project, source read denied, source read fails, connection invalid but historical Metadata remains readable.
2. Task page 1 has no match but later pages do; user edits enabled job vs manually runs disabled job; saving fails and unsaved range remains visible.
3. Preview ≠ collection; historical scope differs from new task configuration; timeout or failed/partial run cannot be matched by timestamp.
4. Two databases contain the same table name, Schema differs, 0/1/N/unknown saved ODS candidates, mapping changed manually before source drift, simultaneous saves of model version.
5. Asset has no write access while Modeling does; reconcile accepted/previous history success/current failure or unknown, provider absent/forbidden, project switched while request pending.
6. Saved ODS reopens exact modelId and mappings after return/back/refresh; draft failure preserves user input; no implicit DDL or consumption.

## Evidence level

Code in a draft PR is development work, not proof of end-to-end integration. Existing V3.1 UX prototype browser checks: 40/40, golden story 11/11, **static simulated interaction only**. No business SQL, real database Run, deployment, full CI, or product E2E is asserted here.
