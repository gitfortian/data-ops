export type StandardContextState = 'IDLE' | 'LOADING' | 'READY' | 'EMPTY' | 'FORBIDDEN' | 'UNAVAILABLE';

export const parseStandardId = (value: string | null): number | null => {
  if (!value || !/^\d+$/.test(value)) return null;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null;
};

/**
 * Navigation/read projection only. It does not replace Semantic provider/validation evidence.
 * 404 stays EMPTY inside the current Project Space so a cross-project id cannot leak existence.
 */
export const classifyStandardContextFailure = (error: unknown): Extract<StandardContextState, 'EMPTY' | 'FORBIDDEN' | 'UNAVAILABLE'> => {
  const status = (error as { response?: { status?: number } } | undefined)?.response?.status;
  if (status === 403) return 'FORBIDDEN';
  if (status === 404) return 'EMPTY';
  return 'UNAVAILABLE';
};

export const withStandardContext = (current: URLSearchParams, standardId: number | null): URLSearchParams => {
  const next = new URLSearchParams(current);
  if (standardId) next.set('standardId', String(standardId));
  else next.delete('standardId');
  return next;
};
