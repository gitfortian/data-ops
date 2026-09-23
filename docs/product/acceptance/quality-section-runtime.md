# Quality Section Runtime Acceptance

## Truth Owner

Quality Section only projects Quality domain facts.

- Truth Owner: Quality domain
- Consumer: Asset Detail Section
- Asset does not create a quality truth table.

## Status Semantics

- OK: Quality query succeeded and monitor facts exist.
- EMPTY: Quality query succeeded but no monitor is registered.
- UNAVAILABLE: Quality dependency, location resolution, or reader failed.
- NOT_APPLICABLE: Asset type is outside Quality scope.

## Evidence / Provenance

Every Quality response must retain source domain ownership and query evidence. Missing evidence is not replaced with inferred quality conclusions.
