# J1 / S1 · V3.1 Owner Contract and Phase Acceptance Ledger

Status: **IN_PROGRESS / one S1 draft PR**. Design: [#539](https://github.com/gitfortian/data-ops/issues/539) UX-GATE-A/B APPROVED (2026-10-12). Engineering: [#538](https://github.com/gitfortian/data-ops/issues/538). **UX-D6=B: no Home modifications.**

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
