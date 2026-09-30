# Phase7 Asset Runtime Mapping Evidence

## Asset runtime chain

```
AssetController
    ↓
AssetAppService / AssetDiscoverService / AssetLifecycleService
    ↓
Asset API models
    ↓
Result<Response>
```

## Evidence

| Layer | Location | Status |
|---|---|---|
| Controller | data-ops-business-asset/src/main/java/io/yak/ops/business/asset/controller/v1/AssetController.java | Located |
| Application | data-ops-business-asset/src/main/java/io/yak/ops/business/asset/application | Located |
| API DTO | data-ops-business-asset/src/main/java/io/yak/ops/business/asset/api | Located |
| Controller DTO | data-ops-business-asset/src/main/java/io/yak/ops/business/asset/controller/v1/dto | Located |
| Test | data-ops-business-asset/src/test/java | Located, fixture verification pending |

## Endpoint examples

- GET /api/v1/assets
- GET /api/v1/assets/{id}
- GET /api/v1/assets/{id}/sections/{sectionType}
- POST /api/v1/assets
- PUT /api/v1/assets/{id}
- POST /api/v1/assets/publish

## Acceptance

Asset domain source mapping is complete. Runtime test evidence remains in final acceptance stage.
