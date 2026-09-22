# BI Editor Engineering Boundary

Phase 12 closes the first BI editor roadmap by making the runtime and persistence boundaries explicit. The goal is not to add another chart feature; it is to keep the editor predictable as the number of widgets, interactions and saved Dashboard versions grows.

## Runtime query coordination

`src/components/analysis/query-runtime.ts` is the shared Dataset query coordinator used by `AnalysisPreview`.

- identical in-flight queries share one Promise;
- successful raw Dataset results are kept for a short 1.5 second TTL;
- Dataset id **and Dataset version** are part of the cache key;
- failed queries are evicted immediately;
- the cache is bounded to 64 entries;
- calculated fields are still materialized after the shared raw result returns, so chart-local formulas do not contaminate another chart's result.

The existing 180 ms editor debounce and request sequence guard stay in `AnalysisPreview`. Together the flow is:

```text
Spec / runtime filters change
        ↓
180 ms debounce
        ↓
Dataset + version + payload key
        ↓
shared in-flight / short TTL result
        ↓
calculated-field materialization
        ↓
latest-request sequence guard
        ↓
chart renderer
```

## Render failure isolation

`AnalysisErrorBoundary` wraps each `AnalysisPreview`. Unexpected React/chart-option render failures are isolated to the current chart. Dataset query failures continue to use the normal query-error state and are not treated as render crashes.

This matters on a Dashboard canvas: one malformed persisted chart should not blank the entire editor or viewer.

## Dashboard document integrity

`src/pages/dashboard/dashboard-integrity.ts` owns referential cleanup that is safe to perform without understanding business semantics.

It removes:

- duplicate/blank widget ids;
- global-filter bindings that point to missing widgets;
- duplicate filter bindings;
- filter interactions whose source widget or target filter no longer exists;
- direct chart links whose target widget no longer exists;
- duplicate direct chart links.

It deliberately does **not** rewrite Encoding, formulas, styling, aggregation choices, Top N, drill hierarchies or chart types.

The normalizer runs when a server Dashboard becomes a `DashboardDocument` and again before the document is serialized for create/save. This makes old snapshots forward-tolerant without introducing a Dashboard document version migration.

## Focused regression suite

Run the BI-only logic regression suite from `yak-ops-ui`:

```bash
npm run test:bi
```

The command groups the focused tests for:

- Phase 8 analysis calculations;
- Phase 9 calculated fields;
- Phase 10 chart options;
- Phase 11 interaction runtime;
- Phase 12 query coordination;
- Phase 12 Dashboard integrity.

Full release validation still includes `npm run lint`, `npm run build` and browser regression of editor/viewer behavior.
