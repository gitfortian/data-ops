/**
 * Data Development query serialization keeps its original empty-string check.
 *
 * Unlike the shared HTTP query helper, an object whose String() is empty
 * is retained as a key with an empty value. Preserve this legacy contract
 * while both raw-envelope and unwrapped APIs coexist.
 */
export const queryString = (query: object): string => {
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return;
    params.set(key, String(value));
  });
  const value = params.toString();
  return value ? `?${value}` : '';
};
