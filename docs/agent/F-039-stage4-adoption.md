# F-039 / #497 — 4/5 独立人工批量采纳（单 PR #508）

Status: **ENGINEERING IMPLEMENTATION / NOT E2E-ACCEPTED**, 2026-10-10  
Branch: `feature/f-039-phase-4-complete`  
Issue: #497; prerequisites #495/#496 engineering merged but their full operator E2E remains OPEN.

## Truth, transaction and permissions

| Responsibility | Owner | Implemented boundary |
|---|---|---|
| Source project/datasource, capture and complete column fingerprint | Datasource + Metadata | Neutral SPI `SourceSchemaAdoptionProof` implemented by Metadata, reusing authorized READ projection; called before and during Semantic writes, no dependency cycle |
| Task, original Turn, plan, chunks, candidate revision / selection | Agent | HTTP command from authenticated original task owner only; reread immutable chunk proofs and re-run **current** catalog/Skill/source preflight before sending a frozen candidate list |
| Official domain/process/standard/field/binding | Semantic | Only `SemanticSourceAdoptionApi` and source-domain services; never a model tool or Agent DAO |
| Idempotency receipt | Semantic | Unique(project_id,task_id,candidate_id) + canonical **per-item** payload hash, same transaction as business creation/reuse; MySQL and PostgreSQL additive V2 (do not alter V1) |
| Approval, status, version | Original Semantic and Approval | TYPE/UNIT/Field enabled references rechecked by original owner; no automatic enable/publish |

Feature gate: `yak.agent.source-semantic.enabled` remains required for Agent origin. **Formal adoption requires independent opt-in `yak.agent.source-semantic.adoption-enabled=true`**, default false. Without the two toggles and the production security/project context, this feature MUST NOT be exposed as a production promise.

### Per-item linearization and retry

1. Re-fetch trusted identity, exact preflight revision/ticket/digest, selected dependency closure and completed original turns; no HTTP client IDs used as formal truth.
2. Semantic independently rechecks user ID, project, Semantic READ/CREATE permissions, Metadata/DataSource READ and evidence fingerprint. For process-field binding also requires Semantic UPDATE.
3. In a business DB transaction insert unique PENDING receipt reservation, call **original Semantic** create/reuse, update committed receipt to CREATED/REUSED/LINKED, and commit **together**.
4. If create / validation / receipt write rolls back, PENDING row is rolled back with business write. Competing same item insert is resolved by DB unique key, never by a JVM lock.
5. If response is lost or concurrent insert throws, first READ authoritative receipt. If none is found, report **NEEDS_RECONCILIATION** and stop subsequent writes; do not invent definite failure or blindly recreate.
6. Successful item is replayable across later review revisions only when its own exact payload, operator, source evidence and dependency identity remain unchanged. A reused id with changed payload is **DIGEST_CONFLICT**, not an update. New selected independent items may continue.
7. Dependencies must be explicitly selected and form an acyclic topological graph; missing/failed prerequisite means NOT_EXECUTED, not a silent extra save. Existing success is not automatically rolled back after partial failure.

### Current supported behavior and explicit blockages

- DOMAIN new/reuse, PROCESS new/reuse, FIELD new/reuse, PROCESS_FIELD binding via original field service are implemented as opt-in formal writes with original source-domain validation/audit and commit receipts.
- Existing TYPE/UNIT references can be checked at their original ID/category/version/status. **New TYPE/UNIT** need category-specific property capture (type_code/std_type/unit_code) and owner approval that 3/5 candidate format does not contain: marked NOT_EXECUTED, not fabricated.
- CODE has no complete confirmed values/labels in physical schema. Both new and candidate reuse remain NOT_EXECUTED until source-domain code-set transaction and active whole-set semantics are integrated.
- SOURCE_LINK uses the original **process→source table** binding owner with a Metadata-resolved table and explicitly reviewed MAIN/DETAIL/DIM role. Repeated column witnesses may reference the same real process/table binding. **This does not persist source-column→standard-field mappings or inferred lineage**; incompatible existing table roles are rejected.
- For metadata drift, revoked permissions, transient timeout or unknown receipt, no new writes should proceed. Human confirmations never replace source-domain permission checks.
- Candidate completion, preflight ready and selected items all have **separate** meaning from selected items formally committed. UI renders receipt status per item.

## Required CI / tests and real E2E

- [ ] Product Guard + architecture + full Maven/Jest + MySQL/PG migration checks PASS on newest HEAD of #508.
- [ ] Same key/same content returns identical stored receipt (actual same formal ID).
- [ ] Same key/different canonical item digest rejects without changing old definition/receipt.
- [ ] Concurrent duplicate insert on two nodes resolves to one row/business object; rollback on failing receipt update rolls back business row.
- [ ] Existing code same-meaning reuse and same-code different meaning/privilege/version reject; independent item succeeds after unrelated failure.
- [ ] Source fingerprint/capture/column change, expired Skill, cross-project, deleted membership, DataSource/Metadata/Semantic revoked, unknown request ID, cancelled source task blocked.
- [ ] New standard with publish approval enabled and disabled: creation DISABLED must remain waiting, not bypass original approval. CODE sets are atomic as original domain requires.
- [ ] REAL authorized UI: table selection → original Turn(s) → verified candidate → preflight → separate explicit save → original Semantic official ID/version readback; verify DB and audit timeline.
- [ ] Disconnected/HTTP timeout re-query original receipt; no blind POST loops.
- [ ] Expert review of SI01/SI09/SI10/SI11/SI12/SI13, evidence and production pilot preconditions; field/standard semantics cannot be established by JSON validity alone.

**Status**: The underlying semantic partial write + durable receipt corridor and UI are implemented under an independent OFF-by-default gate. **#497 is not yet closed**: source association and code-set/TYPE/UNIT category-specific positive save, approval waiting, real DB concurrency/E2E and production sign-off are not proved. Subsequent modifications remain exclusively inside #508; do not create micro-PRs.
