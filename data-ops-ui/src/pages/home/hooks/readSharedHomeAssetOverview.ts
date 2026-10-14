import { homeAssetOverviewApi } from '@/services/home';

/**
 * Coalesce simultaneous Home consumers of the same project-scoped Asset read.
 * Only in-flight promises are shared; no cross-project value cache is retained.
 * Project header injection remains owned by the existing HTTP client.
 */
const inFlight = new Map<string, ReturnType<typeof homeAssetOverviewApi.overview>>();

export function readSharedHomeAssetOverview(projectId: string) {
  const existing = inFlight.get(projectId);
  if (existing) return existing;
  const request = homeAssetOverviewApi.overview();
  inFlight.set(projectId, request);
  void request.then(
    () => { if (inFlight.get(projectId) === request) inFlight.delete(projectId); },
    () => { if (inFlight.get(projectId) === request) inFlight.delete(projectId); },
  );
  return request;
}
