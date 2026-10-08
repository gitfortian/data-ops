# A8.1a — Retire the unused File Framework artifact from the application reactor

> Parent: [A8 Issue #412](https://github.com/gitfortian/data-ops/issues/412). This change is the **first isolated implementation slice of A8.1**, not the full Framework removal. The preceding A8.0 guard is [PR #415](https://github.com/gitfortian/data-ops/pull/415) and remains a separate protected Draft PR.

## Confirmed source facts

- `data-ops-framework/pom.xml` still aggregates `data-file` although the current Data-Ops application owns Resource / Storage through `data-ops-business-resource` and the `data-ops-plugin-storage-{api,local,minio,hdfs}` modules.
- `data-ops-bom/pom.xml` manages `io.github.weifuwan:data-file:0.1.0`. The active application Maven modules reviewed in A8.0 declared **no direct production dependency** on that artifact. The new static guard verifies this as a continuous invariant across the full checkout, not only a hand-written allowlist.
- The old `data-file` includes 12 production Java classes, covering Local/MinIO/OSS and metadata/file management abstractions. It has its own source, tests and an independent Maven POM. Historical *external* consumers and published artifacts have **not** been exhaustively audited. Hence the source and its release coordinates must not be deleted or repurposed in this first slice.

## Minimal changes

1. Remove `<module>data-file</module>` from **nested** Framework reactor `data-ops-framework/pom.xml`. Data-Ops root still includes Framework, and Security/Schedule/Workflow/Common remain unchanged.
2. Remove only `io.github.weifuwan:data-file` dependencyManagement entry from the application BOM. Do **not** substitute an external Maven Central dependency.
3. Add `scripts/architecture/check-file-reactor-retirement.mjs` and focused fixture tests. Reject any new active reactor membership, direct/managed/plugin POM dependency on `data-file`, or production Java/resources reference to `io.yak.framework.file.*` outside the retained File tree and the separately quarantined legacy job tree.
4. Keep the old standalone source and execute `bash ./mvnw -f data-ops-framework/data-file/pom.xml -B -ntp test` as an additional Framework Integration CI step, **independently** from the root application reactor, ensuring source remains buildable for compatibility assessment.
5. Update the original Framework documentation to make current reactor membership and historical File status truthful.

## Required evidence before declaring this slice complete

- [ ] Focused guard positive/negative tests and checked-out-tree assertion pass.
- [ ] Framework Integration: full application Maven build and separately invoked retained File unit tests pass.
- [ ] Architecture Checks and Product Guard complete successfully at the final PR SHA.
- [ ] Confirm application distribution has no new missing artifact/descriptor.
- [ ] A8.0 + A8.1a **fresh combination** passes before use as integration evidence; do not reuse an older A7 success.
- [ ] Before **physically deleting** legacy File source / stopping external releases, inspect Maven Central artifact consumers, repository/downstream published clients and supported compatibility windows. Unknown external usage is a blocker for deletion, not evidence that it can be discarded.

## Non-goals / rollback

- Does **not** migrate Security, Schedule, Workflow DAG, or Common. Does **not** merge/close protected architecture PRs or alter `main`.
- Does **not** replace current Storage Plugin implementation, change Resource behavior, file metadata schema, OSS/MinIO/Local data format, HTTP API or Project access checks.
- Rollback is to restore the single reactor `<module>` and the corresponding BOM entry, and re-run full build, **without migrating or deleting any stored files**.
- Standalone File source remains under `data-ops-framework/data-file` temporarily; final physical disposition belongs to A8.5 following external compatibility evidence.
