# Section Query API → Asset Understanding Loop

Scope: #13, Slice 1, PD-001, F-001, F-001-A; depends on #12.

Asset Detail first resolves an authorized Asset identity. The query then returns
independent sections answering: what is it; who owns it; where did it come from;
can it be trusted or used; what depends on it; where should the user go next.
`SectionContext` carries the Asset key and source identity, while each
`SectionResult` carries a typed summary, owner, five-state status,
explanation, source observation time, provenance, evidence, capability and
authorized actions. `SectionResponse` is a read-only aggregate.

Acceptance scenarios:
- Physical Table: Metadata resolves from Metadata; Quality can be OK or
  confirmed EMPTY; Lifecycle is NOT_APPLICABLE. Source actions retain IDs.
- Model/Metric: technical Metadata and Quality are NOT_APPLICABLE;
  Model TTL reads Lifecycle; Metric TTL is NOT_APPLICABLE.
- Quality timeout: only Quality is UNAVAILABLE with a safe reason;
  other sections remain usable.
- Security denial: PERMISSION_DENIED carries no protected summary,
  evidence or actions and cannot be rendered as EMPTY.
- Unregistered provider: UNAVAILABLE, not EMPTY.

Evidence required at implementation: contract tests for state invariants and
applicability, an integration test showing per-section failure isolation and
project-scoped authorization, plus Asset Detail E2E screenshots/response
samples with source links and return path. These are not claimed as completed
by this API-only change.
