# A8.0 — 移除 Framework：依赖基线、归属决策及迁移门槛

> Status: A8.0 Draft implementation only. Parent [#412](https://github.com/gitfortian/data-ops/issues/412) / architecture program [#368](https://github.com/gitfortian/data-ops/issues/368).
>
> Immutable pre-A8 **inventory anchor**: `main@3919d93962fafe0e9394be5b3735220fb64bfa0c`. It is not the A0–A7 combined tree. Always distinguish this source-tree snapshot from A7's successful temporary combination / actual integration release.
>
> Target: remove the entire `data-ops-framework/` source and its effective artifact dependencies, without removing any currently used Security/Schedule/Workflow/File behavior or substituting Maven Central artifacts to cosmetically hide the old folder.

## 1. Why the dedicated baseline is necessary

The existing nested reactor actively owns five capabilities and a retired, **non-reactor** legacy tree. Its structure is not only a removable Maven aggregator. As of the immutable anchor, source-tree `src/main/java` files (not runtime component counts) are:

| Source subtree | Production Java files | Owner to confirm | Migration order |
| --- | ---: | --- | --- |
| `data-common` | 9 | Shared contracts in existing `data-ops-common` | A8.1 |
| `data-file` | 12 | Resource and Storage Plugin integration after consumer verification | A8.1 |
| `data-security` | 268 | Platform identity/auth/Project/RBAC, **not** business Data Security | A8.2 |
| `data-schedule` | 55 | Stable schedule contract + Boot scheduler/adapter | A8.3 |
| `data-workflow` | 70 | Independently owned pure-Java DAG Engine | A8.4 |
| **Active total** | **414** | No new umbrella framework | |
| `legacy/data-job` | **97 separately** | Excluded from current reactor, pending external compatibility decision | A8.5 |

The actual baseline includes nested POMs, BOM managed dependencies, Boot/Business consumers and Java / Spring metadata references—not only the root module declaration. This is important because the root `pom.xml` still aggregates Framework; `data-ops-bom` manages its `io.github.weifuwan` artifacts, `data-ops-common` uses `data-common` and `data-schedule-api`, Boot uses the Security Starter + Schedule Core/Quartz, Workflow uses `data-workflow-engine`. Numerous Business modules depend directly on Security / Schedule.

## 2. Machine-readable inventory and non-regression guard

Run from the checked-out repository root:

`node scripts/architecture/framework-coupling.mjs --report > a8-framework-coupling.json`

`node scripts/architecture/framework-coupling.mjs --check`

The report is a deterministic *source-reference inventory* with `baselineCommit`, `baseline` and `current` aggregate counts, `outstanding[]` (path / token / occurrence count / baseline count), and `newlyIntroduced[]`. The generation timestamp is evidence metadata, not a reproducible source fact.

- `--check` fails if a new Framework reference is added, or if the same reference gains occurrences in the same source file; moving an old reference to a different file is **not** grandfathered. Deletions are allowed and count down the debt.
- Exact references are tracked by source path + Maven group/artifact/role or Java/resource `io.yak.framework.*` symbol. Detects import, static import, wildcard, fully qualified production type names and Spring metadata references. POM scan includes dependencyManagement, plugin and parent declarations, and discards commented-out XML.
- Baseline is pinned to the **full commit SHA** in the script, not resolved from a moving `main` ref and not mutable through workflow inputs. Baseline commit must exist locally; missing history fails closed rather than silently resetting the debt.
- Scans checked-in POMs; production Java and resources; workflow, Docker/Compose wiring. Explicitly excludes `src/test`, Markdown, generated target/dist, and `data-ops-framework/legacy/**`. The legacy exclusion is temporary and has its own older `framework-legacy.test.mjs` guard; it is not evidence that the legacy tree can be deleted.
- **Limit:** this is an inventory of *textual source references*, not a complete Maven effective-dependency tree, Java reflection runtime map, binary API compatibility check, or cross-repo consumer audit. For example, YAML properties resolving to old packages, external plugins, generated code and Maven property-derived coordinates need additional A8.0 manual or separately automated validation. No source scanner by itself proves safe removal.
- Architecture Checks execute the guard and seven positive/negative focused tests for baseline preservation, new POM deps, FQCN/wildcard/static Java refs, auto-config metadata, duplication and relocation. On A8 Draft branches they archive the **full JSON** as an action artifact; no secrets are needed or captured.
- Do not edit the pinned baseline when a migration fails. Fix the new reference or ask for review of an explicitly justified policy change. After A8.5, the stronger Definition of Done is **absolute zero effective Framework consumers**, not merely `--check` passing.

## 3. Migration ownership ADR (decision log)

### Decision A8-001: no cosmetic removal

Never remove `data-ops-framework/pom.xml` and switch modules to Maven Central `io.github.weifuwan` artifacts while leaving the old implementation as an external runtime dependency. The owner must become a Data-Ops module with matching product contracts and actual tests; root reactor / BOM / dist must follow.

### Decision A8-002: Security is a Platform capability, not business Data Security

Current Security owns account login, role/permission/User, Project space, password/session/HTTP authentication, operation audit, MyBatis/Flyway and Spring auto-configuration. Preserve:

- `/yak-security/api/v1/**` and `X-YAK-SECURITY-PROJECT-ID`, current identity and menu/permission semantics, 401/403 and existing permission annotations;
- security datasource, transaction manager, Spring Bean names, Boot auto-configuration ordering and classpath imports; MySQL/PostgreSQL historical migrations and data identity;
- account hash, Session/token continuity and invalidation, Project selection and RBAC revocation;
- no double security engine and no accidental dependency from Framework Security into data governance Business Security.

Candidate: `data-ops-platform` with a real focused security/platform responsibility. Validate exact consumers before creating any new Maven module. Boot keeps the composition/HTTP ownership.

### Decision A8-003: Schedule separates time triggers from durable workflow facts

`data-schedule-api` is a real direct consumer contract. Existing Quartz, optional XXL-JOB, Cron/timezone/Misfire/concurrency behavior are separately assessed, not silently eliminated. Project-owned Schedule/Trigger Ledger/Backfill stay in their Business owners, not in scheduler plugins. Recheck PostgreSQL Quartz **JDBC** vs shared **memory** configuration; running an empty clean DB is not enough to validate historical triggers.

### Decision A8-004: Workflow DAG Engine remains independent and pure Java

Keep `data-workflow-engine`'s definition/graph/Node/Attempt/transition/command/SPI/recovery contract. The Workflow Business module continues owning version publication, schedule ledger, business metadata, backfill, audit. `WorkflowRuntime` currently creates local worker/scheduler threads and `LocalExecutionLock`. **A8 does not make it distributed**; that is a separate A9 architecture and failover project. No second workflow state machine, no product truth migrated into the engine, and no change in durable historical execution identity.

### Decision A8-005: File consumers before consolidation

`data-file` includes Local, MinIO, OSS abstractions and its own metadata SPI. Existing Resource / Storage Plugins have overlapping concerns; first determine if `data-file` has production runtime or downstream/external consumers. Move only live functionality, preserve authorization and data compatibility; do not copy a second File Service just to preserve a directory.

### Decision A8-006: legacy tree requires explicit disposal assessment

`data-ops-framework/legacy/data-job` (Spring Boot 2 / Java 8) is excluded from the current Framework Maven `<modules>` and from the current architecture dependency guard. The old `COMPATIBILITY.md` requires explicit external compatible release decisions. To truly meet directory-removal DoD in A8.5, decide independently whether the module can be deleted or archived outside active source, with concrete evidence about published artifacts and consumers. An unproven external consumer is a blocker—not an implicit right to delete.

## 4. Execution packages and exit gates

| Stage | Action | Hard exit gate |
| --- | --- | --- |
| **A8.0** | Actual dependency and ownership inventory, fixed baseline guard, negative tests, ADR | CI passes for pinned snapshot; no production change; resolve remaining effective/reflective/external reference audits before A8.1 |
| **A8.1** | Common/File | Consumers transferred and behavior tests pass; only corresponding old refs shrink |
| **A8.2** | Security | Login/Project/RBAC, audit, Flyway data migration and MySQL+PG behavior tests; auto-config and auth contract unchanged |
| **A8.3** | Schedule | Quartz/XXL-JOB parity by verified use, Trigger Ledger, Misfire and restart recovery; no duplicate fire |
| **A8.4** | Workflow DAG | Stable Engine SPI, durable Execution/Attempt, idempotency/timeout/cancel/restart/backfill regression |
| **A8.5** | Remove root/bom/framework/legacy/release/CI dependencies | No effective old Maven dependencies, FQCN, runtime resource or distribution references; clean reactor build |
| **A8.6** | Fresh ordered temporary combination and isolated real-environment proof | Full Maven verify, FE contracts/build, MySQL/PG, project/RBAC/Workflow/Schedule recovery, packaging and rollback, then user-authorized integration only |

## 5. Important integration constraints

1. A0–A7 / A7.1 are protected Draft PRs; never cherry-pick blindly or merge to make A8 easier. Future combined tests must use **current main + exact approved PR heads**, not the historical green run's outdated HEAD.
2. Each A8 stage is a small independent Draft PR, `architecture-refactor` + `do-not-merge`. Keep code migration separate from feature work and A9 distributed processing.
3. Do not rewrite applied Flyway migration history or change user/Project/Task identifiers. If new historical-data copy migration or rollback is required, run it only on an isolated copy with before/after checks.
4. `--report` is honest evidence of *remaining debt*, not completion. If it contains old dependencies, they remain work items by design.
5. A8.0 does not claim live browser E2E, production data migration acceptance or Flink/Link-Up true data-plane E2E.
