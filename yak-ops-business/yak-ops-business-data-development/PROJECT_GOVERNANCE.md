# Data Development Project & Permission Governance

Stage 2 turns Data Development from an authenticated global workbench into a project-scoped, permission-governed authoring context.

## Two independent gates

Every Data Development HTTP request must pass both gates:

```text
Authenticated user
      |
      +--> Project Space membership / running project
      |       X-YAK-SECURITY-PROJECT-ID
      |       @ProjectScope(PROJECT_REQUIRED)
      |
      +--> Yak Security permission
              data-development:*
```

Project membership answers **which workspace can this user enter?** Permission codes answer **which actions can this user perform?** Neither substitutes for the other.

## Permission contract

| Permission | Responsibility |
| --- | --- |
| `data-development:read` | Read workspace tree, drafts, revisions, execution history, release center and editor settings |
| `data-development:edit` | Create/rename resources and save Task/Dataset/Data Service drafts and editor settings |
| `data-development:delete` | Delete development directories and nodes |
| `data-development:execute` | Run Task/Dataset/Data Service queries and cancel/retry executions |
| `data-development:publish` | Create immutable Task/Data Service revisions |
| `data-development:release` | Online/offline Task Catalog revisions and Data Service Runtime projections |

DataSource authorization remains owned by `resource:data-source:*`; Data Development does not duplicate datasource permissions.

## Project cutover

Data Development endpoints now require a project header. The frontend Project Switcher remains the single project selector and attaches the selected project to `/api/v1/data-development/**` requests.

Historical rows with `project_id IS NULL` are not made visible through an `OR project_id IS NULL` compatibility query. At application startup the composition root performs a one-time, idempotent compatibility backfill:

1. infer directory ownership when all referenced scoped nodes agree on one project;
2. reject ambiguous directories referenced by multiple projects;
3. move remaining legacy directories/root nodes into the compatibility default Project Space;
4. inherit execution and lineage-outbox project ownership from their node;
5. fail startup if any Data Development project root remains unscoped.

The database columns remain nullable during the wider Project Space migration because the compatibility project id belongs to Yak Security and is resolved dynamically. The HTTP/application contract is nevertheless PROJECT_REQUIRED; physical `NOT NULL` constraints can be added after all project-aware modules finish their cutover.

## Background work

HTTP project headers do not exist in scheduled/outbox threads. Durable background work must therefore restore the project stored with the work item before entering project-scoped repositories or adjacent contexts.

SQL lineage uses this contract:

```text
yak_dev_lineage_outbox.project_id
        -> ProjectContextScope
        -> CurrentProject
        -> Node / Revision / Lineage IO
```

The worker also verifies that the persisted outbox project matches the owning Development Node before writing lineage. A missing or mismatched project is a failed work item, not permission to fall back to a global read.

## Data Service Runtime owner boundary

Data Service Runtime remains an adjacent consumption projection rather than a Data Development project root. However, a Runtime whose source provider declares `managesServiceDefinition()` is owned by that authoring context.

For Data Development Data Service Nodes:

```text
Data Service Node
    -> immutable DS Revision
    -> data-development:release
    -> DevelopmentDataServicePublicationService
    -> adjacent Data Service Runtime
```

Generic `/api/v1/data-service` publication endpoints reject source-managed publish/republish/enable/disable/delete operations and managed-source publication-state discovery. This prevents callers from bypassing Data Development's project and RELEASE permission gates while preserving the existing Data Service Runtime implementation.

## Role migration

The security catalog registers Data Development permissions and menus. Only roles carrying `security:root` receive all new Data Development permissions automatically. Other roles must be granted the minimum required actions explicitly in the existing Role & Permission management UI.

This is intentional: Stage 2 removes the previous implicit rule that every authenticated user could author, execute and release Data Development assets.
