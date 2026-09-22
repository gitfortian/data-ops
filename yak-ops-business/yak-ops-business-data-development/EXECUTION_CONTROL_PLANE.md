# Data Development Execution Control Plane

Stage 1 separates the editor HTTP lifecycle from the shared Task Runtime lifecycle.

## Runtime flow

```text
Editor
  -> POST /api/v1/data-development/nodes/{nodeId}/run
  -> create yak_dev_task_execution(PENDING)
  -> TaskExecutionGateway.start(...)
  -> attach runtimeExecutionId
  <- return DevelopmentTaskExecutionSubmission immediately

Background reconciler / editor polling
  -> TaskExecutionGateway.status(...)
  -> update durable execution status/output
```

The HTTP submit request never waits for a terminal runtime state. Durable `yak_dev_task_execution.id` is the client-facing execution identity; `runtime_execution_id` remains the adjacent Task Runtime identity.

## Control APIs

```text
GET  /api/v1/data-development/executions/{id}         refresh + read
GET  /api/v1/data-development/executions/active       reattach active run by node
POST /api/v1/data-development/executions/{id}/cancel  cancel runtime execution
POST /api/v1/data-development/executions/{id}/retry   retry persisted definition
```

Retry persists and reuses the original `schema_version`, `content` and `config_json`, and stores `retry_of_execution_id` so retry chains remain auditable.

## Recovery semantics

- Runtime terminal state is reconciled into durable history by `DevelopmentTaskExecutionReconciler`.
- A PENDING record that never receives a runtime execution id is failed after a short grace period.
- If an active durable record references a runtime id that the current in-process runtime no longer knows, the record is failed with an explicit runtime-state-lost message instead of remaining RUNNING forever.
- Completion writes are terminal-guarded so polling/reconciliation races do not overwrite an already terminal record.

## Boundary

This stage intentionally does not make the shared Task Runtime distributed or durable. The current plugin runtime remains in-process. The control plane makes that limitation observable and recoverable for Data Development; a future runtime persistence/remote executor stage can replace the gateway implementation without changing the Data Development execution contract.
