# Phase8.3 PR4 Model Impact Analysis Enhancement

## Goal

Enhance the PR3 impact analysis foundation into a governance-oriented impact analysis capability.

## Scope

### 1. Impact Resolver Enhancement

- Introduce resolver strategy abstraction.
- Support model relationship traversal.
- Separate impact discovery from persistence.

### 2. Relationship Resolution

Initial relationship chain:

```
LogicalModel
    -> LogicalEntity
        -> LogicalAttribute
        -> Mapping
```

### 3. Query Enhancement

Provide impact query capability for governance scenarios:

- model change assessment
- dependency discovery
- impact review preparation

## Non Goals

- SQL parser
- complete lineage engine
- automatic remediation

## Implementation Order

1. Resolver abstraction
2. Relationship adapters
3. Impact query service enhancement
4. Test coverage
