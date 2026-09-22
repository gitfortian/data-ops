# Notification Alert Sink (Stage 4.3)

## Scope

Stage 4.3 connects the Notification Router to the existing Yak Ops Alert plugin system.

Business modules still publish only durable notification intent and policy. They do not depend on `AlertService`, plugin classes, webhook addresses, or channel credentials.

```text
Offline Sync final failure
        ↓
NotificationIntent
        ↓
NotificationPolicyResolver
        ↓
NotificationPolicy
  destinations = IN_APP / ALERT
  alertChannelIds = [...]
        ↓
DefaultNotificationRouter
        ├─ IN_APP -> Yak Security Message Center
        └─ ALERT  -> AlertNotificationSink
                         ↓
                    AlertService.notify
                         ↓
                   AlertPluginRegistry
                         ↓
                  registered plugin
```

## Alert Channel ownership

`AlertChannel` remains a GLOBAL capability.

Stage 4.3 does **not** add `project_id` to `yak_ops_alert_channel` and does not create a Project-specific copy of channel secrets.

A Project-owned business task expresses its external delivery choice by storing references to global channel primary keys:

```text
task notification policy
  alertChannelIds = [7, 8]
        ↓
yak_ops_alert_channel.id
```

Project ownership therefore belongs to the task / notification intent, while the reusable delivery endpoint remains global.

## Stable channel identity

The Router policy references the persisted database primary key, not `channelType` text.

```text
NotificationPolicy.alertChannelIds
        ↓
AlertChannelRepository.findById(id)
        ↓
AlertChannelDefinition.channelType
        ↓
AlertService.notify
```

`AlertChannelVO.id` is exposed so task editors can persist the stable key.

Plugins that are registered but have never been configured do not have a persistent id and cannot be selected by a task.

## Secret boundary

`AlertNotificationSink` never reads or assembles channel credentials.

It only resolves:

```text
channelId -> channelType
```

The existing `DefaultAlertService.notify()` remains responsible for:

1. resolving the plugin from `AlertPluginRegistry`;
2. loading persisted channel configuration;
3. checking whether the channel is enabled;
4. merging runtime non-address parameters;
5. sending the final message through the plugin.

Webhook URLs, tokens and other sensitive channel configuration therefore stay inside the Alert module.

## Failure semantics

External delivery is a secondary side effect.

The Router already isolates destinations, and the ALERT sink additionally isolates every selected channel:

```text
channel 7 missing / disabled / failed
        ↓
log failure
        ↓
continue channel 8
```

A broken external channel must not:

- fail the originating Offline Sync execution;
- suppress Message Center delivery;
- suppress another selected external channel.

## Severity mapping

Notification and Alert use slightly different level vocabularies:

```text
Notification INFO     -> Alert INFO
Notification SUCCESS  -> Alert INFO
Notification WARNING  -> Alert WARN
Notification ERROR    -> Alert ERROR
```

Stage 4.3 does not invent a success-specific Alert severity.

## Offline Sync policy

The existing `notification_config_json` gains optional external delivery fields:

```json
{
  "enabled": true,
  "triggers": ["FINAL_FAILURE"],
  "recipientType": "PROJECT_OWNER",
  "recipientUserIds": [],
  "inAppEnabled": true,
  "alertEnabled": true,
  "alertChannelIds": [7]
}
```

Compatibility defaults are intentionally unchanged:

```text
legacy NULL policy
  -> IN_APP enabled
  -> Project OWNER
  -> ALERT disabled
```

Existing Stage 4.2 JSON that has no `alertEnabled` or `alertChannelIds` also normalizes to ALERT disabled.

External alert delivery is therefore opt-in and cannot start unexpectedly after upgrade.

## Policy validation

An enabled ALERT destination requires at least one valid positive channel id.

```text
alertEnabled = true
alertChannelIds = []
        ↓
reject policy
```

`IN_APP` recipients are validated only when the IN_APP destination is enabled. An alert-only policy does not require a Project OWNER lookup or explicit in-app recipients.

## UI

The shared Offline Sync editor supports destination composition:

```text
通知方式
  [x] 站内消息
  [x] 外部告警渠道

外部告警渠道
  [DingTalk ...]
```

The channel selector uses `/api/v1/alert/channels` and only presents channels with persisted IDs.

Disabled channels remain visible for historical selections but cannot be newly selected. Missing historical channel ids remain visible as stale references so the editor does not silently rewrite task configuration.

## Current plugin availability

At Stage 4.3 the repository contains a DingTalk Alert plugin.

The sink is intentionally plugin-agnostic. Future Email, Webhook or other Alert plugins should be added behind `AlertPluginRegistry`; the Notification Router and business modules should not require another integration change.

## Persistence

Stage 4.3 adds no new table and no Flyway migration.

It reuses:

```text
yak_ops_alert_channel.id
```

and the Stage 4.2 task-level:

```text
yak_offline_job_definition.notification_config_json
```

## Non-goals

Stage 4.3 does not add:

- Project ownership to Alert Channel;
- Email or generic Webhook plugin implementations that do not already exist;
- success notifications;
- retry-attempt notification spam;
- MQ / WebSocket / SSE;
- synchronous coupling from Offline Sync to `AlertService`;
- a second alert-channel configuration table.
