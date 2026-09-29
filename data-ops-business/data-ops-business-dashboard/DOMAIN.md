# Dashboard Domain

## Aggregate truth

Dashboard is a Project-owned, versioned composition aggregate with two levels of truth:

```text
DashboardAsset
    = stable identity
      + current-version pointer
      + published-version pointer
      + current-list projection fields

DashboardVersionSnapshot
    = immutable composition truth for one version
```

`yak_dashboard.project_id` is durable root ownership derived from trusted `CurrentProject`. It is not accepted from a request DTO. DashboardVersion, Widget, Filter, Binding and Interaction inherit that ownership through `dashboard_id` and `dashboard_version_id`.

`DashboardAsset.name` and `description` follow the current-version projection for list/read convenience. They do not replace historical snapshots.

## Version lifecycle

DashboardVersion is append-only. Saving a candidate appends the next immutable snapshot and moves `currentVersionId`; it never mutates the current row in place.

`publishedVersionId` identifies the externally effective published snapshot. Current and published may diverge for any number of edits.

Restore is copy-forward:

```text
V1 -> V2 -> V3(current)
restore V1
        ↓
append V4(copy of V1 composition)
current -> V4
```

The published pointer remains unchanged until an explicit publish. The deprecated activate name is only a compatibility alias.

## Composition

A widget freezes exactly one content binding:

```text
reusable Analysis reference
OR
inline Analysis payload
```

Analysis owns reusable analytical definitions. Dashboard owns only the frozen Analysis ID reference or inline payload in a DashboardVersion.

A reusable Analysis reference must exist in the current Project. An inline payload may declare `datasetId`; when it does, that Dataset must exist in the current Project. The optional `activeDatasetId` follows the same ownership rule.

Those checks do not prove that a Dataset is ONLINE, has a current version, or that every field binding matches its schema. Dataset lifecycle, schema and query truth remain Dataset-owned.

Layout, theme, global filters, bindings and interactions belong to the immutable DashboardVersion snapshot.

## Historical reference safety

Any historical DashboardVersion that references Analysis X prevents Analysis X from being deleted. The reference query is constrained by the current Project, so one workspace cannot observe or control another workspace's references.

## Change fact and lineage

`DashboardChangedEvent` contains the owning `projectId` because the listener runs after commit. `ProjectContextScope` restores that context before any Dashboard read or Lineage projection.

Lineage owns graph truth. Dashboard owns only deterministic/best-effort projection input and convergence logic. Projection failure cannot roll back committed Dashboard truth.

The effective lineage rule remains:

```text
published exists -> project published
otherwise         -> project current
```

## Persistence truth

Database rows own Dashboard identity, Project ownership, pointers and immutable snapshots. Repository adapters translate persistence rows/JSON columns; PO/Mapper/MyBatis types are not domain contracts.

Cross-Project IDs fail closed as absent in the current workspace. Child rows are never considered independently accessible merely because their numeric ID exists.

## Known domain gap: version allocation concurrency

The current flow obtains `nextVersionNo` and then appends. The unique `(dashboard_id, version_no)` database key protects duplicate durable numbers, but the application does not yet define a richer retry/CAS allocator contract. This remains a future domain/concurrency concern.
