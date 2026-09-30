import { readFileSync } from 'node:fs';
import path from 'node:path';

import {
  YAK_OPS_MENU_CODES,
  YAK_SECURITY_MENU_CODES,
} from '../constants/securityMenuCodes';
import {
  appRoutes,
  navigationGroups,
  resolveNavigationMenuCode,
  type NavigationRoute,
} from './navigation';

type MenuCatalogRow = {
  menuCode: string;
  menuName: string;
  sortOrder: number;
  parentCode: string | null;
  routePath: string | null;
  menuType: number;
  visible: boolean;
  active: boolean;
  requiredPermissionCode: string | null;
};

const SECURITY_MIGRATION_ROOT = path.resolve(
  __dirname,
  '../../../data-ops-boot/src/main/resources/yak-security/db/migration',
);
const BASE_CATALOG_MIGRATION = path.join(
  SECURITY_MIGRATION_ROOT,
  'V2006__reconcile_menu_permission_catalog.sql',
);
const CATALOG_EXTENSION_MIGRATIONS = [
  path.join(
    SECURITY_MIGRATION_ROOT,
    'V2007__register_data_service_access_page.sql',
  ),
  path.join(
    SECURITY_MIGRATION_ROOT,
    'V2018__register_modeling_menu.sql',
  ),
  path.join(
    SECURITY_MIGRATION_ROOT,
    'V2019__register_semantic_menu.sql',
  ),
  path.join(
    SECURITY_MIGRATION_ROOT,
    'V2020__register_semantic_domain_menu.sql',
  ),
  path.join(
    SECURITY_MIGRATION_ROOT,
    'V2021__register_semantic_process_menu.sql',
  ),
  path.join(
    SECURITY_MIGRATION_ROOT,
    'V2022__register_semantic_field_menu.sql',
  ),
  path.join(
    SECURITY_MIGRATION_ROOT,
    'V2023__register_semantic_layer_menu.sql',
  ),
  path.join(
    SECURITY_MIGRATION_ROOT,
    'V2024__register_mdm_menu.sql',
  ),
  path.join(
    SECURITY_MIGRATION_ROOT,
    'V2025__register_mdm_modeling_menu.sql',
  ),
  path.join(
    SECURITY_MIGRATION_ROOT,
    'V2026__register_mdm_identification_menu.sql',
  ),
  path.join(
    SECURITY_MIGRATION_ROOT,
    'V2027__register_mdm_cleansing_menu.sql',
  ),
  path.join(
    SECURITY_MIGRATION_ROOT,
    'V2028__register_mdm_approval_menu.sql',
  ),
  path.join(
    SECURITY_MIGRATION_ROOT,
    'V2029__register_metric_menu.sql',
  ),
  path.join(
    SECURITY_MIGRATION_ROOT,
    'V2030__register_data_security_menu.sql',
  ),
  path.join(
    SECURITY_MIGRATION_ROOT,
    'V2031__register_data_lifecycle_menu.sql',
  ),
  path.join(
    SECURITY_MIGRATION_ROOT,
    'V2032__register_data_asset_menu.sql',
  ),
  path.join(
    SECURITY_MIGRATION_ROOT,
    'V2033__register_data_approval_menu.sql',
  ),
  path.join(
    SECURITY_MIGRATION_ROOT,
    'V2034__register_data_metadata_menu.sql',
  ),
  path.join(
    SECURITY_MIGRATION_ROOT,
    'V2035__fix_metric_menu_group.sql',
  ),
  path.join(
    SECURITY_MIGRATION_ROOT,
    'V2036__domain_navigation_regroup.sql',
  ),
  path.join(
    SECURITY_MIGRATION_ROOT,
    'V2037__retire_metadata_catalog_menus.sql',
  ),
  path.join(
    SECURITY_MIGRATION_ROOT,
    'V2038__reinstate_mdm_change_ledger_menu.sql',
  ),
  path.join(
    SECURITY_MIGRATION_ROOT,
    'V2039__register_sql_execution_audit_menu.sql',
  ),
  path.join(
    SECURITY_MIGRATION_ROOT,
    'V2042__task_oriented_navigation.sql',
  ),
];

const sqlValue = (token: string): string | null =>
  token === 'NULL' ? null : token.slice(1, -1);

const parseMenuRows = (sql: string): MenuCatalogRow[] => {
  const rowPattern = /\(\s*'([^']+)'\s*,\s*'([^']*)'\s*,\s*(NULL|'[^']*')\s*,\s*(NULL|'[^']*')\s*,\s*'[^']*'\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(NULL|'[^']*')\s*,\s*'[^']*'\s*,\s*'\$\{appName\}'\s*\)/g;

  return [...sql.matchAll(rowPattern)].map((match) => ({
    menuCode: match[1],
    menuName: match[2],
    parentCode: sqlValue(match[3]),
    routePath: sqlValue(match[4]),
    menuType: Number(match[5]),
    sortOrder: Number(match[6]),
    visible: match[7] === '1',
    active: match[8] === '1',
    requiredPermissionCode: sqlValue(match[9]),
  }));
};

const parseFinalYakOpsMenuCatalog = (): MenuCatalogRow[] => {
  const sql = readFileSync(BASE_CATALOG_MIGRATION, 'utf8');
  const sectionStart = sql.indexOf(
    '-- 3. Upsert the complete set of current visible Yak Ops menus.',
  );
  const sectionEnd = sql.indexOf('ON DUPLICATE KEY UPDATE', sectionStart);

  if (sectionStart < 0 || sectionEnd < 0) {
    throw new Error(
      'Cannot locate the baseline Yak Ops menu catalog in V2006__reconcile_menu_permission_catalog.sql',
    );
  }

  let rows = parseMenuRows(sql.slice(sectionStart, sectionEnd));
  if (rows.length === 0) {
    throw new Error('Parsed zero rows from the V2006 Yak Ops menu catalog');
  }

  for (const migrationPath of CATALOG_EXTENSION_MIGRATIONS) {
    const extensionRows = parseMenuRows(readFileSync(migrationPath, 'utf8'));
    for (const extension of extensionRows) {
      rows = [
        ...rows.filter((row) => row.menuCode !== extension.menuCode),
        extension,
      ];
    }
  }

  // Retired menus (visible/active = 0, e.g. the two metadata catalog pages via
  // V2037) drop out of the effective catalog: the frontend must not declare
  // them, and their old upsert rows must not be reported as orphans.
  return rows.filter((row) => row.visible && row.active);
};

const duplicateValues = (values: readonly string[]) =>
  values.filter((value, index) => values.indexOf(value) !== index);

const routePermissionCodes = (route: NavigationRoute): readonly string[] => {
  if (!route.mode || route.mode === 'public') return [];
  if (route.mode === 'one') return [route.permission];
  return route.permissions;
};

const finalCatalog = parseFinalYakOpsMenuCatalog();
const catalogByCode = new Map(finalCatalog.map((row) => [row.menuCode, row]));
const yakOpsMenuCodes = Object.values(YAK_OPS_MENU_CODES);
const frameworkMenuCodes = Object.values(YAK_SECURITY_MENU_CODES);
const yakOpsCodeSet = new Set<string>(yakOpsMenuCodes);
const frameworkCodeSet = new Set<string>(frameworkMenuCodes);
const navigationGroupById = new Map(
  navigationGroups.map((group) => [group.id, group]),
);

describe('Navigation ↔ Yak Security menu catalog contract', () => {
  it('keeps stable menu-code namespaces unique and disjoint', () => {
    expect(duplicateValues(yakOpsMenuCodes)).toEqual([]);
    expect(duplicateValues(frameworkMenuCodes)).toEqual([]);
    expect(
      yakOpsMenuCodes.filter((code) => frameworkCodeSet.has(code)),
    ).toEqual([]);
  });

  it('requires visible routes to declare stable menu codes and hidden protected routes to inherit one', () => {
    const errors: string[] = [];

    for (const route of appRoutes) {
      if (!route.hidden && !route.menuCode) {
        errors.push(`Missing menuCode: ${route.id} (${route.path})`);
      }

      if (
        route.hidden &&
        route.mode !== 'public' &&
        !route.menuCode &&
        !route.parentId
      ) {
        errors.push(`Protected hidden route has no menu owner: ${route.id} (${route.path})`);
      }

      if (route.hidden && route.parentId && !route.menuCode) {
        const parent = appRoutes.find((candidate) => candidate.id === route.parentId);
        if (!parent) {
          errors.push(`Unknown parent route: ${route.id} -> ${route.parentId}`);
        } else if (resolveNavigationMenuCode(route) !== resolveNavigationMenuCode(parent)) {
          errors.push(
            `Hidden route menu inheritance drift: ${route.id} -> ${route.parentId}`,
          );
        }
      }
    }

    expect(errors).toEqual([]);
  });

  it('keeps the frontend Yak Ops menu-code set exactly aligned with the effective Flyway catalog', () => {
    const frontendEntries = [
      ...navigationGroups
        .filter((group) => yakOpsCodeSet.has(group.menuCode))
        .map((group) => ({ code: group.menuCode, owner: `group:${group.id}` })),
      ...appRoutes
        .filter(
          (route): route is NavigationRoute & { menuCode: string } =>
            Boolean(route.menuCode && yakOpsCodeSet.has(route.menuCode)),
        )
        .map((route) => ({ code: route.menuCode, owner: `route:${route.id}` })),
    ];

    const duplicateFrontendCodes = duplicateValues(
      frontendEntries.map((entry) => entry.code),
    );
    const frontendCodes = new Set<string>(frontendEntries.map((entry) => entry.code));
    const backendCodes = new Set(finalCatalog.map((row) => row.menuCode));
    const errors = [
      ...duplicateFrontendCodes.map((code) => `Duplicate frontend menuCode: ${code}`),
      ...[...frontendCodes]
        .filter((code) => !backendCodes.has(code))
        .map((code) => `Missing backend menu: ${code}`),
      ...[...backendCodes]
        .filter((code) => !frontendCodes.has(code))
        .map((code) => `Orphan backend menu: ${code}`),
      ...yakOpsMenuCodes
        .filter((code) => !frontendCodes.has(code))
        .map((code) => `Unused Yak Ops menu constant: ${code}`),
    ];

    expect(errors).toEqual([]);
  });

  it('keeps parent, type, path and required-permission metadata aligned', () => {
    const errors: string[] = [];

    for (const group of navigationGroups) {
      if (!yakOpsCodeSet.has(group.menuCode)) continue;
      const backend = catalogByCode.get(group.menuCode);
      if (!backend) continue;

      if (backend.menuName !== group.title || backend.sortOrder !== group.order) {
        errors.push(`Menu group label/order mismatch: ${group.menuCode}`);
      }

      const expectedParentCode = group.parentGroupId
        ? navigationGroupById.get(group.parentGroupId)?.menuCode ?? null
        : null;
      if (backend.parentCode !== expectedParentCode) {
        errors.push(
          `Menu group parent mismatch: ${group.menuCode} frontend=${expectedParentCode ?? 'ROOT'} backend=${backend.parentCode ?? 'ROOT'}`,
        );
      }
      if (backend.routePath !== null) {
        errors.push(`Menu group must not own a route path: ${group.menuCode}`);
      }
      if (backend.menuType !== 1) {
        errors.push(`Menu group type mismatch: ${group.menuCode} expected=1 actual=${backend.menuType}`);
      }
      if (!backend.visible || !backend.active) {
        errors.push(`Menu group must be visible and active: ${group.menuCode}`);
      }
    }

    for (const route of appRoutes) {
      if (!route.menuCode || !yakOpsCodeSet.has(route.menuCode)) continue;
      const backend = catalogByCode.get(route.menuCode);
      if (!backend) continue;

      if (backend.menuName !== route.title || backend.sortOrder !== (route.order ?? 0)) {
        errors.push(`Menu page label/order mismatch: ${route.menuCode}`);
      }

      const parentCode = route.menuGroup
        ? navigationGroupById.get(route.menuGroup)?.menuCode ?? null
        : null;
      if (backend.parentCode !== parentCode) {
        errors.push(
          `Parent mismatch: ${route.menuCode} frontend=${parentCode ?? 'ROOT'} backend=${backend.parentCode ?? 'ROOT'}`,
        );
      }
      if (backend.routePath !== route.path) {
        errors.push(
          `Route path mismatch: ${route.menuCode} frontend=${route.path} backend=${backend.routePath ?? 'NULL'}`,
        );
      }
      if (backend.menuType !== 2) {
        errors.push(`Menu page type mismatch: ${route.menuCode} expected=2 actual=${backend.menuType}`);
      }
      if (!backend.visible || !backend.active) {
        errors.push(`Menu page must be visible and active: ${route.menuCode}`);
      }

      const declaredPermissions = routePermissionCodes(route);
      if (
        backend.requiredPermissionCode &&
        !declaredPermissions.includes(backend.requiredPermissionCode)
      ) {
        errors.push(
          `Required permission mismatch: ${route.menuCode} backend=${backend.requiredPermissionCode} frontend=${declaredPermissions.join(',') || 'PUBLIC'}`,
        );
      }
      if (
        route.mode !== 'public' &&
        !backend.requiredPermissionCode
      ) {
        errors.push(`Protected menu has no backend required permission: ${route.menuCode}`);
      }
    }

    expect(errors).toEqual([]);
  });

  it('keeps framework-owned system menus out of the Yak Ops Flyway catalog', () => {
    const duplicatedFrameworkMenus = finalCatalog
      .map((row) => row.menuCode)
      .filter((code) => frameworkCodeSet.has(code));
    const unknownSystemCodes = [
      ...navigationGroups.map((group) => group.menuCode),
      ...appRoutes.flatMap((route) => (route.menuCode ? [route.menuCode] : [])),
    ].filter(
      (code) => code.startsWith('system') && !frameworkCodeSet.has(code),
    );

    expect(duplicatedFrameworkMenus).toEqual([]);
    expect(unknownSystemCodes).toEqual([]);
  });
});
