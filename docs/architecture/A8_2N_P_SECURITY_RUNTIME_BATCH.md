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

## A8.2r — Security API DTO / VO / enum / constants consolidated ownership

- **75 production Java type relocations in one deferred CI batch:** 37 request DTO classes, 27 response VO classes, and 11 enum/constant types. Every old `io.yak.framework.security.common.*` FQCN is unchanged; new class owner is `data-ops-platform-security-contract`. Old Starter physical class copies are deleted. Migration reuses the *original Git blob SHA* for every new owner source, preserving exact bytes.
- Existing `UserProjectDTO` Platform ownership and `SecurityPermissionCode` canonical/legacy facade continue unchanged; `ResultCode` intentionally remains Starter-owned because its `io.yak.framework.common.ErrorCode` parent remains in the legacy Common module until a separate dependency-safe Common consolidation. No compile-time reverse dependency is added.
- Minimal compile dependencies explicitly declared at Platform Contract: `jakarta.validation:jakarta.validation-api` for `@NotBlank` request inputs, and `com.fasterxml.jackson.core:jackson-annotations` for existing JSON null-inclusion policy. Existing Lombok annotations remain preserved.
- New static guard `scripts/architecture/check-security-api-contract-owner.mjs` freezes the exact 75-class manifest, rejects old Starter source duplicates or imports from upper application/database/runtime layers, and checks login validation, current user Project/RBAC shape, role-permission JSON null behavior and paging defaults; accompanied by **17 positive/negative Node tests**, wired into the existing Architecture Checks workflow (not a new CI workflow).
- New Platform Java regression checks ensure login JSON `userName`/`pw`, Bean Validation annotations, pagination defaults, `ControlLevelCode` semantics, `RoleVO.permissionTreeVO` nullable field JSON, nested permissions, `CurrentUserVO` role/menu/permission/project JSON shapes and key public accessor method signatures.
- New Starter-side Java classpath test loads every relocated FQCN and requires exactly **one compiled `.class` resource** (including all DTO, VO, enums and constants), preserving old Starter consumers' dependency behavior.
- Still not migrated: `data-common`-derived `ResultCode`, Spring auto-configuration, MyBatis/Flyway/DataSource, project/role authorization services, Redis Session wiring, controllers and business services. They must be moved only after their dependency graph and history/HTTP contracts can be honored; pure DTO/VO migration does not prove real environment acceptance.
- **CI policy:** accumulated separately staged Git commits were not pushed individually; one final PR branch update is used to trigger the next complete CI. The four workflow checks on the preceding HEAD are not evidence for this batch.

## Remaining hard gates
- Verify clean multi-module Java compilation and existing Sa-Token authentication regressions.
- Verify Spring bean uniqueness, unauthenticated 401 and unauthorized 403.
- Verify Redis-backed session across two application instances and memory storage compatibility.
- Verify independent MySQL/PostgreSQL historical Flyway upgrade, not just an empty database.
- Run ordered A0–A8 integration from exact prerequisite PR heads with trustworthy artifacts.

**Do not merge.** Draft-only stacked architecture work. This batch does not yet claim final Security Runtime, database or A8 acceptance.
