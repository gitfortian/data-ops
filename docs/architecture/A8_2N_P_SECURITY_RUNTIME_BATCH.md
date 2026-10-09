# A8.2n–p Security Runtime consolidated batch (in progress)

Prerequisite: Draft PR #453, exact head `876b7495d64060af25834f1e17d676aae0640e59`. This branch is stacked on that head, not on `main`.

## A8.2n — Sa-Token runtime binary owner
- Move `SaTokenAuthenticationManager` source unchanged to `data-ops-platform-security-runtime`; same package and public signatures.
- Keep `AuthenticationManager` as the sole Platform Contract owner.
- Retain Sa-Token 1.45.0, Session key, dynamic timeout, cookie handling, and login/logout methods.
- Starter consumes Runtime through Maven; actual Spring auto-configuration remains in Starter.
- Keep old Starter's Sa-Token/Redisx dependencies for its existing configuration/storage classes. No Redis or HTTP behavior change intended.
- Explicitly prohibit duplicate compiled adapter ownership via a static source guard.

## Remaining hard gates
- Verify clean multi-module Java compilation and existing Sa-Token authentication regressions.
- Verify Spring bean uniqueness, unauthenticated 401 and unauthorized 403.
- Verify Redis-backed session across two application instances and memory storage compatibility.
- Verify independent MySQL/PostgreSQL historical Flyway upgrade, not just an empty database.
- Run ordered A0–A8 integration from exact prerequisite PR heads with trustworthy artifacts.

**Do not merge.** Draft-only stacked architecture work. This batch does not yet claim final Security Runtime, database or A8 acceptance.
