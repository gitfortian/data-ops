# Stage 7.1 — Offline Sync Project Space

Stage 7.1 turns Offline Sync from a nullable / optionally filtered migration surface into a fail-closed Project runtime boundary.

Because Yak Ops is still before the first formal release, this stage consolidates the intended schema directly into the Offline Sync V1 baseline instead of adding development-only expand/backfill/contract migrations. The database baseline can be rebuilt before v1 release.

## Ownership model

```text
yak_offline_job_definition     PROJECT_ROOT
yak_offline_batch_execution    PROJECT_RUNTIME
yak_offline_job_execution      PROJECT_RUNTIME
yak_offline_execution_event    INHERITED(execution_id)
yak_offline_sync_cursor        INHERITED(job_definition_id)

Link-Up / engine health         GLOBAL
Yak Job Registry               GLOBAL infrastructure
```

`Definition` is the business Project source truth. Batch and Attempt/Execution persist Project directly because background dispatch, reconciliation and retry scan them independently after the originating HTTP request has ended.

ExecutionEvent and Cursor deliberately do not duplicate `project_id`; every business read/write first resolves the owning Project-scoped Execution or Definition.

## HTTP contract

Normal Offline business APIs are `PROJECT_REQUIRED`:

```text
/api/v1/job/batch-definition/**
/api/v1/job/batch-execution/**
/api/v1/job/batch-instance/**
/api/v1/job/batch-control/**
/api/v1/executor/**
```

The two Link-Up engine health endpoints remain platform-global:

```text
/api/v1/job/batch-execution/health
/api/v1/executor/health
```

Cron helper endpoints under `/api/v1/job/schedule/**` are pure schedule-expression utilities and do not read Project-owned Offline data, so they remain global.

The frontend request policy mirrors this split and injects `X-YAK-SECURITY-PROJECT-ID` only into Project-owned Offline routes.

## Definition Project Root

`OfflineJobDefinition.projectId` is the durable Project source truth and exposes `requireProjectId()`.

Normal Definition DAO / Repository paths require `CurrentProject.requireProjectId()` and qualify reads/writes with:

```text
project_id + definition identity
```

Creation and update ignore caller-selected ownership: persisted `project_id` is bound from trusted CurrentProject. A mismatching pre-populated Project is rejected instead of being silently moved.

Project-local job-name uniqueness remains:

```text
UNIQUE(project_id, job_name)
```

Source and Sink DataSource resolution runs only after the Offline Project boundary has been established. Therefore guessed DataSource IDs from another Project are rejected by the already Project-scoped DataSource DAO boundary.

## Runtime ownership

`yak_offline_batch_execution` and `yak_offline_job_execution` persist `project_id NOT NULL`.

Normal Batch / Execution DAO and Repository operations are fail-closed and require CurrentProject. This includes:

- find by ID;
- task + batch-key lookups;
- active Batch checks;
- Attempt reads;
- retry reservation;
- page/detail reads;
- mutations.

Background code is not allowed to obtain a full business object from an unscoped global lookup.

Instead, Stage 7.1 defines explicit dispatcher scans that return only durable identities:

```text
ProjectDefinitionRef(projectId, definitionId)
ProjectBatchRef(projectId, batchId)
ProjectExecutionRef(projectId, executionId)
```

The dispatcher restores `ProjectContext` from that durable Project and then reloads the object through the normal fail-closed Repository before any business IO.

## Backfill dispatcher

```text
cross-Project candidate scan
    -> ProjectBatchRef(projectId, batchId)
    -> ProjectContextScope(Project)
    -> BatchRepository.findById(batchId)
    -> Cursor / Task checks
    -> executePendingBackfill
```

A PENDING Backfill therefore cannot use an empty ThreadLocal to access a Batch, Cursor or DataSource.

## Execution reconcile / retry

```text
active / retry candidate scan
    -> ProjectExecutionRef(projectId, executionId)
    -> ProjectContextScope(Project)
    -> ExecutionRepository.findById(executionId)
    -> Link-Up reconcile / retry
```

The Link-Up node probe itself remains global infrastructure. The execution-specific state transition happens only inside the restored Project scope.

## Schedule runtime

Project identity is part of the durable Yak Schedule target payload:

```text
ScheduleTarget.payload = {
  definitionId,
  projectId
}
```

The Schedule Handler does not depend on the HTTP header that originally created the schedule:

```text
Schedule payload.projectId
    -> ProjectContextScope(Project)
    -> load Definition / Schedule
    -> verify Definition.projectId == payload.projectId
    -> submit scheduled Batch
```

Startup schedule reconciliation also begins with a cross-Project identity scan and restores Project per Definition before using normal repositories.

This makes Project Context independent of browser state and request lifetime.

## Inherited Event / Cursor ownership

`yak_offline_execution_event` remains `INHERITED` from its Execution. `OfflineExecutionEventRepositoryAdapter` verifies the owning `OfflineJobExecution` through the Project-scoped execution repository before append/list/listAfter.

`yak_offline_sync_cursor` remains `INHERITED` from its Definition. Cursor find/initialize/advance first verifies the owning Definition through the Project-scoped definition repository.

No duplicate Project column is added to either table.

## Overview / logs

Offline overview JDBC projections now always require CurrentProject and append a `project_id` predicate.

Unified execution logs receive a Project-scoped Execution from the execution service, and Yak Ops event reads additionally inherit scope through the owning Execution. Knowing another Project's execution ID is therefore insufficient to read its event/log timeline.

## Yak Job Registry boundary

Yak Job Registry remains GLOBAL infrastructure; it does not own Offline business Project identity.

`InMemoryTaskRegistry` refreshes providers on list/get/snapshot. The Offline TaskProvider therefore contributes only the current Project's Offline tasks when a ProjectContext exists, and returns no cross-Project Offline enumeration when the registry is called without ProjectContext.

Workflow final same-Project task-reference enforcement remains Stage 8.1.

## DataSource legacy corridor

Stage 7.1 no longer needs the DataSource DAO compatibility behavior where an Offline background thread with no CurrentProject resolves a persisted datasourceId globally.

Offline scheduled/backfill/reconcile/retry business IO now restores Project before entering normal DataSource resolution.

The shared DataSource legacy corridor is **not removed in this PR**, because Realtime Sync Stage 7.2 must be migrated first. After Stage 7.2 verifies its background restore path, the now-unused DataSource corridor can be deleted immediately instead of waiting until the final audit.

## Pre-v1 schema strategy

The Offline Sync baseline now directly declares:

```text
yak_offline_job_definition.project_id   NOT NULL
yak_offline_batch_execution.project_id  NOT NULL
yak_offline_job_execution.project_id    NOT NULL
```

No historical compatibility backfill is added. Existing development databases should be recreated or normalized as part of the ongoing pre-v1 database consolidation.

After the first formal release, the published baseline/migration history should become immutable and later schema changes must follow normal upgrade-safe migrations.

## Non-goals

- Realtime Sync Project Space (Stage 7.2)
- removing the shared DataSource no-Project compatibility corridor before Realtime is migrated
- Workflow same-Project Task binding (Stage 8.1)
- final Task Catalog / Lineage contract cleanup
- cross-Project sharing
- Project-local RBAC overrides
- production upgrade compatibility for unreleased development databases

## Minimum verification

Backend:

```bash
mvn -pl yak-ops-business/yak-ops-business-sync/yak-ops-business-sync-offline -am test
```

Frontend:

```bash
cd yak-ops-ui
npm test -- --runInBand src/utils/security/projectContext.test.ts
npm run tsc
```

Manual A/B isolation:

1. Project A / B can create Offline tasks with the same job name.
2. A cannot view/edit/delete/online/offline/execute B's definition ID.
3. A cannot view/cancel/retry B's execution ID or read its events/logs.
4. A cannot build/execute a definition using B's DataSource IDs.
5. Scheduled execution still works after the original HTTP request has ended.
6. Startup schedule reconciliation restores each definition's persisted Project.
7. Backfill dispatcher restores Project before Cursor/Batch/Execution IO.
8. Execution reconcile/retry restores Project before state mutations.
9. Engine health works without Project header.
10. Project switch changes Offline definition / execution / overview data without leakage.
