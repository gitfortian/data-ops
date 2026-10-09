/** A 403 is an explicit permission failure; no other failure proves an empty list. */
export type DataServiceReadIssue = 'FORBIDDEN' | 'UNAVAILABLE';
export const classifyDataServiceReadIssue = (error: unknown): DataServiceReadIssue => {
  if (!error || typeof error !== 'object') return 'UNAVAILABLE';
  const e = error as { code?: unknown; response?: { status?: unknown; code?: unknown } };
  return e.response?.status === 403 || e.code === 403 || e.response?.code === 403
    ? 'FORBIDDEN' : 'UNAVAILABLE';
};

/**
 * A 404 may be an intentionally hidden cross-Project resource. Never assert
 * actual absence merely from a detail endpoint's 404 response.
 */
export type DataServiceDetailReadIssue = DataServiceReadIssue | 'NOT_FOUND_OR_INACCESSIBLE';
export const classifyDataServiceDetailReadIssue = (error: unknown): DataServiceDetailReadIssue => {
  if (error && typeof error === 'object') {
    const candidate = error as { response?: { status?: unknown } };
    if (candidate.response?.status === 404) return 'NOT_FOUND_OR_INACCESSIBLE';
  }
  return classifyDataServiceReadIssue(error);
};
