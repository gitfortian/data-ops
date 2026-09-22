# Stage 7.2 — Realtime Sync Project Space

## 1. Goal

Stage 7.2 closes the Realtime Sync Project boundary after Stage 7.1 completed Offline Sync.

The rule is not "add `project_id` everywhere". The rule is:

```text
Project-owned source truth
  -> Project-owned durable runtime identity
  -> explicit background dispatcher
  -> restore ProjectContext
  -> ordinary fail-closed business repositories
```

Realtime must remain safe after the originating HTTP request has ended, including scheduled/background reconciliation, runtime identity recovery, observability and SSE notifications.

## 2. Ownership

| Object / table | Ownership | Project identity |
| --- | --- | --- |
| `yak_realtime_job_definition` | `PROJECT_ROOT` | direct `project_id NOT NULL` |
| `yak_realtime_definition_version` | `INHERITED` | inherits from `task_id -> definition` |
| `yak_realtime_job_deployment` | `PROJECT_RUNTIME` | direct `project_id NOT NULL` |
| `yak_realtime_job_event` | `INHERITED` | inherits from definition/deployment |
| `yak_compute_environment` | `GLOBAL` | no Project column |
| `yak_realtime_runtime_lease` | `GLOBAL` | no Project column |

`ComputeEnvironment` is a platform runtime capability shared by workspaces. It answers "where can a realtime job run", not "which Project owns the business job".

The reconciliation lease is also platform-global. It only elects the process allowed to perform the next discovery pass; it does not grant access to Project business rows.

## 3. HTTP boundary

Realtime management APIs are `PROJECT_REQUIRED`:

```text
/api/v1/realtime-sync/**
```

The backend controller uses the default `@ProjectScope`, whose default mode is `PROJECT_REQUIRED`.

The frontend Project request table mirrors this rule and attaches:

```text
X-YAK-SECURITY-PROJECT-ID
```

to Realtime management requests.

Compute Environment remains explicitly global:

```text
/api/v1/compute-environments/** -> LEGACY_GLOBAL
```

## 4. Definition = PROJECT_ROOT

`yak_realtime_job_definition.project_id` is the persisted Source Truth for the Realtime business definition.

Ordinary Definition operations require trusted `CurrentProject`:

- create;
- update;
- publish;
- detail;
- lock;
- page;
- delete;
- event ownership validation.

No request body Project ID is accepted as authority.

Task names are unique inside one Project:

```text
UNIQUE(project_id, job_name)
```

Therefore Project A and Project B may both define `orders-cdc` without becoming the same business object.

## 5. DefinitionVersion = INHERITED

`yak_realtime_definition_version` deliberately does not repeat `project_id`.

The immutable version belongs to its owning Definition:

```text
DefinitionVersion.task_id
  -> RealtimeJobDefinition.id
  -> RealtimeJobDefinition.project_id
```

Version lookup is Project-qualified through a parent JOIN rather than a naked `selectById(versionId)`:

```text
version id + current Project
  -> JOIN definition
  -> only then return immutable version
```

Creation, lookup, published-reference binding and source-digest reuse all require the owning Definition to belong to the trusted CurrentProject.

## 6. Deployment = PROJECT_RUNTIME

`yak_realtime_job_deployment` persists `project_id` directly because a Deployment must remain independently discoverable after the HTTP request has ended.

Ordinary Deployment operations are fail-closed:

- idempotency lookup;
- latest deployment;
- deployment lookup;
- insert;
- bind immutable DefinitionVersion;
- RUNNING / STOPPING / FAILED state mutation;
- replacement reservation;
- runtime identity binding;
- reconcile mutation.

The Deployment Project must equal the owning Definition Project.

## 7. Project-local idempotency, globally unique Flink identity

Business idempotency is scoped to one Project:

```text
UNIQUE(project_id, idempotency_key)
```

This is intentional: two independent Projects may submit the same client-generated `Idempotency-Key` without conflicting in Yak Ops.

However, Flink `pipeline.name` / `runtime_job_name` lives in a shared runtime namespace and must not collide across Projects.

Therefore the external runtime identity is derived from:

```text
projectId + ":" + idempotencyKey
  -> SHA-256
  -> yak-rt-<digest>
```

while the raw Project-local `idempotency_key` remains persisted in the Deployment row.

`runtime_job_name` remains globally unique because it identifies the actual external Flink job, not a Project-local business name.

## 8. Explicit cross-Project reconciliation dispatcher

The background reconciler is platform infrastructure and may discover work across Projects, but ordinary repositories must not become globally readable because of that requirement.

Stage 7.2 introduces an explicit durable identity:

```text
ProjectDeploymentRef(
  projectId,
  definitionId,
  deploymentId
)
```

Only the explicitly named dispatcher scan may enumerate Project-owned rows without CurrentProject:

```text
findReconcileCandidatesForDispatch()
```

It returns durable identity only.

The reconciliation path is:

```text
GLOBAL runtime lease
  -> cross-Project candidate discovery
  -> ProjectDeploymentRef
  -> ProjectContextScope(Project)
  -> reload latest Deployment through normal scoped store
  -> verify durable deployment still current
  -> Flink status / identity recovery / state mutation
```

This keeps "who gets to scan" GLOBAL while keeping "which business row may be read or mutated" Project-scoped.

## 9. Event = INHERITED

`yak_realtime_job_event` does not repeat `project_id`.

Before an event is written or read, its Definition must be visible in CurrentProject. If the event references a Deployment, that Deployment must also belong to CurrentProject and to the same Definition.

Knowing another Project's `definitionId` or `deploymentId` is not sufficient to access its event stream/history.

## 10. SSE is Project-partitioned

The original SSE implementation kept one global list of `SseEmitter` subscribers and broadcast every Realtime change to every authenticated client.

That would bypass otherwise-correct REST isolation.

Stage 7.2 stores the trusted Project when the subscription is created:

```text
ProjectSubscriber(projectId, emitter)
```

A committed Realtime business event is sent only to subscribers whose `projectId` equals the current event Project.

Heartbeat messages may still be broadcast to every subscriber because they contain no business data.

## 11. DataSource resolution

Realtime source/sink resolution already uses the normal `DataSourceRepository`.

After background reconciliation now restores ProjectContext, Realtime no longer needs the historical DataSource "no Project -> explicit ID global lookup" corridor.

Stage 7.2 therefore closes that corridor in `DataSourceDaoImpl`:

```text
no CurrentProject
  -> fail closed
```

The old method signatures remain temporarily for source compatibility, but their semantics no longer fall back to global access. Explicit Project ID overloads must equal trusted CurrentProject.

Offline Stage 7.1 and Realtime Stage 7.2 are now both independent of that legacy path.

## 12. First-release database contract

Yak Ops is still before its first formal release, so Realtime uses the same consolidation rule as Offline Stage 7.1:

- the final first-release schema is written directly into `V1__baseline_realtime_sync.sql`;
- development-only nullable Project expansion is removed;
- no temporary Expand -> ApplicationReady Backfill -> Contract sequence is preserved for disposable development databases.

Final V1 contains:

```text
yak_realtime_job_definition.project_id   BIGINT NOT NULL
yak_realtime_job_deployment.project_id   BIGINT NOT NULL
```

and intentionally no Project column on:

```text
yak_realtime_definition_version
yak_realtime_job_event
yak_compute_environment
yak_realtime_runtime_lease
```

After the first formal release, V1 must be frozen and all future schema changes must use new Flyway versions.

## 13. Security invariants

Stage 7.2 should preserve these invariants:

1. Missing CurrentProject cannot turn a Realtime DAO query into a global query.
2. Project A cannot read/update/delete/publish/start/stop/reconcile Project B's Definition ID.
3. Project A cannot resolve Project B's immutable DefinitionVersion ID.
4. Project A cannot read or mutate Project B's Deployment ID.
5. Project A cannot read Project B's events, logs or observability through a known ID.
6. Background reconciliation restores persisted Project before DataSource or business IO.
7. SSE business events never cross Project subscribers.
8. Same Idempotency-Key in two Projects does not collide in DB or external Flink runtime identity.
9. Compute Environment remains GLOBAL and is not duplicated per Project.
10. Runtime lease remains GLOBAL and never acts as a business-data authorization boundary.

## 14. Suggested verification

Backend:

```bash
mvn -pl yak-ops-business/yak-ops-business-sync/yak-ops-business-sync-realtime -am test
```

Frontend:

```bash
cd yak-ops-ui
npm test -- --runInBand src/utils/security/projectContext.test.ts
npm run tsc
```

Manual A/B regression:

1. Project A/B can create the same Realtime task name.
2. A cannot detail/update/publish/delete B's Definition ID.
3. A cannot start/stop/restart/reconcile B's Definition ID.
4. A cannot read B's event/history/observability/log endpoints.
5. A cannot use B's DataSource ID as source or sink.
6. Background reconcile still works after the original HTTP request has ended.
7. Same Idempotency-Key used in A and B creates different Flink runtime job names.
8. SSE subscribed under A never receives B's business event.
9. Compute Environment APIs work without Project header.
10. Project switching never mixes list/detail/deployment state.

## 15. Non-goals

This stage does not implement:

- Workflow Stage 8.1 same-Project task binding;
- cross-Project sharing;
- Project-specific Compute Environments;
- Project role overlays;
- a new Task Catalog publication model for Realtime;
- production upgrade compatibility for disposable pre-v1 development schemas.
