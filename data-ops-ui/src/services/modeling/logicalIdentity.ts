/** Small legacy IDs remain numbers; larger Java Long identities stay exact decimal strings. */
export type LogicalId = number | string;

export function parseLogicalId(value: unknown): LogicalId | undefined {
  if (typeof value === 'number') return Number.isSafeInteger(value) && value > 0 ? value : undefined;
  if (typeof value !== 'string' || !/^[1-9]\d{0,18}$/.test(value)) return undefined;
  if (value.length === 19 && value > '9223372036854775807') return undefined;
  const small = Number(value);
  return Number.isSafeInteger(small) ? small : value;
}

export const compareLogicalIds = (left: LogicalId, right: LogicalId): number =>
  String(left).length - String(right).length || String(left).localeCompare(String(right));

/** Shield complete JSON strings before matching numeric tokens, including escaped quotes.
 * This opt-in boundary does not change the application's other response contracts. */
export function parseLogicalJson<T>(source: string): T {
  const preserved = source.replace(/"(?:\\[\s\S]|[^"\\])*"|-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?/g, token =>
    token.startsWith('"') || !/^-?\d+$/.test(token) || Number.isSafeInteger(Number(token))
      ? token : JSON.stringify(token));
  return JSON.parse(preserved);
}
