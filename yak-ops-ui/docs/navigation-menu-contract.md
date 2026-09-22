# Navigation ↔ Menu Authorization Contract

Yak Ops navigation and Yak Security menu authorization are separate models that share one stable identifier: `menuCode`.

## Contract

- `route.id`, `path`, `title`, `component`, icon and grouping are frontend UI metadata. They may change without changing RBAC identity.
- `menuCode` is the stable database-backed authorization identifier. Do not derive it from `route.id` or URL shape.
- Yak Ops business menu codes are declared in `src/constants/securityMenuCodes.ts` and correspond to the Yak Ops security catalog reconciled by `V2006__reconcile_menu_permission_catalog.sql`.
- System-management menu codes in the same constants file are framework-owned and correspond to the Yak Security menu catalog. Yak Ops must not duplicate those rows in its own Flyway migrations.
- Every visible route declares a stable `menuCode`.
- Hidden detail/editor routes normally omit `menuCode` and inherit the nearest parent route's stable menu code.
- Hidden utility/public routes that are not database-backed menus may omit `menuCode`.

## Runtime authorization

Navigation visibility and direct-route access use the same `canAccessNavigationRoute` function. Access therefore requires the route's permission requirement and, when the backend exposes `menuCodes`, its resolved stable menu grant.

Compatibility rules:

1. `mode: public` keeps its existing public behavior; this contract does not convert public routes into protected routes.
2. `security:root` bypasses menu-grant checks as before.
3. `menuCodes === undefined` means an older backend has not exposed menu authorization yet, so the frontend keeps staggered rollout compatibility.
4. Once `menuCodes` is present, an empty list or a missing stable mapping denies protected menu access.
5. A hidden child route resolves the same menu code as its parent, so sidebar visibility and direct URL authorization cannot drift.

## Ownership boundary

This contract intentionally does not make TypeScript generate backend menu rows and does not make the frontend navigation tree database-driven. Each side keeps the metadata it owns while sharing stable menu codes.

Yak Ops owns the business-menu rows in `V2006__reconcile_menu_permission_catalog.sql`. Yak Security owns `system-*` menu rows. The frontend may reference both namespaces but the Yak Ops Flyway catalog must contain only the Yak Ops-owned namespace.

## Consistency enforcement

`src/config/navigationMenuContract.test.ts` reads the real final Yak Ops menu section from `V2006__reconcile_menu_permission_catalog.sql` and compares it with `navigationGroups` and `appRoutes`.

The contract test fails on:

- missing or duplicate stable menu codes;
- frontend menu codes missing from the backend catalog;
- orphan backend menu rows with no frontend resource;
- parent, route-path or menu-type drift;
- protected routes whose backend required permission is not declared by the frontend route;
- visible routes without a stable menu code;
- hidden protected routes without a menu owner;
- framework-owned `system-*` rows accidentally duplicated in the Yak Ops migration.

Run it locally from `yak-ops-ui` with:

```bash
yarn jest src/config/navigationMenuContract.test.ts --runInBand
```

`.github/workflows/navigation-menu-contract.yml` runs the same targeted test for pull requests that modify the navigation contract or the final business-menu catalog.
