import { appRoutes, canAccessNavigationRoute } from '@/config/navigation';

export type HomePulseKey = 'sources' | 'models' | 'metrics' | 'assets' | 'quality' | 'running';
export type DynamicHomePulseKey = 'models' | 'metrics' | 'assets' | 'quality';

export const HOME_PULSE_ROUTES: Readonly<Record<Exclude<HomePulseKey, 'running'>, string>> = {
  sources: 'data-source',
  models: 'modeling-workspace',
  metrics: 'metric-manage',
  assets: 'data-asset-catalog',
  quality: 'data-quality-overview',
};

/** Require a true Owner count, not a numeric string or inferred zero. */
export function safeOwnerCount(value: unknown): number | null {
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= 0
    ? value : null;
}

/** The scope includes the identity and grants as well as the active project. */
export function pulseScopeKey(
  projectId: string | number | undefined,
  userId: string | number | undefined,
  permissionCodes: readonly string[] | null | undefined,
  menuCodes: readonly string[] | null | undefined,
): string | null {
  if (projectId == null || userId == null) return null;
  return JSON.stringify([
    String(projectId), String(userId),
    [...(permissionCodes ?? [])].sort(), menuCodes == null ? null : [...menuCodes].sort(),
  ]);
}

export function canReadHomePulse(
  key: Exclude<HomePulseKey, 'running'>,
  permissionCodes: readonly string[] | null | undefined,
  menuCodes: readonly string[] | null | undefined,
): boolean {
  const route = appRoutes.find((candidate) => candidate.id === HOME_PULSE_ROUTES[key]);
  return Boolean(route && canAccessNavigationRoute(route, permissionCodes, menuCodes));
}
