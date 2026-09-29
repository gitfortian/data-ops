# Home Dependency Contract

## Allowed module dependencies

`data-ops-business-home` is a read-only composition module. Its module dependencies are intentionally one-way:

```text
data-ops-business-home
├── data-ops-common
├── data-ops-core
├── data-ops-business-datasource
├── data-ops-business-dataset
├── data-ops-business-lineage
├── data-ops-business-sync-offline
├── data-ops-business-workflow
├── data-ops-business-quality
├── data-security-spring-boot-starter (HTTP permission annotation)
├── data-schedule-api
└── Quartz (cron occurrence projection only)
```

Sibling business modules must not depend on Home.

## Package dependency matrix

| From | May depend on | Must not depend on |
| --- | --- | --- |
| `controller.v1` | Home capability Readers, `Result`, `ProjectScope`, permission annotations | sibling DAO/PO/Repository, Quartz |
| `cockpit` | datasource/query, offline/workflow/quality overview Readers | Controller, sibling persistence |
| `datacenter` | offline/workflow/quality overview Readers | Controller, sibling persistence |
| `asset` | Dataset overview facade, Lineage query facade/domain projection | sibling DAO/Mapper/PO |
| `quality` | Quality read-side | quality persistence/runtime internals |
| `schedule` | `YakScheduleGateway`, Schedule API, Quartz cron parser | business schedule tables/DAO |

## Boundary rules

1. Home has no database schema and no Flyway directory.
2. Home has no DAO, Mapper, PO or Repository implementation.
3. Home never calls sibling persistence adapters directly.
4. Home does not expose command methods; all public business roles are read-side.
5. `data-ops-boot` depends on Home only for application assembly; Home never depends on Boot.
6. External REST addresses remain `/api/v1/home/**` even though implementation ownership moves out of Boot.
