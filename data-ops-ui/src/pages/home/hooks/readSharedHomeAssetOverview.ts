import { homeAssetOverviewApi } from '@/services/home';

/**
 * Coalesce simultaneous Home consumers of the same authorized project scope.
 * Scope includes project, identity and grants. Only in-flight promises are shared; no results are cached.
 * Project header injection remains owned by the existing HTTP client.
 */
const inFlight = new Map<string, ReturnType<typeof homeAssetOverviewApi.overview>>();

export function readSharedHomeAssetOverview(scopeKey: string) {
  const existing = inFlight.get(scopeKey);
  if (existing) return existing;
  const request = homeAssetOverviewApi.overview();
  inFlight.set(scopeKey, request);
  void request.then(
    () => { if (inFlight.get(scopeKey) === request) inFlight.delete(scopeKey); },
    () => { if (inFlight.get(scopeKey) === request) inFlight.delete(scopeKey); },
  );
  return request;
}
