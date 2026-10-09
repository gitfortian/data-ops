/** A 403 is an explicit permission failure; no other failure proves an empty list. */
export type DataServiceReadIssue = 'FORBIDDEN' | 'UNAVAILABLE';
export const classifyDataServiceReadIssue = (error: unknown): DataServiceReadIssue => {
  if (!error || typeof error !== 'object') return 'UNAVAILABLE';
  const e = error as { code?: unknown; response?: { status?: unknown; code?: unknown } };
  return e.response?.status === 403 || e.code === 403 || e.response?.code === 403
    ? 'FORBIDDEN' : 'UNAVAILABLE';
};
