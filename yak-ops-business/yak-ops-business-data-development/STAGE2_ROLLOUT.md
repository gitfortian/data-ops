# Data Development Stage 2 Rollout Checklist

Stage 2 makes Data Development project-required and permission-governed. Before marking the pull request ready for review, validate the following in an environment with Yak Security and the business database enabled.

## Build and migration

```bash
mvn -pl yak-ops-business/yak-ops-business-data-development,yak-ops-business/yak-ops-business-data-service,yak-ops-boot -am test
```

Run the frontend lint/typecheck/test commands used by `yak-ops-ui`, then start against a database that contains pre-Stage-2 Data Development data and verify startup completes the compatibility backfill.

## Project isolation

Prepare Project A and Project B with two users who belong to their respective projects.

- A cannot list or fetch B directories/nodes by changing ids.
- A cannot read B drafts, revisions, execution records or release assets.
- A cannot run/cancel/retry B executions.
- A cannot publish, online/offline or activate B assets.
- SQL lineage outbox work restores its persisted project and never falls back to a global repository read.

## Permission matrix

Validate at least these roles:

| Role | Expected |
| --- | --- |
| root | all Data Development actions |
| read-only | browse workspace/releases/executions only |
| developer | READ + EDIT + EXECUTE; cannot DELETE/PUBLISH/RELEASE |
| publisher | READ + PUBLISH; cannot edit/run/release unless separately granted |
| release operator | READ + RELEASE; can online/offline/activate only |

Direct URLs and direct HTTP calls must be rejected when the permission is absent; frontend button visibility is only an additional UX guard.

## Data Service owner boundary

For a Data Service Runtime created from a Data Development Data Service Node:

- generic `/api/v1/data-service` publish/republish/enable/disable/delete routes reject the source-managed service;
- generic publication-state discovery rejects the managed source;
- Data Development publication state works through `/api/v1/data-development/nodes/{nodeId}/data-service/publication`;
- online/offline succeeds only with `data-development:release` in the correct project.

## Cutover note

Non-root roles do not receive the new Data Development permissions automatically. Grant the required `data-development:*` permissions in Role & Permission management before exposing Stage 2 to those users.
