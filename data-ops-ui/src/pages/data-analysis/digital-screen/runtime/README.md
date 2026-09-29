# Digital Screen Runtime

`runtime` owns execution of a saved Digital Screen definition. Persistence belongs to
`services/digital-screen`; rendering primitives belong to `components/screen-engine`.

## Execution pipeline

```text
ScreenTemplate + bindings + Datasets
               |
               v
             Planner
               |
               v
      ScreenRuntimeCandidate[]
               |
               v
        Runtime Executor
        /      |       \
 Query Key   Cache   Concurrency
    |          |          |
    +----------+----------+
               |
               v
          Dataset API
               |
               v
       Component Adapter
               |
               v
        ScreenRenderer
```

## PR 4 runtime policy

- Stable query keys include Dataset id/version and the complete query payload.
- Candidates with the same key share one network request, even when component types differ.
- Raw Dataset query results are cached per runtime session for 10 seconds by default.
- At most 4 unique Dataset queries run concurrently by default.
- Every execution owns an `AbortController`; changing bindings/template or leaving the page cancels stale requests.
- A refresh keeps the last successful component data visible while new data is loading.
- Component/query failures remain isolated to the affected components.
- Production Viewer refreshes every 30 seconds while the page is visible; background tabs pause polling and refresh once when visible again.
- Viewer loads metadata only for Dataset ids referenced by the current PublishedVersion instead of expanding the complete online Dataset catalog.
- Editor remains event-driven and keeps the full Dataset catalog because the binding panel needs it.

All timing/concurrency defaults live in `policy.ts` and can be overridden through `useScreenRuntime` options.

## Boundaries

Runtime plugins answer: can a component bind/query, how is its query built, and how is raw Dataset data adapted.
Renderer plugins answer only: how is a component drawn.

PR 4 intentionally does **not** add global filters, component linkage, drill-down or cross-component interaction. Those are PR 5 concerns.
