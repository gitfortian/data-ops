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

## A8.2q — Consolidated Security SPI and implementation retirement batch
- Move **three pure extension SPIs** into the established `data-ops-platform-security-contract`: `PasswordEncoder`, `PermissionExtend`, `OperationLogExtend`. These interfaces contain no framework, servlet, persistence, business or Spring imports. Retain their old FQCN and method descriptors.
- Move **nine production classes** into `data-ops-platform-security-runtime`: `CurrentUserProvider`, `DefaultCurrentUserProvider`, `DefaultPasswordEncoder`, `DefaultPermissionExtend`, `LoginAttemptGuard`, `NoOpOperationLogExtend`, `DatabaseNumberUtils`, `JsonUtils`, `MathUtil`. Preserve the existing source bodies and fully qualified class names; delete matching Starter copies.
- Move `DefaultCurrentUserProviderTest` and `LoginAttemptGuardTest` to Runtime, keeping the same-package test access. Add four regression suites: BCrypt password hashes/matches and invalid values, default deny and no-op audit, JDBC numeric scalar/JSON/list/random math behavior, plus Java reflection for the three legacy SPI binary signatures.
- Explicit Runtime dependency closure: `spring-security-crypto`, `jackson-databind`; the existing Runtime already provides contract, Spring and provided Servlet API. No reverse dependency from Contract/Runtime to Starter, Business or Boot.
- New `check-security-extension-owner.mjs` and **16 positive/negative Node cases** check unique binary/test owners, Spring default bean references, login throttling integration, denial-by-default, BCrypt/JSON ABI, and Maven dependency direction. Reuse Architecture Checks without a fifth workflow.
- Starter still retains the Spring auto-configuration and final Bean ownership, `DefaultLoginExtendImpl` (dependent on `data-common`, `UserService` and Security user DTO/VO), `LoginExtend`, `ResourceExtend`, MyBatis/Flyway/DataSourceConfig, CaffeinePermissionCache, HTTP controllers and Redis/session registration. These are **not** moved by guessing dependencies.
- This batch does not assert real Redis multi-instance, HTTP 401/403, old Flyway-history upgrades, external old-JAR ABI, or ordered A0–A8 release; these remain explicit hard gates. No behavior was intentionally changed by the source relocation.

## Remaining hard gates
- Verify clean multi-module Java compilation and existing Sa-Token authentication regressions.
- Verify Spring bean uniqueness, unauthenticated 401 and unauthorized 403.
- Verify Redis-backed session across two application instances and memory storage compatibility.
- Verify independent MySQL/PostgreSQL historical Flyway upgrade, not just an empty database.
- Run ordered A0–A8 integration from exact prerequisite PR heads with trustworthy artifacts.

**Do not merge.** Draft-only stacked architecture work. This batch does not yet claim final Security Runtime, database or A8 acceptance.
