# Phase7 Quality Runtime Mapping

## Step 3.8.3 Evidence

## Module

`data-ops-business/data-ops-business-quality`

## Source Layout

Confirmed runtime packages:

- controller
- domain
- execution
- gateway
- monitor
- repository
- schedule
- task

## Controller Mapping

| Controller | Responsibility |
|---|---|
| QualityExecutionController | Quality execution API |
| QualityExecutionWorkspaceController | Execution workspace API |
| QualityMonitorController | Quality monitoring API |
| QualityOverviewController | Quality overview API |
| QualityTableAssetController | Table asset quality API |
| QualityTemplateController | Template API |
| QualityWorkspaceController | Workspace API |
| CustomTemplateController | Custom template API |

## Runtime Chain

Controller
  -> Quality domain services/components
  -> Repository/Gateway/Execution modules
  -> Response DTO/API models

## Evidence Status

| Domain | Controller | Service | Endpoint | DTO | Fixture |
|---|---|---|---|---|---|
| Quality | DONE | IN PROGRESS | IN PROGRESS | IN PROGRESS | LOCATED |

## Test Evidence

Test root located:

`data-ops-business/data-ops-business-quality/src/test/java`

Runtime execution validation pending.
