# Yak Ops Digital Screen

Digital Screen owns Project-scoped persisted screen definitions, mutable Draft state and append-only published snapshots. It does not execute Dataset queries; runtime data continues to flow through the owning data capabilities.

`yak_digital_screen` is a `PROJECT_ROOT`. Published versions inherit ownership through `screen_id`. The management controller is `PROJECT_REQUIRED`, root CRUD always uses trusted `CurrentProject`, and version reads prove the owning Screen before returning an inherited row.

Historical bindings are template-defined opaque JSON, so the Project migration never guesses ownership. Existing rows must be assigned explicitly after business review; unresolved rows intentionally block the `NOT NULL` contract.

## Lifecycle

```text
create Draft
 -> update Draft (revision increases)
 -> publish immutable version
 -> optionally offline
 -> rollback by copying an old version into a new published version
```

Duplicate creates a new Project-owned Draft and does not copy publication history.

## API

- `GET /api/v1/digital-screens`
- `GET /api/v1/digital-screens/{id}`
- `GET /api/v1/digital-screens/{id}/published`
- `GET /api/v1/digital-screens/{id}/versions`
- `GET /api/v1/digital-screens/{id}/versions/{versionNo}`
- `POST /api/v1/digital-screens`
- `PUT /api/v1/digital-screens/{id}`
- `POST /api/v1/digital-screens/{id}/publish`
- `POST /api/v1/digital-screens/{id}/offline`
- `POST /api/v1/digital-screens/{id}/versions/{versionNo}/rollback`
- `POST /api/v1/digital-screens/{id}/duplicate`
- `DELETE /api/v1/digital-screens/{id}`
