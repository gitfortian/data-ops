/** Preserve Data Development's original policy: omit only null, undefined and the literal empty string. */
export const queryString = (query: object): string => {
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return;
    params.set(key, String(value));
  });
  const value = params.toString();
  return value ? `?${value}` : '';
};

