# Offline Sync Notification Policy (Stage 4.2)

## Scope

Stage 4.2 makes the Offline Sync final-failure notification introduced earlier configurable per task without coupling Offline Sync to Yak Security Message persistence or the Alert plugin system.

The business event remains:

```text
Offline attempt first enters FAILED
+ no nextRetryTime is scheduled
        ↓
OfflineExecutionFinalFailureEvent
        ↓
NotificationIntent
sourceType = OFFLINE_SYNC_EXECUTION
sourceId   = executionId
```

Routing is decided later by the Stage 4.1 `NotificationRouter`.

## Persistent model

Task-level policy is stored on the owning definition:

```text
yak_offline_job_definition.notification_config_json
```

The column is nullable by design.

```text
NULL
  = legacy compatibility policy
  = final failure -> current Project OWNER -> IN_APP
```

The migration does not backfill old rows. This keeps existing tasks behavior-compatible while distinguishing an untouched legacy task from an explicitly configured policy.

The dedicated column is the policy source of truth. `definition_json` may also contain the request field for round-trip compatibility, but edit responses always project the dedicated column back over it.

## API contract

`OfflineJobDefinitionDTO` accepts a top-level sibling configuration:

```json
{
  "notification": {
    "enabled": true,
    "triggers": ["FINAL_FAILURE"],
    "recipientType": "PROJECT_OWNER",
    "recipientUserIds": [],
    "inAppEnabled": true
  }
}
```

Stage 4.2 supports only:

```text
trigger:
  FINAL_FAILURE

recipientType:
  PROJECT_OWNER
  EXPLICIT_USERS

destination:
  IN_APP
```

External Alert channels are intentionally deferred to Stage 4.3.

## Backward compatibility

Older clients do not know the `notification` field.

When an existing task is updated with `notification == null`, the service preserves the already persisted `notification_config_json` instead of resetting it. A new task that omits the field keeps `NULL` and therefore inherits the legacy compatibility policy.

## Routing

`OfflineSyncNotificationPolicyResolver` has order `100`, before the global fallback resolver.

It resolves policy using explicit durable identity:

```text
(intent.projectId, intent.sourceId/executionId)
        ↓
yak_offline_job_execution
        ↓
job_definition_id
        ↓
yak_offline_job_definition.notification_config_json
```

The resolver does not parse `actionPath` and does not depend on a ThreadLocal current Project. This matters because `DefaultNotificationRouter` may execute after the originating business transaction commits.

If execution/definition/policy data cannot be resolved, routing fails closed rather than silently falling back and potentially violating an explicit user opt-out.

## JobSpec isolation

Notification is a Yak Ops control-plane setting. `OfflineDefinitionModelAdapter.forJobSpec` explicitly removes the root `notification` field before engine JobSpec construction.

Therefore changing only notification preferences must not change engine execution configuration or make the notification payload part of an engine snapshot/config digest.

## UI

The shared Offline editor exposes **通知设置** for both GUIDE_SINGLE and GUIDE_MULTI tasks.

Stage 4.2 UI provides:

```text
开启任务通知
触发条件: 最终执行失败
通知方式: 站内消息
接收人:
  - 项目负责人
  - 指定用户
```

Explicit-user candidates are loaded from the current Project detail and limited to its owners and members. Only stable numeric user IDs are persisted.

## Non-goals

Stage 4.2 does not add:

- success notifications;
- per-retry failure spam;
- DingTalk/email/webhook delivery;
- Project-scoped Alert channel configuration;
- MQ/WebSocket/SSE;
- a new shared notification-policy table.

Stage 4.3 should implement external delivery as `NotificationPolicy.Destination.ALERT -> AlertNotificationSink`, not by calling `AlertService` from Offline Sync business code.
