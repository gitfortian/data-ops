/**
 * Serialize optional HTTP query parameters without changing falsy values.
 * Only null, undefined and values whose string form is empty are omitted.
 * This preserves the existing Data Source, Data Service and Realtime APIs.
 */
export const queryString = (params: object): string => {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).length > 0) {
      search.set(key, String(value));
    }
  });
  const result = search.toString();
  return result ? `?${result}` : '';
};
