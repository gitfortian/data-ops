# Notification Routing Architecture

## Purpose

Yak Ops separates three concepts that were previously easy to mix together:

- **Event**: a durable business fact, for example an Offline Sync final failure.
- **Notification Intent**: user-facing copy and durable source identity derived from that fact.
- **Delivery**: routing an intent to one or more destinations such as the in-app Message Center or an external Alert channel.

Business modules must not know how Message Center persistence, DingTalk, email, webhook, or recipient lookup is implemented.

## Runtime flow

```text
Offline Sync / Workflow / Quality
        |
        v
NotificationIntent
        |
        v
NotificationRouter
        |
        +--> NotificationPolicyResolver(s)
        |       |
        |       +--> first supporting resolver by ascending order
        |       +--> fallback: Project OWNER + IN_APP
        |
        v
NotificationPolicy
        |
        +--> IN_APP ----> YakSecurityInAppNotificationSink ----> Message Center
        |
        +--> ALERT -----> future AlertNotificationSink --------> AlertService / AlertPlugin
```

## Core contracts

### NotificationIntent

Contains only stable business-facing notification data:

- `projectId`
- `type` / `level`
- `title` / `summary` / `content`
- `sourceType` / `sourceId`
- safe internal `actionPath`

It does not contain MessageDTO, DingTalk webhook configuration, Project membership queries, or transport-specific payloads.

### NotificationPolicy

Describes the effective routing decision:

- enabled / disabled
- recipient strategy (`PROJECT_OWNER`, `EXPLICIT_USERS`)
- explicit recipient user IDs when a business resolver has already resolved task creator/custom users
- destinations (`IN_APP`, `ALERT`)
- selected external alert channel IDs for future Alert routing

The current compatibility default is:

```text
enabled            = true
recipientStrategy  = PROJECT_OWNER
recipientUserIds   = []
destinations       = [IN_APP]
alertChannelIds    = []
```

This preserves the behavior introduced by PR #906.

`EXPLICIT_USERS` is intentionally generic. A business-specific resolver may map product concepts such as "task creator" or "selected users" to durable user IDs without teaching the Router or Message Center about Offline Sync concepts.

### NotificationPolicyResolver

Resolvers are extension points for product-specific configuration.

The router sorts resolvers by ascending `order()` and uses the first resolver whose `supports(intent)` returns true. The Boot fallback resolver uses `Integer.MAX_VALUE`, so business modules can override it without modifying the router.

A resolver failure is **fail-closed**. The router does not silently fall back to the default policy because that could violate an explicit user opt-out.

### NotificationSink

A sink owns one delivery destination. It receives an already-resolved intent and policy.

Current sink:

```text
IN_APP -> Yak Security Message Center
```

The IN_APP sink supports both:

```text
PROJECT_OWNER  -> resolve current Project owners through UserProjectService
EXPLICIT_USERS -> use policy.recipientUserIds directly
```

Future sink:

```text
ALERT -> yak-ops-business-alert -> AlertPluginRegistry -> DingTalk / Email / Webhook / ...
```

## Transaction and failure semantics

`DefaultNotificationRouter` owns the common side-effect semantics:

- if called inside a business transaction, routing runs after commit;
- rolled-back business transactions do not produce notifications;
- policy resolution failure does not fail the business operation;
- one sink failure does not fail the business operation;
- one sink failure does not prevent another selected sink from being attempted;
- no MQ is required by this foundation.

Individual sinks must not reimplement transaction timing.

## Business module rule

Business code publishes an intent:

```java
notificationRouter.publish(new NotificationIntent(...));
```

Business code must not call:

```text
MessageService / NotificationPublisher / MessageDTO
AlertService / AlertPlugin
UserProjectService
```

Those belong behind Router/Sink boundaries.

## Stage 4.2: Offline Sync task policy

Offline Sync can add a higher-priority resolver, for example:

```text
OfflineSyncNotificationPolicyResolver
supports: sourceType == OFFLINE_SYNC_EXECUTION
order: 100
```

That resolver can read the task's `notification_config_json` and map it to `NotificationPolicy`.

Examples:

```text
Project owner
  -> recipientStrategy = PROJECT_OWNER

Task creator / selected users
  -> recipientStrategy = EXPLICIT_USERS
  -> recipientUserIds = resolved user ids
```

For old tasks with no configuration, it should return the current compatibility policy:

```text
final failure -> Project OWNER -> IN_APP
```

This means the existing `OfflineFailureNotificationListener` does not need to know recipients or channels.

## Stage 4.3: Alert integration

Alert integration should add an `ALERT` sink rather than changing business listeners:

```text
NotificationPolicy.Destination.ALERT
        |
        v
AlertNotificationSink
        |
        v
AlertService
        |
        v
AlertPluginRegistry
```

The task policy selects configured Alert channel IDs. Webhook secrets and protocol configuration remain owned by the Alert module/channel configuration and must not be copied into Offline Sync definitions.

## Non-goals of Stage 4.1

This foundation intentionally does not add:

- Offline Sync notification fields or UI;
- Flyway migrations;
- Project-scoped Alert channel configuration;
- DingTalk/email/webhook delivery;
- MQ/WebSocket/SSE;
- success notifications.
