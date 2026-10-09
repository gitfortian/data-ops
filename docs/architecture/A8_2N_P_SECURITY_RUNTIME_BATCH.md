# A8.2n–p Security Runtime consolidated batch (in progress)

Prerequisite: Draft PR #453, exact head `876b7495d64060af25834f1e17d676aae0640e59`. This branch is stacked on that head, not on `main`.

## A8.2n — Sa-Token runtime binary owner
- Move `SaTokenAuthenticationManager` source unchanged to `data-ops-platform-security-runtime`; same package and public signatures.
- Keep `AuthenticationManager` as the sole Platform Contract owner.
- Retain Sa-Token 1.45.0, Session key, dynamic timeout, cookie handling, and login/logout methods.
- Starter consumes Runtime through Maven; actual Spring auto-configuration remains in Starter.
- Keep old Starter's Sa-Token/Redisx dependencies for its existing configuration/storage classes. No Redis or HTTP behavior change intended.
- Explicitly prohibit duplicate compiled adapter ownership via a static source guard.

## A8.2o-p: Runtime config, HTTP context, audit helper migration
- Four unchanged production sources move from Starter to Runtime: YakSecurityProperties, HttpRequestUtil, NetworkUtil, SensitiveDataSanitizer. Preserve original FQCN and binary method signatures.
- Preserve yak.security configuration prefix, Sa-Token storage/default values, project ID header, HTTP identity/Proxy IP behavior and audit secret masking.
- Move two existing tests along with properties and request helper. Add default/binding/password masking, malformed/missing header, proxy IP fallback and audit token tests.
- Extend Runtime source owner check to all four helpers and their four tests; 12 positive/negative Node cases prevent duplicate/absent classes, Starter reverse imports, Maven reverse dependencies and contract drift.
- Maven runtime explicitly declares Lombok, Spring Boot configuration processor, Spring Web and Servlet provided API; Starter depends on Runtime and is still the only owner of DB/Flyway/MyBatis, Filter wiring, CaffeinePermissionCache and Redis DAO.
- No production behavior intentionally changed. External compiled consumers, 401/403, real Redis multi-instance and historical database upgrades remain unmet acceptance gates.

## Remaining hard gates
- Verify clean multi-module Java compilation and existing Sa-Token authentication regressions.
- Verify Spring bean uniqueness, unauthenticated 401 and unauthorized 403.
- Verify Redis-backed session across two application instances and memory storage compatibility.
- Verify independent MySQL/PostgreSQL historical Flyway upgrade, not just an empty database.
- Run ordered A0–A8 integration from exact prerequisite PR heads with trustworthy artifacts.

**Do not merge.** Draft-only stacked architecture work. This batch does not yet claim final Security Runtime, database or A8 acceptance.
